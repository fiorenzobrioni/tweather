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
 * stays the full data source. Curated summary by design, and independent of the
 * `show_details` toggle, which governs the JSON's technical fields: the hourly
 * forecast is here too (Fase 11c — it is the most consulted forecast there is, and
 * what the JSON was found to get wrong was its POSITION, not its presence),
 * compressed to [HourlyRows] hours starting from the NEXT full hour (Fase 11e —
 * the current hour is `## Current`'s job, rain probability included). The full
 * 24-hour run, technical fields included, stays the JSON's.
 *
 * Both tables are [markdownTable]s: columns padded to their widest cell, numbers
 * right-aligned, exactly one emoji per cell against the cell's left edge with the
 * description after it, and the status column LAST (Fase 11d) — see the table
 * comment below for why.
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
    // Rain probability of the CURRENT hour (the mapper reads it off the same hourly
    // slot the table's dropped first row came from — see below), on the feels-like
    // line rather than its own: Current stays a two-line glance. Same table
    // vocabulary (`readme_t_rain`), so "Pioggia" means one thing across the page.
    add(
        "${s(R.string.readme_feels_like)}: ${temp(current.feelsLikeC)} · " +
            "${s(R.string.readme_t_rain)}: ${current.precipitation.chancePct}%"
    )

    add("")
    add("## ${s(R.string.readme_h_today)}")
    daily.firstOrNull()?.let { today ->
        add("${s(R.string.readme_high)}: ${temp(today.highC)} · ${s(R.string.readme_low)}: ${temp(today.lowC)}")
        add("${s(R.string.readme_precipitation)}: ${today.precipPct}%")
        // Today's MAXIMUM, like the two lines above it — this used to print
        // `current.uvIndex`, the instant reading, which under this heading read as a
        // daily figure and was 0 all evening (committente's report, Aug 2026: "UV 0
        // (Basso)" at 23:52 on a day whose max was 2.1). The instant value keeps its
        // place in the JSON's `current_conditions`, where it is what it says it is.
        add("${s(R.string.readme_uv)}: ${today.uvIndexMax} (${translate(today.uvDescription)})")
    }

    // Both forecasts sit straight after Today, before every detail section: they are
    // what a weather app is opened for, and the hours read into the days without a
    // page of conditions and pollen in between. Both tables share one column plan
    // (Fase 11d): the status — emoji first, description after — sits LAST, so the
    // numeric columns never pan off-screen and a description that outgrows the
    // display just clips ("Temporale con grand…" still reads); leading with the
    // emoji keeps the sky legible at a glance even then. Until 11d the hourly table
    // had no description at all — the committente found the emoji alone ambiguous,
    // and the repetition down the rows is itself information: it shows WHEN the
    // weather turns.
    //
    // The first hourly slot is dropped (Fase 11e): it is the hour we are IN, already
    // told by `## Current` right above — at 08:44 a "08:00" row is a duplicate of
    // the section AND mostly elapsed. The table reads +1h..+[HourlyRows]h, seamless
    // with Current covering now.
    val nextHours = hourly.drop(1).take(HourlyRows)
    if (nextHours.isNotEmpty()) {
        add("")
        add("## ${s(R.string.readme_h_hourly)}")
        addAll(
            markdownTable(
                columns = listOf(
                    TableColumn(s(R.string.readme_t_hour)),
                    TableColumn(s(R.string.readme_t_temp), TableAlign.RIGHT),
                    TableColumn(s(R.string.readme_t_rain), TableAlign.RIGHT),
                    TableColumn(s(R.string.readme_t_status))
                ),
                rows = nextHours.map { hour ->
                    listOf(
                        TableCell(hour.time.format(ClockTime)),
                        TableCell(tempInt(hour.tempC)),
                        TableCell("${hour.precipChancePct}%"),
                        TableCell(translate(hour.condition.description), hour.condition.emoji)
                    )
                }
            )
        )
    }

    if (daily.isNotEmpty()) {
        add("")
        add("## ${s(R.string.readme_h_forecast)}")
        addAll(
            markdownTable(
                columns = listOf(
                    TableColumn(s(R.string.readme_t_day)),
                    TableColumn(s(R.string.readme_t_high), TableAlign.RIGHT),
                    TableColumn(s(R.string.readme_t_low), TableAlign.RIGHT),
                    TableColumn(s(R.string.readme_t_rain), TableAlign.RIGHT),
                    TableColumn(s(R.string.readme_t_status))
                ),
                rows = daily.map { day ->
                    listOf(
                        TableCell(day.date.dayOfWeek.shortName(locale)),
                        TableCell(tempInt(day.highC)),
                        TableCell(tempInt(day.lowC)),
                        TableCell("${day.precipPct}%"),
                        TableCell(translate(day.condition.description), day.condition.emoji)
                    )
                }
            )
        )
    }

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

/**
 * Hours in `## Next hours`, counted from the hour AFTER the current one. Fourteen is
 * the committente's call (Fase 11e): a morning glance reaches the evening — at 08:00
 * the table runs to 22:00. It knowingly gives up the 12h symmetry with the
 * AlertEngine/`next_12h.*` horizon that 11c argued for; the committente's counter
 * was decisive: the JSON tab shows 24 hours and never had that symmetry either.
 * Past the window the daily table takes over: no sampling, no lying about which
 * hour it rains.
 */
private const val HourlyRows = 14

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
