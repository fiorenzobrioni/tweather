package com.callbackdev.chiaro.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * DESIGN.md §2.3 and §10 print numbers; this is what stops them from becoming
 * decoration. Every ratio in the document is asserted here, so re-picking a token
 * without re-measuring it fails the build instead of the reader's eyes.
 */
class PaletteContrastTest {

    private fun channel(c: Float) =
        if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).toDouble().pow(2.4)

    private fun luminance(color: Color) =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertAtLeast(expected: Double, a: Color, b: Color, what: String) {
        val actual = contrast(a, b)
        assertTrue("$what is %.2f:1, below $expected:1".format(actual), actual >= expected)
    }

    @Test
    fun `verdict ink reads on its surface in both schemes`() {
        val light = ChiaroLightScheme.surface
        val dark = ChiaroDarkScheme.surface
        listOf(
            "pass" to ChiaroLightColors.pass, "unstable" to ChiaroLightColors.unstable,
            "fail" to ChiaroLightColors.fail, "unknown" to ChiaroLightColors.unknown
        ).forEach { (name, v) -> assertAtLeast(4.5, v.ink, light, "light $name ink") }
        listOf(
            "pass" to ChiaroDarkColors.pass, "unstable" to ChiaroDarkColors.unstable,
            "fail" to ChiaroDarkColors.fail, "unknown" to ChiaroDarkColors.unknown
        ).forEach { (name, v) -> assertAtLeast(4.5, v.ink, dark, "dark $name ink") }
    }

    @Test
    fun `verdict ink reads on its own container`() {
        (listOf(ChiaroLightColors, ChiaroDarkColors)).forEach { colors ->
            listOf(colors.pass, colors.unstable, colors.fail, colors.unknown).forEach {
                assertAtLeast(4.5, it.ink, it.container, "ink on container")
            }
        }
    }

    @Test
    fun `the two surfaces are as far apart as the document says`() {
        assertAtLeast(17.0, ChiaroLightScheme.surface, ChiaroDarkScheme.surface, "surface span")
    }

    @Test
    fun `body text reads on every surface container`() {
        listOf(
            ChiaroLightScheme.onSurface to listOf(
                ChiaroLightScheme.surface, ChiaroLightScheme.surfaceContainerLowest,
                ChiaroLightScheme.surfaceContainerLow, ChiaroLightScheme.surfaceContainer,
                ChiaroLightScheme.surfaceContainerHigh, ChiaroLightScheme.surfaceContainerHighest
            ),
            ChiaroDarkScheme.onSurface to listOf(
                ChiaroDarkScheme.surface, ChiaroDarkScheme.surfaceContainerLowest,
                ChiaroDarkScheme.surfaceContainerLow, ChiaroDarkScheme.surfaceContainer,
                ChiaroDarkScheme.surfaceContainerHigh, ChiaroDarkScheme.surfaceContainerHighest
            )
        ).forEach { (ink, surfaces) ->
            surfaces.forEach { assertAtLeast(4.5, ink, it, "onSurface over a container") }
        }
    }

    @Test
    fun `the secondary text role still reads, which is where a generated scheme usually fails`() {
        assertAtLeast(4.5, ChiaroLightScheme.onSurfaceVariant, ChiaroLightScheme.surface, "light onSurfaceVariant")
        assertAtLeast(4.5, ChiaroDarkScheme.onSurfaceVariant, ChiaroDarkScheme.surface, "dark onSurfaceVariant")
    }

    @Test
    fun `on-color roles read on the color they are named for`() {
        listOf(ChiaroLightScheme, ChiaroDarkScheme).forEach { s ->
            assertAtLeast(4.5, s.onPrimary, s.primary, "onPrimary")
            assertAtLeast(4.5, s.onSecondary, s.secondary, "onSecondary")
            assertAtLeast(4.5, s.onTertiary, s.tertiary, "onTertiary")
            assertAtLeast(4.5, s.onError, s.error, "onError")
            assertAtLeast(4.5, s.onPrimaryContainer, s.primaryContainer, "onPrimaryContainer")
            assertAtLeast(4.5, s.onSecondaryContainer, s.secondaryContainer, "onSecondaryContainer")
            assertAtLeast(4.5, s.onTertiaryContainer, s.tertiaryContainer, "onTertiaryContainer")
            assertAtLeast(4.5, s.onErrorContainer, s.errorContainer, "onErrorContainer")
        }
    }

    @Test
    fun `the rain ramp is one hue, light to dark, with no step that repeats`() {
        listOf(ChiaroLightColors.rainRamp, ChiaroDarkColors.rainRamp).forEach { ramp ->
            val ys = ramp.map(::luminance)
            val descending = ys.zipWithNext().all { (a, b) -> a > b }
            val ascending = ys.zipWithNext().all { (a, b) -> a < b }
            assertTrue("the rain ramp is not monotonic: $ys", descending || ascending)
        }
    }

    @Test
    fun `the temperature ramp peaks at its neutral middle, and troughs at it in dark`() {
        val light = ChiaroLightColors.temperatureRamp.map(::luminance)
        assertTrue("the light ramp should be lightest in the middle: $light",
            light.indexOf(light.max()) == 3)
        val dark = ChiaroDarkColors.temperatureRamp.map(::luminance)
        assertTrue("the dark ramp should be darkest in the middle: $dark",
            dark.indexOf(dark.min()) == 3)
    }

    @Test
    fun `the temperature ramp is anchored to the world`() {
        // 15 C is the middle step, and it stays the middle step whatever is on screen.
        val mid = ChiaroLightColors.temperatureAt(ChiaroColors.ANCHOR_MID)
        assertTrue("15 C must sample the neutral step", mid == ChiaroLightColors.temperatureRamp[3])
        assertTrue("below the floor clamps", ChiaroLightColors.temperatureAt(-40.0) == ChiaroLightColors.temperatureRamp.first())
        assertTrue("above the ceiling clamps", ChiaroLightColors.temperatureAt(60.0) == ChiaroLightColors.temperatureRamp.last())
    }
}
