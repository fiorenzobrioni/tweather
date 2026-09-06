package com.callbackdev.tweather.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.data.UnitSettings
import com.callbackdev.tweather.data.WindSpeedUnit
import com.callbackdev.tweather.domain.Alert
import com.callbackdev.tweather.domain.AlertKind
import com.callbackdev.tweather.domain.model.AirQuality
import com.callbackdev.tweather.domain.model.Astronomical
import com.callbackdev.tweather.domain.model.CacheStatus
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.CurrentConditions
import com.callbackdev.tweather.domain.model.DailyForecast
import com.callbackdev.tweather.domain.model.HourlyForecast
import com.callbackdev.tweather.domain.model.Location
import com.callbackdev.tweather.domain.model.MoonPhase
import com.callbackdev.tweather.domain.model.Pollutants
import com.callbackdev.tweather.domain.model.Precipitation
import com.callbackdev.tweather.domain.model.SystemInfo
import com.callbackdev.tweather.domain.model.WeatherCondition
import com.callbackdev.tweather.domain.model.WeatherReport
import com.callbackdev.tweather.domain.model.Wind
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AlertNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)

    private val thunder = WeatherCondition(95, "Thunderstorm", "⛈️")
    private val showers = WeatherCondition(80, "Rain Showers", "🌦️")
    private val partlyCloudy = WeatherCondition(2, "Partly Cloudy", "⛅")
    private val day = LocalDate.of(2023, 10, 27)

    private val severe = Alert(
        kind = AlertKind.SEVERE,
        fingerprint = "fp",
        cityLabel = "Milan",
        condition = thunder,
        at = day.atTime(18, 0),
        precipPct = 85
    )

    private val summary = Alert(
        kind = AlertKind.DAILY_SUMMARY,
        fingerprint = "2023-10-27",
        cityLabel = "Milan",
        condition = partlyCloudy,
        precipPct = 20,
        highC = 24.0,
        lowC = 15.0
    )

    /** A storm from 17:00 to 19:00, worst at 18:00, with calm hours either side. */
    private val hours = listOf(
        HourlyForecast(day.atTime(16, 0), 20.0, partlyCloudy, 20, 40),
        HourlyForecast(day.atTime(17, 0), 19.0, thunder, 70, 95),
        HourlyForecast(day.atTime(18, 0), 18.0, thunder, 85, 100),
        HourlyForecast(day.atTime(19, 0), 17.0, thunder, 60, 90),
        HourlyForecast(day.atTime(20, 0), 16.0, partlyCloudy, 30, 50)
    )

    private fun report(
        hourly: List<HourlyForecast> = hours,
        airQuality: AirQuality? = AirQuality(42, "Good ⚪", Pollutants(8.2, 15.5, 35.1, 12.4, 2.1, 0.4))
    ) = WeatherReport(
        location = Location(
            city = "Milan",
            region = null,
            country = "Italy",
            coordinates = Coordinates(45.46, 9.19),
            timezone = "Europe/Rome",
            localTime = day.atTime(16, 20)
        ),
        current = CurrentConditions(
            condition = partlyCloudy,
            tempC = 21.0,
            feelsLikeC = 20.0,
            humidityPct = 54,
            dewPointC = 9.0,
            visibilityKm = 16.1,
            pressureMb = 1015.2,
            uvIndex = 4,
            uvDescription = "Moderate ☀️",
            wind = Wind(12.0, "NE", 45, 20.0),
            precipitation = Precipitation(0.0, 20)
        ),
        airQuality = airQuality,
        pollen = null,
        astronomical = Astronomical(
            sunrise = LocalTime.of(7, 12),
            sunset = LocalTime.of(18, 4),
            moonPhase = MoonPhase.WAXING_GIBBOUS,
            daylightDuration = Duration.ofHours(10).plusMinutes(52)
        ),
        hourly = hourly,
        daily = listOf(DailyForecast(day, 24.0, 15.0, partlyCloudy, 20, 5, "Moderate ☀️")),
        systemInfo = SystemInfo("Open-Meteo API", Instant.ofEpochSecond(1_698_413_400), CacheStatus.MISS, 142)
    )

    private fun notify(alert: Alert, units: UnitSettings = UnitSettings()) =
        AlertNotifier.notify(context, alert, report(), units)

    private fun bigText() = shadowOf(manager).allNotifications.single()
        .extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()

    @Test
    fun `severe alert posts localized title plus a pretty-printed json body`() {
        assertTrue(notify(severe))
        val notifications = shadowOf(manager).allNotifications
        assertEquals(1, notifications.size)
        assertTrue(notifications[0].extras.getString(Notification.EXTRA_TITLE)!!.contains("Milan"))
        assertEquals(
            """
            $ tweather --alert severe
            {
              "time": "18:00",
              "status": "Thunderstorm ⛈️",
              "wmo_code": 95,
              "precip_chance": 85,
              "window": {
                "from": "17:00",
                "to": "19:00",
                "peak_precip_chance": 85,
                "peak_at": "18:00",
                "low_c": 17,
                "high_c": 19
              },
              "current_conditions": {
                "temp_c": 21,
                "status": "Partly Cloudy ⛅",
                "wind": {
                  "speed_kph": 12,
                  "direction": "NE"
                }
              }
            }
            """.trimIndent(),
            bigText()
        )
    }

    @Test
    fun `collapsed text folds the headline object onto one line, children hidden`() {
        notify(severe)
        val collapsed = shadowOf(manager).allNotifications.single()
            .extras.getString(Notification.EXTRA_TEXT)
        assertEquals(
            """{ "time": "18:00", "status": "Thunderstorm ⛈️", "wmo_code": 95, """ +
                """"precip_chance": 85 }""",
            collapsed
        )
        // The fold is a fold: what the expanded body adds is not on this line at all.
        assertFalse(collapsed!!.contains("window"))
        assertFalse(collapsed.contains("current_conditions"))
    }

    @Test
    fun `expanding says strictly more than the collapsed line`() {
        notify(severe)
        val posted = shadowOf(manager).allNotifications.single().extras
        val collapsed = posted.getString(Notification.EXTRA_TEXT)!!
        val expanded = posted.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        // Every headline field survives the unfold…
        listOf(""""time": "18:00"""", """"wmo_code": 95""", """"precip_chance": 85""")
            .forEach { assertTrue(it, expanded.contains(it)) }
        // …and the unfold is worth the gesture: this was the whole complaint.
        assertTrue(expanded.lines().size > collapsed.lines().size + 4)
    }

    @Test
    fun `an open-ended run says null rather than naming an end it never saw`() {
        AlertNotifier.notify(
            context,
            severe.copy(at = day.atTime(19, 0)),
            report(hourly = hours.dropLast(1)),
            UnitSettings()
        )
        assertTrue(bigText().contains(""""to": null"""))
        assertTrue(bigText().contains(""""from": "17:00""""))
    }

    @Test
    fun `a window the report no longer covers is simply not written`() {
        // A cached report can outlive the alert built from it: 18:00 is gone from
        // these hours, so there is no run to describe and no run is claimed.
        AlertNotifier.notify(
            context,
            severe,
            report(hourly = hours.filter { it.time.hour >= 19 }),
            UnitSettings()
        )
        val big = bigText()
        assertFalse(big.contains("window"))
        assertTrue(big.contains("current_conditions"))
    }

    @Test
    @Config(qualifiers = "it")
    fun `data values follow the device language, keys and command do not`() {
        notify(summary)
        assertEquals(
            """
            $ tweather --daily
            {
              "status": "Parzialmente nuvoloso ⛅",
              "high_c": 24,
              "low_c": 15,
              "precip_pct": 20,
              "current_conditions": {
                "temp_c": 21,
                "status": "Parzialmente nuvoloso ⛅",
                "wind": {
                  "speed_kph": 12,
                  "direction": "NE"
                }
              },
              "astronomical": {
                "sunrise": "07:12",
                "sunset": "18:04"
              },
              "uv_index_max": 5,
              "uv_description": "Moderato ☀️",
              "air_quality": {
                "aqi_index": 42,
                "status": "Buona ⚪"
              }
            }
            """.trimIndent(),
            bigText()
        )
    }

    @Test
    fun `the summary drops the air quality it does not have`() {
        AlertNotifier.notify(context, summary, report(airQuality = null), UnitSettings())
        assertFalse(bigText().contains("air_quality"))
    }

    @Test
    fun `daily summary converts temperatures to the user unit and says so in the key`() {
        notify(summary, UnitSettings(temperature = TemperatureUnit.FAHRENHEIT))
        val big = bigText()
        assertTrue(big.contains(""""high_f": 75"""))
        assertTrue(big.contains(""""low_f": 59"""))
        assertTrue(big.contains(""""temp_f": 70"""))
    }

    @Test
    fun `wind follows the user unit too, key included`() {
        notify(severe, UnitSettings(windSpeed = WindSpeedUnit.MPH))
        assertTrue(bigText().contains(""""speed_mph": 7"""))
    }

    @Test
    fun `precipitation warning carries the rain type it is warning about`() {
        AlertNotifier.notify(
            context,
            Alert(
                kind = AlertKind.PRECIPITATION, fingerprint = "c", cityLabel = "Milan",
                condition = showers, at = day.atTime(17, 0), precipPct = 70
            ),
            report(),
            UnitSettings()
        )
        // The rain window is the run at or above the engine's own threshold — 17:00
        // and 18:00, not the whole storm, whose 19:00 hour is under it.
        assertEquals(
            """
            $ tweather --alert precip
            {
              "time": "17:00",
              "status": "Rain Showers 🌦️",
              "precip_chance": 70,
              "window": {
                "from": "17:00",
                "to": "18:00",
                "peak_precip_chance": 85,
                "peak_at": "18:00",
                "low_c": 18,
                "high_c": 19
              },
              "current_conditions": {
                "temp_c": 21,
                "status": "Partly Cloudy ⛅",
                "wind": {
                  "speed_kph": 12,
                  "direction": "NE"
                }
              }
            }
            """.trimIndent(),
            bigText()
        )
    }

    @Test
    fun `same kind overwrites, different kinds coexist`() {
        notify(severe)
        notify(severe.copy(fingerprint = "b"))
        AlertNotifier.notify(
            context,
            Alert(
                kind = AlertKind.PRECIPITATION, fingerprint = "c", cityLabel = "Milan",
                at = day.atTime(17, 0), precipPct = 70
            ),
            report(),
            UnitSettings()
        )
        assertEquals(2, shadowOf(manager).allNotifications.size)
    }
}
