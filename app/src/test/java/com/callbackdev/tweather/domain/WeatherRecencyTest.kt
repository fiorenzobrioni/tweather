package com.callbackdev.tweather.domain

import com.callbackdev.tweather.domain.model.Astronomical
import com.callbackdev.tweather.domain.model.CacheStatus
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.CurrentConditions
import com.callbackdev.tweather.domain.model.DailyForecast
import com.callbackdev.tweather.domain.model.HourlyForecast
import com.callbackdev.tweather.domain.model.Location
import com.callbackdev.tweather.domain.model.MoonPhase
import com.callbackdev.tweather.domain.model.Precipitation
import com.callbackdev.tweather.domain.model.SystemInfo
import com.callbackdev.tweather.domain.model.WeatherCondition
import com.callbackdev.tweather.domain.model.WeatherReport
import com.callbackdev.tweather.domain.model.Wind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 17: what is left of a report the app could not refresh.
 *
 * The rule the whole offline fallback rests on — a forecast fetched three hours ago
 * opens with three hours that are over, and printing them under `## Next hours` would
 * not be old data, it would be wrong data.
 */
class WeatherRecencyTest {

    private val rome: ZoneId = ZoneId.of("Europe/Rome")
    private val fetchLocal = LocalDateTime.parse("2026-08-26T06:00")
    private val fetchedAt: Instant = fetchLocal.atZone(rome).toInstant()

    private fun report(hours: Int = 48, days: Int = 7): WeatherReport {
        val clear = WeatherCondition(0, "Clear", "☀️")
        return WeatherReport(
            location = Location(
                "Milan", "Lombardy", "Italy", Coordinates(45.46, 9.19), rome.id, fetchLocal
            ),
            current = CurrentConditions(
                clear, 20.0, 20.0, 50, 10.0, 10.0, 1013.0, 3, "Moderate",
                Wind(5.0, "N", 0, 8.0), Precipitation(0.0, 0)
            ),
            airQuality = null,
            pollen = null,
            astronomical = Astronomical(null, null, MoonPhase.FULL_MOON, null),
            hourly = List(hours) {
                HourlyForecast(fetchLocal.plusHours(it.toLong()), 20.0, clear, 0, 0)
            },
            daily = List(days) {
                DailyForecast(
                    LocalDate.parse("2026-08-26").plusDays(it.toLong()),
                    28.0, 18.0, clear, 0, 5, "Moderate ☀️"
                )
            },
            systemInfo = SystemInfo("Open-Meteo API", fetchedAt, CacheStatus.HIT, 100)
        )
    }

    private fun at(local: String): Instant =
        LocalDateTime.parse(local).atZone(rome).toInstant()

    @Test
    fun `a report fetched this hour is returned untouched`() {
        val report = report()
        // Same instance, not just an equal one: the trim runs on every load, and a
        // fresh fetch must not pay a copy of two lists for nothing.
        assertSame(report, WeatherRecency.trim(report, at("2026-08-26T06:59")))
    }

    @Test
    fun `elapsed hours go, the hour we are in stays`() {
        val trimmed = WeatherRecency.trim(report(), at("2026-08-26T09:30"))
        assertEquals(LocalDateTime.parse("2026-08-26T09:00"), trimmed.hourly.first().time)
        // hourly[0] is "the hour we are in" by contract — both renderers drop it
        // themselves and read `current_conditions` for it.
        assertEquals(45, trimmed.hourly.size)
        assertEquals(LocalDate.parse("2026-08-26"), trimmed.daily.first().date)
    }

    @Test
    fun `yesterday's fetch still holds today, and says so from today`() {
        val trimmed = WeatherRecency.trim(report(), at("2026-08-27T09:30"))
        assertEquals(LocalDateTime.parse("2026-08-27T09:00"), trimmed.hourly.first().time)
        // The day the document calls "Today" is today — `## Today` reads daily.first()
        assertEquals(LocalDate.parse("2026-08-27"), trimmed.daily.first().date)
        assertEquals(6, trimmed.daily.size)
    }

    @Test
    fun `the timezone is the city's, not the device's`() {
        // 23:30 UTC on the 26th is already 01:30 on the 27th in Rome: the day that
        // has ended is the city's day, which is the one the document prints.
        val trimmed = WeatherRecency.trim(report(), Instant.parse("2026-08-26T23:30:00Z"))
        assertEquals(LocalDate.parse("2026-08-27"), trimmed.daily.first().date)
    }

    @Test
    fun `a report still reaching the present covers now, one past its horizon does not`() {
        assertTrue(WeatherRecency.coversNow(report(), at("2026-08-27T09:30")))
        // 48 hourly slots from 06:00 on the 26th end at 05:00 on the 28th
        assertFalse(WeatherRecency.coversNow(report(), at("2026-08-28T06:00")))
    }

    @Test
    fun `a report whose days are over does not cover now either`() {
        // The hours would still reach, the days would not: a document with no `## Today`
        // is not a weather report, and both halves have to be there.
        assertFalse(WeatherRecency.coversNow(report(hours = 48, days = 1), at("2026-08-27T09:30")))
    }
}
