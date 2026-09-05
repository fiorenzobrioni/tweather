package com.callbackdev.tweather.domain.sky

import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.HourlyForecast
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * One stretch of the day when the sky is arranged for a rainbow: the sun low enough
 * behind you, rain likely in front, and enough gap in the cloud for the light to get
 * through.
 *
 * [lookTowardsDeg] is the ANTISOLAR bearing, degrees clockwise from north — where the
 * bow would stand, which is the only part of this a person can act on.
 */
data class Rainbow(
    val start: Instant,
    val end: Instant,
    val lookTowardsDeg: Double,
    /** The forecast's own rain probability for the hours in the window, 0..100. */
    val precipChancePct: Int
)

/**
 * The one sky event in the module that is not astronomy (Fase 19).
 *
 * Every other job here is the sun and the moon doing what they were always going to
 * do; this one needs the forecast, because a rainbow is **geometry times weather**.
 * The geometry is exact and this app already computes it — a rainbow's arc is centred
 * on the antisolar point and rises 42° from it, so it only clears the horizon while
 * the sun is under 42° — and the weather half is two numbers the fetch already
 * carries: how likely rain is in that hour, and how much of the sky is covered.
 *
 * That intersection is the only place a weather app can beat an ephemeris site at its
 * own game, and it is deliberately stated as a POSSIBILITY: this returns the window
 * in which the sky is arranged for one, never a promise that there will be a rainbow.
 * The thresholds are constants here and the rain probability travels with the window,
 * so whatever renders it can print the number it was decided on — the same rule the
 * verdicts follow.
 */
object RainbowWindow {

    /**
     * The bow's own radius: its top is 42° above the antisolar point, so with the sun
     * higher than that the whole arc is under the horizon and there is nothing to see
     * however hard it rains.
     */
    const val MAX_SUN_ALTITUDE = 42.0

    /** Rain has to be likely enough to matter: the verdict engine's own threshold. */
    const val MIN_PRECIP_PCT = SkyVerdictEngine.PRECIP_UNSTABLE_PCT

    /**
     * And the sky cannot be shut. A rainbow is a sunbeam in falling rain, so total
     * overcast is the one cloud state that rules it out — this is a ceiling on cloud,
     * not the floor the other jobs use, which is why it is its own number.
     */
    const val MAX_CLOUD_PCT = 85

    /**
     * The windows inside [hours], merged where they are consecutive.
     *
     * Works hour by hour because the forecast does: each hour is tested at its own
     * midpoint, which is where its numbers are truest, and neighbouring hours that
     * both qualify become one window rather than two rows saying the same thing.
     */
    fun windows(
        hours: List<HourlyForecast>,
        zone: ZoneId,
        coords: Coordinates
    ): List<Rainbow> {
        val open = mutableListOf<Rainbow>()
        var current: MutableList<HourlyForecast>? = null

        fun close() {
            val run = current ?: return
            current = null
            val start = run.first().time.atZone(zone).toInstant()
            val end = run.last().time.atZone(zone).toInstant().plus(Duration.ofHours(1))
            val best = run.maxBy { it.precipChancePct }
            val middle = best.time.atZone(zone).toInstant().plus(Duration.ofMinutes(30))
            open += Rainbow(
                start = start,
                end = end,
                // Opposite the sun, which is where the bow is centred. The reader is
                // told to turn their back on the sun and this is that sentence's number.
                lookTowardsDeg = (AstronomyEngine.sunAzimuth(middle, coords) + 180.0) % 360.0,
                precipChancePct = best.precipChancePct
            )
        }

        hours.forEach { hour ->
            val middle = hour.time.atZone(zone).toInstant().plus(Duration.ofMinutes(30))
            val altitude = AstronomyEngine.sunAltitude(middle, coords)
            val qualifies = altitude > 0 && altitude < MAX_SUN_ALTITUDE &&
                hour.precipChancePct >= MIN_PRECIP_PCT &&
                hour.cloudCoverPct <= MAX_CLOUD_PCT
            if (qualifies) {
                (current ?: mutableListOf<HourlyForecast>().also { current = it }).add(hour)
            } else {
                close()
            }
        }
        close()
        return open
    }
}
