package com.callbackdev.tweather.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.domain.Alert
import com.callbackdev.tweather.domain.AlertKind
import com.callbackdev.tweather.domain.model.WeatherCondition
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AlertNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test
    fun `severe alert posts hybrid title plus terminal body`() {
        val posted = AlertNotifier.notify(
            context,
            Alert(
                kind = AlertKind.SEVERE,
                fingerprint = "fp",
                cityLabel = "Milan",
                condition = WeatherCondition(95, "Thunderstorm", "⛈️"),
                at = LocalDateTime.of(2023, 10, 27, 18, 0),
                precipPct = 85
            ),
            TemperatureUnit.CELSIUS
        )
        assertTrue(posted)
        val notifications = shadowOf(manager).allNotifications
        assertEquals(1, notifications.size)
        val extras = notifications[0].extras
        assertTrue(extras.getString(Notification.EXTRA_TITLE)!!.contains("Milan"))
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        assertTrue(big.contains("$ tweather --alert severe"))
        assertTrue(big.contains("18:00"))
        assertTrue(big.contains("(wmo 95)"))
    }

    @Test
    fun `daily summary converts temperatures to the user unit`() {
        AlertNotifier.notify(
            context,
            Alert(
                kind = AlertKind.DAILY_SUMMARY,
                fingerprint = "2023-10-27",
                cityLabel = "Milan",
                condition = WeatherCondition(2, "Partly Cloudy", "⛅"),
                precipPct = 20,
                highC = 24.0,
                lowC = 15.0
            ),
            TemperatureUnit.FAHRENHEIT
        )
        val big = shadowOf(manager).allNotifications.single()
            .extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        assertTrue(big.contains("high 75°F"))
        assertTrue(big.contains("low 59°F"))
    }

    @Test
    fun `same kind overwrites, different kinds coexist`() {
        val severe = Alert(
            kind = AlertKind.SEVERE, fingerprint = "a", cityLabel = "Milan",
            condition = WeatherCondition(95, "Thunderstorm", "⛈️"),
            at = LocalDateTime.of(2023, 10, 27, 18, 0)
        )
        AlertNotifier.notify(context, severe, TemperatureUnit.CELSIUS)
        AlertNotifier.notify(context, severe.copy(fingerprint = "b"), TemperatureUnit.CELSIUS)
        AlertNotifier.notify(
            context,
            Alert(
                kind = AlertKind.PRECIPITATION, fingerprint = "c", cityLabel = "Milan",
                at = LocalDateTime.of(2023, 10, 27, 15, 0), precipPct = 80
            ),
            TemperatureUnit.CELSIUS
        )
        assertEquals(2, shadowOf(manager).allNotifications.size)
    }
}
