package com.callbackdev.chiaro.domain

import com.callbackdev.chiaro.domain.model.MoonPhase
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class MoonPhaseTest {

    // Reference instants from astronomical tables (times UTC)

    @Test
    fun `new moon on 2024-01-11`() {
        assertEquals(MoonPhase.NEW_MOON, MoonPhase.at(Instant.parse("2024-01-11T11:57:00Z")))
    }

    @Test
    fun `first quarter on 2024-01-18`() {
        assertEquals(MoonPhase.FIRST_QUARTER, MoonPhase.at(Instant.parse("2024-01-18T03:52:00Z")))
    }

    @Test
    fun `full moon on 2024-01-25`() {
        assertEquals(MoonPhase.FULL_MOON, MoonPhase.at(Instant.parse("2024-01-25T17:54:00Z")))
    }

    @Test
    fun `last quarter on 2024-02-02`() {
        assertEquals(MoonPhase.LAST_QUARTER, MoonPhase.at(Instant.parse("2024-02-02T23:18:00Z")))
    }

    @Test
    fun `waxing gibbous between first quarter and full`() {
        assertEquals(MoonPhase.WAXING_GIBBOUS, MoonPhase.at(Instant.parse("2024-01-22T00:00:00Z")))
    }

    @Test
    fun `dates before the reference new moon do not crash`() {
        // 1999-12-22 was a full moon, ~15 days before the 2000-01-06 reference
        assertEquals(MoonPhase.FULL_MOON, MoonPhase.at(Instant.parse("1999-12-22T17:31:00Z")))
    }
}
