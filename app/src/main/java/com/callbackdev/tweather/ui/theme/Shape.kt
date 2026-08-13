package com.callbackdev.tweather.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Geometric and sharp: every container uses a strict 4px radius. The FAB is the single
// non-rectangular element and uses CircleShape explicitly (GlowFab, Fase 2).
private val EditorCorner = RoundedCornerShape(4.dp)

val TweatherShapes = Shapes(
    extraSmall = EditorCorner,
    small = EditorCorner,
    medium = EditorCorner,
    large = EditorCorner,
    extraLarge = EditorCorner
)
