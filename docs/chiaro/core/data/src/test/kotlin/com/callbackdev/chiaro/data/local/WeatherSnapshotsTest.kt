package com.callbackdev.chiaro.data.local

import com.callbackdev.chiaro.domain.model.DailyForecast
import com.callbackdev.chiaro.domain.model.WeatherCondition
import com.callbackdev.chiaro.domain.sample.sampleWeatherReport
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherSnapshotsTest {

    private val sunny = WeatherCondition(0, "Sunny", "☀️")
    private val rainy = WeatherCondition(63, "Rainy", "🌧️")

    // sampleWeatherReport's local time is 2023-10-27 14:30
    private val today: LocalDate = LocalDate.of(2023, 10, 27)

    private fun reportWithDaily(vararg days: DailyForecast) =
        sampleWeatherReport().copy(daily = days.toList())

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

    @Test
    fun `empty daily flattens to an empty map`() {
        assertEquals(
            emptyMap<String, String>(),
            WeatherSnapshots.flattenForecast(reportWithDaily())
        )
    }
}
