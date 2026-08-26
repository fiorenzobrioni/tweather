package com.callbackdev.tweather.ui.sky

import com.callbackdev.tweather.data.SkySubscription
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.MoonPhase
import com.callbackdev.tweather.domain.sky.AstronomyEngine
import com.callbackdev.tweather.domain.sky.SkyAlmanac
import com.callbackdev.tweather.domain.sky.SkyJob
import com.callbackdev.tweather.domain.sky.SkyJobCatalog
import com.callbackdev.tweather.domain.sky.SkyNotScheduled
import com.callbackdev.tweather.domain.sky.SkyOccurrence
import com.callbackdev.tweather.domain.sky.SkyScheduler
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * `sky.crontab` as data (Fase 16c): the pure step between the scheduler and the
 * renderer, so the whole document can be asserted in a JVM test without composing
 * anything. The Compose layer turns [SkyDocument] into tappable lines and adds not
 * one fact of its own.
 *
 * Everything here is **English**: job names, cron expressions and the comment
 * channel are code, the same rule `weather_data.json` and `alerts.rules` follow. The
 * localized register of the sky lives in the README (Fase 16e).
 */
data class SkyDocument(
    val header: List<String>,
    val rows: List<SkyRow>,
    /** Jobs still in the catalog but not in the file — what `+ add job` offers. */
    val available: List<SkyJob>,
    val footer: List<String>
) {
    /**
     * The name column is as wide as the widest name IN THIS FILE, not as the widest
     * the catalog could ever hold. That is what `column -t` does to a real crontab,
     * and here it is also what keeps the file usable: padded to the catalog's longest
     * id (`meteor.eta_aquariids.peak`, 25 characters) a two-line file pushed its own
     * `[rm]` past the right edge of a 360dp screen, so the control could only be
     * reached by panning. A file pads to itself.
     */
    val nameColumnWidth: Int = rows.maxOfOrNull { it.job.id.length } ?: 0

    /** Likewise, and it includes the `#` a commented-out line carries. */
    val expressionColumnWidth: Int =
        rows.maxOfOrNull { it.expression.length + if (it.enabled) 0 else 1 } ?: 0
}

/**
 * One line of the file. [enabled] false renders the whole line commented out, which
 * is how a cron job is really disabled — the line stays in your file and comes back
 * with one tap, where `[rm]` puts it back in the catalog.
 */
data class SkyRow(
    val job: SkyJob,
    val enabled: Boolean,
    /** The cron field: a nickname or the polling expression. Padded by the renderer. */
    val expression: String,
    /** The comment channel: resolved instant, window, `∅` reason. Empty when disabled. */
    val comment: String
)

/** Everything [SkyDocumentBuilder] needs to resolve a file; no Android, no clock. */
data class SkyContext(
    val cityLabel: String,
    val coordinates: Coordinates,
    val zone: ZoneId,
    val now: Instant
)

object SkyDocumentBuilder {

    private val ClockTime = DateTimeFormatter.ofPattern("HH:mm")
    private val DayAndClock = DateTimeFormatter.ofPattern("MMM d HH:mm")
    private val IsoDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun build(subscriptions: List<SkySubscription>, context: SkyContext): SkyDocument {
        val known = subscriptions.mapNotNull { subscription ->
            SkyJobCatalog.byId(subscription.jobId)?.let { it to subscription }
        }
        // File order is the CATALOG's, never the subscription list's and never "what
        // fires next": a crontab is a file. The next job to fire is the header's job.
        val ordered = known.sortedBy { (job, _) -> SkyJobCatalog.orderOf(job) }
        val rows = ordered.map { (job, subscription) ->
            SkyRow(
                job = job,
                enabled = subscription.enabled,
                expression = job.expression,
                // A commented-out line is not evaluated. That is not an optimization:
                // a disabled job that still printed a resolved time would be a line
                // claiming to be off while doing the work of being on.
                comment = if (subscription.enabled) comment(job, context) else ""
            )
        }
        return SkyDocument(
            header = header(rows, ordered.map { it.first }, context),
            rows = rows,
            available = SkyJobCatalog.all.filter { job -> known.none { it.first.id == job.id } },
            footer = FOOTER
        )
    }

    private fun header(rows: List<SkyRow>, jobs: List<SkyJob>, context: SkyContext): List<String> =
        buildList {
            add("# sky.crontab — ${context.cityLabel} (${context.zone.id})")
            val disabled = rows.count { !it.enabled }
            val counts = buildString {
                append("# ").append(rows.size).append(if (rows.size == 1) " job" else " jobs")
                if (disabled > 0) append(" · ").append(disabled).append(" disabled")
                nextToFire(jobs, rows, context)?.let { append(" · next: ").append(it) }
            }
            add(counts)
            add("# times are computed per occurrence, not fixed; see each line")
            dstNote(context)?.let { add(it) }
        }

    /** `sun.set in 2h 14m` — the header's one concession to being a queue. */
    private fun nextToFire(jobs: List<SkyJob>, rows: List<SkyRow>, context: SkyContext): String? {
        val enabled = rows.filter { it.enabled }.map { it.job.id }.toSet()
        val next = SkyScheduler.nextToFire(
            jobs.filter { it.id in enabled }, context.now, context.zone, context.coordinates
        ) ?: return null
        return "${next.job.id} in ${humanGap(Duration.between(context.now, next.start))}"
    }

    /**
     * The DST note (`VISION_SKY.md` §11). It is the showcase for the `@daily` choice
     * and not a footnote: the RECURRENCE stays true across the switch while every
     * instant it resolves to jumps an hour, which is exactly why the cron field says
     * `@daily` and the time lives in the comment.
     *
     * Rendered for today and tomorrow, because tonight's jobs are already resolving
     * into a day whose clock is about to move.
     */
    private fun dstNote(context: SkyContext): String? {
        val today = context.now.atZone(context.zone).toLocalDate()
        return listOf(today, today.plusDays(1)).firstNotNullOfOrNull { date ->
            val hours = Duration.between(
                date.atStartOfDay(context.zone), date.plusDays(1).atStartOfDay(context.zone)
            ).toHours()
            // Spelled out rather than signed. `VISION_SKY.md` §11 sketched
            // `# DST +1h on Oct 25`, and on that date the offset goes DOWN an hour
            // while the day gets an hour LONGER: a `+` would have been right twice
            // and wrong twice depending on which of the two the reader meant.
            when {
                hours < 24 -> "# DST: the clock jumps forward 1h on ${date.format(MonthDay)}"
                hours > 24 -> "# DST: the clock falls back 1h on ${date.format(MonthDay)}"
                else -> null
            }
        }
    }

    private val MonthDay = DateTimeFormatter.ofPattern("MMM d")

    /** The comment channel of one line: what the job resolves to, and when. */
    private fun comment(job: SkyJob, context: SkyContext): String {
        // `moon.today` is the odd one: it is not an EVENT the sky has scheduled, it
        // is a statement about the day you are in. Resolved as "the next occurrence"
        // it read `Aug 27 12:00` from six in the evening — a line called `today`
        // naming tomorrow. It answers for today, and it answers without a clock,
        // because the phase is not something that happens at noon.
        if (job.id == SkyJobCatalog.MoonToday.id) {
            return moonSummary(context.now, context)
        }
        val occurrence = SkyScheduler
            .next(job, context.now, context.zone, context.coordinates, limit = 1)
            .firstOrNull() ?: return "?"
        return when (occurrence) {
            is SkyOccurrence.None -> "∅ not scheduled  // ${reason(occurrence.reason)}"
            is SkyOccurrence.At -> instant(job, occurrence, context)
        }
    }

    private fun instant(
        job: SkyJob,
        occurrence: SkyOccurrence.At,
        context: SkyContext
    ): String = buildString {
        val zone = context.zone
        val start = occurrence.start.atZone(zone)
        val today = context.now.atZone(zone).toLocalDate()
        val far = Duration.between(context.now, occurrence.start).toDays() >= 60

        when {
            // An annual job is a date first: `20:14` alone, 351 days out, would read
            // as tonight.
            far -> append(start.format(IsoDate)).append(" ").append(start.format(ClockTime))
            start.toLocalDate() != today -> append(start.format(DayAndClock))
            else -> append(start.format(ClockTime))
        }
        occurrence.end?.let { append("..").append(it.atZone(zone).format(ClockTime)) }

        if (far) append("   in ").append(Duration.between(context.now, occurrence.start).toDays())
            .append("d")

        when (job.id) {
            // How much later the sun comes up than it did yesterday. The one number
            // in the file that is about the schedule DRIFTING, which is the whole
            // reason the cron field cannot carry a fixed minute.
            SkyJobCatalog.SunRise.id, SkyJobCatalog.SunSet.id ->
                driftVsYesterday(job, occurrence.start, context)?.let { append("   ").append(it) }
            SkyJobCatalog.MoonPhase.id ->
                append("   ").append(quarterName(occurrence.start))
            SkyJobCatalog.DarknessWindow.id ->
                moonlessFrom(occurrence, context)?.let { append("   ").append(it) }
        }
    }

    /** `+1m02s vs yesterday` — signed, because in half the year it is negative. */
    private fun driftVsYesterday(job: SkyJob, at: Instant, context: SkyContext): String? {
        val yesterday = at.atZone(context.zone).toLocalDate().minusDays(1)
        val previous = SkyScheduler
            .resolve(job, yesterday, context.zone, context.coordinates)
            as? SkyOccurrence.At ?: return null
        val drift = Duration.between(previous.start, at).minusDays(1)
        val sign = if (drift.isNegative) "−" else "+"
        val seconds = abs(drift.seconds)
        return "$sign${seconds / 60}m${(seconds % 60).toString().padStart(2, '0')}s vs yesterday"
    }

    private fun moonSummary(at: Instant, context: SkyContext): String {
        val day = SkyAlmanac.lunarDay(
            at.atZone(context.zone).toLocalDate(), context.zone, context.coordinates
        )
        val phase = MoonPhase.at(at)
        return "${phase.emoji} ${phase.label.lowercase()}, " +
            "${(day.illuminatedFraction * 100).toInt()}% lit"
    }

    private fun quarterName(at: Instant): String {
        val phase = MoonPhase.at(at)
        return "${phase.emoji} ${phase.label.lowercase()}"
    }

    /**
     * The moonless part of the dark window — the reason `darkness.window` is in the
     * catalog at all. When the moon is up for the whole window the line says so
     * rather than leaving the reader to assume the dark is usable.
     */
    private fun moonlessFrom(occurrence: SkyOccurrence.At, context: SkyContext): String? {
        val end = occurrence.end ?: return null
        val night = occurrence.start.atZone(context.zone).toLocalDate()
        val moonset = listOf(night, night.plusDays(1))
            .mapNotNull { SkyAlmanac.lunarDay(it, context.zone, context.coordinates).moonset }
            .firstOrNull { !it.isBefore(occurrence.start) && it.isBefore(end) }
        return when {
            moonset != null -> "moonless from ${moonset.atZone(context.zone).format(ClockTime)}"
            // Below the horizon for the whole window: the dark is genuinely dark.
            isMoonDown(occurrence.start, context) -> "moonless"
            else -> "moon up all night"
        }
    }

    private fun isMoonDown(at: Instant, context: SkyContext): Boolean =
        AstronomyEngine.moonAltitude(at, context.coordinates) < 0

    private fun reason(reason: SkyNotScheduled): String = when (reason) {
        // Worded for whichever job asks. Written the obvious way — "the sun does not
        // set" — the reason showed up under `sun.rise` too, where it is nonsense.
        SkyNotScheduled.POLAR_DAY -> "polar day: the sun stays above the horizon here"
        SkyNotScheduled.POLAR_NIGHT -> "polar night: the sun stays below the horizon here"
        SkyNotScheduled.MOON_ABSENT -> "the moon does not do that on this calendar day"
        SkyNotScheduled.NO_DARKNESS -> "the sun stays too high: no astronomical night"
    }

    private fun humanGap(gap: Duration): String {
        val minutes = gap.toMinutes()
        return when {
            minutes < 60 -> "${minutes}m"
            minutes < 24 * 60 -> "${minutes / 60}h ${minutes % 60}m"
            else -> "${gap.toDays()}d"
        }
    }

    /** The two things the file must say about itself, every time it is opened. */
    private val FOOTER = listOf(
        "// light pollution is not modelled: the app does not know your sky",
        "// this file is the schedule; whether the clouds allow it comes next"
    )

    /** A commented-out line keeps its columns by moving the `#` into the padding. */
    const val DISABLED_PREFIX = "#"
}
