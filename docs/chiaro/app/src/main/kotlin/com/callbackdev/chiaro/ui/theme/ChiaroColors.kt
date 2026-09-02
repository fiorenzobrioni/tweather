package com.callbackdev.chiaro.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The colors Material has no slot for (DESIGN.md §2.3): verdicts, and the ramps that
 * carry a weather quantity.
 *
 * They do not follow the generated scheme and they must not: a verdict means the same
 * thing whatever the wallpaper is, and a rain ramp derived from someone's photo of a
 * sunset would stop being readable. The dark values are SELECTED for dark, never the
 * light ones flipped.
 */
@Immutable
data class VerdictColors(val ink: Color, val container: Color)

@Immutable
data class ChiaroColors(
    val pass: VerdictColors,
    val unstable: VerdictColors,
    val fail: VerdictColors,
    val unknown: VerdictColors,
    /** Five steps, one hue, monotonic in luminance. Index 0 is "almost none". */
    val rainRamp: List<Color>,
    /** Seven steps, diverging, the middle one neutral. [temperatureAt] anchors it. */
    val temperatureRamp: List<Color>
) {
    /**
     * "This data is old" and "the sky is iffy" are the same class of statement, so they
     * do not learn two colors.
     */
    val freshness: VerdictColors get() = unstable

    /** The ramp step for a probability, interpolated. */
    fun rainAt(percent: Int): Color = sample(rainRamp, percent.coerceIn(0, 100) / 100f)

    /**
     * The ramp step for a temperature. The scale is anchored to the WORLD — −5 °C at one
     * end, 35 °C at the other, 15 °C exactly in the middle — and never to the range of
     * whatever is on screen (DESIGN.md §9.1): a scale that re-anchors itself makes a mild
     * week look like a heatwave.
     */
    fun temperatureAt(celsius: Double): Color =
        sample(temperatureRamp, (((celsius - ANCHOR_LOW) / (ANCHOR_HIGH - ANCHOR_LOW)).toFloat()).coerceIn(0f, 1f))

    private fun sample(ramp: List<Color>, t: Float): Color {
        val pos = t * (ramp.size - 1)
        val low = pos.toInt().coerceIn(0, ramp.size - 1)
        val high = (low + 1).coerceAtMost(ramp.size - 1)
        return lerp(ramp[low], ramp[high], pos - low)
    }

    companion object {
        const val ANCHOR_LOW = -5.0
        const val ANCHOR_MID = 15.0
        const val ANCHOR_HIGH = 35.0
    }
}

internal val ChiaroLightColors = ChiaroColors(
    pass = VerdictColors(Color(0xFF0F5C30), Color(0xFFD7EBDD)),
    unstable = VerdictColors(Color(0xFF7A5200), Color(0xFFF7E6BF)),
    fail = VerdictColors(Color(0xFF8E1B10), Color(0xFFF9DEDA)),
    unknown = VerdictColors(Color(0xFF4F5359), Color(0xFFE7E7E4)),
    rainRamp = listOf(
        Color(0xFFE3EEF7), Color(0xFFBBD7EB), Color(0xFF8CBADB),
        Color(0xFF5896C6), Color(0xFF2E6F9E)
    ),
    temperatureRamp = listOf(
        Color(0xFF2E6F9E), Color(0xFF6BA3C6), Color(0xFFA9C7DC), Color(0xFFDCD7CC),
        Color(0xFFEBC190), Color(0xFFD9843A), Color(0xFFB45415)
    )
)

internal val ChiaroDarkColors = ChiaroColors(
    pass = VerdictColors(Color(0xFF7FD69A), Color(0xFF173D28)),
    unstable = VerdictColors(Color(0xFFF2C063), Color(0xFF3D2F08)),
    fail = VerdictColors(Color(0xFFFFB4AB), Color(0xFF4A1712)),
    unknown = VerdictColors(Color(0xFFA8ADB6), Color(0xFF2B2B2E)),
    rainRamp = listOf(
        Color(0xFF1A2E3D), Color(0xFF234A63), Color(0xFF2F6B8C),
        Color(0xFF4A90B5), Color(0xFF79B9DA)
    ),
    temperatureRamp = listOf(
        Color(0xFF7FB6D8), Color(0xFF4E90BC), Color(0xFF356F99), Color(0xFF4A4740),
        Color(0xFF93601F), Color(0xFFC1782A), Color(0xFFE39A4A)
    )
)

val LocalChiaroColors = staticCompositionLocalOf { ChiaroLightColors }

/** The semantic palette of §2.3, next to `MaterialTheme.colorScheme` rather than inside
 * it: Material's ColorScheme is a fixed set of roles and adding to it is not on offer. */
object ChiaroTheme {
    val colors: ChiaroColors
        @Composable @ReadOnlyComposable get() = LocalChiaroColors.current
}
