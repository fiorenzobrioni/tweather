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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AlertNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)

    private val severe = Alert(
        kind = AlertKind.SEVERE,
        fingerprint = "fp",
        cityLabel = "Milan",
        condition = WeatherCondition(95, "Thunderstorm", "⛈️"),
        at = LocalDateTime.of(2023, 10, 27, 18, 0),
        precipPct = 85
    )

    private val summary = Alert(
        kind = AlertKind.DAILY_SUMMARY,
        fingerprint = "2023-10-27",
        cityLabel = "Milan",
        condition = WeatherCondition(2, "Partly Cloudy", "⛅"),
        precipPct = 20,
        highC = 24.0,
        lowC = 15.0
    )

    @Test
    fun `severe alert posts localized title plus a pretty-printed json body`() {
        val posted = AlertNotifier.notify(context, severe, TemperatureUnit.CELSIUS)
        assertTrue(posted)
        val notifications = shadowOf(manager).allNotifications
        assertEquals(1, notifications.size)
        val extras = notifications[0].extras
        assertTrue(extras.getString(Notification.EXTRA_TITLE)!!.contains("Milan"))
        assertEquals(
            """
            $ tweather --alert severe
            {
              "time": "18:00",
              "status": "Thunderstorm ⛈️",
              "wmo_code": 95,
              "precip_chance": 85
            }
            """.trimIndent(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        )
    }

    @Test
    fun `collapsed text folds the same object onto one line`() {
        AlertNotifier.notify(context, severe, TemperatureUnit.CELSIUS)
        assertEquals(
            """{ "time": "18:00", "status": "Thunderstorm ⛈️", "wmo_code": 95, """ +
                """"precip_chance": 85 }""",
            shadowOf(manager).allNotifications.single()
                .extras.getString(Notification.EXTRA_TEXT)
        )
    }

    @Test
    @Config(qualifiers = "it")
    fun `data values follow the device language, keys and command do not`() {
        AlertNotifier.notify(context, summary, TemperatureUnit.CELSIUS)
        val big = shadowOf(manager).allNotifications.single()
            .extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        assertEquals(
            """
            $ tweather --daily
            {
              "status": "Parzialmente nuvoloso ⛅",
              "high_c": 24,
              "low_c": 15,
              "precip_pct": 20
            }
            """.trimIndent(),
            big
        )
    }

    @Test
    fun `daily summary converts temperatures to the user unit and says so in the key`() {
        AlertNotifier.notify(context, summary, TemperatureUnit.FAHRENHEIT)
        val big = shadowOf(manager).allNotifications.single()
            .extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        assertTrue(big.contains(""""high_f": 75"""))
        assertTrue(big.contains(""""low_f": 59"""))
    }

    @Test
    fun `precipitation warning carries the rain type it is warning about`() {
        AlertNotifier.notify(
            context,
            Alert(
                kind = AlertKind.PRECIPITATION, fingerprint = "c", cityLabel = "Milan",
                condition = WeatherCondition(80, "Rain Showers", "🌦️"),
                at = LocalDateTime.of(2023, 10, 27, 15, 0), precipPct = 80
            ),
            TemperatureUnit.CELSIUS
        )
        assertEquals(
            """
            $ tweather --alert precip
            {
              "time": "15:00",
              "status": "Rain Showers 🌦️",
              "precip_chance": 80
            }
            """.trimIndent(),
            shadowOf(manager).allNotifications.single()
                .extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        )
    }

    @Test
    fun `same kind overwrites, different kinds coexist`() {
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
