package com.callbackdev.tweather.ui.theme

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// "No shadow" rule: depth comes from 1px borders and tonal stacking, never from
// elevation. Components must not use Modifier.shadow / elevated Material variants;
// they stack surface-container tones and draw an editorBorder instead. The FAB glow
// below is the single sanctioned exception.

/** Standard 1px structural border (`#30363d` in Obsidian), 4px corner radius. */
@Composable
fun Modifier.editorBorder(shape: Shape = MaterialTheme.shapes.small): Modifier =
    border(1.dp, TweatherTheme.syntax.border, shape)

/** Focused/active variant: same 1px border in primary blue. */
@Composable
fun Modifier.editorFocusBorder(shape: Shape = MaterialTheme.shapes.small): Modifier =
    border(1.dp, MaterialTheme.colorScheme.primaryContainer, shape)

/**
 * Circular glow for the FAB — the only shadow-like effect in the app, equivalent to
 * the mockups' `box-shadow: 0 0 15px #79c0ff88`: full [color] at the button edge
 * fading to transparent over a 15dp spread.
 */
@Composable
fun Modifier.fabGlow(color: Color = TweatherTheme.syntax.glow): Modifier = drawBehind {
    val buttonRadius = size.minDimension / 2f
    val glowRadius = buttonRadius + 15.dp.toPx()
    if (glowRadius <= 0f) return@drawBehind
    drawCircle(
        brush = Brush.radialGradient(
            buttonRadius / glowRadius to color,
            1f to color.copy(alpha = 0f),
            center = center,
            radius = glowRadius
        ),
        radius = glowRadius,
        center = center
    )
}
