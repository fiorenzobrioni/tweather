package com.callbackdev.tweather.domain.sky

/**
 * How a job's schedule is expressed, and therefore what its cron line says (Fase
 * 16b). The taxonomy is the feature, not a workaround for the metaphor: a crontab
 * line asserts a RECURRENCE, which is true of every job here, while the instant it
 * resolves to lives in the comment channel, which is where a real crontab puts a
 * computed fact. See `VISION_SKY.md` §3.
 */
enum class SkyJobKind(val expression: String) {
    /** Once per local day; the instant is computed per day. */
    DAILY("@daily"),

    /** Once per year; the instant is computed per year. */
    ANNUAL("@yearly"),

    /**
     * Aperiodic, detected by evaluation. A half-hourly polling expression is the
     * honest way to schedule an event with no recurrence rule — you look often and
     * act when the condition holds — and it also happens to be literally what the app
     * does: it evaluates on each fetch.
     *
     * (The expression is not spelled out in this comment for a dull reason: it opens
     * with the two characters that end a block comment.)
     */
    POLLING("*/30 * * * *")
}

/** What the job resolves to: one instant, or a window with two ends. */
enum class SkyJobShape { INSTANT, RANGE }

/**
 * One line of `sky.crontab`. A value, not a class with behaviour: the catalog is a
 * fixed list of these ([SkyJobCatalog]) and [SkyScheduler] turns one plus a date
 * into an occurrence, exactly as the 22 variables of `alerts.rules` are values that
 * `RuleEngine` resolves.
 *
 * [id] is the dotted name the file shows and the store persists. It is **English and
 * never localized**: it is code, like a JSON key — see `VISION_SKY.md` §4.
 */
data class SkyJob(
    val id: String,
    val kind: SkyJobKind,
    val shape: SkyJobShape,
    /**
     * True when clouds decide whether the event is worth anything. A sunset happens
     * whether or not you can see it; the Perseids peak is only an event if the sky
     * is clear. Fase 16f suppresses reminders for the second kind on a `✗ fail`, and
     * 16d gives them the moon condition on top of the cloud one.
     */
    val visibilityDependent: Boolean = false,
    /** True when the darkness of the sky, not just its clearness, is the point. */
    val needsDarkness: Boolean = false
) {
    val expression: String get() = kind.expression
}
