package com.callbackdev.tweather.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.tweather.domain.sky.SkyJobCatalog
import com.callbackdev.tweather.domain.sky.SkyVerdict
import com.callbackdev.tweather.domain.sky.SkyVerdictKind
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The reminder's two bodies (Fase 25). They were one string until then, so opening
 * the notification returned the line it already showed.
 */
@RunWith(RobolectricTestRunner::class)
class SkyNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val zone: ZoneId = ZoneId.of("Europe/Rome")
    private val now: Instant = Instant.parse("2026-09-06T16:51:00Z")
    private val at: Instant = now.plusSeconds(30 * 60)

    private fun post(jobId: String = SkyJobCatalog.GoldenPm.id) = SkyNotifier.notify(
        context,
        jobId = jobId,
        occurrenceAt = at,
        zone = zone,
        verdict = SkyVerdict(SkyVerdictKind.PASS, cloudPct = 8),
        now = now
    )

    private fun extras() = shadowOf(manager).allNotifications.single().extras

    @Test
    fun `collapsed stays the one line the crontab would print`() {
        assertTrue(post())
        assertEquals("in 30 min · 19:21 · ✓ pass  cloud 8%", extras().getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun `expanded says what the dotted id means and where it is explained`() {
        post()
        assertEquals(
            """
            🌇 The evening golden hour
            in 30 min · 19:21 · ✓ pass  cloud 8%
            $ man 7 golden_hour.pm
            """.trimIndent(),
            extras().getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        )
    }

    @Test
    @Config(qualifiers = "it")
    fun `the name localizes, the id and the command do not`() {
        post()
        val big = extras().getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        assertTrue(big, big.startsWith("🌇 L'ora d'oro della sera"))
        assertTrue(big, big.endsWith("$ man 7 golden_hour.pm"))
        // The verdict is the evidence column's own vocabulary and stays put.
        assertTrue(big, big.contains("✓ pass  cloud 8%"))
    }
}
