package com.callbackdev.chiaro.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs

/**
 * The sky canvas (DESIGN.md §3): a gradient computed from the sky above the active city
 * rather than chosen to look nice.
 *
 * It does not follow the reader's theme, and that is the one deliberate exception to
 * "roles, never hexes": at 23:00 it is dark outside whatever the phone is set to. What
 * makes the exception safe is the scrim contract of §3.6, which `ScrimContractTest`
 * holds.
 */
@Immutable
data class SkyGradient(val top: Color, val mid: Color, val bottom: Color) {
    fun stops(): List<Color> = listOf(top, mid, bottom)
}

object SkyPalette {

    /**
     * The rows of DESIGN.md §3.2 as ANCHORS on solar altitude, not as buckets: a value
     * between two anchors is the blend of the two, which is what makes a sunset move
     * instead of snapping through seven states.
     */
    private val anchors: List<Pair<Double, SkyGradient>> = listOf(
        90.0 to SkyGradient(Color(0xFF4E8FBF), Color(0xFF7FB4D6), Color(0xFFC7DDEB)),
        12.0 to SkyGradient(Color(0xFF4E8FBF), Color(0xFF7FB4D6), Color(0xFFC7DDEB)),
        8.0 to SkyGradient(Color(0xFF5583B0), Color(0xFF93B5CE), Color(0xFFE0CFB4)),
        // TWO golden anchors, not one. With a single anchor at the horizon the golden
        // hour was only golden in its last minutes: at 3° the canvas rendered as the
        // midpoint between a cool low sun and the amber, which is a washed-out tan and
        // is not what anybody means by the golden hour. Rendering the sheet and looking
        // at it is what found this; no test would have.
        4.0 to SkyGradient(Color(0xFF5C7FA8), Color(0xFFE0A45C), Color(0xFFF3D3A0)),
        0.0 to SkyGradient(Color(0xFF54739C), Color(0xFFD68F45), Color(0xFFF0C68C)),
        -6.0 to SkyGradient(Color(0xFF2A3E63), Color(0xFF4B5F8F), Color(0xFF8A7FA8)),
        -12.0 to SkyGradient(Color(0xFF1B2540), Color(0xFF2C3A5E), Color(0xFF46527A)),
        -18.0 to SkyGradient(Color(0xFF121A2E), Color(0xFF18223C), Color(0xFF232E4B)),
        -90.0 to SkyGradient(Color(0xFF0E1320), Color(0xFF131A2A), Color(0xFF1A2233))
    )

    /**
     * How much of a stop's color a fully overcast sky removes, and how much of its
     * brightness.
     *
     * Clouds take the COLOR out of a sky, not a fixed amount of light into it: the first
     * draft of §3.3 mixed every stop toward one grey, which made an overcast midnight
     * brighter than a clear dusk. Desaturating each stop toward its own brightness keeps
     * an overcast noon grey and an overcast midnight dark, which is the thing anyone
     * looking out of a window already knows.
     */
    private const val CloudDesaturation = 0.7f
    private const val CloudDarkening = 0.15f

    /** What moonlight lifts a night sky toward. */
    private val Moonlight = Color(0xFF2A3550)

    /**
     * The canvas for one moment.
     *
     * Order matters and is part of the spec: band, then cloud, then rain, then the moon
     * — with the moon's lift scaled DOWN by the cloud cover, because clouds hide the
     * moon. (Applying the lift after the cloud mix without that scaling would have made
     * an overcast full-moon night brighter than a clear one, which is how the
     * implementation found the hole in the first draft of §3.4.)
     *
     * @param sunAltitudeDeg the sun's altitude, from AstronomyEngine
     * @param cloudPct 0..100
     * @param precipPct 0..100
     * @param moonIllumination 0..1
     * @param moonAltitudeDeg the moon's altitude; below the horizon it contributes nothing
     */
    fun gradient(
        sunAltitudeDeg: Double,
        cloudPct: Int = 0,
        precipPct: Int = 0,
        moonIllumination: Double = 0.0,
        moonAltitudeDeg: Double = -90.0
    ): SkyGradient {
        val base = interpolate(sunAltitudeDeg)
        val cloud = (cloudPct.coerceIn(0, 100) / 100f)
        val clouded = base.map { stop ->
            val grey = stop.red * 0.2126f + stop.green * 0.7152f + stop.blue * 0.0722f
            val flat = lerp(stop, Color(grey, grey, grey, stop.alpha), CloudDesaturation * cloud)
            val dim = 1f - CloudDarkening * cloud
            Color(flat.red * dim, flat.green * dim, flat.blue * dim, flat.alpha)
        }
        val rain = if (precipPct > 50) 1f - 0.25f * ((precipPct.coerceAtMost(100) - 50) / 50f) else 1f
        val rained = clouded.map { Color(it.red * rain, it.green * rain, it.blue * rain, it.alpha) }
        val moonlit = if (sunAltitudeDeg < -6.0 && moonAltitudeDeg > 0.0) {
            val altitude = (moonAltitudeDeg / 40.0).coerceIn(0.0, 1.0)
            val lift = (moonIllumination.coerceIn(0.0, 1.0) * altitude * (1.0 - cloud)).toFloat()
            rained.map { lerp(it, Moonlight, lift) }
        } else {
            rained
        }
        return SkyGradient(moonlit[0], moonlit[1], moonlit[2])
    }

    /**
     * The scrim of DESIGN.md §3.6, living here rather than in the component that paints
     * it — which is where the first draft put it, until `NoRawColorTest` pointed out
     * that a hex outside `ui/theme/` is a hex outside `ui/theme/` whatever its excuse.
     * It belongs here anyway: the scrim is part of the sky's contract, and now
     * `ScrimContractTest` guards the value the canvas actually uses instead of a copy of
     * it.
     */
    val ScrimColor = Color(0xFF101216)

    /**
     * 0.55, and the number has a reason: against the brightest stop this palette can
     * produce, white lands at 5.29:1. 0.50 gives 4.53:1 and leaves no headroom for a
     * band added later; 0.45 gives 3.95:1 and fails outright.
     */
    const val ScrimAlpha = 0.55f

    /** The brightest canvas this palette can produce — what the scrim has to survive. */
    fun brightestBottomStop(): Color = anchors.first().second.bottom

    private fun interpolate(altitude: Double): List<Color> {
        val clamped = altitude.coerceIn(-90.0, 90.0)
        val upper = anchors.last { it.first >= clamped }
        val lower = anchors.first { it.first <= clamped }
        if (upper.first == lower.first) return upper.second.stops()
        val span = upper.first - lower.first
        val t = (if (abs(span) < 1e-9) 0.0 else (upper.first - clamped) / span).toFloat()
        return upper.second.stops().zip(lower.second.stops()) { a, b -> lerp(a, b, t) }
    }
}
