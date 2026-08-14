package com.callbackdev.tweather.notifications

import com.callbackdev.tweather.data.NotificationSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertSchedulerTest {

    private val allOff = NotificationSettings(
        severeWeatherAlerts = false, dailySummary = false, precipitationWarning = false
    )

    @Test
    fun `runs only with at least one toggle on and permission granted`() {
        assertTrue(
            AlertScheduler.shouldRun(NotificationSettings(), notificationsEnabled = true)
        )
        assertTrue(
            AlertScheduler.shouldRun(allOff.copy(dailySummary = true), notificationsEnabled = true)
        )
        assertFalse(AlertScheduler.shouldRun(allOff, notificationsEnabled = true))
        assertFalse(
            AlertScheduler.shouldRun(NotificationSettings(), notificationsEnabled = false)
        )
    }
}
