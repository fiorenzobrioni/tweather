package com.callbackdev.chiaro.domain.rules

import kotlinx.serialization.Serializable

/**
 * The "Weather CI" mini-language (Fase 11) is deliberately NOT a language: a rule is
 * a structure of 1–2 (variable, operator, threshold) conditions plus a message,
 * edited token by token in `alerts.rules`. A syntax error is not writable, so there
 * is no parser and no diagnostics anywhere in this package.
 */
@Serializable
enum class RuleOp(val symbol: String) {
    GT(">"),
    GTE(">="),
    LT("<"),
    LTE("<="),
    EQ("=="),
    NEQ("!=");

    fun compare(value: Double, threshold: Double): Boolean = when (this) {
        GT -> value > threshold
        GTE -> value >= threshold
        LT -> value < threshold
        LTE -> value <= threshold
        EQ -> value == threshold
        NEQ -> value != threshold
    }

    /** The operator token cycles on tap, like every enum value in settings.config. */
    fun next(): RuleOp = entries[(ordinal + 1) % entries.size]

    /** Boolean variables only compare for equality; anything else is nonsense. */
    fun nextBoolean(): RuleOp = if (this == EQ) NEQ else EQ
}

@Serializable
data class RuleCondition(
    /** A [RuleVariables] id — always the canonical metric name (`current.temp_c`). */
    val variable: String,
    val op: RuleOp,
    /** Canonical metric units (°C, km/h); booleans are 0.0/1.0. The UI renders and
     * edits it in the user's units — the stored value never changes with settings. */
    val threshold: Double
)

@Serializable
data class NotificationRule(
    val id: Long,
    /** Slug named by the user: shown in the file, in the notification's command line
     * (`$ tweather run <name>`) and in the Logs check line. */
    val name: String,
    val enabled: Boolean = true,
    /** 1..[MaxConditions] conditions, all required (`and`). `or` = two rules. */
    val conditions: List<RuleCondition>,
    /** User content in the user's language — never localized. `{placeholders}`
     * ([RuleMessages]) interpolate at notify time. */
    val message: String
)

/** Low ceiling on purpose: alerts.rules is a config file, not a database. */
const val MaxRules = 10

/** One `and`, no `or`, no parentheses — the v1 boundary (PLANNING, Fase 11). */
const val MaxConditions = 2
