package com.callbackdev.tweather.ui.weather

import android.content.res.Resources
import com.callbackdev.tweather.R
import com.callbackdev.tweather.data.NotificationSettings
import com.callbackdev.tweather.domain.AlertEngine
import com.callbackdev.tweather.domain.AlertKind
import com.callbackdev.tweather.domain.AlertState
import com.callbackdev.tweather.domain.model.WeatherReport
import com.callbackdev.tweather.domain.sky.SkyVerdict
import com.callbackdev.tweather.domain.sky.SkyVerdictKind
import com.callbackdev.tweather.domain.sky.SkyVerdictNote
import com.callbackdev.tweather.ui.sky.SkyJobNames
import com.callbackdev.tweather.ui.sky.SkySummary
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
 * notifications decide what's news) render as `>` blockquote warnings. It reads third,
 * right after Current and Today (Fase 13d) — a badge below the fold is not a badge.
 * Every one of its lines is a SENTENCE (Fase 16g), the sky's included: see
 * [skyVerdictProse] and [SkyJobNames].
 */
fun WeatherReport.toReadmeMarkdown(
    resources: Resources,
    translate: (String) -> String = { it },
    locale: Locale = Locale.ENGLISH,
    options: DisplayOptions = DisplayOptions(),
    /**
     * The sky module's contribution (Fase 16e), or null when `sky.enabled` is false.
     * Null does not blank the section — `## Astronomy` is today's sky either way; it
     * only drops the lines the module added to it and the `## Status` line that
     * reports the reader's own subscriptions.
     */
    sky: SkySummary? = null,
    /**
     * The moment the document describes, in the city's own time. Defaults to the
     * report's own `location.localTime` — the clock of the fetch that produced it,
     * which is the right answer for data that just landed and is what every caller
     * wanted until Fase 17.
     *
     * A document recovered from a fetch the app could not refresh passes the REAL
     * now: `## Status` asks "is anything coming", and asking it with a clock three
     * hours slow searches a window that has closed and answers "everything looks
     * good" every time. A badge that goes quiet because it is looking at the wrong
     * hours is the one failure mode this section must not have.
     */
    now: java.time.LocalDateTime = location.localTime
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

    // Status sits HERE, third of the curated sections, not at the foot of the document
    // (Fase 13d): it is the only actionable line on the page, and at the bottom it landed
    // on line 57 of 58 — a thunderstorm warning two screens below the fold, with the moon
    // phase above it. A real README puts its build badge under the H1 and this section is
    // exactly that badge; it stops one notch short of the top only because the page must
    // answer "how warm is it" before "is anything wrong", and Current + Today cost 8 lines
    // between them. It stays in a FIXED place whether or not there is anything to warn
    // about: a section that moves with its content is harder to learn than one that is
    // early, and "Everything looks good." earns its two lines the way a green badge does.
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
        now = now,
        cityKey = "readme"
    )
    // The sky's one line in this document's one badge (Fase 16e). It is raised only
    // for a job the user actually subscribed to, so `## Status` reports THEIR file
    // and never advertises a module they have not opened.
    //
    // It speaks the document's language, not the crontab's (Fase 16g): the job by
    // NAME and the verdict as a sentence, where until now it printed the dotted id
    // and `SkyDocumentBuilder.render` — `golden_hour.pm alle 19:21: ✗ fail  cloud
    // 100%`, four tokens of another file's grammar in the middle of the only page
    // this app writes in prose. The number survives the translation (`VISION_SKY.md`
    // §7): a verdict without the figure it was built from is an opinion.
    val skyWarning = sky?.warning?.let { warning ->
        "> " + s(
            R.string.readme_status_sky,
            SkyJobNames.label(resources, warning.jobId),
            warning.at.atZone(
                runCatching { ZoneId.of(location.timezone) }.getOrDefault(ZoneId.systemDefault())
            ).format(ClockTime),
            skyVerdictProse(resources, warning.verdict)
        )
    }
    if (warnings.isEmpty() && skyWarning == null) {
        add(s(R.string.readme_status_ok))
    } else if (warnings.isEmpty()) {
        add(skyWarning!!)
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
        skyWarning?.let { add(it) }
    }

    // Both forecasts sit before every detail section: they are what a weather app is
    // opened for, and the hours read into the days without a page of conditions and
    // pollen in between. Fase 11c put them "straight after Today"; Status now sits in
    // that gap, which the rule was never about — two lines of warning are not the page
    // of detail it was written to keep out. Both tables share one column plan
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

    // Air quality leads the two detail sections (Fase 13d): AQI and pollen are things
    // you act on — whether to run outside, whether to take the antihistamine — while
    // the block below them is reference, pressure in mb being the least actionable
    // value on the page. A README documents what exists, so a section the APIs could
    // not fill is simply absent (the JSON's in-character `null` has no equivalent
    // here) — which is also why this one cannot be merged into Conditions: outside
    // the pollen coverage it has to be able to disappear whole.
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
    add("## ${s(R.string.readme_h_conditions)}")
    add(
        "🌬️ ${s(R.string.readme_wind)}: " +
            "${decimal1(options.windSpeed.convert(current.wind.speedKph))} " +
            "${options.windSpeed.symbol} ${current.wind.directionCompass}"
    )
    add("💧 ${s(R.string.readme_humidity)}: ${current.humidityPct}%")
    add("🌡️ ${s(R.string.readme_pressure)}: ${decimal1(current.pressureMb)} mb")
    add("👁️ ${s(R.string.readme_visibility)}: ${decimal1(current.visibilityKm)} km")

    // The sky's home in this document (Fase 16e). `VISION_SKY.md` first proposed a
    // separate `## Tonight` block after `## Next hours`, which would have put sunset,
    // phase and moonset there while THIS section, six lines below, reprinted sunrise,
    // sunset, daylight and phase: one document, two sections, one subject. So the
    // section that was already here grows instead, and stays where Fase 13d put it.
    // It is always present — it is today, not an advertisement for a module — and
    // the two middle lines appear only for someone who turned the module on.
    add("")
    add("## ${s(R.string.readme_h_astronomy)}")
    add(
        "${s(R.string.readme_sunrise)}: ${clock(astronomical.sunrise)} · " +
            "${s(R.string.readme_sunset)}: ${clock(astronomical.sunset)}"
    )
    add("${s(R.string.readme_daylight)}: ${astronomical.daylightDuration?.hhMm() ?: Absent}")
    sky?.let { summary ->
        summary.goldenHourEvening?.let { golden ->
            add(
                "${s(R.string.readme_golden_hour)}: ${span(golden)}" +
                    (summary.blueHourEvening?.let { " · ${s(R.string.readme_blue_hour)}: ${span(it)}" } ?: "")
            )
        }
        summary.darkness?.let { dark ->
            add(
                "${s(R.string.readme_darkness)}: ${span(dark)}" + when {
                    summary.moonlessFrom != null ->
                        ", ${s(R.string.readme_moonless_from, clock(summary.moonlessFrom))}"
                    summary.moonUpAllNight -> ", ${s(R.string.readme_moon_up_all_night)}"
                    else -> ""
                }
            )
        }
    }
    add(
        "${s(R.string.readme_moon)}: " +
            status(astronomical.moonPhase.label, astronomical.moonPhase.emoji) +
            (sky?.let { " · ${s(R.string.readme_moon_lit, it.illuminationPct)}" } ?: "") +
            (sky?.moonrise?.let { " · ${s(R.string.readme_moonrise)} ${clock(it)}" } ?: "") +
            (sky?.moonset?.let { " · ${s(R.string.readme_moonset)} ${clock(it)}" } ?: "")
    )

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

/**
 * A [SkyVerdict] as a sentence: `the sky will be overcast (100% cloud)`, not
 * `✗ fail  cloud 100%` (Fase 16g).
 *
 * Same verdict, same number, other language. `sky.crontab` keeps the glyph and the
 * word because a crontab's comment channel is where this app puts facts in the
 * file's own grammar; `## Status` is one line of a document written for somebody who
 * does not read `git` for a living, and `✗ fail` in the middle of it asks them to
 * learn a vocabulary to be told it will be cloudy.
 *
 * The reason comes FIRST, before the clouds: naming the sky for a night the moon
 * ruined, or for one the rain will, is the same lie in a friendlier font. Only
 * `~ unstable` and `✗ fail` reach the README (`SkyReadme.warning` filters), and both
 * always carry their figure — the last branch is a safety net, not a case.
 */
private fun skyVerdictProse(resources: Resources, verdict: SkyVerdict): String = when {
    verdict.note == SkyVerdictNote.PRECIPITATION -> resources.getString(
        if (verdict.kind == SkyVerdictKind.FAIL) R.string.readme_status_sky_rain_likely
        else R.string.readme_status_sky_rain_possible,
        verdict.precipPct ?: 0
    )
    verdict.note == SkyVerdictNote.MOONLIGHT -> resources.getString(
        R.string.readme_status_sky_moonlight, verdict.moonPct ?: 0
    )
    verdict.cloudPct != null -> resources.getString(
        if (verdict.kind == SkyVerdictKind.FAIL) R.string.readme_status_sky_overcast
        else R.string.readme_status_sky_cloudy,
        verdict.cloudPct
    )
    else -> resources.getString(R.string.readme_status_sky_uncertain)
}

/**
 * What stands in for a time the sky does not have. Not `00:00` and not an empty
 * cell: above the Arctic circle in June there is no sunrise, and the README says so
 * with the same glyph `sky.crontab` uses for the same fact.
 */
private const val Absent = "∅"

private fun clock(at: java.time.LocalTime?): String = at?.format(ClockTime) ?: Absent

private fun span(range: ClosedRange<java.time.LocalTime>): String =
    "${range.start.format(ClockTime)}–${range.endInclusive.format(ClockTime)}"

/** `34.2` but `34` instead of `34.0` — a README wouldn't write trailing zeros. */
private fun decimal1(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}
