package com.callbackdev.tweather.domain

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

    /**
     * How long a fetched report is still the provider's own answer for "now" — the
     * cache TTL of [com.callbackdev.tweather.data.WeatherRepository], and the whole of
     * the 6 set 2026 fix (device report: the current block read like the last hour,
     * not this minute).
     *
     * Open-Meteo publishes the `current` block on a 15-minute grid: every response
     * carries `"interval": 900` beside it, and `current.time` is the last quarter
     * hour, not the request's own minute. Fifteen minutes is therefore the point past
     * which a held report is not merely old, it is a value the provider has already
     * replaced — and re-reading it costs one request the reader asked for by opening
     * the app.
     *
     * It is deliberately NOT `update_frequency_min`, which is what it used to be. That
     * setting is the BACKGROUND polling interval — a battery choice, 15/30/60/120 with
     * 60 the default — and using it as the TTL let it decide something it was never
     * offered for: how old the numbers may be with the reader looking straight at
     * them. An hour by default, two at the top of the range, and nothing said so
     * because a hit was never counted as stale. Battery is a feature, but the periodic
     * job is where that is paid; a screen the reader just opened is not.
     *
     * Below every [staleAfter] this object can return (the shortest is 2 × 15 = 30
     * minutes), so a cache hit still cannot be stale.
     */
    val ProviderResolution: Duration = Duration.ofMinutes(15)

    fun staleAfter(updateFrequencyMin: Int): Duration =
        Duration.ofMinutes(2L * updateFrequencyMin)

    fun isStale(lastSync: Instant, updateFrequencyMin: Int, now: Instant): Boolean =
        Duration.between(lastSync, now) > staleAfter(updateFrequencyMin)
}
