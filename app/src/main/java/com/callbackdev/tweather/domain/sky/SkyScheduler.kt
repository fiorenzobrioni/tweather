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
    NO_DARKNESS,

    /** The night is astronomically dark all year here: there are no white nights. */
    DARKNESS_ALL_YEAR,

    /**
     * The ecliptic lies too flat on the horizon for the zodiacal light to stand out
     * of it — a fact about the season, not about tonight's weather.
     */
    ECLIPTIC_TOO_FLAT,

    /** The galactic core does not get high enough above the horizon tonight. */
    CORE_TOO_LOW,

    /** No eclipse of this kind is visible from here inside the search horizon. */
    NO_ECLIPSE_AHEAD
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
 *
 * Solar and lunar days come through [SkyAlmanac] rather than straight from the
 * engine: `sky.crontab` resolves every subscribed job against the same date, and
 * without the memo one screen would recompute one day thirty-two times.
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
                job, SkyAlmanac.solarDay(date, zone, coords).solarNoon
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
            SkyJobCatalog.MilkyWayCore.id -> milkyWayCore(job, date, zone, coords)
            SkyJobCatalog.ZodiacalPm.id -> zodiacal(job, date, zone, coords, evening = true)
            SkyJobCatalog.ZodiacalAm.id -> zodiacal(job, date, zone, coords, evening = false)
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
            in QuarterJobs.keys -> SkyOccurrence.At(
                job,
                AstronomyEngine.nextMoonQuarter(
                    date.atStartOfDay(zone).toInstant(), QuarterJobs.getValue(job.id)
                ).at
            )
            SkyJobCatalog.MoonClosestFull.id -> yearly(job, date, zone, coords) {
                YearEvents.closestFullMoon(date.year)
            }
            SkyJobCatalog.LunarEclipse.id -> lunarEclipse(job, date, zone, coords)
            SkyJobCatalog.SolarEclipse.id -> solarEclipse(job, date, zone, coords)
            SkyJobCatalog.EquinoxSpring.id -> season(job, date.year, Season.MARCH_EQUINOX)
            SkyJobCatalog.SolsticeSummer.id -> season(job, date.year, Season.JUNE_SOLSTICE)
            SkyJobCatalog.EquinoxAutumn.id -> season(job, date.year, Season.SEPTEMBER_EQUINOX)
            SkyJobCatalog.SolsticeWinter.id -> season(job, date.year, Season.DECEMBER_SOLSTICE)
            SkyJobCatalog.Perihelion.id -> yearly(job, date, zone, coords) {
                YearEvents.perihelion(date.year)
            }
            SkyJobCatalog.Aphelion.id -> yearly(job, date, zone, coords) {
                YearEvents.aphelion(date.year)
            }
            SkyJobCatalog.EarliestSunset.id -> yearly(job, date, zone, coords) {
                YearEvents.earliestSunset(date.year, zone, coords)
            }
            SkyJobCatalog.LatestSunrise.id -> yearly(job, date, zone, coords) {
                YearEvents.latestSunrise(date.year, zone, coords)
            }
            SkyJobCatalog.WhiteNightsStart.id -> whiteNight(job, date, zone, coords) {
                YearEvents.whiteNightsStart(date.year, zone, coords)
            }
            SkyJobCatalog.WhiteNightsEnd.id -> whiteNight(job, date, zone, coords) {
                YearEvents.whiteNightsEnd(date.year, zone, coords)
            }
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
        // An annual job's answer does not have to fall in its own year — the latest
        // sunrise of the winter of 2026 is a morning in January 2027 — so the walk
        // starts a year back and lets the "after `from`" filter below do the work.
        // Anything already past is dropped there; nothing else moves.
        var date = from.atZone(zone).toLocalDate()
            .let { if (job.kind == SkyJobKind.ANNUAL) it.minusYears(1) else it }
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
        val day = SkyAlmanac.solarDay(date, zone, coords)
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
        val day = SkyAlmanac.solarDay(date, zone, coords)
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
        val instant = pick(SkyAlmanac.lunarDay(date, zone, coords))
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
        val tonight = SkyAlmanac.solarDay(date, zone, coords)
        val tomorrow = SkyAlmanac.solarDay(date.plusDays(1), zone, coords)
        val dusk = tonight.astronomicalDusk
        val dawn = tomorrow.astronomicalDawn
        if (dusk == null || dawn == null) return SkyOccurrence.None(job, SkyNotScheduled.NO_DARKNESS)
        return SkyOccurrence.At(job, dusk, dawn)
    }

    /**
     * The galactic core, high enough to be worth looking at, inside tonight's dark
     * window: the intersection of two windows the module already computes, which is
     * what makes it a line worth having rather than a second name for `darkness`.
     *
     * Ten degrees is the threshold. Lower than that the core sits in the horizon
     * murk, and at Milan's latitude it never climbs past sixteen, so a stricter
     * number would delete the whole of Italy from the answer.
     */
    private fun milkyWayCore(
        job: SkyJob,
        date: LocalDate,
        zone: ZoneId,
        coords: Coordinates
    ): SkyOccurrence {
        val dark = darkness(job, date, zone, coords) as? SkyOccurrence.At
            ?: return SkyOccurrence.None(job, SkyNotScheduled.NO_DARKNESS)
        val window = AstronomyEngine.galacticCoreAbove(
            dark.start, dark.end ?: dark.start, coords, CORE_MIN_ALTITUDE
        ) ?: return SkyOccurrence.None(job, SkyNotScheduled.CORE_TOO_LOW)
        return SkyOccurrence.At(job, window.start, window.endInclusive)
    }

    /**
     * The zodiacal light: the ninety minutes at the dark end of twilight, on the
     * nights the ecliptic stands steeply enough out of the horizon for the dust along
     * it to be a cone rather than a smear.
     *
     * Fifty degrees, measured for the ecliptic point a quarter-turn from the sun,
     * picks out the evenings of late winter and the mornings of autumn at mid-northern
     * latitudes — which is where the observing guides put them.
     */
    private fun zodiacal(
        job: SkyJob,
        date: LocalDate,
        zone: ZoneId,
        coords: Coordinates,
        evening: Boolean
    ): SkyOccurrence {
        val day = SkyAlmanac.solarDay(date, zone, coords)
        val edge = (if (evening) day.astronomicalDusk else day.astronomicalDawn)
            ?: return SkyOccurrence.None(job, polarReason(day))
        if (AstronomyEngine.eclipticStand(edge, coords, evening) < ECLIPTIC_MIN_STAND) {
            return SkyOccurrence.None(job, SkyNotScheduled.ECLIPTIC_TOO_FLAT)
        }
        return if (evening) {
            SkyOccurrence.At(job, edge, edge.plus(ZODIACAL_WINDOW))
        } else {
            SkyOccurrence.At(job, edge.minus(ZODIACAL_WINDOW), edge)
        }
    }

    /**
     * The next lunar eclipse the moon is up for here, as the window that can actually
     * be watched — the umbral phase clipped to the moon being above the horizon.
     */
    private fun lunarEclipse(
        job: SkyJob,
        date: LocalDate,
        zone: ZoneId,
        coords: Coordinates
    ): SkyOccurrence {
        val local = SkyAlmanac.nextLunarEclipse(date, zone, coords)
            ?: return SkyOccurrence.None(job, SkyNotScheduled.NO_ECLIPSE_AHEAD)
        return SkyOccurrence.At(job, local.window.start, local.window.endInclusive)
    }

    /** The next solar eclipse with a bite visible from here, in daylight. */
    private fun solarEclipse(
        job: SkyJob,
        date: LocalDate,
        zone: ZoneId,
        coords: Coordinates
    ): SkyOccurrence {
        val eclipse = SkyAlmanac.nextSolarEclipse(date, zone, coords)
            ?: return SkyOccurrence.None(job, SkyNotScheduled.NO_ECLIPSE_AHEAD)
        return SkyOccurrence.At(job, eclipse.contacts.start, eclipse.contacts.endInclusive)
    }

    /**
     * A once-a-year instant, through the almanac's memo: these are searches over
     * dozens of days, and the Sky screen resolves every annual job in the catalog on
     * every state build. A polar latitude that simply cannot have it gets the reason.
     */
    private fun yearly(
        job: SkyJob,
        date: LocalDate,
        zone: ZoneId,
        coords: Coordinates,
        pick: () -> Instant?
    ): SkyOccurrence {
        val instant = SkyAlmanac.yearEvent(job.id, date.year, zone, coords, pick)
            ?: return SkyOccurrence.None(job, polarReason(SkyAlmanac.solarDay(date, zone, coords)))
        return SkyOccurrence.At(job, instant)
    }

    private fun whiteNight(
        job: SkyJob,
        date: LocalDate,
        zone: ZoneId,
        coords: Coordinates,
        pick: () -> Instant?
    ): SkyOccurrence =
        SkyAlmanac.yearEvent(job.id, date.year, zone, coords, pick)
            ?.let { SkyOccurrence.At(job, it) }
            ?: SkyOccurrence.None(job, SkyNotScheduled.DARKNESS_ALL_YEAR)

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
        val dusk = SkyAlmanac.solarDay(eveningOf, zone, coords).astronomicalDusk
        val dawn = SkyAlmanac.solarDay(eveningOf.plusDays(1), zone, coords).astronomicalDawn
        if (dusk == null || dawn == null) return SkyOccurrence.None(job, SkyNotScheduled.NO_DARKNESS)
        return SkyOccurrence.At(job, maxOf(dusk, peak.coerceAtMost(dawn)), dawn)
    }

    /**
     * The aperiodic jobs, walked forward one answer at a time: each next occurrence is
     * found from the last one rather than from a calendar step, which is what
     * "aperiodic" means and why they are their own kind.
     */
    private fun pollingSeries(
        job: SkyJob,
        from: Instant,
        zone: ZoneId,
        coords: Coordinates,
        limit: Int
    ): List<SkyOccurrence> {
        var at = from
        val results = mutableListOf<SkyOccurrence>()
        repeat(limit) {
            val occurrence: SkyOccurrence = when {
                job.id == SkyJobCatalog.LunarEclipse.id ->
                    EclipseEngine.nextLunarFrom(at, coords)?.let { local ->
                        at = local.eclipse.penumbral.endInclusive
                        SkyOccurrence.At(job, local.window.start, local.window.endInclusive)
                    } ?: SkyOccurrence.None(job, SkyNotScheduled.NO_ECLIPSE_AHEAD)

                job.id == SkyJobCatalog.SolarEclipse.id ->
                    EclipseEngine.nextSolar(at, coords)?.let { eclipse ->
                        at = eclipse.contacts.endInclusive
                        SkyOccurrence.At(job, eclipse.contacts.start, eclipse.contacts.endInclusive)
                    } ?: SkyOccurrence.None(job, SkyNotScheduled.NO_ECLIPSE_AHEAD)

                // `nextMoonQuarter` is strictly-after, so feeding it its own answer
                // walks the series. That guard lives in the engine rather than here,
                // where the second caller to need it would have had to remember it.
                else -> {
                    val kind = QuarterJobs[job.id]
                    val quarter = if (kind == null) {
                        AstronomyEngine.nextMoonQuarter(at)
                    } else {
                        AstronomyEngine.nextMoonQuarter(at, kind)
                    }
                    at = quarter.at
                    SkyOccurrence.At(job, quarter.at)
                }
            }
            results += occurrence
            if (occurrence is SkyOccurrence.None) return results
        }
        return results
    }

    /** The four named quarters and the elongation each one is. */
    private val QuarterJobs: Map<String, MoonQuarterKind> = mapOf(
        SkyJobCatalog.MoonNew.id to MoonQuarterKind.NEW_MOON,
        SkyJobCatalog.MoonFirstQuarter.id to MoonQuarterKind.FIRST_QUARTER,
        SkyJobCatalog.MoonFull.id to MoonQuarterKind.FULL_MOON,
        SkyJobCatalog.MoonLastQuarter.id to MoonQuarterKind.LAST_QUARTER
    )

    private fun polarReason(day: SolarDay): SkyNotScheduled = when {
        day.sunUpAllDay -> SkyNotScheduled.POLAR_DAY
        day.sunDownAllDay -> SkyNotScheduled.POLAR_NIGHT
        // The sun rises and sets but never reaches the requested depth: a white night
        // at 60°N has no astronomical dusk without having polar anything.
        else -> SkyNotScheduled.NO_DARKNESS
    }

    /** Local hour before which the small hours still belong to the previous night. */
    private const val NIGHT_ENDS_HOUR = 12

    /** How high the galactic core has to stand to be worth a line. */
    private const val CORE_MIN_ALTITUDE = 10.0

    /** How steeply the ecliptic has to stand for the zodiacal light to be a cone. */
    private const val ECLIPTIC_MIN_STAND = 50.0

    /** How long the zodiacal light is worth looking for after (or before) the dark. */
    private val ZODIACAL_WINDOW: java.time.Duration = java.time.Duration.ofMinutes(90)

    /**
     * Ceiling on the day-by-day walk. A daily job answers every day, `∅` included, so
     * the loop cannot actually spin — this is here so a future job with a rarer
     * recurrence cannot turn a scroll of the crontab into a hang.
     */
    private const val MAX_STEPS = 400
}
