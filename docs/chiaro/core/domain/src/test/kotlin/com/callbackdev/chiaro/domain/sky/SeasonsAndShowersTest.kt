package com.callbackdev.chiaro.domain.sky

import com.callbackdev.chiaro.domain.model.Coordinates
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Solstices, equinoxes and meteor peaks — the three things in the catalog that are
 * defined by the sun's position in its own orbit rather than by its position in the
 * sky over a particular place.
 */
class SeasonsAndShowersTest {

    /**
     * Published instants (UTC) against a ±2 min budget. The engine does not carry a
     * polynomial for these: it root-finds the moment the sun's apparent longitude
     * reaches 0/90/180/270 with the SAME solar model that produces sunrise, so this
     * test is also a second, independent probe of that model — one that looks at the
     * ecliptic instead of at the horizon.
     */
    @Test
    fun `solstices and equinoxes match published instants within two minutes`() {
        listOf(
            Triple(2026, Season.MARCH_EQUINOX, "2026-03-20T14:46:00Z"),
            Triple(2026, Season.JUNE_SOLSTICE, "2026-06-21T08:24:00Z"),
            Triple(2026, Season.SEPTEMBER_EQUINOX, "2026-09-23T00:05:00Z"),
            Triple(2026, Season.DECEMBER_SOLSTICE, "2026-12-21T20:50:00Z"),
            Triple(2024, Season.MARCH_EQUINOX, "2024-03-20T03:06:00Z"),
            Triple(2024, Season.JUNE_SOLSTICE, "2024-06-20T20:51:00Z"),
            Triple(2030, Season.DECEMBER_SOLSTICE, "2030-12-21T20:09:00Z")
        ).forEach { (year, season, expected) ->
            val delta = Duration.between(
                Instant.parse(expected), AstronomyEngine.season(year, season)
            ).seconds
            assertTrue(
                "$year $season expected $expected got ${AstronomyEngine.season(year, season)} (${delta}s)",
                abs(delta) <= 120
            )
        }
    }

    /**
     * The engine holds two ways of asking "when is the sun at longitude λ": the season
     * series (authoritative, used for solstices and equinoxes) and the root-find on
     * the low-accuracy solar position (used for shower peaks, where the answer is a
     * night). This test is the reason that is a decision and not an accident — it
     * measures the gap and pins it.
     *
     * What it records: at a true season instant the solar series reads within its own
     * stated 0.01°, which is a quarter of an hour of the sun's motion, which is why
     * root-finding an equinox produced a time eight minutes out. If either method
     * moves, this fails and someone has to say which one was meant to.
     */
    @Test
    fun `the two ways of asking where the sun is disagree by a known amount`() {
        Season.entries.forEach { season ->
            val authoritative = AstronomyEngine.season(2026, season)
            val longitudeError = AstronomyMath.norm180(
                AstronomyMath.sunApparentLongitude(AstronomyMath.centuriesTT(authoritative)) -
                    season.longitudeDeg
            )
            assertTrue(
                "$season: solar series reads ${longitudeError}° off at the true instant",
                abs(longitudeError) <= 0.01
            )
            val rootFound = AstronomyEngine.solarLongitudeInstant(2026, season.longitudeDeg)
            val gap = Duration.between(authoritative, rootFound).toMinutes()
            assertTrue("$season: the two methods are ${gap} minutes apart", abs(gap) <= 15)
        }
    }

    /**
     * The table pins each shower to a solar longitude, not a date — so the peaks it
     * produces have to land on the nights everybody knows them by, in any year, with
     * nothing shipped in between.
     */
    @Test
    fun `shower peaks land on the nights they are known by, year after year`() {
        val expected = mapOf(
            "quadrantids" to (1 to 3..4),
            "lyrids" to (4 to 22..23),
            "eta_aquariids" to (5 to 5..7),
            "perseids" to (8 to 12..13),
            "orionids" to (10 to 21..22),
            "leonids" to (11 to 17..18),
            "geminids" to (12 to 13..14),
            "ursids" to (12 to 21..23)
        )
        listOf(2026, 2027, 2030, 2041).forEach { year ->
            expected.forEach { (id, monthAndDays) ->
                val (month, days) = monthAndDays
                val shower = requireNotNull(MeteorShowerTable.byId(id))
                val peak = AstronomyEngine
                    .solarLongitudeInstant(year, shower.solarLongitudeDeg)
                    .atZone(ZoneId.of("UTC")).toLocalDate()
                assertEquals("$id $year month", month, peak.monthValue)
                assertTrue("$id $year on ${peak.dayOfMonth}, expected $days", peak.dayOfMonth in days)
            }
        }
    }

    @Test
    fun `every shower has a catalog job and every shower job has a shower`() {
        MeteorShowerTable.all.forEach { shower ->
            val jobId = MeteorShowerTable.jobId(shower)
            assertEquals(shower, MeteorShowerTable.showerOf(jobId))
            assertTrue("no catalog job for $jobId", SkyJobCatalog.byId(jobId) != null)
        }
        assertEquals(null, MeteorShowerTable.showerOf("sun.rise"))
        assertEquals(null, MeteorShowerTable.showerOf("meteor.not_a_shower.peak"))
    }

    /**
     * A peak is a night, not an instant (`VISION_SKY.md` §6): the scheduler hands back
     * the local dark window the maximum falls in, so the renderer cannot print a bare
     * timestamp that promises a precision nobody has.
     */
    @Test
    fun `a shower peak resolves to the dark window it falls in`() {
        val job = requireNotNull(SkyJobCatalog.byId("meteor.perseids.peak"))
        val occurrence = SkyScheduler.resolve(
            job, LocalDate.of(2026, 8, 12), ZoneId.of("Europe/Rome"), Coordinates(45.4642, 9.19)
        )
        assertTrue("expected a window, got $occurrence", occurrence is SkyOccurrence.At)
        val at = occurrence as SkyOccurrence.At
        assertTrue("peak should be a range", at.isRange)
        val end = requireNotNull(at.end)
        assertTrue("window runs backwards", end.isAfter(at.start))
        assertTrue("window longer than a night", Duration.between(at.start, end).toHours() < 12)
    }
}
