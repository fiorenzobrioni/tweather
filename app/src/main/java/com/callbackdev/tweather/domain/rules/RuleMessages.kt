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

    // Both braces escaped: Android's regex engine is ICU, stricter than the JVM's
    // about brace metacharacters — a pattern must compile on BOTH, and this object
    // initializes lazily exactly (and only) when a rule fires.
    private val Placeholder = Regex("""\{([A-Za-z0-9_.]+)\}""")
    private val ClockTime = DateTimeFormatter.ofPattern("HH:mm")

    /** The two names the trigger carries; every other name is a [RuleVariables] id.
     * Written down because a screen that offers them must spell them the same way. */
    const val TriggerValue = "trigger.value"
    const val TriggerTime = "trigger.time"

    /** A name as it is written inside a message: the one place that knows the braces. */
    fun placeholder(name: String): String = "{$name}"

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
            TriggerValue -> {
                val kind = rule.conditions.firstOrNull()
                    ?.let { RuleVariables.byId(it.variable)?.kind }
                    ?: RuleVariableKind.NUMBER
                RuleVariables.formatValue(kind, triggerValue, units)
            }
            TriggerTime -> (triggerAt ?: now).format(ClockTime)
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
