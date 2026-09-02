package com.callbackdev.chiaro.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * DESIGN.md §7. Springs, not durations: a duration says how long, a spring says how it
 * feels, and Material 3 Expressive's whole motion argument is that the second is what a
 * reader notices.
 *
 * [ChiaroMotion.reducedMotionFade] is the escape hatch every one of these collapses to
 * when the reader has animations turned off. Nothing here may ever gate information:
 * a reader with motion off sees the same content at the same moment.
 */
object ChiaroMotion {

    /** Anything that moves or resizes. */
    fun <T> spatial() = spring<T>(dampingRatio = 0.8f, stiffness = 380f)

    /** Chips, toggles, small state. */
    fun <T> spatialFast() = spring<T>(dampingRatio = 0.9f, stiffness = 800f)

    /** Color, alpha, elevation — things that change without moving. */
    fun <T> effects() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1600f)

    const val reducedMotionFadeMillis = 100
}
