package com.callbackdev.tweather.domain

import com.callbackdev.tweather.data.NotificationSettings
import com.callbackdev.tweather.domain.model.HourlyForecast
import com.callbackdev.tweather.domain.model.WeatherCondition
import com.callbackdev.tweather.domain.model.WeatherReport
import com.callbackdev.tweather.ui.weather.sampleWeatherReport
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEngineTest {

    private val now: LocalDateTime = LocalDateTime.of(2023, 10, 27, 9, 0)
    private val cityKey = "4546:919"
    private val allOn = NotificationSettings(
        severeWeatherAlerts = true, dailySummary = true, precipitationWarning = true
    )

    private fun hour(plusHours: Long, wmoCode: Int = 2, precipPct: Int = 0) = HourlyForecast(
        time = now.plusHours(plusHours),
        tempC = 18.0,
        condition = WeatherCondition(wmoCode, "desc-$wmoCode", "⛅"),
        precipChancePct = precipPct
    )

    private fun report(hourly: List<HourlyForecast>): WeatherReport =
        sampleWeatherReport().copy(hourly = hourly)

    private fun evaluate(
        hourly: List<HourlyForecast>,
        settings: NotificationSettings = allOn,
        state: AlertState = AlertState(),
        at: LocalDateTime = now
    ) = AlertEngine.evaluate(report(hourly), settings, state, at, cityKey)

    // --- severe ---

    @Test
    fun `every severe code maps to a bucket and triggers`() {
        for ((code, bucket) in AlertEngine.SevereCodes) {
            val alerts = evaluate(listOf(hour(2, wmoCode = code)))
            val severe = alerts.single { it.kind == AlertKind.SEVERE }
            assertTrue(severe.fingerprint.contains(":sev:${bucket.name}:"))
        }
    }

    @Test
    fun `non-severe codes do not trigger`() {
        // rain showers 80, moderate rain 63, light snow showers 85: not severe
        val alerts = evaluate(listOf(hour(1, 80), hour(2, 63), hour(3, 85)))
        assertNull(alerts.find { it.kind == AlertKind.SEVERE })
    }

    @Test
    fun `severe respects the 12h lookahead boundary`() {
        assertTrue(evaluate(listOf(hour(12, 95))).any { it.kind == AlertKind.SEVERE })
        assertNull(evaluate(listOf(hour(13, 95))).find { it.kind == AlertKind.SEVERE })
    }

    @Test
    fun `same storm same day is deduped, new day or new bucket re-fires`() {
        val storm = listOf(hour(2, 95))
        val fingerprint = evaluate(storm).single { it.kind == AlertKind.SEVERE }.fingerprint
        // same fingerprint already notified → silent
        assertNull(
            evaluate(storm, state = AlertState(severeFingerprints = setOf(fingerprint)))
                .find { it.kind == AlertKind.SEVERE }
        )
        // different hazard class → re-fires (75 = SNOW)
        assertTrue(
            evaluate(listOf(hour(2, 75)), state = AlertState(severeFingerprints = setOf(fingerprint)))
                .any { it.kind == AlertKind.SEVERE }
        )
        // 96 is still THUNDER on the same date → same storm, still silent
        assertNull(
            evaluate(listOf(hour(3, 96)), state = AlertState(severeFingerprints = setOf(fingerprint)))
                .find { it.kind == AlertKind.SEVERE }
        )
    }

    @Test
    fun `another city's fingerprint does not silence this one, and both stay silenced together`() {
        val storm = listOf(hour(2, 95))
        val fingerprint = evaluate(storm).single { it.kind == AlertKind.SEVERE }.fingerprint
        val otherCity = fingerprint.replace(cityKey, "other-city")
        // the state holding only the OTHER city's storm must not silence this city
        assertTrue(
            evaluate(storm, state = AlertState(severeFingerprints = setOf(otherCity)))
                .any { it.kind == AlertKind.SEVERE }
        )
        // with both recorded (the alternating-cities scenario) this city stays silent
        assertNull(
            evaluate(storm, state = AlertState(severeFingerprints = setOf(otherCity, fingerprint)))
                .find { it.kind == AlertKind.SEVERE }
        )
    }

    // --- precipitation ---

    @Test
    fun `precipitation triggers at threshold within 6h`() {
        val alerts = evaluate(listOf(hour(2, precipPct = 70)))
        val precip = alerts.single { it.kind == AlertKind.PRECIPITATION }
        assertEquals(70, precip.precipPct)
    }

    @Test
    fun `precipitation below threshold or beyond 6h is silent`() {
        assertNull(
            evaluate(listOf(hour(2, precipPct = 69)))
                .find { it.kind == AlertKind.PRECIPITATION }
        )
        assertNull(
            evaluate(listOf(hour(7, precipPct = 90)))
                .find { it.kind == AlertKind.PRECIPITATION }
        )
    }

    @Test
    fun `precipitation dedups per half-day`() {
        val rain = listOf(hour(1, precipPct = 80)) // 10:00 → AM
        val fingerprint = evaluate(rain).single { it.kind == AlertKind.PRECIPITATION }.fingerprint
        assertTrue(fingerprint.endsWith(":AM"))
        assertNull(
            evaluate(rain, state = AlertState(precipFingerprints = setOf(fingerprint)))
                .find { it.kind == AlertKind.PRECIPITATION }
        )
        // afternoon rain is a new half-day bucket → re-fires
        val pmRain = listOf(hour(4, precipPct = 80)) // 13:00 → PM
        assertTrue(
            evaluate(pmRain, state = AlertState(precipFingerprints = setOf(fingerprint)))
                .any { it.kind == AlertKind.PRECIPITATION }
        )
    }

    @Test
    fun `a severe alert suppresses the precipitation warning`() {
        val alerts = evaluate(listOf(hour(2, wmoCode = 95, precipPct = 90)))
        assertTrue(alerts.any { it.kind == AlertKind.SEVERE })
        assertNull(alerts.find { it.kind == AlertKind.PRECIPITATION })
    }

    // --- daily summary ---

    @Test
    fun `summary fires once inside the morning window`() {
        val summary = evaluate(emptyList()).single { it.kind == AlertKind.DAILY_SUMMARY }
        assertEquals(now.toLocalDate().toString(), summary.fingerprint)
        // already sent today → silent
        assertNull(
            evaluate(emptyList(), state = AlertState(summaryDate = now.toLocalDate()))
                .find { it.kind == AlertKind.DAILY_SUMMARY }
        )
        // sent yesterday → fires again
        assertTrue(
            evaluate(emptyList(), state = AlertState(summaryDate = now.toLocalDate().minusDays(1)))
                .any { it.kind == AlertKind.DAILY_SUMMARY }
        )
    }

    @Test
    fun `summary respects the 06-12 window edges`() {
        fun at(hour: Int, minute: Int) = evaluate(
            emptyList(), at = now.withHour(hour).withMinute(minute)
        ).find { it.kind == AlertKind.DAILY_SUMMARY }
        assertNull(at(5, 59))
        assertTrue(at(6, 0) != null)
        assertTrue(at(12, 0) != null)
        assertNull(at(12, 1))
    }

    // --- gating ---

    @Test
    fun `each toggle gates its own rule`() {
        val stormyRainyMorning = listOf(hour(2, wmoCode = 75, precipPct = 90))
        val none = NotificationSettings(
            severeWeatherAlerts = false, dailySummary = false, precipitationWarning = false
        )
        assertTrue(evaluate(stormyRainyMorning, settings = none).isEmpty())

        val precipOnly = none.copy(precipitationWarning = true)
        // severe disabled → no suppression, the rain itself warns
        assertEquals(
            listOf(AlertKind.PRECIPITATION),
            evaluate(stormyRainyMorning, settings = precipOnly).map { it.kind }
        )
    }

    @Test
    fun `empty hourly list produces no severe or precipitation`() {
        val alerts = evaluate(emptyList(), settings = allOn.copy(dailySummary = false))
        assertTrue(alerts.isEmpty())
    }
}
