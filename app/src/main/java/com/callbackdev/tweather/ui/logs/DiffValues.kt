package com.callbackdev.tweather.ui.logs

import com.callbackdev.tweather.data.UnitSettings
import com.callbackdev.tweather.data.local.SnapshotDiff
import com.callbackdev.tweather.data.local.WeatherSnapshots
import com.callbackdev.tweather.ui.weather.convert
import com.callbackdev.tweather.ui.weather.keySuffix
import kotlin.math.roundToInt

/**
 * How a STORED diff line becomes the line the reader sees.
 *
 * Room keeps every snapshot in one canonical form — English, Celsius, km/h — so that
 * a diff never churns because the phone changed language or units: what is compared
 * is always the same two numbers. Everything the reader's settings touch happens
 * HERE, at render time, exactly like the language does since Fase 9h.
 *
 * Three steps, in order:
 *
 * 1. **Language.** Weather values localize (`Overcast ☁️` → `Coperto ☁️`), gated by
 *    key so a future value colliding with a translated word cannot be caught by it.
 * 2. **Units.** A temperature or a wind speed is rendered in the user's unit — and
 *    the KEY is renamed with it (`temp_c` → `temp_f`), because that is what
 *    `weather_data.json` does and a JSON file does not lie about its units. Until
 *    Fase 28 the Logs were the last surface still printing metric while the editor
 *    tab, the README, the widget and the notifications all converted.
 * 3. **The pair that no longer says anything.** Converting rounds, and rounding can
 *    make the two halves of a change identical: 10.1 and 10.2 km/h are both 6.3 mph.
 *    A `-`/`+` pair of two equal lines reads as a bug, so it collapses back into the
 *    context line it has become. This is the unit twin of [ForecastDiff]'s
 *    thresholds — sub-resolution wiggle is not a revision.
 */
internal fun List<SnapshotDiff.Line>.rendered(
    units: UnitSettings,
    translate: (String) -> String
): List<SnapshotDiff.Line> =
    map { it.localized(translate).inUserUnits(units) }.collapseEqualPairs()

/**
 * Weather DATA values localize at render time (app-wide l10n rule); everything
 * else in a diff line — keys, city names, compass points, clock times — is code
 * or proper nouns and passes through. Gated by key so a future snapshot value
 * that happens to collide with a translated word cannot be mistranslated.
 */
private fun SnapshotDiff.Line.localized(translate: (String) -> String): SnapshotDiff.Line =
    if (key == "status" || key.endsWith(".status") || key.endsWith(".moon_phase")) {
        copy(value = translate(value))
    } else {
        this
    }

/**
 * The suffix carries the unit, in both files: `current.temp_c` and `high_c` are
 * Celsius, `current.wind_kph` is km/h. Nothing else in either snapshot ends that way
 * — `precip_pct`, `pressure_mb`, `uv_index`, `wind_dir` are all safe — and the same
 * convention is what `weather_data.json` renames its own keys by.
 *
 * With the metric units selected the string is returned untouched rather than
 * re-formatted: the stored value IS the rendered one, and re-printing it through a
 * rounder would be a way to introduce a difference where the data has none.
 */
private fun SnapshotDiff.Line.inUserUnits(units: UnitSettings): SnapshotDiff.Line = when {
    key.endsWith(CelsiusSuffix) -> retyped(
        suffix = CelsiusSuffix,
        unitKey = units.temperature.keySuffix,
        convert = units.temperature::convert
    )
    key.endsWith(KmhSuffix) -> retyped(
        suffix = KmhSuffix,
        unitKey = units.windSpeed.keySuffix,
        convert = units.windSpeed::convert
    )
    else -> this
}

private fun SnapshotDiff.Line.retyped(
    suffix: String,
    unitKey: String,
    convert: (Double) -> Double
): SnapshotDiff.Line {
    val renamed = key.removeSuffix(suffix) + "_" + unitKey
    if (renamed == key) return this
    // `null` is a value too (no sunrise, no probability) and converts to nothing;
    // so does anything the provider sent that will not parse as a number.
    val metric = value.takeUnless { it == WeatherSnapshots.NullValue }?.toDoubleOrNull()
        ?: return copy(key = renamed)
    return copy(key = renamed, value = decimal1(convert(metric)))
}

/**
 * Collapses `- x` / `+ x` — two halves of a change that render the same — into the
 * one context line they amount to. Pairs only: a `-` with no `+` after it is a key
 * that left the file, and that is a real removal.
 */
private fun List<SnapshotDiff.Line>.collapseEqualPairs(): List<SnapshotDiff.Line> {
    if (none { it.type == SnapshotDiff.Type.REMOVED }) return this
    val out = ArrayList<SnapshotDiff.Line>(size)
    var i = 0
    while (i < size) {
        val line = this[i]
        val next = getOrNull(i + 1)
        val collapses = line.type == SnapshotDiff.Type.REMOVED &&
            next != null &&
            next.type == SnapshotDiff.Type.ADDED &&
            next.key == line.key &&
            next.value == line.value
        if (collapses) {
            out.add(line.copy(type = SnapshotDiff.Type.CONTEXT))
            i += 2
        } else {
            out.add(line)
            i += 1
        }
    }
    return out
}

/** One decimal, like `weather_data.json`'s own numbers (raw doubles carry noise). */
private fun decimal1(value: Double): String = ((value * 10).roundToInt() / 10.0).toString()

private const val CelsiusSuffix = "_c"
private const val KmhSuffix = "_kph"
