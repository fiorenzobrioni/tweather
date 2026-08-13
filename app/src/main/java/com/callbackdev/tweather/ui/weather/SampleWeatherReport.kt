package com.callbackdev.tweather.ui.weather

import com.callbackdev.tweather.domain.model.AirQuality
import com.callbackdev.tweather.domain.model.Astronomical
import com.callbackdev.tweather.domain.model.CacheStatus
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.CurrentConditions
import com.callbackdev.tweather.domain.model.DailyForecast
import com.callbackdev.tweather.domain.model.HourlyForecast
import com.callbackdev.tweather.domain.model.Location
import com.callbackdev.tweather.domain.model.MoonPhase
import com.callbackdev.tweather.domain.model.PollenLevel
import com.callbackdev.tweather.domain.model.PollenReport
import com.callbackdev.tweather.domain.model.Pollutants
import com.callbackdev.tweather.domain.model.Precipitation
import com.callbackdev.tweather.domain.model.SystemInfo
import com.callbackdev.tweather.domain.model.WeatherCondition
import com.callbackdev.tweather.domain.model.WeatherReport
import com.callbackdev.tweather.domain.model.Wind
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The PRD's `weather_data.json_full_sample.json` as a domain object, for `@Preview`s
 * that must match the mockups without touching the network.
 */
internal fun sampleWeatherReport(): WeatherReport {
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
        hourly = listOf(
            HourlyForecast(baseDate.atTime(15, 0), 19.0, sunny, 0),
            HourlyForecast(baseDate.atTime(16, 0), 18.0, sunny, 0),
            HourlyForecast(baseDate.atTime(17, 0), 17.0, partlyCloudy, 5),
            HourlyForecast(baseDate.atTime(18, 0), 15.0, partlyCloudy, 10),
            HourlyForecast(baseDate.atTime(19, 0), 14.0, clearNight, 0)
        ),
        daily = listOf(
            DailyForecast(baseDate.plusDays(3), 20.0, 12.0, sunny, 0),
            DailyForecast(baseDate.plusDays(4), 18.0, 11.0, WeatherCondition(63, "Rainy", "🌧️"), 85),
            DailyForecast(baseDate.plusDays(5), 16.0, 10.0, WeatherCondition(3, "Cloudy", "☁️"), 20),
            DailyForecast(baseDate.plusDays(6), 19.0, 13.0, partlyCloudy, 10)
        ),
        systemInfo = SystemInfo(
            source = "Open-Meteo API",
            lastSync = Instant.ofEpochSecond(1_698_413_400),
            cacheStatus = CacheStatus.HIT,
            responseTimeMs = 142
        )
    )
}
