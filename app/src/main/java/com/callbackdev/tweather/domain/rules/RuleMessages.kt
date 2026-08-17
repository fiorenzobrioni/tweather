package com.callbackdev.tweather.domain.rules

import com.callbackdev.tweather.data.UnitSettings
import com.callbackdev.tweather.domain.model.WeatherReport
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * `{placeholder}` interpolation for rule messages. Two namespaces:
 *
 * - any [RuleVariables] name, canonical or as displayed in the user's units
 *   (`{current.temp_c}` and `{current.temp_f}` both resolve);
 * - `{trigger.value}` / `{trigger.time}` — the first condition's resolved value
 *   and hour, what made the rule fire.
 *
 * Values render in the user's units, like every other surface. An unknown
 * placeholder stays literal: a typo must never eat part of the user's message.
 */
object RuleMessages {

    private val Placeholder = Regex("""\{([A-Za-z0-9_.]+)}""")
    private val ClockTime = DateTimeFormatter.ofPattern("HH:mm")

    fun interpolate(
        message: String,
        trigger: RuleTrigger,
        report: WeatherReport,
        now: LocalDateTime,
        units: UnitSettings
    ): String = interpolate(message, trigger.rule, trigger.value, trigger.at, report, now, units)

    /** Same substitution for the dry run, which has a [RuleCheck.Fires] instead. */
    fun interpolate(
        message: String,
        rule: NotificationRule,
        triggerValue: Double,
        triggerAt: LocalDateTime?,
        report: WeatherReport,
        now: LocalDateTime,
        units: UnitSettings
    ): String = Placeholder.replace(message) { match ->
        val name = match.groupValues[1]
        when (name) {
            "trigger.value" -> {
                val kind = rule.conditions.firstOrNull()
                    ?.let { RuleVariables.byId(it.variable)?.kind }
                    ?: RuleVariableKind.NUMBER
                RuleVariables.formatValue(kind, triggerValue, units)
            }
            "trigger.time" -> (triggerAt ?: now).format(ClockTime)
            else -> {
                val variable = RuleVariables.canonicalId(name)?.let { RuleVariables.byId(it) }
                val resolved = variable?.resolve?.invoke(report, now)
                if (variable != null && resolved != null) {
                    RuleVariables.formatValue(variable.kind, resolved.value, units)
                } else {
                    match.value // unknown or unavailable: leave the text untouched
                }
            }
        }
    }
}
