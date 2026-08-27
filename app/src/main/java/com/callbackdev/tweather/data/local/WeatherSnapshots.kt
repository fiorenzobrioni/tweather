package com.callbackdev.tweather.data.local

import com.callbackdev.tweather.domain.model.WeatherReport
import java.security.MessageDigest

/**
 * Flattens a [WeatherReport] into the ordered key→value map stored with each history
 * "commit". Fase 8 renders the Logs screen by diffing consecutive snapshots, so keys
 * must stay stable and values are the strings the UI would show.
 */
object WeatherSnapshots {

    fun flatten(report: WeatherReport): Map<String, String> = buildMap {
        put("location", listOfNotNull(report.location.city, report.location.region)
            .joinToString(", "))
        put("current.status", report.current.condition.label)
        put("current.temp_c", report.current.tempC.toString())
        put("current.feels_like_c", report.current.feelsLikeC.toString())
        put("current.humidity_pct", report.current.humidityPct.toString())
        put("current.pressure_mb", report.current.pressureMb.toString())
        put("current.uv_index", report.current.uvIndex.toString())
        put("current.wind_kph", report.current.wind.speedKph.toString())
        put("current.wind_dir", report.current.wind.directionCompass)
        put("current.precip_chance_pct", report.current.precipitation.chancePct.toString())
        report.airQuality?.let { put("air_quality.aqi", it.aqiIndex.toString()) }
        put("astronomical.sunrise", report.astronomical.sunrise.toString())
        put("astronomical.sunset", report.astronomical.sunset.toString())
        put("astronomical.moon_phase", report.astronomical.moonPhase.text)
    }

    /**
     * Flattens the daily forecast for the next two target dates (tomorrow and the
     * day after, in the city's local time) into `<ISO date>.<field>` keys, e.g.
     * `2026-08-18.high_c`. Absolute dates keep consecutive snapshots aligned on the
     * same target day, so `forecast.diff` compares two predictions of the
     * same future moment. Values stay English and metric like [flatten].
     */
    fun flattenForecast(report: WeatherReport): Map<String, String> = buildMap {
        val today = report.location.localTime.toLocalDate()
        val horizon = setOf(today.plusDays(1), today.plusDays(2))
        report.daily.filter { it.date in horizon }.forEach { day ->
            val prefix = day.date.toString()
            put("$prefix.status", day.condition.label)
            put("$prefix.high_c", day.highC.toString())
            put("$prefix.low_c", day.lowC.toString())
            put("$prefix.precip_pct", day.precipPct.toString())
        }
    }

    /** Short pseudo-git hash identifying a history entry. */
    fun commitHash(vararg parts: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(parts.joinToString("|").toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(7)
}
