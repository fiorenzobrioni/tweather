package com.callbackdev.tweather.domain.sky

import java.time.Duration
import java.time.Instant

/**
 * The leads a `--notify` token can hold (Fase 16f), in the order it cycles them.
 *
 * **`off` and then fifteen minutes.** There is no five-minute lead and there never
 * will be: these reminders ride inexact alarms (`VISION_SKY.md` §10), which drift by
 * about ten minutes, so a five-minute lead can be delivered after the thing it
 * announces. That is not a shorter lead, it is a lie — the draft offered `5m` in one
 * section and forbade it in another, and the section that forbade it was right.
 */
enum class SkyLead(val minutes: Int?, val label: String) {
    OFF(null, "off"),
    FIFTEEN(15, "15m"),
    THIRTY(30, "30m"),
    ONE_HOUR(60, "1h"),
    THREE_HOURS(180, "3h"),
    ONE_DAY(24 * 60, "1d");

    fun next(): SkyLead = entries[(ordinal + 1) % entries.size]

    companion object {
        /** The lead a job added from the catalog starts with (`notify_default`). */
        val Default = THIRTY

        fun ofMinutes(minutes: Int?): SkyLead =
            entries.firstOrNull { it.minutes == minutes } ?: OFF
    }
}

/** One reminder waiting to be delivered: which job, for which occurrence, when. */
data class SkyReminder(
    val jobId: String,
    /** When the alarm should fire. */
    val fireAt: Instant,
    /** The occurrence it announces — half of the dedup fingerprint. */
    val occurrenceAt: Instant
) {
    /**
     * One notification per job per occurrence. The occurrence is identified to the
     * MINUTE, not the second: the engine's answer for one sunset moves by fractions
     * of a second between two evaluations, and a fingerprint that moved with it would
     * dedup nothing at all.
     */
    val fingerprint: String get() = "$jobId@${occurrenceAt.epochSecond / 60}"
}

/**
 * Works out which reminder is next (Fase 16f). Pure: subscriptions and a clock
 * reading in, at most one reminder out.
 *
 * **One at a time.** The app arms a single alarm for the nearest reminder and, when
 * it fires, arms the following one. A queue of twenty alarms would buy nothing — the
 * schedule is recomputed on every fetch anyway — and would cost twenty wakeups to
 * cancel every time the user edited a line.
 */
object SkyReminderPlanner {

    fun next(
        subscriptions: List<Pair<SkyJob, SkyLead>>,
        now: Instant,
        zone: java.time.ZoneId,
        coordinates: com.callbackdev.tweather.domain.model.Coordinates
    ): SkyReminder? = subscriptions
        .asSequence()
        .filter { (_, lead) -> lead.minutes != null }
        .mapNotNull { (job, lead) -> reminderFor(job, lead, now, zone, coordinates) }
        .minByOrNull { it.fireAt }

    /**
     * The next occurrence of [job] whose reminder is still ahead of [now].
     *
     * Looks at more than one occurrence on purpose: with a one-day lead the NEXT
     * sunset's reminder is already behind us, and the honest answer is the one after
     * it rather than nothing at all.
     */
    private fun reminderFor(
        job: SkyJob,
        lead: SkyLead,
        now: Instant,
        zone: java.time.ZoneId,
        coordinates: com.callbackdev.tweather.domain.model.Coordinates
    ): SkyReminder? {
        val minutes = lead.minutes ?: return null
        return SkyScheduler
            .next(job, now, zone, coordinates, limit = LOOKAHEAD)
            .filterIsInstance<SkyOccurrence.At>()
            .asSequence()
            .map { SkyReminder(job.id, it.start.minus(Duration.ofMinutes(minutes.toLong())), it.start) }
            .firstOrNull { it.fireAt.isAfter(now) }
    }

    /** Enough occurrences that even a one-day lead finds one still in the future. */
    private const val LOOKAHEAD = 3
}
