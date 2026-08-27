package com.callbackdev.tweather.ui.sky

import com.callbackdev.tweather.data.SkySubscription
import com.callbackdev.tweather.domain.model.MoonPhase
import com.callbackdev.tweather.domain.sky.SkyAlmanac
import com.callbackdev.tweather.domain.sky.SkyJob
import com.callbackdev.tweather.domain.sky.SkyJobCatalog
import com.callbackdev.tweather.domain.sky.SkyOccurrence
import com.callbackdev.tweather.domain.sky.SkyScheduler
import com.callbackdev.tweather.domain.sky.SkyVerdict
import com.callbackdev.tweather.domain.sky.SkyVerdictKind
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * What the sky contributes to the city's `README.md` (Fase 16e), as DATA.
 *
 * Times and numbers only, no words: [com.callbackdev.tweather.ui.weather.toReadmeMarkdown]
 * renders them with its own string resources, because the README is prose and prose
 * is localized in exactly one place in this app. Keeping the sentence-building out of
 * here is also what makes the agreement test possible — the same instants feed
 * `sky.crontab` and this summary, so a test can assert the two files never disagree.
 */
data class SkySummary(
    val goldenHourEvening: ClosedRange<LocalTime>?,
    val blueHourEvening: ClosedRange<LocalTime>?,
    val darkness: ClosedRange<LocalTime>?,
    /** When the moon leaves the dark window, if it does while the window is open. */
    val moonlessFrom: LocalTime?,
    /** True when the moon is up for the whole dark window. */
    val moonUpAllNight: Boolean,
    val moonPhase: MoonPhase,
    val illuminationPct: Int,
    val moonrise: LocalTime?,
    val moonset: LocalTime?,
    /** The one thing worth a `>` in `## Status`, or null when nothing is. */
    val warning: SkyWarning?
)

/**
 * A subscribed job in the next half day whose verdict is not a pass. It is the only
 * thing the sky is allowed to say in `## Status`, and only when the user has actually
 * put that job in their file: the section reports YOUR subscriptions, it does not
 * advertise the module to somebody who never opened it.
 *
 * [jobId] is the catalog id, as everywhere else in this module: the README looks its
 * NAME up in [SkyJobNames] and never prints the id itself (Fase 16g).
 */
data class SkyWarning(val jobId: String, val verdict: SkyVerdict, val at: Instant)

object SkyReadme {

    /** How far ahead `## Status` looks for something to warn about. */
    private val WARNING_HORIZON: Duration = Duration.ofHours(12)

    fun summarize(context: SkyContext, subscriptions: List<SkySubscription>): SkySummary {
        val today = context.now.atZone(context.zone).toLocalDate()
        val solar = SkyAlmanac.solarDay(today, context.zone, context.coordinates)
        val lunar = SkyAlmanac.lunarDay(today, context.zone, context.coordinates)
        val darknessOccurrence = SkyScheduler.resolve(
            SkyJobCatalog.DarknessWindow, today, context.zone, context.coordinates
        ) as? SkyOccurrence.At
        val moonlessFrom = darknessOccurrence?.let { moonlessFrom(it, context) }

        fun clock(at: Instant?) = at?.atZone(context.zone)?.toLocalTime()
        fun range(range: ClosedRange<Instant>?) = range?.let {
            clock(it.start)!!..clock(it.endInclusive)!!
        }

        return SkySummary(
            goldenHourEvening = range(solar.goldenHourEvening),
            blueHourEvening = range(solar.blueHourEvening),
            darkness = darknessOccurrence?.end?.let { clock(darknessOccurrence.start)!!..clock(it)!! },
            moonlessFrom = moonlessFrom,
            moonUpAllNight = darknessOccurrence != null && moonlessFrom == null &&
                moonIsUp(darknessOccurrence.start, context),
            moonPhase = MoonPhase.at(context.now),
            illuminationPct = (lunar.illuminatedFraction * 100).roundToInt(),
            moonrise = clock(lunar.moonrise),
            moonset = clock(lunar.moonset),
            warning = warning(context, subscriptions)
        )
    }

    /**
     * The worst thing coming in the next twelve hours among the jobs the user
     * subscribed to — and nothing at all if they subscribed to none of it.
     *
     * Only one, and only in `## Status`: the README has exactly one place where it
     * says something is off, and a section that grew a list of them would stop being
     * a badge.
     */
    private fun warning(context: SkyContext, subscriptions: List<SkySubscription>): SkyWarning? {
        val document = SkyDocumentBuilder.build(subscriptions, context)
        return document.rows
            .asSequence()
            .filter { it.enabled && it.at != null && it.verdict != null }
            // A sunset that will be overcast is worth a line; a solstice is not, and
            // neither is a job whose verdict the app does not have.
            .filter { it.job.visibilityDependent || it.job.id in EVENING_JOBS }
            .filter { it.verdict!!.kind == SkyVerdictKind.UNSTABLE || it.verdict.kind == SkyVerdictKind.FAIL }
            .filter { Duration.between(context.now, it.at!!) <= WARNING_HORIZON }
            .minByOrNull { it.at!! }
            ?.let { SkyWarning(it.job.id, it.verdict!!, it.at!!) }
    }

    /**
     * The sun jobs that are worth a warning even though they happen regardless: an
     * overcast sunset is the thing this module was built to tell you about, and
     * `visibilityDependent` is false for it on purpose (its REMINDER still goes out).
     */
    private val EVENING_JOBS = setOf(
        SkyJobCatalog.SunSet.id,
        SkyJobCatalog.SunRise.id
    )

    private fun moonlessFrom(occurrence: SkyOccurrence.At, context: SkyContext): LocalTime? {
        val end = occurrence.end ?: return null
        val night = occurrence.start.atZone(context.zone).toLocalDate()
        return listOf(night, night.plusDays(1))
            .mapNotNull { SkyAlmanac.lunarDay(it, context.zone, context.coordinates).moonset }
            .firstOrNull { !it.isBefore(occurrence.start) && it.isBefore(end) }
            ?.atZone(context.zone)?.toLocalTime()
    }

    private fun moonIsUp(at: Instant, context: SkyContext): Boolean =
        com.callbackdev.tweather.domain.sky.AstronomyEngine
            .moonAltitude(at, context.coordinates) > 0

    /** Whether a job is one the summary would ever mention. */
    fun mentions(job: SkyJob): Boolean = job.observable
}
