package com.callbackdev.tweather.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt

// Domain model, shaped after weather_data.json_full_sample.json. Times are java.time
// values in the location's local timezone; formatting happens at render time.

data class Coordinates(val lat: Double, val lon: Double)

/** A place as returned by city search; also identifies the report's subject. */
data class City(
    val id: Long,
    val name: String,
    val region: String?,   // Open-Meteo admin1
    val country: String?,
    val coordinates: Coordinates,
    val timezone: String?
) {
    val label: String
        get() = listOfNotNull(name, region ?: country).joinToString(", ")

    /** Stable cache/history key independent of float noise in coordinates. */
    val cacheKey: String
        get() = "${(coordinates.lat * 100).roundToInt()}:${(coordinates.lon * 100).roundToInt()}"
}

data class Location(
    val city: String,
    val region: String?,
    val country: String?,
    val coordinates: Coordinates,
    val timezone: String,
    val localTime: LocalDateTime
)

data class WeatherCondition(
    val wmoCode: Int,
    val description: String,
    val emoji: String
) {
    /** Rendered form used in the JSON UI, e.g. `"Partly Cloudy ⛅"`. */
    val label: String get() = "$description $emoji"
}

data class Wind(
    val speedKph: Double,
    val directionCompass: String,
    val degree: Int,
    val gustKph: Double
)

data class Precipitation(
    val lastHourMm: Double,
    val chancePct: Int
)

data class CurrentConditions(
    val condition: WeatherCondition,
    val tempC: Double,
    val feelsLikeC: Double,
    val humidityPct: Int,
    val dewPointC: Double,
    val visibilityKm: Double,
    val pressureMb: Double,
    val uvIndex: Int,
    val uvDescription: String,
    val wind: Wind,
    val precipitation: Precipitation
)

/** Concentrations in µg/m³ except [coMg] (mg/m³, as in the sample). */
data class Pollutants(
    val pm25: Double,
    val pm10: Double,
    val o3: Double,
    val no2: Double,
    val so2: Double,
    val coMg: Double
)

data class AirQuality(
    val aqiIndex: Int,
    val status: String,
    val pollutants: Pollutants
)

enum class PollenLevel(val label: String) {
    NONE("None"),
    LOW("Low"),
    MODERATE("Moderate"),
    HIGH("High")
}

data class PollenReport(
    val grass: PollenLevel,
    val tree: PollenLevel,
    val weed: PollenLevel
)

data class Astronomical(
    val sunrise: LocalTime,
    val sunset: LocalTime,
    val moonPhase: MoonPhase,
    val daylightDuration: Duration
)

data class HourlyForecast(
    val time: LocalDateTime,
    val tempC: Double,
    val condition: WeatherCondition,
    val precipChancePct: Int
)

data class DailyForecast(
    val date: LocalDate,
    val highC: Double,
    val lowC: Double,
    val condition: WeatherCondition,
    val precipPct: Int
)

enum class CacheStatus { HIT, MISS }

data class SystemInfo(
    val source: String,
    val lastSync: Instant,
    val cacheStatus: CacheStatus,
    val responseTimeMs: Long
)

data class WeatherReport(
    val location: Location,
    val current: CurrentConditions,
    val airQuality: AirQuality?,   // air quality API can fail independently of forecast
    val pollen: PollenReport?,     // Open-Meteo pollen coverage is Europe-only
    val astronomical: Astronomical,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
    val systemInfo: SystemInfo
)
