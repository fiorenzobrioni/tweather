package com.callbackdev.chiaro.domain.sky

/**
 * Whether a reminder that has come due is worth posting (Fase 16f, `VISION_SKY.md`
 * §10). Pure, and separated from the alarm plumbing so the rule can be read on its
 * own — it is the only part of the reminder path with an opinion.
 */
object SkyReminderPolicy {

    /** What the app decided, and why, so the caller can log it honestly. */
    enum class Decision {
        SEND,

        /** The sky will not allow it, and `notify_on_fail` is off. */
        SUPPRESSED_FAIL,

        /** The app has no recent opinion, and this job is only worth seeing. */
        SUPPRESSED_UNKNOWN
    }

    /**
     * The two suppression rules, and the distinction between them is
     * [SkyJob.visibilityDependent] rather than [SkyJob.observable]:
     *
     * - A **`✗ fail`** suppresses the reminder unless `notify_on_fail` is on, because
     *   a reminder for something you cannot see is noise.
     * - A **`? unknown`** is SENT for a job that happens regardless of the sky (a
     *   sunset: you may have somewhere to be at dusk) and suppressed for one that is
     *   only an event if the sky is clear (a shower's peak). Both carry the reason.
     */
    fun decide(
        job: SkyJob,
        verdict: SkyVerdict?,
        notifyOnFail: Boolean
    ): Decision = when {
        verdict == null -> Decision.SEND
        verdict.kind == SkyVerdictKind.FAIL && !notifyOnFail -> Decision.SUPPRESSED_FAIL
        verdict.kind == SkyVerdictKind.UNKNOWN && job.visibilityDependent ->
            Decision.SUPPRESSED_UNKNOWN
        else -> Decision.SEND
    }
}
