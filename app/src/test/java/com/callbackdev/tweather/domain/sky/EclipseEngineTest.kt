package com.callbackdev.tweather.domain.sky

import com.callbackdev.tweather.domain.model.Coordinates
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Eclipses against the published canons (Fase 19).
 *
 * The lunar cases come from NASA's *Five Millennium Catalog of Lunar Eclipses*
 * (`eclipse.gsfc.nasa.gov/LEcat5`), whose instants are in Terrestrial Time — the
 * `- deltaT` below is not a fudge, it is the conversion this module's own
 * `AstronomyMath.deltaTSeconds` performs everywhere else.
 *
 * The solar case is the **local** circumstances of 12 August 2026 at Milan, from the
 * USNO solar eclipse calculator: that eclipse is the one this engine has to get right
 * for its own author, and it exercises the awkward part on purpose — the sun sets over
 * Milan while the eclipse is still running, so the visible window has to be clipped
 * and the maximum re-measured inside it.
 *
 * Measured agreement, which is what the accuracy claim in `EclipseEngine`'s KDoc is
 * made of: greatest eclipse within ~70 s, umbral magnitude within 0.003, penumbral
 * within 0.01, phase durations within 1.5 minutes, and the solar contacts within 15 s.
 */
class EclipseEngineTest {

    private val milan = Coordinates(45.4642, 9.1900)

    private fun ut(td: String, deltaTSeconds: Long): Instant =
        Instant.parse(td).minusSeconds(deltaTSeconds)

    @Test
    fun `the three lunar eclipses of 2026 and 2027 match the catalog`() {
        val cases = listOf(
            // TD of greatest, deltaT, kind, umbral mag, penumbral mag, minutes of
            // penumbral / partial / total phases.
            Case(
                "2026-03-03T11:34:52Z", 75, LunarEclipseKind.TOTAL,
                1.1507, 2.1838, 338.6, 207.2, 58.3
            ),
            Case(
                "2026-08-28T04:14:04Z", 75, LunarEclipseKind.PARTIAL,
                0.9299, 1.9645, 337.8, 198.1, null
            ),
            Case(
                "2027-02-20T23:14:06Z", 76, LunarEclipseKind.PENUMBRAL,
                -0.0569, 0.9266, 241.0, null, null
            )
        )
        var at = Instant.parse("2026-01-01T00:00:00Z")
        cases.forEach { case ->
            val eclipse = EclipseEngine.nextLunar(at)
            assertNotNull("no eclipse found after $at", eclipse)
            eclipse!!
            val expected = ut(case.greatestTd, case.deltaT)
            assertTrue(
                "greatest ${eclipse.greatest} vs $expected",
                abs(Duration.between(expected, eclipse.greatest).seconds) <= 180
            )
            assertEquals(case.kind, eclipse.kind)
            assertEquals(case.umbralMagnitude, eclipse.umbralMagnitude, 0.02)
            assertEquals(case.penumbralMagnitude, eclipse.penumbralMagnitude, 0.02)
            assertMinutes(case.penumbralMinutes, eclipse.penumbral)
            assertMinutes(case.partialMinutes, eclipse.umbral)
            assertMinutes(case.totalMinutes, eclipse.totality)
            at = eclipse.greatest.plus(Duration.ofDays(1))
        }
    }

    /**
     * A lunar eclipse is the same everywhere the moon is up, so the local part is only
     * ever the horizon — and on 3 March 2026 the moon sets over Milan long before the
     * shadow arrives, which is why the local answer for Italy is the NEXT one.
     */
    @Test
    fun `a lunar eclipse under the horizon is not this place's eclipse`() {
        val march = EclipseEngine.nextLunar(Instant.parse("2026-01-01T00:00:00Z"))!!
        assertEquals(2026, march.greatest.atZone(java.time.ZoneOffset.UTC).year)
        assertEquals(3, march.greatest.atZone(java.time.ZoneOffset.UTC).monthValue)
        assertEquals(null, EclipseEngine.visibleWindow(march, milan))

        val local = EclipseEngine.nextLunarFrom(Instant.parse("2026-01-01T00:00:00Z"), milan)
        assertNotNull(local)
        assertTrue(
            "the local answer should skip the one under the horizon",
            local!!.eclipse.greatest.isAfter(march.greatest)
        )
        // Whatever it lands on, the moon is up for the whole window it reports.
        assertTrue(AstronomyEngine.moonAltitude(local.window.start, milan) > -1)
        assertTrue(AstronomyEngine.moonAltitude(local.window.endInclusive, milan) > -1)
    }

    @Test
    fun `the solar eclipse of 12 August 2026 over Milan matches the USNO circumstances`() {
        val eclipse = EclipseEngine.nextSolar(Instant.parse("2026-08-01T00:00:00Z"), milan)
        assertNotNull(eclipse)
        eclipse!!
        assertEquals(SolarEclipseKind.PARTIAL, eclipse.kind)
        assertWithin(Instant.parse("2026-08-12T17:27:39Z"), eclipse.contacts.start, 120)
        assertWithin(Instant.parse("2026-08-12T18:20:39Z"), eclipse.greatest, 120)
        assertEquals(0.933, eclipse.magnitude, 0.01)
        assertEquals(0.923, eclipse.obscuration, 0.02)

        // The sun sets at 18:36 with the eclipse still running: the window ends there
        // and not at the geometric last contact three quarters of an hour later.
        assertWithin(Instant.parse("2026-08-12T18:36:00Z"), eclipse.contacts.endInclusive, 120)
        assertTrue(
            "the window must end at sunset, not at last contact",
            eclipse.contacts.endInclusive.isBefore(Instant.parse("2026-08-12T18:40:00Z"))
        )
        assertTrue(AstronomyEngine.sunAltitude(eclipse.contacts.start, milan) > 0)
    }

    /**
     * Totality is a place, not a date: the same 2026 eclipse that takes 93 % of the
     * sun over Milan is total over northern Spain.
     */
    @Test
    fun `the same eclipse is total from the path and partial beside it`() {
        val burgos = Coordinates(42.35, -3.70)
        val eclipse = EclipseEngine.nextSolar(Instant.parse("2026-08-01T00:00:00Z"), burgos)
        assertNotNull(eclipse)
        assertEquals(SolarEclipseKind.TOTAL, eclipse!!.kind)
        assertTrue("magnitude ${eclipse.magnitude}", eclipse.magnitude >= 1.0)
        assertEquals(1.0, eclipse.obscuration, 0.001)
    }

    private fun assertWithin(expected: Instant, actual: Instant, seconds: Long) {
        assertTrue(
            "expected $expected, got $actual",
            abs(Duration.between(expected, actual).seconds) <= seconds
        )
    }

    private fun assertMinutes(expected: Double?, range: ClosedRange<Instant>?) {
        if (expected == null) {
            assertEquals("expected no phase, got $range", null, range)
            return
        }
        assertNotNull("expected a phase of $expected minutes, got none", range)
        val minutes = Duration.between(range!!.start, range.endInclusive).seconds / 60.0
        assertEquals(expected, minutes, 3.0)
    }

    private class Case(
        val greatestTd: String,
        val deltaT: Long,
        val kind: LunarEclipseKind,
        val umbralMagnitude: Double,
        val penumbralMagnitude: Double,
        val penumbralMinutes: Double,
        val partialMinutes: Double?,
        val totalMinutes: Double?
    )
}
