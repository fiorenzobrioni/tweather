package com.callbackdev.tweather.ui.sky

import com.callbackdev.tweather.data.DefaultUpdateFrequencyMin
import com.callbackdev.tweather.data.SkySubscription
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.MoonPhase
import com.callbackdev.tweather.domain.WeatherFreshness
import com.callbackdev.tweather.domain.model.WeatherReport
import com.callbackdev.tweather.domain.sky.AstronomyEngine
import com.callbackdev.tweather.domain.sky.SkyAlmanac
import com.callbackdev.tweather.domain.sky.SkyJob
import com.callbackdev.tweather.domain.sky.SkyJobCatalog
import com.callbackdev.tweather.domain.sky.SkyLead
import com.callbackdev.tweather.domain.sky.SkyNotScheduled
import com.callbackdev.tweather.domain.sky.SkyOccurrence
import com.callbackdev.tweather.domain.sky.SkyScheduler
import com.callbackdev.tweather.domain.sky.SkyVerdict
import com.callbackdev.tweather.domain.sky.SkyVerdictEngine
import com.callbackdev.tweather.domain.sky.SkyVerdictKind
import com.callbackdev.tweather.domain.sky.SkyVerdictNote
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

    /**
     * Width of the `--notify=…` column, zero when no line in the file has one — a
     * file nobody set a reminder on does not pay a column for the possibility.
     */
    val leadColumnWidth: Int = rows
        .filter { it.lead != SkyLead.OFF }
        .maxOfOrNull { "--notify=${it.lead.label}".length } ?: 0

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
    /**
     * The `--notify=<lead>` argument, or [SkyLead.OFF]. Already RESOLVED against
     * `notify_default`, so this is what the line shows, not what it stores.
     *
     * Rendered since Fase 16f — before it there was a reminder the app could not
     * send, and a token promising one would have been the first thing this module
     * lied about.
     */
    val lead: SkyLead = SkyLead.OFF,
    /** The comment channel: resolved instant, window, `∅` reason. Empty when disabled. */
    val comment: String,
    /**
     * Whether the sky will let it run (Fase 16d). Null for a disabled line and for a
     * `∅` one: there is nothing to have an opinion about.
     */
    val verdict: SkyVerdict? = null,
    /** The resolved start, kept so the dry run can print it beside the verdict. */
    val at: Instant? = null,
    /** The resolved end of a window job. */
    val until: Instant? = null
)

/** Everything [SkyDocumentBuilder] needs to resolve a file; no Android, no clock. */
data class SkyContext(
    val cityLabel: String,
    val coordinates: Coordinates,
    val zone: ZoneId,
    val now: Instant,
    /**
     * The last report the app already has (Fase 16d), or null when none has landed.
     * Never fetched for this file's sake: the schedule needs no network, and the
     * verdicts read whatever the last sync brought back.
     */
    val report: WeatherReport? = null,
    /** Polling interval from `settings.config`; twice it is when data goes stale. */
    val updateFrequencyMin: Int = DefaultUpdateFrequencyMin
) {
    val dataAge: Duration? = report?.let { Duration.between(it.systemInfo.lastSync, now) }

    val staleAfter: Duration = WeatherFreshness.staleAfter(updateFrequencyMin)
}

/**
 * The sentences `sky.crontab` prints, already in the reader's language.
 *
 * The register rule (`PLANNING.md` Fase 18) splits this file's comment channel in
 * two, and the split runs *inside* it. The **evidence column** — the resolved
 * instant, the verdict word, the quantity behind it, the drift — is a readout and
 * stays English: it is the same vocabulary `sky_runs.log` prints and the check
 * lines match, and one translated fragment would leave an aligned column speaking
 * two languages. The **explanations** — why a job is not scheduled, why there is no
 * verdict, what the moon is doing, and every whole-line note — exist only to be
 * understood, so they are prose and they are here.
 *
 * They arrive as strings rather than through a `Resources` because
 * [SkyDocumentBuilder] is a pure value with no Android in it and its tests are
 * plain JVM tests. [EN] is the one place the English lives in Kotlin, and
 * `SkyNotesTest` asserts it says exactly what `values/strings.xml` says — so the
 * two copies cannot drift apart without the suite going red.
 */
data class SkyNotes(
    val times: String,
    val dstForward: (String) -> String,
    val dstBack: (String) -> String,
    val footer: List<String>,
    val polarDay: String,
    val polarNight: String,
    val moonAbsent: String,
    val noDarkness: String,
    val beyondHorizon: String,
    val noFetchYet: String,
    val staleData: String,
    val noCoverage: String,
    val moonlessFrom: (String) -> String,
    val moonless: String,
    val moonAllNight: String,
    /**
     * A moon phase is a weather **value**, and values have localized since Fase 6b
     * — this module had simply never asked, so `full moon` sat in the crontab while
     * `weather_data.json` two tabs away said `luna piena`.
     */
    val moonPhase: (String) -> String
) {
    companion object {
        /** The English, in one place, tied to `values/strings.xml` by a test. */
        val EN: SkyNotes = SkyNotes(
            times = "times are computed per occurrence, not fixed; see each line",
            dstForward = { "the clock jumps forward 1h on $it" },
            dstBack = { "the clock falls back 1h on $it" },
            footer = listOf(
                "pass ≤ ${SkyVerdictEngine.CLOUD_PASS_PCT}% cloud · " +
                    "fail above ${SkyVerdictEngine.CLOUD_FAIL_PCT}% · " +
                    "rain ≥ ${SkyVerdictEngine.PRECIP_FAIL_PCT}% fails it whatever the sky does",
                "a bright moon (≥ ${SkyVerdictEngine.MOON_WASH_PCT}%) unsettles a dark-sky job " +
                    "under a clear sky",
                "light pollution is not modelled: the app does not know your sky",
                "a verdict is the forecast's opinion, not an observation; it will change"
            ),
            polarDay = "polar day: the sun stays above the horizon here",
            polarNight = "polar night: the sun stays below the horizon here",
            moonAbsent = "the moon does not do that on this calendar day",
            noDarkness = "the sun stays too high: no astronomical night",
            beyondHorizon = "past the forecast horizon",
            noFetchYet = "no fetch yet",
            staleData = "no recent data",
            noCoverage = "no forecast hour covers it",
            moonlessFrom = { "moonless from $it" },
            moonless = "moonless",
            moonAllNight = "moon up all night",
            moonPhase = { it }
        )
    }
}

object SkyDocumentBuilder {

    /**
     * `$ tweather run sky` — every enabled job, its instant and its verdict, in one
     * aligned block. It sends nothing, touches no state and writes no run record,
     * exactly like `$ tweather run rules`.
     *
     * It is a SECOND VIEW of facts the rows already carry, and that is the point: a
     * resolved crontab row is wide enough to pan sideways, so the verdicts are the
     * one thing you cannot take in at a glance from the file itself. Here they line
     * up under each other, with the window each one was computed over spelled out
     * rather than abbreviated to fit a column.
     */
    fun dryRun(
        document: SkyDocument,
        context: SkyContext,
        notes: SkyNotes = SkyNotes.EN
    ): List<String> =
        document.rows.filter { it.enabled }.map { row ->
            buildString {
                append("// ").append(row.job.id.padEnd(document.nameColumnWidth + 1))
                // A job with no verdict prints the FACT it resolved to instead of a
                // window and a dash. `moon.today` used to print `12:00` here, which is
                // the instant its phase is measured at and means nothing to a reader.
                if (row.verdict == null) {
                    append(row.comment)
                    return@buildString
                }
                append(window(row, context).padEnd(WINDOW_COLUMN))
                append(render(row.verdict, notes))
            }
        }

    /** `19:32..20:12`, or `2027-08-13` when the event is not in the next day or two. */
    private fun window(row: SkyRow, context: SkyContext): String {
        val at = row.at ?: return ""
        val far = Duration.between(context.now, at).toDays() >= 2
        if (far) return at.atZone(context.zone).format(IsoDate)
        val start = clock(at, context)
        return row.until?.let { "$start..${clock(it, context)}" } ?: start
    }

    private fun clock(at: Instant, context: SkyContext): String =
        at.atZone(context.zone).format(ClockTime)

    /** Wide enough for `00:32..04:22`, so the verdict column lines up under itself. */
    private const val WINDOW_COLUMN = 14

    private val ClockTime = DateTimeFormatter.ofPattern("HH:mm")
    private val DayAndClock = DateTimeFormatter.ofPattern("MMM d HH:mm")
    private val IsoDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun build(
        subscriptions: List<SkySubscription>,
        context: SkyContext,
        /**
         * `notify_default`: the lead a line uses when it carries none of its own.
         * The README and the widget line pass nothing — they render no `--notify`
         * token, so the fallback would only be a value they compute and discard.
         */
        defaultLeadMinutes: Int? = null,
        notes: SkyNotes = SkyNotes.EN
    ): SkyDocument {
        val known = subscriptions.mapNotNull { subscription ->
            SkyJobCatalog.byId(subscription.jobId)?.let { it to subscription }
        }
        // File order is the CATALOG's, never the subscription list's and never "what
        // fires next": a crontab is a file. The next job to fire is the header's job.
        val ordered = known.sortedBy { (job, _) -> SkyJobCatalog.orderOf(job) }
        val rows = ordered.map { (job, subscription) ->
            // A commented-out line is not evaluated. That is not an optimization: a
            // disabled job that still printed a resolved time would be a line
            // claiming to be off while doing the work of being on.
            val lead = SkyLead.ofMinutes(subscription.notifyLeadMinutes ?: defaultLeadMinutes)
            if (!subscription.enabled) {
                return@map SkyRow(
                    job, enabled = false, expression = job.expression, lead = lead, comment = ""
                )
            }
            val occurrence = SkyScheduler
                .next(job, context.now, context.zone, context.coordinates, limit = 1)
                .firstOrNull()
            val at = occurrence as? SkyOccurrence.At
            val verdict = at?.let { verdictOf(job, it, context) }
            SkyRow(
                job = job,
                enabled = true,
                expression = job.expression,
                lead = lead,
                comment = comment(job, occurrence, verdict, context, notes),
                verdict = verdict,
                at = at?.start,
                until = at?.end
            )
        }
        return SkyDocument(
            header = header(rows, ordered.map { it.first }, context, notes),
            rows = rows,
            available = SkyJobCatalog.all.filter { job -> known.none { it.first.id == job.id } },
            footer = notes.footer.map { "// $it" }
        )
    }

    private fun header(
        rows: List<SkyRow>,
        jobs: List<SkyJob>,
        context: SkyContext,
        notes: SkyNotes
    ): List<String> =
        buildList {
            // `·` and not an em dash: the file already separates with `·` on the
            // next line, and the dash was the one typographic mark it borrowed for a
            // single use. It also keeps the README able to quote this line verbatim,
            // which its house style forbids a dash from doing.
            add("# sky.crontab · ${context.cityLabel} (${context.zone.id})")
            val disabled = rows.count { !it.enabled }
            val counts = buildString {
                append("# ").append(rows.size).append(if (rows.size == 1) " job" else " jobs")
                if (disabled > 0) append(" · ").append(disabled).append(" disabled")
                nextToFire(jobs, rows, context)?.let { append(" · next: ").append(it) }
            }
            add(counts)
            add("# " + notes.times)
            dstNote(context, notes)?.let { add(it) }
        }

    /** `sun.set in 2h 14m ✓` — the header's one concession to being a queue. */
    private fun nextToFire(jobs: List<SkyJob>, rows: List<SkyRow>, context: SkyContext): String? {
        val enabled = rows.filter { it.enabled }.map { it.job.id }.toSet()
        val next = SkyScheduler.nextToFire(
            jobs.filter { it.id in enabled }, context.now, context.zone, context.coordinates
        ) ?: return null
        return buildString {
            append(next.job.id)
            append(" in ").append(humanGap(Duration.between(context.now, next.start)))
            // The glyph alone up here, not the whole verdict: the header is one line
            // and the reasoning belongs on the job's own row, where its numbers are.
            rows.firstOrNull { it.job.id == next.job.id }?.verdict
                ?.takeIf { it.isKnown }
                ?.let { append(" ").append(it.kind.glyph) }
        }
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
    private fun dstNote(context: SkyContext, notes: SkyNotes): String? {
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
                hours < 24 -> "# DST: " + notes.dstForward(date.format(MonthDay))
                hours > 24 -> "# DST: " + notes.dstBack(date.format(MonthDay))
                else -> null
            }
        }
    }

    private val MonthDay = DateTimeFormatter.ofPattern("MMM d")

    /**
     * The verdict on a resolved occurrence, or null when the job cannot have one.
     *
     * The moments of pure geometry are excluded — the solstice, the instant of a
     * quarter, solar noon. They happen at a computed time whether or not anybody can
     * see them, so "will the clouds allow it" is not a question about them, and a
     * `✗ fail` on a first quarter would be the file inventing a stake nobody has.
     *
     * Everything else gets one, `sun.set` first among them: whether tonight's sunset
     * is worth walking outside for is the question this whole module exists to
     * answer. (An earlier cut keyed this off `visibilityDependent`, which is a
     * different predicate — it governs whether a REMINDER is suppressed on a fail —
     * and it silently left the headline case without a verdict.)
     */
    private fun verdictOf(job: SkyJob, at: SkyOccurrence.At, context: SkyContext): SkyVerdict? {
        if (!job.observable) return null
        return SkyVerdictEngine.evaluate(
            job = job,
            start = at.start,
            end = at.end,
            hours = context.report?.hourly.orEmpty(),
            zone = context.zone,
            coordinates = context.coordinates,
            dataAge = context.dataAge,
            staleAfter = context.staleAfter
        )
    }

    /** The comment channel of one line: what the job resolves to, when, and whether. */
    private fun comment(
        job: SkyJob,
        occurrence: SkyOccurrence?,
        verdict: SkyVerdict?,
        context: SkyContext,
        notes: SkyNotes
    ): String {
        // `moon.today` is the odd one: it is not an EVENT the sky has scheduled, it
        // is a statement about the day you are in. Resolved as "the next occurrence"
        // it read `Aug 27 12:00` from six in the evening — a line called `today`
        // naming tomorrow. It answers for today, and it answers without a clock,
        // because the phase is not something that happens at noon.
        if (job.id == SkyJobCatalog.MoonToday.id) {
            return moonSummary(context.now, context, notes)
        }
        return when (occurrence) {
            null -> "?"
            // `∅ not scheduled` is the state name and stays; the `//` after it
            // introduces a sentence, so that half is the reader's (Fase 18).
            is SkyOccurrence.None -> "∅ not scheduled  // ${reason(occurrence.reason, notes)}"
            is SkyOccurrence.At -> instant(job, occurrence, verdict, context, notes)
        }
    }

    /**
     * `✓ pass  cloud 8%` — the glyph, the word, and the NUMBER the verdict was built
     * from. §7 of `VISION_SKY.md` asks for the number by name: a verdict whose
     * evidence is invisible is an opinion, and this app does not print opinions.
     */
    fun render(verdict: SkyVerdict, notes: SkyNotes = SkyNotes.EN): String = buildString {
        append(verdict.kind.glyph).append(" ").append(verdict.kind.word)
        // Parentheses, not a `//`: on a row the verdict can be followed by the job's
        // own trivia (the sunrise drift, the moonless hour), and `// no fetch yet`
        // with three more words after it reads as a comment that failed to comment.
        // `//` stays for the `∅` lines, where nothing ever follows.
        when (verdict.note) {
            SkyVerdictNote.BEYOND_HORIZON -> append(" (${notes.beyondHorizon})")
            SkyVerdictNote.NO_DATA -> append(" (${notes.noFetchYet})")
            SkyVerdictNote.STALE_DATA -> append(" (${notes.staleData})")
            SkyVerdictNote.NO_COVERAGE -> append(" (${notes.noCoverage})")
            // Naming the clouds for a night the MOON ruined would be a different lie
            // of the same size.
            SkyVerdictNote.MOONLIGHT -> append("  moon ${verdict.moonPct}% and up")
            SkyVerdictNote.PRECIPITATION -> append("  rain ${verdict.precipPct}%")
            null -> Unit
        }
        if (verdict.isKnown && verdict.note != SkyVerdictNote.MOONLIGHT) {
            verdict.cloudPct?.let { append("  cloud ").append(it).append("%") }
        }
    }

    private fun instant(
        job: SkyJob,
        occurrence: SkyOccurrence.At,
        verdict: SkyVerdict?,
        context: SkyContext,
        notes: SkyNotes
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

        // The verdict comes straight after the WHEN, before any per-job trivia: it is
        // the answer to the question the file is opened with. The sunrise drift used
        // to sit between them, so `✓ pass` arrived third on the line behind a figure
        // in seconds that nobody came for.
        verdict?.let { append("   ").append(render(it, notes)) }

        when (job.id) {
            // How much later the sun comes up than it did yesterday. The one number
            // in the file that is about the schedule DRIFTING, which is the whole
            // reason the cron field cannot carry a fixed minute.
            SkyJobCatalog.SunRise.id, SkyJobCatalog.SunSet.id ->
                driftVsYesterday(job, occurrence.start, context)?.let { append("   ").append(it) }
            SkyJobCatalog.MoonPhase.id ->
                append("   ").append(quarterName(occurrence.start, notes))
            // The `moonless from 23:11` suffix and a MOONLIGHT verdict are the same
            // sentence twice: one says when the moon goes, the other how bright it
            // is. When the verdict has already named the moon, the suffix stands down.
            SkyJobCatalog.DarknessWindow.id ->
                if (verdict?.note != SkyVerdictNote.MOONLIGHT) {
                    moonlessFrom(occurrence, context, notes)?.let { append("   ").append(it) }
                }
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

    private fun moonSummary(at: Instant, context: SkyContext, notes: SkyNotes): String {
        val day = SkyAlmanac.lunarDay(
            at.atZone(context.zone).toLocalDate(), context.zone, context.coordinates
        )
        val phase = MoonPhase.at(at)
        return "${phase.emoji} ${notes.moonPhase(phase.label).lowercase()}, " +
            "${(day.illuminatedFraction * 100).toInt()}% lit"
    }

    private fun quarterName(at: Instant, notes: SkyNotes): String {
        val phase = MoonPhase.at(at)
        return "${phase.emoji} ${notes.moonPhase(phase.label).lowercase()}"
    }

    /**
     * The moonless part of the dark window — the reason `darkness.window` is in the
     * catalog at all. When the moon is up for the whole window the line says so
     * rather than leaving the reader to assume the dark is usable.
     */
    private fun moonlessFrom(
        occurrence: SkyOccurrence.At,
        context: SkyContext,
        notes: SkyNotes
    ): String? {
        val end = occurrence.end ?: return null
        val night = occurrence.start.atZone(context.zone).toLocalDate()
        val moonset = listOf(night, night.plusDays(1))
            .mapNotNull { SkyAlmanac.lunarDay(it, context.zone, context.coordinates).moonset }
            .firstOrNull { !it.isBefore(occurrence.start) && it.isBefore(end) }
        return when {
            moonset != null ->
                notes.moonlessFrom(moonset.atZone(context.zone).format(ClockTime))
            // Below the horizon for the whole window: the dark is genuinely dark.
            isMoonDown(occurrence.start, context) -> notes.moonless
            else -> notes.moonAllNight
        }
    }

    private fun isMoonDown(at: Instant, context: SkyContext): Boolean =
        AstronomyEngine.moonAltitude(at, context.coordinates) < 0

    private fun reason(reason: SkyNotScheduled, notes: SkyNotes): String = when (reason) {
        // Worded for whichever job asks. Written the obvious way — "the sun does not
        // set" — the reason showed up under `sun.rise` too, where it is nonsense.
        SkyNotScheduled.POLAR_DAY -> notes.polarDay
        SkyNotScheduled.POLAR_NIGHT -> notes.polarNight
        SkyNotScheduled.MOON_ABSENT -> notes.moonAbsent
        SkyNotScheduled.NO_DARKNESS -> notes.noDarkness
    }

    private fun humanGap(gap: Duration): String {
        val minutes = gap.toMinutes()
        return when {
            minutes < 60 -> "${minutes}m"
            minutes < 24 * 60 -> "${minutes / 60}h ${minutes % 60}m"
            else -> "${gap.toDays()}d"
        }
    }

    /**
     * What the file must say about itself every time it is opened.
     *
     * The thresholds are stated HERE rather than in `settings.config`
     * (`VISION_SKY.md` §7 asked that they not be invisible, not that they be
     * adjustable), and the last line is the module's whole epistemic position in
     * nine words: the app never looks at the sky, it reads a forecast.
     */
    // The lines themselves live in [SkyNotes], because they are four sentences and
    // sentences are the reader's (Fase 18). The `//` is added here: the marker is
    // the file's syntax and does not translate.

    /** A commented-out line keeps its columns by moving the `#` into the padding. */
    const val DISABLED_PREFIX = "#"
}
