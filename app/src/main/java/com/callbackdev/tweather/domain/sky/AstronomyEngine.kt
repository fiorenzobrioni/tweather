package com.callbackdev.tweather.domain.sky

import com.callbackdev.tweather.domain.model.Coordinates
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * What the sky above a place is scheduled to do (Fase 16b): the single source of
 * truth for every solar and lunar instant in the app — `sky.crontab`'s lines, the
 * README's `## Astronomy`, `weather_data.json`'s `astronomical` block and
 * [com.callbackdev.tweather.domain.model.MoonPhase] all read from here, so the app
 * can never show two different sunrises for one city.
 *
 * **Pure, and free of Android and of the clock**, like [com.callbackdev.tweather.domain.AlertEngine]
 * and the rule engine: coordinates and a date go in, instants come out, and every
 * function is total — an event that does not happen returns `null` rather than a
 * fabricated time (see [SolarDay] and [LunarDay]).
 *
 * **One primitive, every event.** Rather than a closed-form formula per event, this
 * engine finds the instant at which the body's altitude crosses a threshold
 * ([crossing]). Sunrise is that crossing at −0.833°, civil dawn at −6°, the golden
 * hour between +6° and −0.833°, moonrise at a threshold that varies with the moon's
 * own parallax. That is why the event list can grow without the maths growing, and
 * why polar day, a moonless day and an equatorial ten-minute twilight are all the
 * same code path rather than three special cases.
 *
 * **Accuracy.** Sunrise and sunset land within ~30 s of Open-Meteo's own daily values
 * (there is a test); every other solar event is the same solver at a different angle,
 * and there is a second test asserting the sun really is at the requested altitude at
 * each returned instant. Lunar instants inherit the truncated series of
 * [AstronomyMath] — worth about ten seconds on a moonrise and three minutes on a
 * quarter.
 */
object AstronomyEngine {

    /**
     * Standard refraction-corrected altitude of the sun's UPPER LIMB at the horizon:
     * 34' of refraction plus the sun's 16' semidiameter. Every almanac's sunrise.
     */
    const val SUNRISE_ALTITUDE = -0.833

    const val CIVIL_TWILIGHT = -6.0
    const val NAUTICAL_TWILIGHT = -12.0
    const val ASTRONOMICAL_TWILIGHT = -18.0

    /** Golden hour runs from +6° down to the horizon; blue hour from −4° to −6°. */
    const val GOLDEN_HOUR_ALTITUDE = 6.0
    const val BLUE_HOUR_START = -4.0
    const val BLUE_HOUR_END = -6.0

    // ------------------------------------------------------------------- sun

    /** Every solar instant of one local day at one place. */
    fun solarDay(date: LocalDate, zone: ZoneId, coords: Coordinates): SolarDay {
        val window = DayWindow(date, zone)
        val extremes = extremes(window, coords, Body.SUN)
        fun rise(alt: Double) = crossing(window, coords, alt, rising = true, body = Body.SUN)
        fun set(alt: Double) = crossing(window, coords, alt, rising = false, body = Body.SUN)
        return SolarDay(
            date = date,
            sunrise = rise(SUNRISE_ALTITUDE),
            sunset = set(SUNRISE_ALTITUDE),
            solarNoon = transit(window, coords, Body.SUN),
            civilDawn = rise(CIVIL_TWILIGHT),
            civilDusk = set(CIVIL_TWILIGHT),
            nauticalDawn = rise(NAUTICAL_TWILIGHT),
            nauticalDusk = set(NAUTICAL_TWILIGHT),
            astronomicalDawn = rise(ASTRONOMICAL_TWILIGHT),
            astronomicalDusk = set(ASTRONOMICAL_TWILIGHT),
            goldenHourMorningEnd = rise(GOLDEN_HOUR_ALTITUDE),
            goldenHourEveningStart = set(GOLDEN_HOUR_ALTITUDE),
            blueHourMorningStart = rise(BLUE_HOUR_END),
            blueHourMorningEnd = rise(BLUE_HOUR_START),
            blueHourEveningStart = set(BLUE_HOUR_START),
            blueHourEveningEnd = set(BLUE_HOUR_END),
            // The two polar states, from ONE scan of the day rather than two: the
            // sun is up all day when its lowest sample is still above the horizon,
            // down all day when its highest is still below.
            sunUpAllDay = extremes.min > SUNRISE_ALTITUDE,
            sunDownAllDay = extremes.max < SUNRISE_ALTITUDE
        )
    }

    /** Altitude of the sun above the horizon at [at], degrees, refraction excluded. */
    fun sunAltitude(at: Instant, coords: Coordinates): Double =
        // Position from TT, hour angle from UT — see AstronomyMath's KDoc on why the
        // two arguments are not the same number.
        AstronomyMath.altitude(
            AstronomyMath.sunEquatorial(AstronomyMath.centuriesTT(at)),
            AstronomyMath.julianDay(at),
            coords.lat,
            coords.lon
        )

    // ------------------------------------------------------------------ moon

    /** Rise, set and phase of the moon over one local day at one place. */
    fun lunarDay(date: LocalDate, zone: ZoneId, coords: Coordinates): LunarDay {
        val window = DayWindow(date, zone)
        val noon = window.start.plus(Duration.between(window.start, window.end).dividedBy(2))
        val illumination = AstronomyMath.moonIllumination(AstronomyMath.centuriesTT(noon))
        return LunarDay(
            date = date,
            // Null here is a fact, not a failure: the moon rises ~50 minutes later
            // each day, so roughly once a month a calendar day contains no moonrise
            // (or no moonset) at all. `sky.crontab` prints `∅ not scheduled` for it.
            moonrise = crossing(window, coords, altitude = null, rising = true, body = Body.MOON),
            moonset = crossing(window, coords, altitude = null, rising = false, body = Body.MOON),
            illuminatedFraction = illumination.fraction,
            elongation = illumination.elongation
        )
    }

    /** Altitude of the moon's centre at [at], degrees, refraction and parallax excluded. */
    fun moonAltitude(at: Instant, coords: Coordinates): Double =
        AstronomyMath.altitude(
            AstronomyMath.moonEquatorial(AstronomyMath.centuriesTT(at)),
            AstronomyMath.julianDay(at),
            coords.lat,
            coords.lon
        )

    /** Illuminated fraction (0..1) and elongation of the moon at [at]. */
    fun moonIllumination(at: Instant): MoonLight {
        val light = AstronomyMath.moonIllumination(AstronomyMath.centuriesTT(at))
        return MoonLight(light.fraction, light.elongation)
    }

    /**
     * The first quarter boundary strictly after [after]: the instant the moon's
     * elongation from the sun reaches 0°, 90°, 180° or 270°.
     *
     * Defined by the same elongation the phase name is read from, rather than by a
     * separate polynomial for phase instants — one model, so the name in the README
     * and the instant in the crontab can never disagree about which day it is.
     */
    fun nextMoonQuarter(after: Instant): MoonQuarter {
        val quarter = quarterAfter(after)
        // STRICTLY after, and the degenerate case is the common one: asked for "the
        // quarter after this quarter" — which is what walking a series of them does —
        // the elongation at `after` sits a hair BELOW its own target, so the search
        // brackets the instant it started from and hands it straight back. Nudging
        // past it costs an hour of a 7.4-day cycle and turns "the next four quarters"
        // from four copies of one instant into four quarters.
        if (Duration.between(after, quarter.at) > MIN_QUARTER_ADVANCE) return quarter
        return quarterAfter(after.plus(MIN_QUARTER_ADVANCE))
    }

    private fun quarterAfter(after: Instant): MoonQuarter {
        val current = moonIllumination(after).elongation
        val nextTarget = (Math.floor(current / 90.0).toInt() + 1) * 90.0
        return MoonQuarter(MoonQuarterKind.of(nextTarget), elongationCrossing(after, nextTarget))
    }

    // --------------------------------------------------------------- seasons

    /**
     * Solstice or equinox of [year].
     *
     * `VISION_SKY.md` planned this as the same root-find [solarLongitudeInstant] uses,
     * on the grounds that one solar model cannot disagree with itself. Measurement
     * overruled the plan: the low-accuracy solar series is good to ~0.01°, the sun
     * covers 0.01° in a quarter of an hour, and root-finding the 2026 March equinox
     * landed **eight minutes** from the published instant — inside the model's own
     * error bar and well outside what an `HH:mm` line may claim. So a season comes
     * from the series fitted to season instants (Meeus 27), which agrees with seven
     * published instants across three decades to within a minute.
     *
     * The two are not left to drift apart unwatched: a test measures the gap between
     * this and the root-find at all four longitudes and pins it, so the day one of
     * them changes, the other's disagreement is a failing test rather than a surprise.
     */
    fun season(year: Int, season: Season): Instant {
        val tt = AstronomyMath.seasonJulianDay(year, season.ordinal)
        return AstronomyMath.instantOf(tt - AstronomyMath.deltaTSeconds(tt) / 86_400.0)
    }

    /**
     * The instant in [year] at which the sun's apparent longitude reaches
     * [longitudeDeg]. Meteor shower peaks are defined this way (§6 of VISION_SKY),
     * which is why the table of showers never expires: a solar longitude is the same
     * point of the earth's orbit every year, whatever date the calendar puts on it.
     *
     * **Good to about a quarter of an hour**, which is the solar series' 0.01° turned
     * into time, and which is why [season] does not use it. That precision is ample
     * here and it is not a compromise: a shower peak is rendered as the NIGHT it falls
     * in, a window some nine hours wide, so an instant a few minutes either way picks
     * the same night. The one case it could not survive — a peak within minutes of
     * dusk or dawn — is bounded by the window itself.
     */
    fun solarLongitudeInstant(year: Int, longitudeDeg: Double): Instant {
        // The sun covers ~0.9856°/day, so the target date is within a couple of days
        // of this estimate — a ±10 day bracket is generous and still cheap.
        val estimate = LocalDate.of(year, 1, 1).atStartOfDay(ZoneId.of("UTC")).toInstant()
            .plus(Duration.ofSeconds((longitudeDeg / 360.0 * 365.2422 * 86_400).toLong()))
            .plus(Duration.ofDays(79))    // longitude 0 is the March equinox, ~day 79
        return bisect(
            low = estimate.minus(Duration.ofDays(10)),
            high = estimate.plus(Duration.ofDays(10))
        ) { at ->
            AstronomyMath.norm180(
                AstronomyMath.sunApparentLongitude(AstronomyMath.centuriesTT(at)) - longitudeDeg
            )
        }
    }

    // ------------------------------------------------------------- internals

    private enum class Body { SUN, MOON }

    /** The half-open instant range of one local calendar day. */
    private class DayWindow(date: LocalDate, zone: ZoneId) {
        val start: Instant = date.atStartOfDay(zone).toInstant()

        /**
         * The NEXT day's start, not `start + 24h`: on a DST switch a local day is 23
         * or 25 hours long, and a window that assumed 24 would leave an hour of the
         * day unsearched (or search an hour of the next one).
         */
        val end: Instant = date.plusDays(1).atStartOfDay(zone).toInstant()
    }

    private fun altitudeOf(body: Body, at: Instant, coords: Coordinates): Double =
        when (body) {
            Body.SUN -> sunAltitude(at, coords)
            Body.MOON -> moonAltitude(at, coords)
        }

    /**
     * Threshold the body has to cross to count as risen. Fixed for the sun; for the
     * moon it follows its parallax, which swings the horizon by nearly a tenth of a
     * degree over a month (Meeus 15.1: `0.7275·π − 34'`).
     */
    private fun riseThreshold(body: Body, fixed: Double?, at: Instant): Double =
        fixed ?: when (body) {
            Body.SUN -> SUNRISE_ALTITUDE
            Body.MOON -> {
                val parallax = AstronomyMath
                    .moonEcliptic(AstronomyMath.centuries(AstronomyMath.julianDay(at))).parallax
                0.7275 * parallax - 0.5667
            }
        }

    /**
     * The instant inside [window] at which [body] crosses its threshold going up
     * ([rising]) or down. `null` when it does not: polar day, polar night, or a
     * calendar day the moon simply does not rise on.
     *
     * Scans on a coarse grid to bracket the sign change, then bisects to the second.
     * The grid is 10 minutes: the fastest thing here is the moon near the horizon at
     * a low latitude, which moves ~0.25°/min, so a 10-minute cell cannot hide a
     * crossing and an immediate re-crossing.
     *
     * The [rising] flag is folded into the SIGN of the tested function rather than
     * into the search, so bracketing and bisection are one direction-free `negative →
     * non-negative` hunt. A bisection that assumed "increasing" while chasing a
     * setting sun is not slightly wrong: it walks away from the root and returns the
     * edge of the bracket, which reads as a plausible time on a ten-minute boundary.
     */
    private fun crossing(
        window: DayWindow,
        coords: Coordinates,
        altitude: Double?,
        rising: Boolean,
        body: Body
    ): Instant? {
        val sign = if (rising) 1.0 else -1.0
        fun offset(at: Instant) =
            sign * (altitudeOf(body, at, coords) - riseThreshold(body, altitude, at))
        var previousAt = window.start
        var previous = offset(previousAt)
        var at = previousAt.plus(GRID)
        while (at <= window.end) {
            val current = offset(at)
            if (previous < 0 && current >= 0) return bisect(previousAt, at) { offset(it) }
            previousAt = at
            previous = current
            at = at.plus(GRID)
        }
        return null
    }

    /** The instant of the body's highest altitude inside [window] — solar noon. */
    private fun transit(window: DayWindow, coords: Coordinates, body: Body): Instant {
        var best = window.start
        var bestAltitude = Double.NEGATIVE_INFINITY
        var at = window.start
        while (at <= window.end) {
            val altitude = altitudeOf(body, at, coords)
            if (altitude > bestAltitude) {
                bestAltitude = altitude
                best = at
            }
            at = at.plus(GRID)
        }
        // Golden-section-free refinement: the altitude curve is smooth and symmetric
        // around the transit, so ternary search on the bracketing cells converges fast.
        var low = best.minus(GRID)
        var high = best.plus(GRID)
        repeat(40) {
            val third = Duration.between(low, high).dividedBy(3)
            val a = low.plus(third)
            val b = high.minus(third)
            if (altitudeOf(body, a, coords) < altitudeOf(body, b, coords)) low = a else high = b
        }
        return low.plus(Duration.between(low, high).dividedBy(2))
    }

    private class Extremes(val min: Double, val max: Double)

    /** Lowest and highest altitude of [body] over [window], on the same grid. */
    private fun extremes(window: DayWindow, coords: Coordinates, body: Body): Extremes {
        var min = Double.POSITIVE_INFINITY
        var max = Double.NEGATIVE_INFINITY
        var at = window.start
        while (at <= window.end) {
            val altitude = altitudeOf(body, at, coords)
            if (altitude < min) min = altitude
            if (altitude > max) max = altitude
            at = at.plus(GRID)
        }
        return Extremes(min, max)
    }

    /** The instant after [after] at which the moon's elongation reaches [targetDeg]. */
    private fun elongationCrossing(after: Instant, targetDeg: Double): Instant {
        // Elongation gains ~12.19°/day, so a target at most 90° away is at most ~8
        // days out; 12 days of bracket covers the moon's slowest stretch.
        var low = after
        var high = after.plus(Duration.ofDays(12))
        fun offset(at: Instant) =
            AstronomyMath.norm180(moonIllumination(at).elongation - targetDeg)
        // Walk forward in 6-hour steps to the first sign change: `norm180` makes the
        // offset sawtooth once per cycle, and bisecting across that jump would land
        // on the discontinuity instead of on the quarter.
        var previousAt = low
        var previous = offset(previousAt)
        var at = low.plus(Duration.ofHours(6))
        while (at <= high) {
            val current = offset(at)
            if (previous < 0 && current >= 0) {
                low = previousAt
                high = at
                break
            }
            previousAt = at
            previous = current
            at = at.plus(Duration.ofHours(6))
        }
        return bisect(low, high) { offset(it) }
    }

    /**
     * The instant in `[low, high]` where [offset] changes sign from negative to
     * non-negative, to the second. Plain bisection: 40 halvings of a 20-day bracket
     * land well under a millisecond, and it cannot diverge the way Newton can on a
     * curve that flattens out at the poles.
     */
    private fun bisect(low: Instant, high: Instant, offset: (Instant) -> Double): Instant {
        var lo = low
        var hi = high
        repeat(BISECTIONS) {
            val mid = lo.plus(Duration.between(lo, hi).dividedBy(2))
            if (offset(mid) < 0) lo = mid else hi = mid
            if (abs(Duration.between(lo, hi).seconds) <= 1) return mid
        }
        return lo.plus(Duration.between(lo, hi).dividedBy(2))
    }

    private val GRID: Duration = Duration.ofMinutes(10)

    /** How far past a quarter [nextMoonQuarter] jumps before looking for the next. */
    private val MIN_QUARTER_ADVANCE: Duration = Duration.ofHours(1)
    private const val BISECTIONS = 40
}

/**
 * How much of the moon is lit and where it is in its cycle.
 *
 * Its own type rather than [AstronomyMath]'s, which is internal: the arithmetic is
 * an implementation detail of this package, the answer is not.
 */
data class MoonLight(
    /** 0 at new moon, 1 at full. */
    val illuminatedFraction: Double,
    /** Moon longitude − sun longitude, `[0, 360)`: 0 new, 90 first quarter, 180 full. */
    val elongation: Double
)

/** Which quarter boundary the moon is heading for. */
enum class MoonQuarterKind(val elongationDeg: Double) {
    NEW_MOON(0.0),
    FIRST_QUARTER(90.0),
    FULL_MOON(180.0),
    LAST_QUARTER(270.0);

    companion object {
        fun of(elongationDeg: Double): MoonQuarterKind {
            val target = AstronomyMath.norm360(elongationDeg)
            return entries.minByOrNull { abs(AstronomyMath.norm180(it.elongationDeg - target)) }!!
        }
    }
}

data class MoonQuarter(val kind: MoonQuarterKind, val at: Instant)

enum class Season(val longitudeDeg: Double) {
    /** March equinox — spring in the north, autumn in the south. */
    MARCH_EQUINOX(0.0),
    JUNE_SOLSTICE(90.0),
    SEPTEMBER_EQUINOX(180.0),
    DECEMBER_SOLSTICE(270.0)
}

/**
 * Every solar instant of one local day. A `null` field is an event that does not
 * happen that day at that latitude, never a failure and never a placeholder time —
 * `sky.crontab` renders it `∅ not scheduled` and says why.
 */
data class SolarDay(
    val date: LocalDate,
    val sunrise: Instant?,
    val sunset: Instant?,
    val solarNoon: Instant,
    val civilDawn: Instant?,
    val civilDusk: Instant?,
    val nauticalDawn: Instant?,
    val nauticalDusk: Instant?,
    val astronomicalDawn: Instant?,
    val astronomicalDusk: Instant?,
    val goldenHourMorningEnd: Instant?,
    val goldenHourEveningStart: Instant?,
    val blueHourMorningStart: Instant?,
    val blueHourMorningEnd: Instant?,
    val blueHourEveningStart: Instant?,
    val blueHourEveningEnd: Instant?,
    val sunUpAllDay: Boolean,
    val sunDownAllDay: Boolean
) {
    /** Morning golden hour: from sunrise up to +6°. */
    val goldenHourMorning: ClosedRange<Instant>?
        get() = range(sunrise, goldenHourMorningEnd)

    /** Evening golden hour: from +6° down to sunset. */
    val goldenHourEvening: ClosedRange<Instant>?
        get() = range(goldenHourEveningStart, sunset)

    val blueHourMorning: ClosedRange<Instant>?
        get() = range(blueHourMorningStart, blueHourMorningEnd)

    val blueHourEvening: ClosedRange<Instant>?
        get() = range(blueHourEveningStart, blueHourEveningEnd)

    /** Astronomical darkness: dusk to the next dawn is the stargazer's window. */
    val darkness: ClosedRange<Instant>?
        get() = range(astronomicalDusk, astronomicalDawn)

    val daylight: Duration?
        get() = if (sunrise != null && sunset != null) Duration.between(sunrise, sunset) else null

    private fun range(from: Instant?, to: Instant?): ClosedRange<Instant>? =
        if (from != null && to != null && !to.isBefore(from)) from..to else null
}

/**
 * The moon over one local day. [moonrise] and [moonset] are null on the days the
 * moon does not do that — about one day a month for each, because it rises roughly
 * 50 minutes later every day and eventually skips a calendar box.
 */
data class LunarDay(
    val date: LocalDate,
    val moonrise: Instant?,
    val moonset: Instant?,
    /** 0 at new moon, 1 at full, measured at local noon. */
    val illuminatedFraction: Double,
    /** Moon longitude − sun longitude, `[0, 360)`: 0 new, 90 first quarter, 180 full. */
    val elongation: Double
)
