package com.callbackdev.chiaro.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

// Domain model, shaped after weather_data.json_full_sample.json. Times are java.time
// values in the location's local timezone; formatting happens at render time.

@Serializable
data class Coordinates(val lat: Double, val lon: Double)

/** A place as returned by city search; also identifies the report's subject.
 * Serializable because the saved-cities list persists as JSON in DataStore. */
@Serializable
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

/**
 * The sun and the moon over the report's day.
 *
 * **Nullable since Fase 16e**, and not for tidiness: these values come from
 * [com.callbackdev.chiaro.domain.sky.AstronomyEngine] now rather than from the
 * provider's daily block, and above the Arctic circle in June there is no sunrise to
 * have. The old type could not say that — it could only carry some other time and
 * let the reader assume it meant something.
 */
data class Astronomical(
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
    val moonPhase: MoonPhase,
    /** Sunset − sunrise; null on the days one of them does not happen. */
    val daylightDuration: Duration?
)

data class HourlyForecast(
    val time: LocalDateTime,
    val tempC: Double,
    val condition: WeatherCondition,
    val precipChancePct: Int,
    /**
     * Total cloud cover, 0..100 (Fase 16a). Open-Meteo has been sending it since Fase
     * 13c, where it repairs `weather_code`'s unreliable fog inside the mapper, but it
     * never reached the domain: nothing rendered it and nothing reasoned about it. The
     * sky module does — whether tonight's sunset is worth walking outside for is a
     * statement about this number and almost nothing else — so it stops being a local
     * variable of the fog repair and becomes part of the hour.
     *
     * Rendered nowhere still: `weather_data.json` has no `cloud_cover` key and gains
     * none. The condition emoji already says what the sky looks like.
     *
     * Non-null, and deliberately so. The first draft made it nullable to give the sky
     * verdict's `? unknown` an input that could produce it — but the mapper cannot
     * produce an hour without a cloud cover (the fog repair reads the same column one
     * step earlier and would fail first), so the null would have been a state the type
     * allows and the data never holds. Every reader would then have written `?: 0`,
     * and 0 % cloud is "clear sky": the nullable version was the shortest route to the
     * exact lie it was meant to prevent. The absence 16d actually has to handle is a
     * missing HOUR — an event past the end of [WeatherReport.hourly] — which the list
     * expresses on its own.
     */
    val cloudCoverPct: Int
)

data class DailyForecast(
    val date: LocalDate,
    val highC: Double,
    val lowC: Double,
    val condition: WeatherCondition,
    val precipPct: Int,
    /**
     * The day's PEAK UV (Open-Meteo `uv_index_max`), with [uvDescription] its label
     * — never the instant reading [CurrentConditions.uvIndex]: under a "Today"
     * heading only the maximum says anything, since at 23:52 the current index is 0
     * whatever the day was (which is exactly what the README used to print).
     */
    val uvIndexMax: Int,
    val uvDescription: String
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
