package com.callbackdev.tweather.ui.weather

import android.content.res.Resources
import com.callbackdev.tweather.R
import com.callbackdev.tweather.data.NotificationSettings
import com.callbackdev.tweather.domain.AlertEngine
import com.callbackdev.tweather.domain.AlertKind
import com.callbackdev.tweather.domain.AlertState
import com.callbackdev.tweather.domain.model.WeatherReport
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * [WeatherReport] → the `README.md` document of the active city (Fase 10): the
 * repo metaphor at its deepest — a real repo's README is the HUMAN summary of the
 * machine content, so here it is the at-a-glance view while `weather_data.json`
 * stays the full data source. Curated summary by design: every data group except
 * the hourly detail (that stays the JSON's identity), and independent of the
 * `show_details` toggle, which governs the JSON's technical fields.
 *
 * FULLY localized, headings included — a README is prose, not code, so the
 * keys-stay-English rule doesn't apply (decided with the committente). Weather
 * values go through the same [WeatherTranslations] as everywhere else; units
 * follow [DisplayOptions] like the JSON.
 *
 * `## Status` is the repo's build badge: [AlertEngine]'s severe/precipitation
 * rules evaluated statelessly (no dedup fingerprints — the README shows what IS,
 * notifications decide what's news) render as `>` blockquote warnings.
 */
fun WeatherReport.toReadmeMarkdown(
    resources: Resources,
    translate: (String) -> String = { it },
    locale: Locale = Locale.ENGLISH,
    options: DisplayOptions = DisplayOptions()
): List<String> = buildList {
    fun s(id: Int, vararg args: Any): String = resources.getString(id, *args)
    fun temp(celsius: Double) = "${decimal1(options.temperature.convert(celsius))}${options.temperature.symbol}"
    fun tempInt(celsius: Double) = "${options.temperature.convert(celsius).roundToInt()}°"
    fun status(description: String, emoji: String) = "${translate(description)} $emoji"

    add("# ${location.city}")
    listOfNotNull(location.region, location.country)
        .joinToString(", ")
        .takeIf { it.isNotEmpty() }
        ?.let { add(it) }

    add("")
    add("## ${s(R.string.readme_h_current)}")
    add("**${temp(current.tempC)}** · ${status(current.condition.description, current.condition.emoji)}")
    add("${s(R.string.readme_feels_like)}: ${temp(current.feelsLikeC)}")

    add("")
    add("## ${s(R.string.readme_h_today)}")
    daily.firstOrNull()?.let { today ->
        add("${s(R.string.readme_high)}: ${temp(today.highC)} · ${s(R.string.readme_low)}: ${temp(today.lowC)}")
        add("${s(R.string.readme_precipitation)}: ${today.precipPct}%")
    }
    add("${s(R.string.readme_uv)}: ${current.uvIndex} (${translate(current.uvDescription)})")

    add("")
    add("## ${s(R.string.readme_h_conditions)}")
    add(
        "🌬️ ${s(R.string.readme_wind)}: " +
            "${decimal1(options.windSpeed.convert(current.wind.speedKph))} " +
            "${options.windSpeed.symbol} ${current.wind.directionCompass}"
    )
    add("💧 ${s(R.string.readme_humidity)}: ${current.humidityPct}%")
    add("🌡️ ${s(R.string.readme_pressure)}: ${decimal1(current.pressureMb)} mb")
    add("👁️ ${s(R.string.readme_visibility)}: ${decimal1(current.visibilityKm)} km")

    // A README documents what exists: sections the APIs could not fill are simply
    // absent (the JSON's in-character `null` has no README equivalent).
    if (airQuality != null || pollen != null) {
        add("")
        add("## ${s(R.string.readme_h_air)}")
        airQuality?.let { add("AQI ${it.aqiIndex} · ${translate(it.status)}") }
        pollen?.let {
            add(
                s(
                    R.string.readme_pollen,
                    translate(it.grass.label),
                    translate(it.tree.label),
                    translate(it.weed.label)
                )
            )
        }
    }

    add("")
    add("## ${s(R.string.readme_h_astronomy)}")
    add(
        "${s(R.string.readme_sunrise)}: ${astronomical.sunrise.format(ClockTime)} · " +
            "${s(R.string.readme_sunset)}: ${astronomical.sunset.format(ClockTime)}"
    )
    add("${s(R.string.readme_daylight)}: ${astronomical.daylightDuration.hhMm()}")
    add("${s(R.string.readme_moon)}: ${status(astronomical.moonPhase.label, astronomical.moonPhase.emoji)}")

    if (daily.isNotEmpty()) {
        add("")
        add("## ${s(R.string.readme_h_forecast)}")
        val header = listOf(
            s(R.string.readme_t_day), s(R.string.readme_t_high), s(R.string.readme_t_low),
            s(R.string.readme_t_status), s(R.string.readme_t_rain)
        )
        add(header.joinToString(" | ", "| ", " |"))
        add(header.joinToString(" | ", "| ", " |") { "-".repeat(it.length.coerceAtLeast(3)) })
        daily.forEach { day ->
            add(
                listOf(
                    day.date.dayOfWeek.shortName(locale),
                    tempInt(day.highC),
                    tempInt(day.lowC),
                    status(day.condition.description, day.condition.emoji),
                    "${day.precipPct}%"
                ).joinToString(" | ", "| ", " |")
            )
        }
    }

    add("")
    add("## ${s(R.string.readme_h_status)}")
    val warnings = AlertEngine.evaluate(
        report = this@toReadmeMarkdown,
        settings = NotificationSettings(
            severeWeatherAlerts = true,
            dailySummary = false,
            precipitationWarning = true
        ),
        state = AlertState(),
        now = location.localTime,
        cityKey = "readme"
    )
    if (warnings.isEmpty()) {
        add(s(R.string.readme_status_ok))
    } else {
        warnings.forEach { alert ->
            val at = alert.at?.format(ClockTime) ?: ""
            when (alert.kind) {
                AlertKind.SEVERE -> add(
                    "> " + s(
                        R.string.readme_status_severe,
                        alert.condition?.let { status(it.description, it.emoji) } ?: "",
                        at
                    )
                )
                AlertKind.PRECIPITATION -> add(
                    "> " + s(R.string.readme_status_precip, at, alert.precipPct ?: 0)
                )
                AlertKind.DAILY_SUMMARY -> Unit // disabled above; not a warning
            }
        }
    }

    add("")
    val lastSync = systemInfo.lastSync
        .atZone(
            runCatching { ZoneId.of(location.timezone) }.getOrDefault(ZoneId.systemDefault())
        )
        .format(ClockTime)
    add("*${s(R.string.readme_footer, lastSync)}*")
}

private val ClockTime = DateTimeFormatter.ofPattern("HH:mm")

/** `34.2` but `34` instead of `34.0` — a README wouldn't write trailing zeros. */
private fun decimal1(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}
