package com.callbackdev.tweather.domain

import com.callbackdev.tweather.data.UpdateFrequencies
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two intervals this app keeps, and the fact that they are two (Fase 25).
 */
class WeatherFreshnessTest {

    private val now: Instant = Instant.parse("2026-09-06T10:00:00Z")

    @Test
    fun `the cache ttl is the provider's own resolution, not a setting`() {
        // Open-Meteo answers `"interval": 900` beside every `current` block. A held
        // report past that is a value the provider has already replaced.
        assertEquals(Duration.ofMinutes(15), WeatherFreshness.ProviderResolution)
    }

    @Test
    fun `a cache hit can never be stale, at any polling interval the user can pick`() {
        // What WeatherUiState.staleFor rests on: the document says `// stale` only
        // past twice the interval, and a HIT is always younger than the TTL. The
        // margin used to be exactly 2× at every setting because the TTL WAS the
        // setting; now the tightest case is 15 against 30.
        UpdateFrequencies.forEach { minutes ->
            assertTrue(
                "$minutes min",
                WeatherFreshness.ProviderResolution < WeatherFreshness.staleAfter(minutes)
            )
            val oldestHit = now.minus(WeatherFreshness.ProviderResolution)
            assertFalse("$minutes min", WeatherFreshness.isStale(oldestHit, minutes, now))
        }
    }

    @Test
    fun `stale is still two missed syncs, and it moves with the setting`() {
        assertEquals(Duration.ofMinutes(120), WeatherFreshness.staleAfter(60))
        assertFalse(WeatherFreshness.isStale(now.minus(Duration.ofMinutes(120)), 60, now))
        assertTrue(WeatherFreshness.isStale(now.minus(Duration.ofMinutes(121)), 60, now))
    }
}
