package com.callbackdev.chiaro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.callbackdev.chiaro.ui.theme.ChiaroColors
import com.callbackdev.chiaro.ui.theme.ChiaroTheme

/**
 * DESIGN.md §8.3 and §9. One series, one hue, and an axis that does not move: rain
 * probability is 0..100 whatever today happens to hold, so a quiet day draws a flat line
 * instead of being stretched into a drama.
 *
 * [description] is required. §9.3: a picture of a number is not a number, and this one
 * has no labels of its own.
 */
@Composable
fun RainSparkline(
    percentages: List<Int>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp
) {
    val color = ChiaroTheme.colors.rainAt(percentages.maxOrNull() ?: 0)
    Canvas(
        modifier = modifier.fillMaxWidth().height(height)
            .semantics { contentDescription = description }
    ) {
        if (percentages.size < 2) return@Canvas
        val step = size.width / (percentages.size - 1)
        val path = Path()
        percentages.forEachIndexed { index, value ->
            val x = step * index
            // The axis is fixed at 0..100, never at the data's own range.
            val y = size.height * (1f - value.coerceIn(0, 100) / 100f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * DESIGN.md §8.5. One day's low and high on a scale **shared by the whole week**, so the
 * week has a shape: a bar per row scaled to its own day would make every day look
 * identical, which is the exact opposite of what a week view is for.
 *
 * The ends are printed by the caller as numbers (§9.3). A colored bar is not a number.
 */
@Composable
fun TemperatureRangeBar(
    lowC: Double,
    highC: Double,
    scaleLowC: Double,
    scaleHighC: Double,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val colors: ChiaroColors = ChiaroTheme.colors
    val track = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(
        modifier = modifier.fillMaxWidth().height(height)
            .semantics { contentDescription = description }
    ) {
        val span = (scaleHighC - scaleLowC).takeIf { it > 0.0 } ?: 1.0
        fun fraction(value: Double) = ((value - scaleLowC) / span).coerceIn(0.0, 1.0).toFloat()
        val radius = size.height / 2f
        drawRoundRect(
            color = track,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
        )
        val left = size.width * fraction(lowC)
        val right = size.width * fraction(highC)
        drawRoundRect(
            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                listOf(colors.temperatureAt(lowC), colors.temperatureAt(highC)),
                startX = left,
                endX = right.coerceAtLeast(left + 1f)
            ),
            topLeft = Offset(left, 0f),
            size = Size((right - left).coerceAtLeast(size.height), size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
        )
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun ChartsPreview() {
    com.callbackdev.chiaro.ui.theme.ChiaroTheme(dynamicColor = false) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            RainSparkline(
                percentages = listOf(0, 5, 10, 40, 70, 80, 60, 20, 10, 5, 0, 0),
                description = "Picco di pioggia 80% alle 17"
            )
            listOf(4.0 to 11.0, 8.0 to 19.0, 14.0 to 27.0).forEach { (low, high) ->
                TemperatureRangeBar(
                    lowC = low, highC = high, scaleLowC = 2.0, scaleHighC = 30.0,
                    description = "Da ${low.toInt()} a ${high.toInt()} gradi"
                )
            }
        }
    }
}
