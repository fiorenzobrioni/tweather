package com.callbackdev.tweather.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.tweather.data.UnitSettings
import com.callbackdev.tweather.domain.rules.NotificationRule
import com.callbackdev.tweather.domain.rules.RuleCondition
import com.callbackdev.tweather.domain.rules.RuleOp
import com.callbackdev.tweather.domain.rules.RuleTrigger
import com.callbackdev.tweather.ui.weather.sampleWeatherReport
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class RuleNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val now: LocalDateTime = LocalDateTime.of(2023, 10, 27, 14, 30)
    private val report = sampleWeatherReport()

    private fun rule(id: Long = 1, name: String = "umbrella") = NotificationRule(
        id = id,
        name = name,
        enabled = true,
        conditions = listOf(RuleCondition("next_6h.precip_chance_max", RuleOp.GTE, 10.0)),
        message = "Take an umbrella — {trigger.value}% at {trigger.time}"
    )

    private fun trigger(id: Long = 1, name: String = "umbrella") = RuleTrigger(
        rule = rule(id, name),
        fingerprint = "fp",
        latchKey = null,
        value = 78.0,
        at = LocalDateTime.of(2023, 10, 27, 18, 0)
    )

    @Test
    fun `posts the user's interpolated message under the run command`() {
        val posted = RuleNotifier.notify(
            context, trigger(), "Milan", report, now, UnitSettings()
        )
        assertTrue(posted)
        val extras = shadowOf(manager).allNotifications.single().extras
        assertEquals("🔔 umbrella — Milan", extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            "Take an umbrella — 78% at 18:00",
            extras.getString(Notification.EXTRA_TEXT)
        )
        assertEquals(
            "$ tweather run umbrella\nTake an umbrella — 78% at 18:00",
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        )
    }

    @Test
    fun `same rule overwrites, different rules coexist`() {
        RuleNotifier.notify(context, trigger(id = 1), "Milan", report, now, UnitSettings())
        RuleNotifier.notify(context, trigger(id = 1), "Milan", report, now, UnitSettings())
        RuleNotifier.notify(
            context, trigger(id = 2, name = "sunscreen"), "Milan", report, now, UnitSettings()
        )
        assertEquals(2, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `notification ids never collide with the builtin alerts`() {
        // Builtins own 1001–1003; every possible rule id must land elsewhere
        assertEquals(2001, RuleNotifier.notificationId(1))
        assertEquals(2999, RuleNotifier.notificationId(999))
        assertEquals(2000, RuleNotifier.notificationId(1000))
    }
}
