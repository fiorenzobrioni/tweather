package com.callbackdev.tweather.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.callbackdev.tweather.MainActivity
import com.callbackdev.tweather.R
import com.callbackdev.tweather.domain.sky.SkyJobCatalog
import com.callbackdev.tweather.domain.sky.SkyVerdict
import com.callbackdev.tweather.domain.sky.SkyVerdictKind
import com.callbackdev.tweather.domain.sky.SkyVerdictNote
import com.callbackdev.tweather.ui.sky.SkyJobNames
import com.callbackdev.tweather.ui.sky.SkyManPages
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The sky reminder as a system notification (Fase 16f).
 *
 * Same idiom as the rest of the app: a localized title over a body that reads like
 * the file it came from — the job's own name, the time, and the verdict WITH the
 * number behind it. Never anything motivational, never a digest.
 *
 * Collapsed and expanded are two texts since Fase 25; they were one, and pulling a
 * reminder open gave back the line it already showed. The dotted id in the title is
 * what `sky.crontab` calls this job and stays exactly that, so the unfold says what
 * it MEANS — the localized name from [SkyJobNames] — and where the rest of it is
 * written: `$ man 7 <id>`, the page Fase 23 wrote for precisely this reader.
 *
 * One channel and one notification id per job, so a second reminder for the same job
 * overwrites rather than stacking.
 */
object SkyNotifier {

    const val CHANNEL_ID = "sky"

    private val ClockTime = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Posts the reminder; false when notifications are off or the channel is muted —
     * the caller must then NOT burn the fingerprint, so it can still fire if the
     * user re-enables the channel.
     */
    fun notify(
        context: Context,
        jobId: String,
        occurrenceAt: Instant,
        zone: ZoneId,
        verdict: SkyVerdict?,
        now: Instant
    ): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel(context, manager)
        if (manager.getNotificationChannelCompat(CHANNEL_ID)?.importance ==
            NotificationManagerCompat.IMPORTANCE_NONE
        ) {
            return false
        }

        val minutes = Duration.between(now, occurrenceAt).toMinutes().coerceAtLeast(0)
        val body = body(context, occurrenceAt, zone, verdict, minutes)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tweather)
            .setContentTitle(context.getString(R.string.sky_notification_title, jobId))
            .setContentText(body)
            // BigTextStyle is also what stops the system from eliding the verdict on
            // a narrow screen — but it carries more than the line now, see above.
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(expandedBody(context, jobId, body))
            )
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        return try {
            manager.notify(notificationId(jobId), notification)
            true
        } catch (e: SecurityException) {
            false // POST_NOTIFICATIONS revoked between the check and the post
        }
    }

    /**
     * `in 30 min · 20:12 · ✓ pass  cloud 8%`. The verdict carries its evidence here
     * too: a reminder that says "clear" without the number it read is an opinion,
     * and this app does not send opinions.
     */
    private fun body(
        context: Context,
        occurrenceAt: Instant,
        zone: ZoneId,
        verdict: SkyVerdict?,
        minutes: Long
    ): String = buildString {
        append(context.getString(R.string.sky_notification_in, minutes))
        append(" · ").append(occurrenceAt.atZone(zone).format(ClockTime))
        verdict?.let { append(" · ").append(shortVerdict(it)) }
    }

    /**
     * The line, with what the job is called and where it is explained.
     *
     * The name is prose and localizes; the id and the command do not — the id is what
     * the file prints and what the manual is indexed by, and translating it would
     * break the tie with both. Internal for the test.
     */
    internal fun expandedBody(context: Context, jobId: String, body: String): String {
        val name = SkyJobNames.label(context.resources, jobId)
        return if (SkyManPages.hasPage(jobId)) {
            "$name\n$body\n$ man ${SkyManPages.SECTION} $jobId"
        } else {
            "$name\n$body"
        }
    }

    private fun shortVerdict(verdict: SkyVerdict): String = buildString {
        append(verdict.kind.glyph).append(" ").append(verdict.kind.word)
        when (verdict.note) {
            // The two `? unknown` reasons a reminder can carry: both say the app has
            // nothing recent, which is the honest half of sending it anyway.
            SkyVerdictNote.NO_DATA, SkyVerdictNote.STALE_DATA -> append(" (no recent data)")
            SkyVerdictNote.MOONLIGHT -> append("  moon ${verdict.moonPct}%")
            SkyVerdictNote.PRECIPITATION -> append("  rain ${verdict.precipPct}%")
            else -> Unit
        }
        if (verdict.kind != SkyVerdictKind.UNKNOWN && verdict.note != SkyVerdictNote.MOONLIGHT) {
            verdict.cloudPct?.let { append("  cloud ").append(it).append("%") }
        }
    }

    /**
     * One id per job: a second reminder for the same job replaces the first. Derived
     * from the job's position in the catalog rather than from `jobId.hashCode()`,
     * because two hashes that happened to collide would silently make one reminder
     * overwrite another job's — a bug that would only ever show up on a device.
     */
    private fun notificationId(jobId: String): Int =
        7000 + SkyJobCatalog.all.indexOfFirst { it.id == jobId }.coerceAtLeast(0)

    private fun ensureChannel(context: Context, manager: NotificationManagerCompat) {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(
                CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT
            )
                .setName(context.getString(R.string.sky_channel_name))
                .setDescription(context.getString(R.string.sky_channel_description))
                .build()
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        CHANNEL_ID.hashCode(),
        Intent(context, MainActivity::class.java)
            // Same three flags as AlertNotifier, for the reason documented there:
            // without SINGLE_TOP, CLEAR_TOP rebuilds a launchMode=standard activity
            // and replays the splash instead of resuming where the user was.
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            ),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
