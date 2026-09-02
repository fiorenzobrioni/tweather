package com.callbackdev.chiaro.domain

import java.time.Duration
import java.time.Instant

/**
 * When the app's last fetch stops counting as current.
 *
 * One rule, two readers since Fase 16d: the home widget prints `# stale` past it,
 * and the sky module refuses to build a verdict on data older than it. It lived
 * inside the widget until then, which is where it was written but not where it
 * belongs — how old is too old is a fact about the DATA, not about one surface that
 * happens to draw it.
 *
 * Twice the polling interval: one missed sync is ordinary (the device was asleep, the
 * network was down for a minute), two in a row means the numbers on screen are no
 * longer a claim about now.
 */
object WeatherFreshness {

    fun staleAfter(updateFrequencyMin: Int): Duration =
        Duration.ofMinutes(2L * updateFrequencyMin)

    fun isStale(lastSync: Instant, updateFrequencyMin: Int, now: Instant): Boolean =
        Duration.between(lastSync, now) > staleAfter(updateFrequencyMin)
}
