package com.callbackdev.tweather.domain.sky

import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.HourlyForecast
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable

/**
 * One sky job the app observed as having run (Fase 16e).
 *
 * Stored as a JSON array on the history commit that observed it — not in a table of
 * its own (`VISION_SKY.md` §8.1). A run is not an independent event, it is something
 * a FETCH noticed, so [obsMinutes] is the distance between the event and the commit
 * that carries this record: implied by the schema rather than tracked by hand.
 *
 * [kind] is the verdict's name, or null for `– skipped` — the coverage state, where
 * no fetch came near enough to the event to have an opinion and the app declines to
 * invent one. Those runs count in no statistic.
 */
@Serializable
data class SkyRun(
    val jobId: String,
    val atEpochSeconds: Long,
    val kind: String? = null,
    val cloudPct: Int? = null,
    val obsMinutes: Long = 0
) {
    val verdict: SkyVerdictKind?
        get() = kind?.let { name -> SkyVerdictKind.entries.firstOrNull { it.name == name } }

    /** No fetch came near enough: the app is allowed not to know. */
    val skipped: Boolean get() = verdict == null || verdict == SkyVerdictKind.UNKNOWN
}

/**
 * Decides which subscribed jobs a fetch has just observed as run.
 *
 * Pure and testable: the window `(since, now]` in, the runs out. The worker supplies
 * `since` from the city's previous commit, which is exactly "everything that has
 * happened since the app last looked".
 */
object SkyRunRecorder {

    /**
     * Runs are recorded for ENABLED jobs only, and only for the fetch that first
     * observes the instant as past — a job switched off before it fired leaves no
     * record, and one switched on afterwards does not acquire a past.
     *
     * A window job counts as run when its window ENDS: the golden hour did not
     * happen at 19:32, it happened between 19:32 and 20:12 and was over at 20:12.
     */
    fun runsSince(
        since: Instant,
        now: Instant,
        jobs: List<SkyJob>,
        zone: ZoneId,
        coordinates: Coordinates,
        hours: List<HourlyForecast>,
        dataAge: Duration?,
        staleAfter: Duration
    ): List<SkyRun> = jobs
        .filter { it.observable }
        .mapNotNull { job ->
            val occurrence = lastOccurrence(job, since, now, zone, coordinates) ?: return@mapNotNull null
            val reference = occurrence.end ?: occurrence.start
            val verdict = SkyVerdictEngine.evaluate(
                job = job,
                start = occurrence.start,
                end = occurrence.end,
                hours = hours,
                zone = zone,
                coordinates = coordinates,
                dataAge = dataAge,
                staleAfter = staleAfter
            )
            SkyRun(
                jobId = job.id,
                atEpochSeconds = reference.epochSecond,
                // An UNKNOWN verdict IS the `– skipped` state: the forecast in hand
                // no longer carries the event's hour, so no opinion is invented.
                kind = verdict.kind.takeIf { it != SkyVerdictKind.UNKNOWN }?.name,
                cloudPct = verdict.cloudPct,
                // ROUNDED, not truncated. This is a distance, and 11m53s away is
                // nearer to "+12m" than to "+11m" — truncating would quietly
                // under-report the very weakness the number exists to disclose.
                obsMinutes = (Duration.between(reference, now).seconds + 30) / 60
            )
        }

    /**
     * The job's occurrence that ended inside `(since, now]`, if any.
     *
     * Walks back from [since]: resolving forward from `now` would only ever find the
     * NEXT one, which is the thing that has not happened yet.
     */
    private fun lastOccurrence(
        job: SkyJob,
        since: Instant,
        now: Instant,
        zone: ZoneId,
        coordinates: Coordinates
    ): SkyOccurrence.At? = SkyScheduler
        .next(job, since.minusSeconds(1), zone, coordinates, limit = LOOKAHEAD)
        .filterIsInstance<SkyOccurrence.At>()
        .lastOrNull { occurrence ->
            val reference = occurrence.end ?: occurrence.start
            reference.isAfter(since) && !reference.isAfter(now)
        }

    /**
     * How many occurrences forward to consider. A daily job can only have run a
     * handful of times between two syncs — the interval tops out at two hours — and
     * the bound keeps a long gap (a phone off for a week) from walking a year.
     */
    private const val LOOKAHEAD = 8
}
