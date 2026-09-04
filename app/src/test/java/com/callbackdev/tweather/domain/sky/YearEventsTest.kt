package com.callbackdev.tweather.domain.sky

import com.callbackdev.tweather.domain.model.Coordinates
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The once-a-year facts, against published instants (Fase 19).
 *
 * Perihelion and aphelion are checked against the **USNO Astronomical Applications
 * API** (`aa.usno.navy.mil/api/seasons`), sixteen instants across sixteen years. That
 * is not decoration: the first implementation minimised the two-body distance of
 * Meeus 25 and missed by up to five hours, which put two of the sixteen on the wrong
 * calendar day. The VSOP87 radius series in `AstronomyMath` is what closed the gap,
 * and this test is what says so.
 */
class YearEventsTest {

    private val utc = ZoneId.of("UTC")
    private val milan = Coordinates(45.4642, 9.1900)
    private val rome = ZoneId.of("Europe/Rome")

    /** year to (perihelion, aphelion), UTC, from the USNO seasons API. */
    private val published = mapOf(
        2020 to ("2020-01-05T07:48:00Z" to "2020-07-04T11:35:00Z"),
        2022 to ("2022-01-04T06:54:00Z" to "2022-07-04T07:11:00Z"),
        2024 to ("2024-01-03T00:38:00Z" to "2024-07-05T05:06:00Z"),
        2026 to ("2026-01-03T17:15:00Z" to "2026-07-06T17:30:00Z"),
        2028 to ("2028-01-05T12:28:00Z" to "2028-07-03T22:18:00Z"),
        2030 to ("2030-01-03T10:12:00Z" to "2030-07-04T12:57:00Z"),
        2032 to ("2032-01-03T05:11:00Z" to "2032-07-05T11:53:00Z"),
        2035 to ("2035-01-03T00:54:00Z" to "2035-07-05T18:21:00Z")
    )

    @Test
    fun `perihelion and aphelion land on the published instants`() {
        published.forEach { (year, instants) ->
            val (perihelion, aphelion) = instants
            assertWithin(Instant.parse(perihelion), YearEvents.perihelion(year), TOLERANCE)
            assertWithin(Instant.parse(aphelion), YearEvents.aphelion(year), TOLERANCE)
        }
    }

    /**
     * The date is the claim the screen actually makes — an annual row prints one — so
     * it is asserted separately from the instant behind it.
     */
    @Test
    fun `and on the published day`() {
        published.forEach { (year, instants) ->
            val (perihelion, aphelion) = instants
            assertEquals(
                "perihelion $year",
                Instant.parse(perihelion).atZone(utc).toLocalDate(),
                YearEvents.perihelion(year).atZone(utc).toLocalDate()
            )
            assertEquals(
                "aphelion $year",
                Instant.parse(aphelion).atZone(utc).toLocalDate(),
                YearEvents.aphelion(year).atZone(utc).toLocalDate()
            )
        }
    }

    /**
     * The whole reason the two sunset rows exist: they are NOT the solstice. At
     * Milan's latitude the earliest sunset comes about a fortnight before it and the
     * latest sunrise a week after, and a reader who has been told the shortest day is
     * the earliest sunset has been told something false.
     */
    @Test
    fun `the earliest sunset is well before the solstice and the latest sunrise after it`() {
        val solstice = AstronomyEngine.season(2026, Season.DECEMBER_SOLSTICE)
            .atZone(rome).toLocalDate()
        val earliest = YearEvents.earliestSunset(2026, rome, milan)!!.atZone(rome)
        val latest = YearEvents.latestSunrise(2026, rome, milan)!!.atZone(rome)

        val beforeSolstice = Duration.between(
            earliest.toLocalDate().atStartOfDay(rome), solstice.atStartOfDay(rome)
        ).toDays()
        val afterSolstice = Duration.between(
            solstice.atStartOfDay(rome), latest.toLocalDate().atStartOfDay(rome)
        ).toDays()
        assertTrue("earliest sunset $earliest is $beforeSolstice days before", beforeSolstice in 8..20)
        assertTrue("latest sunrise $latest is $afterSolstice days after", afterSolstice in 5..20)

        // And they really are the extremes: every other day of that fortnight sets later.
        val earliestTime = earliest.toLocalTime()
        (-10..10).forEach { offset ->
            val date = earliest.toLocalDate().plusDays(offset.toLong())
            val sunset = AstronomyEngine
                .sunCrossing(date, rome, milan, AstronomyEngine.SUNRISE_ALTITUDE, rising = false)!!
                .atZone(rome).toLocalTime()
            assertTrue("$date sets at $sunset, earlier than the claimed earliest", sunset >= earliestTime)
        }
    }

    @Test
    fun `the closest full moon of the year is a full moon and the nearest one`() {
        val closest = YearEvents.closestFullMoon(2026)
        assertEquals(
            MoonQuarterKind.FULL_MOON,
            AstronomyEngine.nextMoonQuarter(closest.minus(Duration.ofDays(1))).kind
        )
        val distance = AstronomyEngine.moonDistanceKm(closest)
        assertTrue("$distance km is not a perigee full moon", distance < 360_000)

        var at = LocalDate.of(2026, 1, 1).atStartOfDay(utc).toInstant()
        repeat(12) {
            val full = AstronomyEngine.nextMoonQuarter(at, MoonQuarterKind.FULL_MOON).at
            if (full.atZone(utc).year == 2026) {
                assertTrue(
                    "the full moon of $full is nearer than the one claimed closest",
                    AstronomyEngine.moonDistanceKm(full) >= distance - 1
                )
            }
            at = full
        }
    }

    /**
     * White nights are a fact about latitude. Stockholm loses its astronomical night
     * from late April to late August; Milan never does, and the row says so rather
     * than disappearing.
     */
    @Test
    fun `white nights belong to the latitudes that have them`() {
        assertNull(YearEvents.whiteNightsStart(2026, rome, milan))
        assertNull(YearEvents.whiteNightsEnd(2026, rome, milan))

        val stockholm = Coordinates(59.3293, 18.0686)
        val sweden = ZoneId.of("Europe/Stockholm")
        val start = YearEvents.whiteNightsStart(2026, sweden, stockholm)
        val end = YearEvents.whiteNightsEnd(2026, sweden, stockholm)
        assertNotNull(start)
        assertNotNull(end)
        assertEquals(4, start!!.atZone(sweden).monthValue)
        assertEquals(8, end!!.atZone(sweden).monthValue)
        // The nights inside the season really have no astronomical darkness, and the
        // one before the season does.
        assertTrue(
            AstronomyEngine.sunMinAltitude(
                start.atZone(sweden).toLocalDate(), sweden, stockholm
            ) > AstronomyEngine.ASTRONOMICAL_TWILIGHT
        )
        assertTrue(
            AstronomyEngine.sunMinAltitude(
                start.atZone(sweden).toLocalDate().minusDays(1), sweden, stockholm
            ) < AstronomyEngine.ASTRONOMICAL_TWILIGHT
        )
    }

    private fun assertWithin(expected: Instant, actual: Instant, tolerance: Duration) {
        val gap = Duration.between(expected, actual)
        assertTrue(
            "expected $expected, got $actual (${gap.toMinutes()} min)",
            abs(gap.toMinutes()) <= tolerance.toMinutes()
        )
    }

    /**
     * An hour. The measured spread over the sixteen instants is 43 minutes; the
     * tolerance is the next round number up, so a real regression fails and the
     * model's own noise does not.
     */
    private val TOLERANCE: Duration = Duration.ofMinutes(60)
}
