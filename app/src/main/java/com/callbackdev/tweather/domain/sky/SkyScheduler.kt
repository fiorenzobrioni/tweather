package com.callbackdev.tweather.domain.sky

import com.callbackdev.tweather.domain.model.Coordinates
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Why a job has no occurrence on a given day — the `∅ not scheduled` state's cause. */
enum class SkyNotScheduled {
    /** The sun does not set today: above the horizon for the whole local day. */
    POLAR_DAY,

    /** The sun does not rise today. */
    POLAR_NIGHT,

    /** No moonrise (or moonset) falls inside this calendar day — happens ~monthly. */
    MOON_ABSENT,

    /** The sun never gets 18° below the horizon: no astronomical darkness tonight. */
    NO_DARKNESS
}

/**
 * One resolved line of `sky.crontab`. [SkyOccurrence.At] carries the instant (and the
 * far end, for a job shaped as a window); [SkyOccurrence.None] carries the reason
 * there isn't one.
 *
 * There is deliberately no third case for "we could not work it out". The whole
 * module is local computation: given a latitude and a date the answer exists, and if
 * it doesn't exist the reason is a fact about the sky, not about the app.
 */
sealed interface SkyOccurrence {
    val job: SkyJob

    data class At(
        override val job: SkyJob,
        val start: Instant,
        /** Null for an instant job; the far end for a window. */
        val end: Instant? = null
    ) : SkyOccurrence {
        val isRange: Boolean get() = end != null
    }

    data class None(override val job: SkyJob, val reason: SkyNotScheduled) : SkyOccurrence
}

/**
 * Turns a subscribed [SkyJob] into the occurrences that follow a given instant (Fase
 * 16b). Pure, like [AstronomyEngine] under it: coordinates, zone, an instant in, a
 * list of occurrences out.
 *
 * **Everything is resolved in the CITY's zone**, never the phone's. A sunrise in
 * Tokyo rendered on Rome's clock is the file lying, and the zone is the one piece of
 * context that has to travel all the way down from the active city to this call.
 *
 * The daily jobs walk forward one local calendar day at a time — `date.plusDays(1)`,
 * not "+24 hours" — so a DST switch shortens or lengthens the day the way the clock
 * really does instead of leaving an hour unsearched.
 */
object SkyScheduler {

    /** The occurrence of [job] on the local day [date], with the reason when there is none. */
    fun resolve(
        job: SkyJob,
        date: LocalDate,
        zone: ZoneId,
        coords: Coordinates
    ): SkyOccurrence {
        MeteorShowerTable.showerOf(job.id)?.let { return meteorPeak(job, it, date.year, zone, coords) }
        return when (job.id) {
            SkyJobCatalog.SunRise.id -> solar(job, date, zone, coords) { it.sunrise }
            SkyJobCatalog.SunSet.id -> solar(job, date, zone, coords) { it.sunset }
            SkyJobCatalog.SolarNoon.id -> SkyOccurrence.At(
                job, AstronomyEngine.solarDay(date, zone, coords).solarNoon
            )
            SkyJobCatalog.CivilAm.id -> solar(job, date, zone, coords) { it.civilDawn }
            SkyJobCatalog.CivilPm.id -> solar(job, date, zone, coords) { it.civilDusk }
            SkyJobCatalog.NauticalAm.id -> solar(job, date, zone, coords) { it.nauticalDawn }
            SkyJobCatalog.NauticalPm.id -> solar(job, date, zone, coords) { it.nauticalDusk }
            SkyJobCatalog.AstronomicalAm.id -> solar(job, date, zone, coords) { it.astronomicalDawn }
            SkyJobCatalog.AstronomicalPm.id -> solar(job, date, zone, coords) { it.astronomicalDusk }
            SkyJobCatalog.GoldenAm.id -> solarRange(job, date, zone, coords) { it.goldenHourMorning }
            SkyJobCatalog.GoldenPm.id -> solarRange(job, date, zone, coords) { it.goldenHourEvening }
            SkyJobCatalog.BlueAm.id -> solarRange(job, date, zone, coords) { it.blueHourMorning }
            SkyJobCatalog.BluePm.id -> solarRange(job, date, zone, coords) { it.blueHourEvening }
            SkyJobCatalog.DarknessWindow.id -> darkness(job, date, zone, coords)
            SkyJobCatalog.MoonRise.id -> lunar(job, date, zone, coords) { it.moonrise }
            SkyJobCatalog.MoonSet.id -> lunar(job, date, zone, coords) { it.moonset }
            // The phase is a statement about the day, so it lands at local noon —
            // the same instant its illumination is measured at.
            SkyJobCatalog.MoonToday.id -> SkyOccurrence.At(
                job, date.atTime(LocalTime.NOON).atZone(zone).toInstant()
            )
            SkyJobCatalog.MoonPhase.id -> SkyOccurrence.At(
                job,
                AstronomyEngine.nextMoonQuarter(date.atStartOfDay(zone).toInstant()).at
            )
            SkyJobCatalog.EquinoxSpring.id -> season(job, date.year, Season.MARCH_EQUINOX)
            SkyJobCatalog.SolsticeSummer.id -> season(job, date.year, Season.JUNE_SOLSTICE)
            SkyJobCatalog.EquinoxAutumn.id -> season(job, date.year, Season.SEPTEMBER_EQUINOX)
            SkyJobCatalog.SolsticeWinter.id -> season(job, date.year, Season.DECEMBER_SOLSTICE)
            else -> error("unknown sky job ${job.id}")
        }
    }

    /**
     * The next [limit] occurrences of [job] strictly after [from].
     *
     * A day (or a year) whose answer is `∅` is REPORTED, not skipped: "the moon does
     * not rise today" is the occurrence, and a scheduler that quietly jumped to the
     * next day that worked would turn a fact about the sky into a gap in the file.
     */
    fun next(
        job: SkyJob,
        from: Instant,
        zone: ZoneId,
        coords: Coordinates,
        limit: Int = 1
    ): List<SkyOccurrence> {
        require(limit > 0) { "limit must be positive" }
        val results = mutableListOf<SkyOccurrence>()
        var date = from.atZone(zone).toLocalDate()
        var guard = 0
        while (results.size < limit && guard++ < MAX_STEPS) {
            val occurrence = resolve(job, date, zone, coords)
            val startsAfter = occurrence !is SkyOccurrence.At || occurrence.start.isAfter(from)
            if (startsAfter) results += occurrence
            date = when (job.kind) {
                SkyJobKind.DAILY -> date.plusDays(1)
                // A polling job has one "next" by definition: the following quarter
                // is found from the one just returned, not from the following day.
                SkyJobKind.POLLING -> return pollingSeries(job, from, zone, coords, limit)
                SkyJobKind.ANNUAL -> date.plusYears(1).withDayOfYear(1)
            }
        }
        return results
    }

    /** The first of [jobs] to fire after [from] — the crontab header's `# next:` line. */
    fun nextToFire(
        jobs: List<SkyJob>,
        from: Instant,
        zone: ZoneId,
        coords: Coordinates
    ): SkyOccurrence.At? = jobs
        .flatMap { next(it, from, zone, coords, limit = 1) }
        .filterIsInstance<SkyOccurrence.At>()
        .minByOrNull { it.start }

    // ------------------------------------------------------------- resolution

    private inline fun solar(
        job: SkyJob,
        date: LocalDate,
        zone: ZoneId,
        coords: Coordinates,
        pick: (SolarDay) -> Instant?
    ): SkyOccurrence {
        val day = AstronomyEngine.solarDay(date, zone, coords)
        val instant = pick(day) ?: return SkyOccurrence.None(job, polarReason(day))
        return SkyOccurrence.At(job, instant)
    }

    private inline fun solarRange(
        job: SkyJob,
        date: LocalDate,
        zone: ZoneId,
        coords: Coordinates,
        pick: (SolarDay) -> ClosedRange<Instant>?
    ): SkyOccurrence {
        val day = AstronomyEngine.solarDay(date, zone, coords)
        val range = pick(day) ?: return SkyOccurrence.None(job, polarReason(day))
        return SkyOccurrence.At(job, range.start, range.endInclusive)
    }

    private inline fun lunar(
        job: SkyJob,
        date: LocalDate,
        zone: ZoneId,
        coords: Coordinates,
        pick: (LunarDay) -> Instant?
    ): SkyOccurrence {
        val instant = pick(AstronomyEngine.lunarDay(date, zone, coords))
            ?: return SkyOccurrence.None(job, SkyNotScheduled.MOON_ABSENT)
        return SkyOccurrence.At(job, instant)
    }

    /**
     * Astronomical dusk tonight to astronomical dawn tomorrow. Deliberately NOT the
     * dusk-to-dawn pair inside one calendar day: those are the two ends of two
     * different nights, and a window that ran from tonight's dusk back to this
     * morning's dawn would be a negative-length night rendered as a fact.
     */
    private fun darkness(
        job: SkyJob,
        date: LocalDate,
        zone: ZoneId,
        coords: Coordinates
    ): SkyOccurrence {
        val tonight = AstronomyEngine.solarDay(date, zone, coords)
        val tomorrow = AstronomyEngine.solarDay(date.plusDays(1), zone, coords)
        val dusk = tonight.astronomicalDusk
        val dawn = tomorrow.astronomicalDawn
        if (dusk == null || dawn == null) return SkyOccurrence.None(job, SkyNotScheduled.NO_DARKNESS)
        return SkyOccurrence.At(job, dusk, dawn)
    }

    private fun season(job: SkyJob, year: Int, season: Season): SkyOccurrence =
        SkyOccurrence.At(job, AstronomyEngine.season(year, season))

    /**
     * A shower's peak as the local NIGHT it falls in, not the bare instant: from
     * astronomical dusk (or the peak itself, whichever is later) to dawn. A stream is
     * a degree wide and the app is not going to pretend otherwise.
     */
    private fun meteorPeak(
        job: SkyJob,
        shower: MeteorShowerTable.MeteorShower,
        year: Int,
        zone: ZoneId,
        coords: Coordinates
    ): SkyOccurrence {
        val peak = AstronomyEngine.solarLongitudeInstant(year, shower.solarLongitudeDeg)
        val night = peak.atZone(zone).toLocalDate()
        // The peak can fall in the small hours, which belong to the PREVIOUS evening's
        // night: dusk then lies on the day before.
        val eveningOf = if (peak.atZone(zone).hour < NIGHT_ENDS_HOUR) night.minusDays(1) else night
        val dusk = AstronomyEngine.solarDay(eveningOf, zone, coords).astronomicalDusk
        val dawn = AstronomyEngine.solarDay(eveningOf.plusDays(1), zone, coords).astronomicalDawn
        if (dusk == null || dawn == null) return SkyOccurrence.None(job, SkyNotScheduled.NO_DARKNESS)
        return SkyOccurrence.At(job, maxOf(dusk, peak.coerceAtMost(dawn)), dawn)
    }

    private fun pollingSeries(
        job: SkyJob,
        from: Instant,
        zone: ZoneId,
        coords: Coordinates,
        limit: Int
    ): List<SkyOccurrence> {
        // `nextMoonQuarter` is strictly-after, so feeding it its own answer walks the
        // series. That guard lives in the engine rather than here, where the second
        // caller to need it would have had to remember it.
        var at = from
        return List(limit) {
            val quarter = AstronomyEngine.nextMoonQuarter(at)
            at = quarter.at
            SkyOccurrence.At(job, quarter.at)
        }
    }

    private fun polarReason(day: SolarDay): SkyNotScheduled = when {
        day.sunUpAllDay -> SkyNotScheduled.POLAR_DAY
        day.sunDownAllDay -> SkyNotScheduled.POLAR_NIGHT
        // The sun rises and sets but never reaches the requested depth: a white night
        // at 60°N has no astronomical dusk without having polar anything.
        else -> SkyNotScheduled.NO_DARKNESS
    }

    /** Local hour before which the small hours still belong to the previous night. */
    private const val NIGHT_ENDS_HOUR = 12

    /**
     * Ceiling on the day-by-day walk. A daily job answers every day, `∅` included, so
     * the loop cannot actually spin — this is here so a future job with a rarer
     * recurrence cannot turn a scroll of the crontab into a hang.
     */
    private const val MAX_STEPS = 400
}
