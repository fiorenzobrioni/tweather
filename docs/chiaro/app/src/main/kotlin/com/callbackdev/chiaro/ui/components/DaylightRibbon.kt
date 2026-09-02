package com.callbackdev.chiaro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.callbackdev.chiaro.ui.theme.SkyPalette

/**
 * One phase of light, as a fraction of the day. [start] and [end] are 0..1 from local
 * midnight, and [sunAltitudeDeg] is what the segment is painted with, so the ribbon and
 * the canvas cannot disagree about what nautical twilight looks like.
 */
data class LightPhase(val start: Float, val end: Float, val sunAltitudeDeg: Double)

/**
 * DESIGN.md §4. The signature element: one day of light, as color.
 *
 * It is a **depiction, not an encoding** — nobody has to decode a color into a phase,
 * because every phase is named in text on the Sky screen and [description] says them
 * here. That is why it is allowed a natural sky gradient where §9.1 forbids a rainbow
 * for data.
 */
@Composable
fun DaylightRibbon(
    phases: List<LightPhase>,
    nowFraction: Float?,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    markerColor: Color = Color.White
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
    ) {
        phases.forEach { phase ->
            val left = size.width * phase.start.coerceIn(0f, 1f)
            val right = size.width * phase.end.coerceIn(0f, 1f)
            if (right <= left) return@forEach
            // The middle stop of the canvas' own gradient: the ribbon is a thin slice of
            // the same sky, not a second palette to keep in sync.
            drawRect(
                color = SkyPalette.gradient(phase.sunAltitudeDeg).mid,
                topLeft = Offset(left, 0f),
                size = Size(right - left, size.height)
            )
        }
        nowFraction?.let { fraction ->
            val x = size.width * fraction.coerceIn(0f, 1f)
            drawLine(
                color = markerColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 40)
@Composable
private fun DaylightRibbonPreview() {
    com.callbackdev.chiaro.ui.theme.ChiaroTheme(dynamicColor = false) {
        DaylightRibbon(
            phases = listOf(
                LightPhase(0f, 0.20f, -30.0),
                LightPhase(0.20f, 0.24f, -12.0),
                LightPhase(0.24f, 0.27f, -6.0),
                LightPhase(0.27f, 0.31f, 2.0),
                LightPhase(0.31f, 0.76f, 45.0),
                LightPhase(0.76f, 0.80f, 2.0),
                LightPhase(0.80f, 0.84f, -6.0),
                LightPhase(0.84f, 1f, -30.0)
            ),
            nowFraction = 0.62f,
            description = "Giorno fino alle 18:14, ora d'oro fino alle 19:12, notte dalle 20:06",
            modifier = Modifier.height(12.dp),
            height = 12.dp
        )
    }
}
