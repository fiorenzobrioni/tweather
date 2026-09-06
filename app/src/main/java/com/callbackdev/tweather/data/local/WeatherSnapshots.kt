package com.callbackdev.tweather.data.local

import com.callbackdev.tweather.domain.model.WeatherReport
import com.callbackdev.tweather.domain.model.hhMm
import java.security.MessageDigest

/**
 * Flattens a [WeatherReport] into the ordered key→value map stored with each history
 * "commit". Fase 8 renders the Logs screen by diffing consecutive snapshots, so keys
 * must stay stable and values are the strings the UI would show.
 */
object WeatherSnapshots {

    /**
     * What a key the providers could not fill writes — the same `null`
     * `weather_data.json` prints in its place, and for the same reason: `history.diff`
     * is a diff OF that document, so a value that is absent has to be a LINE saying so
     * rather than a line that quietly leaves the file.
     *
     * It is written on purpose, which is the whole point. Three of these keys used to
     * be `nullable.toString()` — Kotlin's `Any?.toString()` answers `"null"` for them,
     * so the word was already reaching the file, but as an accident: the diff printed
     * it QUOTED, `"astronomical.sunrise": "null"`, as though the sun had risen at a
     * time spelled n-u-l-l, and the widget read the same string back and rendered
     * `Rain: null%` on a home screen. Named and rendered bare, it says the one true
     * thing instead: there is no sunrise here today.
     *
     * It also makes the key set FIXED. `air_quality.aqi` was written only when the air
     * quality call had succeeded, so a failed one dropped the key — and [SnapshotDiff]
     * trails a vanished key at the end of the diff, past `astronomical.*`, then puts it
     * back mid-file when the call recovers. A section that changes value in place is a
     * diff; a section that leaves the file and comes back is noise.
     */
    const val NullValue = "null"

    fun flatten(report: WeatherReport): Map<String, String> = buildMap {
        // `region ?: country`, exactly like City.label — which is what the commit
        // header prints two lines above this one. A place with no admin1 (Singapore,
        // Monaco, the Vatican) had the header saying "Singapore, Singapore" and the
        // body of its own diff saying "Singapore".
        put("location", listOfNotNull(
            report.location.city,
            report.location.region ?: report.location.country
        ).joinToString(", "))
        put("current.status", report.current.condition.label)
        put("current.temp_c", report.current.tempC.toString())
        put("current.feels_like_c", report.current.feelsLikeC.toString())
        put("current.humidity_pct", report.current.humidityPct.toString())
        put("current.pressure_mb", report.current.pressureMb.toString())
        put("current.uv_index", report.current.uvIndex.toString())
        put("current.wind_kph", report.current.wind.speedKph.toString())
        put("current.wind_dir", report.current.wind.directionCompass)
        put("current.precip_chance_pct", report.current.precipitation.chancePct.orNull())
        put("air_quality.aqi", report.airQuality?.aqiIndex.orNull())
        put("astronomical.sunrise", report.astronomical.sunrise.orNull())
        put("astronomical.sunset", report.astronomical.sunset.orNull())
        put("astronomical.moon_phase", report.astronomical.moonPhase.text)
        // The fourth field of `weather_data.json`'s astronomical block, and the one
        // this file was missing: sunrise and sunset were diffed and the span between
        // them was not, so the day getting three minutes shorter was a fact the reader
        // had to subtract for themselves. Formatted like the JSON prints it
        // (`"10h 52m"`), which also truncates away the sub-second precision the engine
        // answers with — so it changes once a day, at the same time its two ends do.
        put(
            "astronomical.daylight_duration",
            report.astronomical.daylightDuration?.hhMm().orNull()
        )
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

    /**
     * The absent value, spelled out rather than left to `Any?.toString()` — which
     * produces the same four characters by accident and reads, at the call site, as
     * if a value were always there.
     */
    private fun Any?.orNull(): String = if (this == null) NullValue else toString()
}
