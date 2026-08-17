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
    fun `alerts wanted only with at least one toggle on and permission granted`() {
        assertTrue(AlertScheduler.alertsWanted(NotificationSettings(), notificationsEnabled = true))
        assertTrue(
            AlertScheduler.alertsWanted(
                allOff.copy(dailySummary = true),
                notificationsEnabled = true
            )
        )
        assertFalse(AlertScheduler.alertsWanted(allOff, notificationsEnabled = true))
        assertFalse(
            AlertScheduler.alertsWanted(NotificationSettings(), notificationsEnabled = false)
        )
    }

    @Test
    fun `runs when alerts are wanted, regardless of widgets`() {
        assertTrue(
            AlertScheduler.shouldRun(
                NotificationSettings(), notificationsEnabled = true, hasWidgets = false
            )
        )
        assertTrue(
            AlertScheduler.shouldRun(
                NotificationSettings(), notificationsEnabled = true, hasWidgets = true
            )
        )
    }

    @Test
    fun `a placed widget keeps the job alive with notifications off`() {
        assertTrue(
            AlertScheduler.shouldRun(allOff, notificationsEnabled = true, hasWidgets = true)
        )
        assertTrue(
            AlertScheduler.shouldRun(
                NotificationSettings(), notificationsEnabled = false, hasWidgets = true
            )
        )
    }

    @Test
    fun `user rules arm the job only with the master toggle on and rules defined`() {
        // rules exist and user_rules on (default) → wanted even with builtins off
        assertTrue(
            AlertScheduler.alertsWanted(allOff, notificationsEnabled = true, hasEnabledRules = true)
        )
        // an empty alerts.rules must not keep the phone polling
        assertFalse(
            AlertScheduler.alertsWanted(allOff, notificationsEnabled = true, hasEnabledRules = false)
        )
        // master toggle off silences the rules
        assertFalse(
            AlertScheduler.alertsWanted(
                allOff.copy(userRules = false), notificationsEnabled = true, hasEnabledRules = true
            )
        )
        // no permission silences everything
        assertFalse(
            AlertScheduler.alertsWanted(allOff, notificationsEnabled = false, hasEnabledRules = true)
        )
        assertTrue(
            AlertScheduler.shouldRun(
                allOff, notificationsEnabled = true, hasWidgets = false, hasEnabledRules = true
            )
        )
    }

    @Test
    fun `nothing to sync for - no alerts and no widget`() {
        assertFalse(
            AlertScheduler.shouldRun(allOff, notificationsEnabled = true, hasWidgets = false)
        )
        assertFalse(
            AlertScheduler.shouldRun(
                NotificationSettings(), notificationsEnabled = false, hasWidgets = false
            )
        )
    }
}
