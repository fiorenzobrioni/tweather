package com.callbackdev.chiaro.domain.settings

/**
 * The preferences the ENGINES read, and only those.
 *
 * They lived in tweather's `SettingsStore` next to the DataStore keys, which made
 * the domain import the data layer to evaluate a rule ([com.callbackdev.chiaro.domain
 * .rules.RuleVariables] renders a threshold in the reader's units, so it needs to know
 * them). Chiaro splits the two: the values that decide an engine's answer live here,
 * the plumbing that persists them stays in the store, and `:core:domain` compiles
 * with nothing underneath it.
 *
 * Everything that only the UI cares about — the theme, the update interval, the
 * widget's opacity — deliberately did NOT move: it is not an input to any engine.
 */
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

enum class WindSpeedUnit { KMH, MPH }

/**
 * The units the reader chose. The engines keep every value metric internally and
 * convert at the edge, so switching this never rewrites stored data — a threshold
 * saved as 20 °C is still 20 °C after a switch to Fahrenheit, and only its rendering
 * changes.
 */
data class UnitSettings(
    val temperature: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windSpeed: WindSpeedUnit = WindSpeedUnit.KMH
)

/**
 * Which built-in alerts are on. Each flag gates one rule in
 * [com.callbackdev.chiaro.domain.AlertEngine]; [userRules] is the master switch of
 * the reader's own alerts, default true because it only matters once one exists.
 */
data class NotificationSettings(
    val severeWeatherAlerts: Boolean = true,
    val dailySummary: Boolean = false,
    val precipitationWarning: Boolean = true,
    val userRules: Boolean = true
)
