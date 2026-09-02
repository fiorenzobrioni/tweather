package com.callbackdev.chiaro.domain.rules

import com.callbackdev.chiaro.domain.model.WeatherReport
import java.time.LocalDateTime

/** One rule whose conditions all hold — ready to notify. Exactly one of
 * [fingerprint]/[latchKey] is set, matching the rule's anti-noise semantics. */
data class RuleTrigger(
    val rule: NotificationRule,
    /** Dedup fingerprint (windowed rules); recorded only after a successful post. */
    val fingerprint: String?,
    /** Latch key (instant rules); recorded after a successful post, cleared by the
     * engine once the condition reads false again. */
    val latchKey: String?,
    /** First condition's resolved value/hour — the `trigger.*` placeholders. */
    val value: Double,
    val at: LocalDateTime?
)

/** Persisted engine bookkeeping (RuleStateStore), never user-visible. */
data class RuleEngineState(
    /** `cityKey:ruleId` of instant rules currently true — the edge-trigger memory. */
    val latched: Set<String> = emptySet(),
    /** Recently fired fingerprints of windowed rules. */
    val firedFingerprints: Set<String> = emptySet()
)

data class RuleEvaluation(
    val triggers: List<RuleTrigger>,
    /** Latch keys whose instant rule reads false now — clear these regardless of
     * whether any notification posts, so the rule re-arms. */
    val unlatch: Set<String>
)

/** A single stateless rule check — what the dry run shows, one line per rule. */
sealed interface RuleCheck {
    data class Fires(val value: Double, val at: LocalDateTime?) : RuleCheck
    data object Passes : RuleCheck

    /** The variable can't be resolved right now (AQ down, empty window). */
    data class Unavailable(val variable: String) : RuleCheck
}

/**
 * Pure evaluation of the user's `alerts.rules` (Fase 11) — no clocks, no Android,
 * no I/O, like [com.callbackdev.chiaro.domain.AlertEngine]. Two anti-noise
 * semantics, chosen by what the rule reads:
 *
 * - all conditions on `current.*` → **edge-triggered**: fire on the false→true
 *   transition, re-arm when it reads false again (else `temp < 5` fires every
 *   poll all January);
 * - any forecast/daily condition → **fingerprint per half-day** (`AM`/`PM`), the
 *   same bucket the builtin precipitation warning uses — an aggregate over a
 *   sliding window never cleanly reads "false again".
 */
object RuleEngine {

    fun evaluate(
        rules: List<NotificationRule>,
        report: WeatherReport,
        state: RuleEngineState,
        now: LocalDateTime,
        cityKey: String
    ): RuleEvaluation {
        val triggers = mutableListOf<RuleTrigger>()
        val unlatch = mutableSetOf<String>()
        rules.filter { it.enabled }.forEach { rule ->
            val instant = rule.conditions.all { RuleVariables.isInstant(it.variable) }
            val latchKey = latchKey(cityKey, rule.id)
            when (val result = check(rule, report, now)) {
                // Missing data is not "false": keep the latch, the data may return
                is RuleCheck.Unavailable -> Unit
                RuleCheck.Passes -> if (instant && latchKey in state.latched) {
                    unlatch += latchKey
                }
                is RuleCheck.Fires -> if (instant) {
                    if (latchKey !in state.latched) {
                        triggers += RuleTrigger(rule, null, latchKey, result.value, result.at)
                    }
                } else {
                    val half = if (now.hour < 12) "AM" else "PM"
                    val fingerprint = "$cityKey:rule:${rule.id}:${now.toLocalDate()}:$half"
                    if (fingerprint !in state.firedFingerprints) {
                        triggers += RuleTrigger(rule, fingerprint, null, result.value, result.at)
                    }
                }
            }
        }
        return RuleEvaluation(triggers, unlatch)
    }

    /**
     * Stateless check of one rule — the dry run's engine (`$ tweather run rules`):
     * no dedup, no latching, exactly what IS true right now.
     */
    fun check(rule: NotificationRule, report: WeatherReport, now: LocalDateTime): RuleCheck {
        var first: ResolvedValue? = null
        rule.conditions.forEach { condition ->
            val variable = RuleVariables.byId(condition.variable)
                ?: return RuleCheck.Unavailable(condition.variable)
            val resolved = variable.resolve(report, now)
                ?: return RuleCheck.Unavailable(condition.variable)
            if (first == null) first = resolved
            if (!condition.op.compare(resolved.value, condition.threshold)) {
                return RuleCheck.Passes
            }
        }
        // The first condition is the rule's subject: its value/hour become trigger.*
        return first?.let { RuleCheck.Fires(it.value, it.at) } ?: RuleCheck.Passes
    }

    fun latchKey(cityKey: String, ruleId: Long): String = "$cityKey:$ruleId"
}
