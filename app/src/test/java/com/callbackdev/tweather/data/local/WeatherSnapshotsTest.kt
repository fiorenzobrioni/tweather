package com.callbackdev.tweather.data.local

import com.callbackdev.tweather.domain.model.DailyForecast
import com.callbackdev.tweather.domain.model.WeatherCondition
import com.callbackdev.tweather.ui.weather.sampleWeatherReport
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherSnapshotsTest {

    private val sunny = WeatherCondition(0, "Sunny", "☀️")
    private val rainy = WeatherCondition(63, "Rainy", "🌧️")

    // sampleWeatherReport's local time is 2023-10-27 14:30
    private val today: LocalDate = LocalDate.of(2023, 10, 27)

    private fun reportWithDaily(vararg days: DailyForecast) =
        sampleWeatherReport().copy(daily = days.toList())

    /** The keys `history.diff` prints, in the order it prints them. */
    private val snapshotKeys = listOf(
        "location",
        "current.status",
        "current.temp_c",
        "current.feels_like_c",
        "current.humidity_pct",
        "current.pressure_mb",
        "current.uv_index",
        "current.wind_kph",
        "current.wind_dir",
        "current.precip_chance_pct",
        "air_quality.aqi",
        "astronomical.sunrise",
        "astronomical.sunset",
        "astronomical.moon_phase",
        "astronomical.daylight_duration"
    )

    @Test
    fun `the astronomical block is diffed whole, daylight included`() {
        val snapshot = WeatherSnapshots.flatten(sampleWeatherReport())

        // The four fields weather_data.json's `astronomical` object prints — the file
        // this one is a diff OF. `daylight_duration` was the one it left out.
        assertEquals(
            mapOf(
                "astronomical.sunrise" to "07:12",
                "astronomical.sunset" to "18:04",
                "astronomical.moon_phase" to "Waxing Gibbous 🌔",
                "astronomical.daylight_duration" to "10h 52m"
            ),
            snapshot.filterKeys { it.startsWith("astronomical.") }
        )
    }

    @Test
    fun `the key set does not depend on what the fetch came back with`() {
        val full = sampleWeatherReport()
        // A June day above the Arctic circle with a failed air-quality call and a
        // model that carries no precipitation probability: every nullable at once.
        val bare = full.copy(
            airQuality = null,
            astronomical = full.astronomical.copy(
                sunrise = null, sunset = null, daylightDuration = null
            ),
            current = full.current.copy(
                precipitation = full.current.precipitation.copy(chancePct = null)
            )
        )

        assertEquals(snapshotKeys, WeatherSnapshots.flatten(full).keys.toList())
        assertEquals(snapshotKeys, WeatherSnapshots.flatten(bare).keys.toList())
    }

    @Test
    fun `an absent value is written as null rather than dropped`() {
        val full = sampleWeatherReport()
        val bare = full.copy(
            airQuality = null,
            astronomical = full.astronomical.copy(
                sunrise = null, sunset = null, daylightDuration = null
            ),
            current = full.current.copy(
                precipitation = full.current.precipitation.copy(chancePct = null)
            )
        )
        val snapshot = WeatherSnapshots.flatten(bare)

        for (key in listOf(
            "current.precip_chance_pct",
            "air_quality.aqi",
            "astronomical.sunrise",
            "astronomical.sunset",
            "astronomical.daylight_duration"
        )) {
            assertEquals(key, WeatherSnapshots.NullValue, snapshot[key])
        }
        // and nothing else quietly became the word
        assertTrue(snapshot.filterValues { it == WeatherSnapshots.NullValue }.keys.size == 5)
    }

    @Test
    fun `location falls back to the country, like the commit header above it`() {
        val report = sampleWeatherReport()
        val withRegion = report.copy(location = report.location.copy(region = "NY"))
        val withoutRegion = report.copy(
            location = report.location.copy(
                city = "Singapore", region = null, country = "Singapore"
            )
        )

        assertEquals("New York, NY", WeatherSnapshots.flatten(withRegion)["location"])
        assertEquals("Singapore, Singapore", WeatherSnapshots.flatten(withoutRegion)["location"])
    }

    @Test
    fun `forecast flatten keeps only tomorrow and the day after, keyed by date`() {
        val report = reportWithDaily(
            DailyForecast(today, 21.0, 14.0, sunny, 0, 5, "Moderate ☀️"),               // today: out
            DailyForecast(today.plusDays(1), 20.0, 12.0, rainy, 85, 2, "Low"),  // tomorrow
            DailyForecast(today.plusDays(2), 16.0, 10.0, sunny, 20, 4, "Moderate ☀️"),  // day after
            DailyForecast(today.plusDays(3), 19.0, 13.0, sunny, 10, 6, "High ☀️")   // beyond: out
        )
        assertEquals(
            mapOf(
                "2023-10-28.status" to "Rainy 🌧️",
                "2023-10-28.high_c" to "20.0",
                "2023-10-28.low_c" to "12.0",
                "2023-10-28.precip_pct" to "85",
                "2023-10-29.status" to "Sunny ☀️",
                "2023-10-29.high_c" to "16.0",
                "2023-10-29.low_c" to "10.0",
                "2023-10-29.precip_pct" to "20"
            ),
            WeatherSnapshots.flattenForecast(report)
        )
    }

    @Test
    fun `horizon follows the city's local date, not the device's`() {
        // Local time 14:30 on the 27th: tomorrow is the 28th wherever the device is
        val report = reportWithDaily(
            DailyForecast(today.plusDays(1), 20.0, 12.0, sunny, 0, 5, "Moderate ☀️")
        )
        assertEquals(
            setOf("2023-10-28.status", "2023-10-28.high_c", "2023-10-28.low_c", "2023-10-28.precip_pct"),
            WeatherSnapshots.flattenForecast(report).keys
        )
    }

    /** Fase 29: the day's probability is nullable, and the forecast snapshot writes
     * the same bare `null` the current one does for a value the model did not fill. */
    @Test
    fun `a day with no probability writes null, never a zero`() {
        val report = reportWithDaily(
            DailyForecast(today.plusDays(1), 20.0, 12.0, sunny, null, 5, "Moderate ☀️")
        )
        assertEquals(
            WeatherSnapshots.NullValue,
            WeatherSnapshots.flattenForecast(report)["2023-10-28.precip_pct"]
        )
    }

    @Test
    fun `empty daily flattens to an empty map`() {
        assertEquals(
            emptyMap<String, String>(),
            WeatherSnapshots.flattenForecast(reportWithDaily())
        )
    }
}
