package com.callbackdev.tweather.domain.sky

import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.MoonPhase
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
 * The moon: quarter instants against published values, rise and set against the
 * altitude that defines them, and the once-a-month day that has neither.
 */
class MoonEventsTest {

    private val milan = Coordinates(45.4642, 9.19)
    private val rome = ZoneId.of("Europe/Rome")

    /**
     * Reference quarter instants (UTC) from astronomical tables. The tolerance is
     * 5 minutes, which is the budget `VISION_SKY.md` §14 set and which the truncated
     * lunar series of [AstronomyMath] comfortably fits: the moon's elongation moves
     * 0.0085°/min, so 5 minutes is 0.04° and the series is good to about 0.02°.
     */
    @Test
    fun `quarter instants match the tables within five minutes`() {
        listOf(
            Triple("2024-01-11T11:57:00Z", MoonQuarterKind.NEW_MOON, "2024-01-09T00:00:00Z"),
            Triple("2024-01-18T03:52:00Z", MoonQuarterKind.FIRST_QUARTER, "2024-01-15T00:00:00Z"),
            Triple("2024-01-25T17:54:00Z", MoonQuarterKind.FULL_MOON, "2024-01-22T00:00:00Z"),
            Triple("2024-02-02T23:18:00Z", MoonQuarterKind.LAST_QUARTER, "2024-01-30T00:00:00Z"),
            Triple("2026-08-28T04:18:00Z", MoonQuarterKind.FULL_MOON, "2026-08-25T00:00:00Z")
        ).forEach { (expected, kind, searchFrom) ->
            val quarter = AstronomyEngine.nextMoonQuarter(Instant.parse(searchFrom))
            assertEquals("kind from $searchFrom", kind, quarter.kind)
            val delta = Duration.between(Instant.parse(expected), quarter.at).seconds
            assertTrue(
                "$kind expected $expected got ${quarter.at} (${delta}s)",
                abs(delta) <= 300
            )
        }
    }

    @Test
    fun `moonrise and moonset land on the moon's own horizon`() {
        val day = AstronomyEngine.lunarDay(LocalDate.of(2026, 8, 26), rome, milan)
        listOf(day.moonrise, day.moonset).forEach { instant ->
            assertNotNull(instant)
            // The moon's rise threshold is not a constant: it follows its parallax,
            // which swings the horizon by nearly a tenth of a degree over a month.
            // So the assertion is "near the horizon", within the band the parallax
            // term can move it — a centre-of-disk-at-0° test would fail by design.
            val altitude = AstronomyEngine.moonAltitude(instant!!, milan)
            assertTrue("moon altitude $altitude", abs(altitude) < 0.5)
        }
    }

    /**
     * The moon rises LATER every day, never earlier — that lag is the whole reason a
     * calendar day eventually contains no moonrise at all (the test below). The
     * popular figure is "about 50 minutes", but the real lag swings between roughly a
     * quarter of an hour and an hour and a half with the moon's declination, so the
     * assertion is the direction plus a band wide enough to be true: anything outside
     * it means the engine is tracking something that is not the moon.
     */
    @Test
    fun `moonrise walks later every day, never earlier`() {
        var previous: Instant? = null
        var seen = 0
        (1..28).forEach { dayOfMonth ->
            val rise = AstronomyEngine.lunarDay(LocalDate.of(2026, 8, dayOfMonth), rome, milan).moonrise
            val before = previous
            if (rise != null && before != null) {
                val lag = Duration.between(before, rise).toMinutes() - 24 * 60
                // Consecutive days only: a skipped (moonless) day doubles the gap.
                if (Duration.between(before, rise).toHours() < 36) {
                    assertTrue("lag of $lag minutes on day $dayOfMonth", lag in 10..95)
                    seen++
                }
            }
            previous = rise ?: previous
        }
        assertTrue("no consecutive moonrises found", seen >= 20)
    }

    /**
     * The moon rises ~50 minutes later every day, so roughly once a month a calendar
     * day contains no moonrise at all. That is a fact about the sky and the engine
     * says `null`, which `sky.crontab` renders `∅ not scheduled` — never `00:00`.
     */
    @Test
    fun `a day with no moonrise reports none instead of inventing one`() {
        val missing = (1..60)
            .map { LocalDate.of(2026, 1, 1).plusDays(it.toLong()) }
            .filter { AstronomyEngine.lunarDay(it, rome, milan).moonrise == null }
        assertTrue("expected a moonless day in two months, found none", missing.isNotEmpty())
        missing.forEach { date ->
            assertNull(AstronomyEngine.lunarDay(date, rome, milan).moonrise)
        }
    }

    @Test
    fun `illumination runs from new to full across half a cycle`() {
        // 2026-08-12 was a new moon, 2026-08-28 a full one.
        val new = AstronomyEngine.moonIllumination(Instant.parse("2026-08-12T18:00:00Z"))
        val full = AstronomyEngine.moonIllumination(Instant.parse("2026-08-28T04:18:00Z"))
        assertTrue("new moon lit ${new.illuminatedFraction}", new.illuminatedFraction < 0.05)
        assertTrue("full moon lit ${full.illuminatedFraction}", full.illuminatedFraction > 0.99)
    }

    /**
     * The reconciliation of Fase 16b: [MoonPhase] stopped carrying its own mean-synodic
     * arithmetic and became a naming of the engine's elongation. These are the same
     * five assertions the enum shipped with — they pass unchanged, which is the point:
     * the rendered value did not change in kind, only in accuracy.
     */
    @Test
    fun `the phase names still land where they always did`() {
        assertEquals(MoonPhase.NEW_MOON, MoonPhase.at(Instant.parse("2024-01-11T11:57:00Z")))
        assertEquals(MoonPhase.FIRST_QUARTER, MoonPhase.at(Instant.parse("2024-01-18T03:52:00Z")))
        assertEquals(MoonPhase.FULL_MOON, MoonPhase.at(Instant.parse("2024-01-25T17:54:00Z")))
        assertEquals(MoonPhase.LAST_QUARTER, MoonPhase.at(Instant.parse("2024-02-02T23:18:00Z")))
        assertEquals(MoonPhase.WAXING_GIBBOUS, MoonPhase.at(Instant.parse("2024-01-22T00:00:00Z")))
    }

    @Test
    fun `the phase name agrees with the quarter the engine is heading for`() {
        // Halfway between new and first quarter the moon is a waxing crescent, and the
        // next boundary is the first quarter. One model, so the two cannot disagree.
        val at = Instant.parse("2026-08-16T12:00:00Z")
        assertEquals(MoonPhase.WAXING_CRESCENT, MoonPhase.at(at))
        assertEquals(MoonQuarterKind.FIRST_QUARTER, AstronomyEngine.nextMoonQuarter(at).kind)
    }
}
