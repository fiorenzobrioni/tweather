package com.callbackdev.tweather.domain.sky

import com.callbackdev.tweather.domain.model.Coordinates
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The once-a-year facts (Fase 19): the two ends of the earth's orbit, the year's
 * closest full moon, the earliest sunset and the latest sunrise of a place, and the
 * nights it spends without any darkness at all.
 *
 * Its own file rather than more of [AstronomyEngine] because these are **searches**,
 * not positions: each one scans a window of days and picks an extreme, where the
 * engine under it answers about one instant. Same rules though — pure, no clock, no
 * Android, and a `null` is a fact about the sky ("the sun never sets there") rather
 * than a failure.
 *
 * **Why these four are worth computing at all.** Every one of them is a thing an
 * ephemeris site will tell you and a weather app will not, and three of the four are
 * *local*: the earliest sunset and the latest sunrise depend on where you stand
 * inside your time zone, and the white nights on your latitude. They are also the
 * quiet correction to two things everybody half-believes — that the earliest sunset
 * is the solstice (it is up to two weeks earlier) and that summer is when the earth
 * is closest to the sun (it is the farthest).
 */
object YearEvents {

    /**
     * The instant the earth passes closest to the sun in [year] (early January).
     *
     * Found by minimising the distance rather than from a mean-orbit formula, and the
     * difference between the two is the whole point: the smooth formula describes the
     * earth–moon BARYCENTRE, whose perihelion is a tidy annual march, while the
     * earth's own centre swings ±4671 km around it once a month. That swing moves the
     * true perihelion by up to about a day and a half — the published instants wander
     * between 2 and 5 January while the mean one does not — so a number that ignored
     * it would name the wrong day and print it to the minute.
     */
    fun perihelion(year: Int): Instant = extremeDistance(year, month = 1, minimum = true)

    /** The instant the earth is farthest from the sun in [year] (early July). */
    fun aphelion(year: Int): Instant = extremeDistance(year, month = 7, minimum = false)

    /**
     * The full moon of [year] that happens nearest to the earth: the biggest one of
     * the year, and the honest reading of a word the internet uses for three or four
     * of them.
     */
    fun closestFullMoon(year: Int): Instant {
        val start = LocalDate.of(year, 1, 1).atStartOfDay(ZoneId.of("UTC")).toInstant()
        val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneId.of("UTC")).toInstant()
        var at = start
        var best: Instant? = null
        var bestDistance = Double.POSITIVE_INFINITY
        // Thirteen at the most: a year holds twelve or thirteen full moons.
        repeat(14) {
            val full = AstronomyEngine.nextMoonQuarter(at, MoonQuarterKind.FULL_MOON).at
            if (!full.isBefore(end)) return best ?: full
            val distance = AstronomyEngine.moonDistanceKm(full)
            if (distance < bestDistance) {
                bestDistance = distance
                best = full
            }
            at = full
        }
        return best ?: start
    }

    /**
     * The earliest sunset of the season the winter solstice of [year] belongs to —
     * which is NOT the solstice, and that is the reason this exists.
     *
     * The earth's orbit is not circular and its axis is tilted, so solar noon drifts
     * against the clock through the year (the equation of time). Around the December
     * solstice that drift outruns the shortening of the day: at Milan's latitude the
     * sun already sets at its earliest around 8 December, gains a minute a day from
     * there, and the solstice two weeks later is the shortest day all the same. At the
     * equator the gap is seven weeks.
     *
     * Null when the sun does not set at all around the solstice — a polar place, where
     * the sentence has nothing to name.
     */
    fun earliestSunset(year: Int, zone: ZoneId, coords: Coordinates): Instant? =
        extremeSolarEvent(year, zone, coords, rising = false, earliest = true)

    /** The latest sunrise of the same season — early January, for the same reason. */
    fun latestSunrise(year: Int, zone: ZoneId, coords: Coordinates): Instant? =
        extremeSolarEvent(year, zone, coords, rising = true, earliest = false)

    /**
     * The sunset that opens the year's white nights: the first evening the sun no
     * longer gets 18° below the horizon, so the night never becomes astronomically
     * dark. Null below roughly 48.5°, where the darkness never pauses — a fact about
     * the latitude, and the reason the row says so rather than disappearing.
     */
    fun whiteNightsStart(year: Int, zone: ZoneId, coords: Coordinates): Instant? =
        whiteNights(year, zone, coords)?.let { sunsetOf(it.first, zone, coords) }

    /** The sunset that closes them: the first evening the dark comes back. */
    fun whiteNightsEnd(year: Int, zone: ZoneId, coords: Coordinates): Instant? =
        whiteNights(year, zone, coords)?.let { sunsetOf(it.second.plusDays(1), zone, coords) }

    // ------------------------------------------------------------- internals

    /**
     * The earth's distance from the sun, in AU (VSOP87D through [AstronomyMath]).
     *
     * The distance is of the earth–MOON BARYCENTRE, which is also what every almanac
     * means by "the earth at perihelion": the earth's own centre swings ±4671 km
     * around that point once a month, so its own closest approach jumps around by a
     * day and a half and is nobody's definition of the event. Measured against sixteen
     * published instants, the minimum of this function lands within half an hour of
     * them (`YearEventsTest`); the two-body series next door misses by up to five,
     * which is the whole reason the VSOP radius terms are in the file.
     */
    private fun earthSunDistanceAu(at: Instant): Double =
        AstronomyMath.earthRadiusVectorAu(AstronomyMath.centuriesTT(at))

    /**
     * Perihelion or aphelion: a coarse walk of the window to the extreme sample, then
     * a ternary search of the day either side of it.
     *
     * The coarse pass is what makes the ternary search safe. The lunar term above adds
     * a monthly ripple to a yearly parabola, and while its curvature is the smaller of
     * the two — so the sum has no second minimum — bracketing the answer by measurement
     * beats reasoning about which curvature wins.
     */
    private fun extremeDistance(year: Int, month: Int, minimum: Boolean): Instant {
        val centre = LocalDate.of(year, month, 4).atStartOfDay(ZoneId.of("UTC")).toInstant()
        val from = centre.minus(Duration.ofDays(12))
        val to = centre.plus(Duration.ofDays(12))
        val sign = if (minimum) 1.0 else -1.0
        fun value(at: Instant) = sign * earthSunDistanceAu(at)

        var best = from
        var bestValue = Double.POSITIVE_INFINITY
        var at = from
        while (at <= to) {
            val v = value(at)
            if (v < bestValue) {
                bestValue = v
                best = at
            }
            at = at.plus(COARSE_STEP)
        }
        var low = best.minus(COARSE_STEP)
        var high = best.plus(COARSE_STEP)
        repeat(40) {
            val third = Duration.between(low, high).dividedBy(3)
            val a = low.plus(third)
            val b = high.minus(third)
            if (value(a) < value(b)) high = b else low = a
        }
        return low.plus(Duration.between(low, high).dividedBy(2))
    }

    /**
     * The earliest sunset (or latest sunrise) of the winter around [year]'s solstice.
     *
     * The window is the solstice ±60 days because the offset between the extreme and
     * the solstice grows toward the equator: two weeks at Milan, seven at the equator,
     * which a tighter window would clip into a wrong answer that still looked right.
     * The hemisphere picks its own solstice — south of the equator the early sunsets
     * belong to June.
     *
     * Compared on the LOCAL CLOCK, which is what the claim means: "the sun sets at its
     * earliest" is a statement about the number on the clock, not about an instant.
     */
    private fun extremeSolarEvent(
        year: Int,
        zone: ZoneId,
        coords: Coordinates,
        rising: Boolean,
        earliest: Boolean
    ): Instant? {
        val solstice = if (coords.lat >= 0) {
            AstronomyEngine.season(year, Season.DECEMBER_SOLSTICE)
        } else {
            AstronomyEngine.season(year, Season.JUNE_SOLSTICE)
        }.atZone(zone).toLocalDate()

        fun clockTime(date: LocalDate): LocalTime? =
            AstronomyEngine.sunCrossing(date, zone, coords, AstronomyEngine.SUNRISE_ALTITUDE, rising)
                ?.atZone(zone)?.toLocalTime()

        fun better(a: LocalTime, b: LocalTime) = if (earliest) a < b else a > b

        var bestDate: LocalDate? = null
        var bestTime: LocalTime? = null
        fun consider(date: LocalDate) {
            val time = clockTime(date) ?: return
            if (bestTime == null || better(time, bestTime!!)) {
                bestTime = time
                bestDate = date
            }
        }
        // Coarse every five days: the curve is a flat parabola around its extreme
        // (a minute over a fortnight), so five days cannot step over the region.
        var offset = -WINDOW_DAYS
        while (offset <= WINDOW_DAYS) {
            consider(solstice.plusDays(offset.toLong()))
            offset += 5
        }
        val centre = bestDate ?: return null
        bestDate = null
        bestTime = null
        for (day in -6..6) consider(centre.plusDays(day.toLong()))
        return bestDate?.let { date ->
            AstronomyEngine.sunCrossing(date, zone, coords, AstronomyEngine.SUNRISE_ALTITUDE, rising)
        }
    }

    /**
     * The first and last local date of [year] whose night never gets astronomically
     * dark, or null when every night of the year does.
     *
     * Tested on the lowest altitude the sun reaches in the day rather than on a missing
     * dusk: a missing dusk is also what polar NIGHT looks like, and calling the darkest
     * fortnight of the year a white night would be the file inverting a fact.
     */
    private fun whiteNights(
        year: Int,
        zone: ZoneId,
        coords: Coordinates
    ): Pair<LocalDate, LocalDate>? {
        val solstice = if (coords.lat >= 0) {
            AstronomyEngine.season(year, Season.JUNE_SOLSTICE)
        } else {
            AstronomyEngine.season(year, Season.DECEMBER_SOLSTICE)
        }.atZone(zone).toLocalDate()
        fun bright(date: LocalDate) =
            AstronomyEngine.sunMinAltitude(date, zone, coords) > AstronomyEngine.ASTRONOMICAL_TWILIGHT
        if (!bright(solstice)) return null
        var first = solstice
        while (bright(first.minusDays(1)) && first > solstice.minusDays(WHITE_NIGHT_DAYS)) {
            first = first.minusDays(1)
        }
        var last = solstice
        while (bright(last.plusDays(1)) && last < solstice.plusDays(WHITE_NIGHT_DAYS)) {
            last = last.plusDays(1)
        }
        return first to last
    }

    private fun sunsetOf(date: LocalDate, zone: ZoneId, coords: Coordinates): Instant? =
        AstronomyEngine.sunCrossing(
            date, zone, coords, AstronomyEngine.SUNRISE_ALTITUDE, rising = false
        )

    /** Half-width of the sunset/sunrise search, in days. */
    private const val WINDOW_DAYS = 60

    /**
     * Half-width of the white-night walk. Wider than the others because the season
     * itself is: 78°N loses its astronomical night for five months, and a walk that
     * stopped at sixty days would return a season with a made-up end.
     */
    private const val WHITE_NIGHT_DAYS = 120L

    private val COARSE_STEP: Duration = Duration.ofHours(6)
}
