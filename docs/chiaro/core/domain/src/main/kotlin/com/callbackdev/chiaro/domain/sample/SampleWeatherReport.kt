package com.callbackdev.chiaro.domain.sample

import com.callbackdev.chiaro.domain.model.AirQuality
import com.callbackdev.chiaro.domain.model.Astronomical
import com.callbackdev.chiaro.domain.model.CacheStatus
import com.callbackdev.chiaro.domain.model.Coordinates
import com.callbackdev.chiaro.domain.model.CurrentConditions
import com.callbackdev.chiaro.domain.model.DailyForecast
import com.callbackdev.chiaro.domain.model.HourlyForecast
import com.callbackdev.chiaro.domain.model.Location
import com.callbackdev.chiaro.domain.model.MoonPhase
import com.callbackdev.chiaro.domain.model.PollenLevel
import com.callbackdev.chiaro.domain.model.PollenReport
import com.callbackdev.chiaro.domain.model.Pollutants
import com.callbackdev.chiaro.domain.model.Precipitation
import com.callbackdev.chiaro.domain.model.SystemInfo
import com.callbackdev.chiaro.domain.model.WeatherCondition
import com.callbackdev.chiaro.domain.model.WeatherReport
import com.callbackdev.chiaro.domain.model.Wind
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The PRD's `weather_data.json_full_sample.json` as a domain object, for `@Preview`s
 * that must match the mockups without touching the network.
 */
fun sampleWeatherReport(): WeatherReport {
    val partlyCloudy = WeatherCondition(2, "Partly Cloudy", "⛅")
    val sunny = WeatherCondition(0, "Sunny", "☀️")
    val clearNight = WeatherCondition(0, "Clear", "🌙")
    val baseDate = LocalDate.of(2023, 10, 27)
    return WeatherReport(
        location = Location(
            city = "New York",
            region = "NY",
            country = "USA",
            coordinates = Coordinates(40.7128, -74.0060),
            timezone = "America/New_York",
            localTime = LocalDateTime.of(2023, 10, 27, 14, 30)
        ),
        current = CurrentConditions(
            condition = partlyCloudy,
            tempC = 18.5,
            feelsLikeC = 17.2,
            humidityPct = 54,
            dewPointC = 9.0,
            visibilityKm = 16.1,
            pressureMb = 1015.2,
            uvIndex = 4,
            uvDescription = "Moderate ☀️",
            wind = Wind(12.5, "NW", 310, 18.0),
            precipitation = Precipitation(0.0, 10)
        ),
        airQuality = AirQuality(
            aqiIndex = 42,
            status = "Good ⚪",
            pollutants = Pollutants(8.2, 15.5, 35.1, 12.4, 2.1, 0.4)
        ),
        pollen = PollenReport(
            grass = PollenLevel.LOW,
            tree = PollenLevel.HIGH,
            weed = PollenLevel.MODERATE
        ),
        astronomical = Astronomical(
            sunrise = LocalTime.of(7, 12),
            sunset = LocalTime.of(18, 4),
            moonPhase = MoonPhase.WAXING_GIBBOUS,
            daylightDuration = Duration.ofHours(10).plusMinutes(52)
        ),
        // Cloud cover tracks the condition of each row (Fase 16a): the field is not
        // rendered anywhere, but a sample whose sunny hour is 90% overcast would be a
        // trap for the first sky verdict written against it.
        hourly = listOf(
            HourlyForecast(baseDate.atTime(15, 0), 19.0, sunny, 0, 5),
            HourlyForecast(baseDate.atTime(16, 0), 18.0, sunny, 0, 10),
            HourlyForecast(baseDate.atTime(17, 0), 17.0, partlyCloudy, 5, 45),
            HourlyForecast(baseDate.atTime(18, 0), 15.0, partlyCloudy, 10, 55),
            HourlyForecast(baseDate.atTime(19, 0), 14.0, clearNight, 0, 8)
        ),
        daily = listOf(
            DailyForecast(baseDate.plusDays(3), 20.0, 12.0, sunny, 0, 5, "Moderate ☀️"),
            DailyForecast(baseDate.plusDays(4), 18.0, 11.0, WeatherCondition(63, "Rainy", "🌧️"), 85, 2, "Low"),
            DailyForecast(baseDate.plusDays(5), 16.0, 10.0, WeatherCondition(3, "Cloudy", "☁️"), 20, 3, "Moderate ☀️"),
            DailyForecast(baseDate.plusDays(6), 19.0, 13.0, partlyCloudy, 10, 6, "High ☀️")
        ),
        systemInfo = SystemInfo(
            source = "Open-Meteo API",
            lastSync = Instant.ofEpochSecond(1_698_413_400),
            cacheStatus = CacheStatus.HIT,
            responseTimeMs = 142
        )
    )
}
