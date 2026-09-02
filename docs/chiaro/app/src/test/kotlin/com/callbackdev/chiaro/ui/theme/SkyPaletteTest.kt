package com.callbackdev.chiaro.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The canvas is computed, so it can be wrong in ways a color chosen by hand cannot be.
 * These are the claims DESIGN.md §3 makes, as assertions.
 */
class SkyPaletteTest {

    private fun channel(c: Float) =
        if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).toDouble().pow(2.4)

    private fun brightness(altitude: Double, cloud: Int = 0, precip: Int = 0,
                           illum: Double = 0.0, moonAlt: Double = -90.0) =
        SkyPalette.gradient(altitude, cloud, precip, illum, moonAlt).stops().sumOf { c ->
            0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
        }

    @Test
    fun `once the sun is down the sky only gets darker`() {
        // Deliberately NOT asserted above the horizon: golden hour genuinely brightens
        // the bottom of the sky while the top darkens, which the first version of this
        // test called a bug at altitude 5.5 and which is the whole reason the band
        // exists. Below the horizon there is no such excuse.
        var previous = brightness(0.0)
        var altitude = -0.5
        while (altitude >= -40.0) {
            val here = brightness(altitude)
            assertTrue("the sky brightened after sunset, at $altitude", here <= previous + 1e-6)
            altitude -= 0.5
            previous = here
        }
    }

    @Test
    fun `the canvas never snaps, at any altitude`() {
        // Continuity is what makes the canvas readable as a sky rather than as seven
        // states: no half-degree of the sun's travel may visibly jump.
        var worst = 0.0
        var worstAt = 0.0
        var altitude = 90.0
        while (altitude > -90.0) {
            val delta = kotlin.math.abs(brightness(altitude) - brightness(altitude - 0.5))
            if (delta > worst) {
                worst = delta
                worstAt = altitude
            }
            altitude -= 0.5
        }
        // The bound is calibrated, not guessed: the largest legitimate half-degree step
        // is 0.104, at the horizon, where the anchors are 6° apart and the sky is
        // genuinely doing the fastest thing it does all day. What this catches is the
        // regression that matters — an implementation that buckets instead of
        // interpolating jumps by a whole band difference (several tenths) at each edge.
        assertTrue("a half-degree step changed the sky by %.4f at %.1f".format(worst, worstAt),
            worst < 0.15)
    }

    @Test
    fun `an anchor renders as itself, and between two anchors is a blend of both`() {
        // Together with the step bound above, this is what says "interpolated" rather
        // than "bucketed": the midpoint between two anchors must be neither of them.
        val golden = SkyPalette.gradient(0.0)
        val civil = SkyPalette.gradient(-6.0)
        val between = SkyPalette.gradient(-3.0)
        assertTrue("the midpoint must not be the anchor above it", between != golden)
        assertTrue("the midpoint must not be the anchor below it", between != civil)
        val mid = brightness(-3.0)
        assertTrue("the midpoint must sit between its anchors",
            mid < brightness(0.0) && mid > brightness(-6.0))
    }

    @Test
    fun `the day is far brighter than the night, which is the only absolute claim here`() {
        assertTrue(brightness(60.0) > brightness(-30.0) * 5)
    }

    @Test
    fun `an overcast sky keeps a third of its band, so morning still looks like morning`() {
        val clearNoon = SkyPalette.gradient(50.0)
        val cloudyNoon = SkyPalette.gradient(50.0, cloudPct = 100)
        assertTrue("full cloud should not erase the band", cloudyNoon != clearNoon)
        assertTrue("full cloud should still be recognisably day",
            brightness(50.0, cloud = 100) > brightness(-20.0))
        assertTrue("an overcast midnight must not read as dusk",
            brightness(-30.0, cloud = 100) < brightness(-3.0))
    }

    @Test
    fun `clouds hide the moon, and not the other way round`() {
        // The hole in the first draft of §3.4: applying the moon lift after the cloud mix
        // without scaling it by the cloud made an overcast full-moon night BRIGHTER than
        // a clear one.
        val clearFullMoon = brightness(-30.0, illum = 1.0, moonAlt = 60.0)
        val overcastFullMoon = brightness(-30.0, cloud = 100, illum = 1.0, moonAlt = 60.0)
        assertTrue("an overcast full moon must not out-shine a clear one",
            overcastFullMoon < clearFullMoon)
    }

    @Test
    fun `a moon below the horizon contributes nothing`() {
        assertEquals(
            SkyPalette.gradient(-30.0),
            SkyPalette.gradient(-30.0, moonIllumination = 1.0, moonAltitudeDeg = -5.0)
        )
    }

    @Test
    fun `the moon only lifts a sky that is actually dark`() {
        assertEquals(
            SkyPalette.gradient(10.0),
            SkyPalette.gradient(10.0, moonIllumination = 1.0, moonAltitudeDeg = 45.0)
        )
    }

    @Test
    fun `rain darkens the sky only once it is likely`() {
        assertEquals(SkyPalette.gradient(30.0), SkyPalette.gradient(30.0, precipPct = 50))
        assertTrue("90% rain should darken", brightness(30.0, precip = 90) < brightness(30.0))
    }

    @Test
    fun `the altitude is clamped, not wrapped`() {
        assertEquals(SkyPalette.gradient(90.0), SkyPalette.gradient(200.0))
        assertEquals(SkyPalette.gradient(-90.0), SkyPalette.gradient(-200.0))
    }
}
