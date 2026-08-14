package com.callbackdev.tweather.ui.weather

import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.data.WindSpeedUnit
import com.callbackdev.tweather.domain.model.WeatherReport
import java.time.DayOfWeek
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * How `weather_data.json` renders the report. [showDetails] false (the app default)
 * hides the technical fields agreed with the user: region/country/coordinates/
 * timezone (location keeps only city + local_time), dew point, pressure, visibility,
 * wind degree/gusts and the pollutant breakdown (AQI index + status stay). Units
 * change VALUES AND KEYS — a JSON file wouldn't lie about its units (`temp_f`,
 * `speed_mph`).
 */
data class DisplayOptions(
    val showDetails: Boolean = true,
    val temperature: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windSpeed: WindSpeedUnit = WindSpeedUnit.KMH
)

/**
 * [WeatherReport] → the `weather_data.json` document the main screen renders. Field
 * names, value formats and section order follow `weather_data.json_full_sample.json`
 * (PRD): location, current_conditions, air_quality, pollen_report, astronomical,
 * hourly_forecast, daily_forecast, system_info. Sections Open-Meteo could not fill
 * (air quality down, pollen outside Europe) render as `null` — this is a fake source
 * file, so a null field is more in-character than a missing one.
 *
 * Keys stay English (they're code); [translate] localizes the DATA values (condition
 * descriptions, UV/AQI/pollen/moon labels) and [locale] the day names. Defaults are
 * the domain's canonical English.
 */
fun WeatherReport.toDisplayJson(
    translate: (String) -> String = { it },
    locale: Locale = Locale.ENGLISH,
    options: DisplayOptions = DisplayOptions()
): JsonObject = buildJsonObject {
    val details = options.showDetails
    val tempKey = options.temperature.keySuffix
    fun temp(celsius: Double) = options.temperature.convert(celsius)
    val speedKey = options.windSpeed.keySuffix
    fun speed(kph: Double) = options.windSpeed.convert(kph)

    putJsonObject("location") {
        put("city", location.city)
        if (details) {
            location.region?.let { put("region", it) }
            location.country?.let { put("country", it) }
            putJsonObject("coordinates") {
                put("lat", location.coordinates.lat)
                put("lon", location.coordinates.lon)
            }
            put("timezone", location.timezone)
        }
        put("local_time", location.localTime.format(LocalTimeStamp))
    }
    putJsonObject("current_conditions") {
        put("status", "${translate(current.condition.description)} ${current.condition.emoji}")
        putDecimal("temp_$tempKey", temp(current.tempC))
        putDecimal("feels_like_$tempKey", temp(current.feelsLikeC))
        put("humidity_pct", current.humidityPct)
        if (details) {
            putDecimal("dew_point_$tempKey", temp(current.dewPointC))
            putDecimal("visibility_km", current.visibilityKm)
            putDecimal("pressure_mb", current.pressureMb)
        }
        put("uv_index", current.uvIndex)
        put("uv_description", translate(current.uvDescription))
        putJsonObject("wind") {
            putDecimal("speed_$speedKey", speed(current.wind.speedKph))
            put("direction", current.wind.directionCompass)
            if (details) {
                put("degree", current.wind.degree)
                putDecimal("gust_$speedKey", speed(current.wind.gustKph))
            }
        }
        putJsonObject("precipitation") {
            putDecimal("last_hour_mm", current.precipitation.lastHourMm)
            put("chance_pct", current.precipitation.chancePct)
        }
    }
    // NOTE: no `?.let { putJsonObject(...) } ?: put(key, JsonNull)` here — the
    // builder's put* functions return the key's PREVIOUS value (null), so the elvis
    // branch would always run and overwrite the section just written.
    val aq = airQuality
    if (aq != null) {
        putJsonObject("air_quality") {
            put("aqi_index", aq.aqiIndex)
            put("status", translate(aq.status))
            if (details) {
                putJsonObject("pollutants") {
                    putDecimal("pm2_5", aq.pollutants.pm25)
                    putDecimal("pm10", aq.pollutants.pm10)
                    putDecimal("o3", aq.pollutants.o3)
                    putDecimal("no2", aq.pollutants.no2)
                    putDecimal("so2", aq.pollutants.so2)
                    putDecimal("co", aq.pollutants.coMg)
                }
            }
        }
    } else {
        put("air_quality", JsonNull)
    }
    val p = pollen
    if (p != null) {
        putJsonObject("pollen_report") {
            put("grass", translate(p.grass.label))
            put("tree", translate(p.tree.label))
            put("weed", translate(p.weed.label))
        }
    } else {
        put("pollen_report", JsonNull)
    }
    putJsonObject("astronomical") {
        put("sunrise", astronomical.sunrise.format(ClockTime))
        put("sunset", astronomical.sunset.format(ClockTime))
        put(
            "moon_phase",
            "${translate(astronomical.moonPhase.label)} ${astronomical.moonPhase.emoji}"
        )
        put("daylight_duration", astronomical.daylightDuration.hhMm())
    }
    putJsonArray("hourly_forecast") {
        hourly.forEach { h ->
            add(buildJsonObject {
                put("time", h.time.format(ClockTime))
                put("temp_$tempKey", temp(h.tempC).roundToInt())
                put("status", "${translate(h.condition.description)} ${h.condition.emoji}")
                put("precip_chance", h.precipChancePct)
            })
        }
    }
    putJsonArray("daily_forecast") {
        daily.forEach { d ->
            add(buildJsonObject {
                put("day", d.date.dayOfWeek.shortName(locale))
                put("high", temp(d.highC).roundToInt())
                put("low", temp(d.lowC).roundToInt())
                put("status", "${translate(d.condition.description)} ${d.condition.emoji}")
                put("precip_pct", d.precipPct)
            })
        }
    }
    putJsonObject("system_info") {
        put("source", systemInfo.source)
        put("last_sync", systemInfo.lastSync.epochSecond.toString())
        put("cache_status", systemInfo.cacheStatus.name)
        put("response_time_ms", systemInfo.responseTimeMs)
    }
}

private val TemperatureUnit.keySuffix: String
    get() = if (this == TemperatureUnit.CELSIUS) "c" else "f"

// internal: AlertNotifier renders temperatures in the user's unit too
internal fun TemperatureUnit.convert(celsius: Double): Double =
    if (this == TemperatureUnit.CELSIUS) celsius else celsius * 9 / 5 + 32

internal val TemperatureUnit.symbol: String
    get() = if (this == TemperatureUnit.CELSIUS) "°C" else "°F"

private val WindSpeedUnit.keySuffix: String
    get() = if (this == WindSpeedUnit.KMH) "kph" else "mph"

// internal: the home widget renders wind in the user's unit too
internal fun WindSpeedUnit.convert(kph: Double): Double =
    if (this == WindSpeedUnit.KMH) kph else kph / 1.609344

internal val WindSpeedUnit.symbol: String
    get() = if (this == WindSpeedUnit.KMH) "km/h" else "mph"

private val LocalTimeStamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val ClockTime = DateTimeFormatter.ofPattern("HH:mm")

/** Sample formats durations as `"10h 52m"`. */
private fun Duration.hhMm(): String = "${toHours()}h ${toMinutesPart()}m"

/** Capitalized short day name (`Mon`, `Lun`) — Italian locales give lowercase. */
private fun DayOfWeek.shortName(locale: Locale): String =
    getDisplayName(TextStyle.SHORT, locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

/** One-decimal numbers like the sample's `18.5` (raw doubles carry float noise). */
private fun JsonObjectBuilder.putDecimal(key: String, value: Double) {
    put(key, (value * 10).roundToInt() / 10.0)
}
