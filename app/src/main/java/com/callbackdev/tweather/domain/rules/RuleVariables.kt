package com.callbackdev.tweather.domain.rules

import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.data.UnitSettings
import com.callbackdev.tweather.data.WindSpeedUnit
import com.callbackdev.tweather.domain.AlertEngine
import com.callbackdev.tweather.domain.model.HourlyForecast
import com.callbackdev.tweather.domain.model.WeatherReport
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** How a variable renders and converts: temperatures and speeds follow the units
 * in settings.config (name AND value — a file doesn't lie about its units, Fase 7);
 * plain numbers are unit-fixed; booleans render `true`/`false`. */
enum class RuleVariableKind { TEMPERATURE, SPEED, NUMBER, BOOLEAN }

/** A resolved variable: the value plus, for forecast aggregates, the hour that
 * produced it — what the `{trigger.time}` placeholder interpolates. */
data class ResolvedValue(val value: Double, val at: LocalDateTime? = null)

class RuleVariable(
    /** Canonical metric name, mirroring `weather_data.json`'s keys. */
    val id: String,
    val kind: RuleVariableKind,
    /** Pure resolution against a report; null when the data is unavailable
     * (air quality down, empty forecast window) — the rule then just skips. */
    val resolve: (WeatherReport, LocalDateTime) -> ResolvedValue?
)

/**
 * The curated variable registry of `alerts.rules` (Fase 11). No functions in the
 * language: forecast aggregates are precomputed variables, so the user picks the
 * time window by picking the name. Namespaces are explicit on purpose — a bare
 * `rain_probability` (now? tonight?) would make notifications ambiguous:
 *
 * - `current.*` mirrors `current_conditions` + `air_quality`
 * - `next_6h.*` / `next_12h.*` aggregate the hourly forecast from now
 * - `today.*` reads today's daily forecast
 */
object RuleVariables {

    val all: List<RuleVariable> = buildList {
        current("current.temp_c", RuleVariableKind.TEMPERATURE) { it.current.tempC }
        current("current.feels_like_c", RuleVariableKind.TEMPERATURE) { it.current.feelsLikeC }
        current("current.humidity_pct", RuleVariableKind.NUMBER) { it.current.humidityPct.toDouble() }
        current("current.dew_point_c", RuleVariableKind.TEMPERATURE) { it.current.dewPointC }
        current("current.uv_index", RuleVariableKind.NUMBER) { it.current.uvIndex.toDouble() }
        current("current.wind.speed_kph", RuleVariableKind.SPEED) { it.current.wind.speedKph }
        current("current.wind.gust_kph", RuleVariableKind.SPEED) { it.current.wind.gustKph }
        current("current.precipitation.chance_pct", RuleVariableKind.NUMBER) {
            it.current.precipitation.chancePct.toDouble()
        }
        current("current.pressure_mb", RuleVariableKind.NUMBER) { it.current.pressureMb }
        current("current.visibility_km", RuleVariableKind.NUMBER) { it.current.visibilityKm }
        // Air quality is best-effort upstream: null when the AQ API had failed
        add(
            RuleVariable("current.aqi_index", RuleVariableKind.NUMBER) { report, _ ->
                report.airQuality?.let { ResolvedValue(it.aqiIndex.toDouble()) }
            }
        )

        for (hours in listOf(6L, 12L)) {
            window("next_${hours}h.precip_chance_max", RuleVariableKind.NUMBER, hours) { w ->
                w.maxByOrNull { it.precipChancePct }
                    ?.let { ResolvedValue(it.precipChancePct.toDouble(), it.time) }
            }
            window("next_${hours}h.temp_c_min", RuleVariableKind.TEMPERATURE, hours) { w ->
                w.minByOrNull { it.tempC }?.let { ResolvedValue(it.tempC, it.time) }
            }
            window("next_${hours}h.temp_c_max", RuleVariableKind.TEMPERATURE, hours) { w ->
                w.maxByOrNull { it.tempC }?.let { ResolvedValue(it.tempC, it.time) }
            }
            // Same hazard classes as the builtin severe alert (AlertEngine.SevereCodes)
            window("next_${hours}h.wmo_severe", RuleVariableKind.BOOLEAN, hours) { w ->
                if (w.isEmpty()) {
                    null
                } else {
                    val hit = w.firstOrNull { it.condition.wmoCode in AlertEngine.SevereCodes }
                    if (hit != null) ResolvedValue(1.0, hit.time) else ResolvedValue(0.0)
                }
            }
        }

        // Suffixed like the notification keys of Fase 9c (`high_c`/`high_f`), not
        // like daily_forecast's bare "high": the unit must live in the name here
        // too, or `today.high > 25` would silently change meaning with the setting.
        today("today.high_c", RuleVariableKind.TEMPERATURE) { it.highC }
        today("today.low_c", RuleVariableKind.TEMPERATURE) { it.lowC }
        today("today.precip_pct", RuleVariableKind.NUMBER) { it.precipPct.toDouble() }
    }

    private val index = all.associateBy { it.id }

    fun byId(id: String): RuleVariable? = index[id]

    /** Instant variables read the present: their rules are edge-triggered instead
     * of fingerprint-deduplicated (see RuleEngine). */
    fun isInstant(id: String): Boolean = id.startsWith("current.")

    /**
     * The name as `alerts.rules` displays it, in the user's units:
     * `current.temp_c` ↔ `current.temp_f`, `…_kph` ↔ `…_mph`. Curated ids contain
     * exactly one unit token, so the replacement is unambiguous.
     */
    fun displayId(variable: RuleVariable, units: UnitSettings): String = when (variable.kind) {
        RuleVariableKind.TEMPERATURE ->
            if (units.temperature == TemperatureUnit.FAHRENHEIT) {
                variable.id.replace("_c", "_f")
            } else {
                variable.id
            }
        RuleVariableKind.SPEED ->
            if (units.windSpeed == WindSpeedUnit.MPH) {
                variable.id.replace("_kph", "_mph")
            } else {
                variable.id
            }
        else -> variable.id
    }

    /** The canonical id for a displayed (possibly imperial) name; null if unknown. */
    fun canonicalId(displayed: String): String? = when {
        index.containsKey(displayed) -> displayed
        else -> displayed.replace("_f", "_c").replace("_mph", "_kph")
            .takeIf { index.containsKey(it) }
    }

    /** Canonical metric value → the user's units, for display and interpolation. */
    fun displayValue(kind: RuleVariableKind, canonical: Double, units: UnitSettings): Double =
        when (kind) {
            RuleVariableKind.TEMPERATURE ->
                if (units.temperature == TemperatureUnit.FAHRENHEIT) {
                    canonical * 9 / 5 + 32
                } else {
                    canonical
                }
            RuleVariableKind.SPEED ->
                if (units.windSpeed == WindSpeedUnit.MPH) canonical / KM_PER_MILE else canonical
            else -> canonical
        }

    /** A value typed in the user's units → canonical metric, for storage. */
    fun canonicalValue(kind: RuleVariableKind, displayed: Double, units: UnitSettings): Double =
        when (kind) {
            RuleVariableKind.TEMPERATURE ->
                if (units.temperature == TemperatureUnit.FAHRENHEIT) {
                    (displayed - 32) * 5 / 9
                } else {
                    displayed
                }
            RuleVariableKind.SPEED ->
                if (units.windSpeed == WindSpeedUnit.MPH) displayed * KM_PER_MILE else displayed
            else -> displayed
        }

    /** `19`, `19.5`, `true` — one decimal at most, integers bare, like the JSON. */
    fun formatValue(kind: RuleVariableKind, canonical: Double, units: UnitSettings): String {
        if (kind == RuleVariableKind.BOOLEAN) return if (canonical != 0.0) "true" else "false"
        val display = (displayValue(kind, canonical, units) * 10).roundToInt() / 10.0
        return if (display == display.toLong().toDouble()) {
            display.toLong().toString()
        } else {
            display.toString()
        }
    }

    private const val KM_PER_MILE = 1.609344

    private fun MutableList<RuleVariable>.current(
        id: String,
        kind: RuleVariableKind,
        value: (WeatherReport) -> Double
    ) = add(RuleVariable(id, kind) { report, _ -> ResolvedValue(value(report)) })

    private fun MutableList<RuleVariable>.window(
        id: String,
        kind: RuleVariableKind,
        hours: Long,
        aggregate: (List<HourlyForecast>) -> ResolvedValue?
    ) = add(
        RuleVariable(id, kind) { report, now ->
            val end = now.plusHours(hours)
            aggregate(
                report.hourly.filter { !it.time.isBefore(now) && !it.time.isAfter(end) }
            )
        }
    )

    private fun MutableList<RuleVariable>.today(
        id: String,
        kind: RuleVariableKind,
        value: (com.callbackdev.tweather.domain.model.DailyForecast) -> Double
    ) = add(
        RuleVariable(id, kind) { report, _ ->
            report.daily.firstOrNull()?.let { ResolvedValue(value(it)) }
        }
    )
}
