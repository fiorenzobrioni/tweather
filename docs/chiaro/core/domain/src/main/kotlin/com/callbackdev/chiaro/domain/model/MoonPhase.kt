package com.callbackdev.chiaro.domain.model

import com.callbackdev.chiaro.domain.sky.AstronomyEngine
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Moon phase, computed locally because Open-Meteo does not provide it.
 *
 * **A classifier, not a computation, since Fase 16b.** Until then this enum carried
 * its own arithmetic: an eight-bucket average of a mean synodic month from a
 * hardcoded reference new moon, accurate to about a day — which its own KDoc admitted
 * was "plenty for an emoji". The sky module needs the real thing (a quarter's INSTANT
 * is a line of `sky.crontab`), and the module rule is that one engine answers for the
 * whole app: two moon models would be two answers to "what night is the full moon",
 * one in the README and one in the crontab.
 *
 * So the arithmetic moved to [AstronomyEngine] and what stayed here is the naming:
 * the eight names, their emoji, and the convention that each covers an eighth of the
 * cycle centred on its own elongation. The rendered value is unchanged in kind and
 * more accurate in fact — near a boundary it can now land on the other side, which is
 * the improvement, not a regression.
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

        /**
         * The phase name at [instant], from the moon's elongation from the sun: 0° is
         * new, 90° first quarter, 180° full. Each name owns the eighth of the cycle
         * centred on its own angle, so `NEW_MOON` covers ±22.5° — about a day and
         * three quarters either side of the instant, which is the span over which a
         * person would actually call the moon new.
         */
        fun at(instant: Instant): MoonPhase {
            val elongation = AstronomyEngine.moonIllumination(instant).elongation
            val index = (elongation / 360.0 * entries.size).roundToInt() % entries.size
            return entries[index]
        }
    }
}
