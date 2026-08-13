package com.callbackdev.tweather.domain.model

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Moon phase, computed locally because Open-Meteo does not provide it. Mean synodic
 * cycle from a reference new moon — accuracy within ~1 day, plenty for an emoji.
 */
enum class MoonPhase(val label: String, val emoji: String) {
    NEW_MOON("New Moon", "🌑"),
    WAXING_CRESCENT("Waxing Crescent", "🌒"),
    FIRST_QUARTER("First Quarter", "🌓"),
    WAXING_GIBBOUS("Waxing Gibbous", "🌔"),
    FULL_MOON("Full Moon", "🌕"),
    WANING_GIBBOUS("Waning Gibbous", "🌖"),
    LAST_QUARTER("Last Quarter", "🌗"),
    WANING_CRESCENT("Waning Crescent", "🌘");

    /** Rendered form used in the JSON UI, e.g. `"Waxing Gibbous 🌔"`. */
    val text: String get() = "$label $emoji"

    companion object {
        private const val SYNODIC_MONTH_DAYS = 29.530588853
        private val REFERENCE_NEW_MOON: Instant = Instant.parse("2000-01-06T18:14:00Z")

        fun at(instant: Instant): MoonPhase {
            val daysSinceReference =
                Duration.between(REFERENCE_NEW_MOON, instant).seconds / 86_400.0
            val age = ((daysSinceReference % SYNODIC_MONTH_DAYS) + SYNODIC_MONTH_DAYS) %
                SYNODIC_MONTH_DAYS
            val index = (age / SYNODIC_MONTH_DAYS * entries.size).roundToInt() % entries.size
            return entries[index]
        }
    }
}
