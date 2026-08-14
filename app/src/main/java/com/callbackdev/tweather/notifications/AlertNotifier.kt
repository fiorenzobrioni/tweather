package com.callbackdev.tweather.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.callbackdev.tweather.MainActivity
import com.callbackdev.tweather.R
import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.domain.Alert
import com.callbackdev.tweather.domain.AlertKind
import com.callbackdev.tweather.ui.weather.convert
import com.callbackdev.tweather.ui.weather.symbol
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Renders an [Alert] as a system notification, hybrid style per the l10n rule:
 * localized title (chrome), English terminal body (code). One channel and one
 * fixed notification id per kind — same-kind alerts overwrite, never stack.
 */
object AlertNotifier {

    /**
     * Posts the notification; false when notifications are disabled or the kind's
     * channel is muted — the caller must then NOT burn the alert's fingerprint,
     * so the alert can still fire if the user re-enables the channel.
     */
    fun notify(context: Context, alert: Alert, temperatureUnit: TemperatureUnit): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel(context, manager, alert.kind)
        val channel = manager.getNotificationChannelCompat(alert.kind.channelId)
        if (channel?.importance == NotificationManagerCompat.IMPORTANCE_NONE) return false

        val body = terminalBody(alert, temperatureUnit)
        val notification = NotificationCompat.Builder(context, alert.kind.channelId)
            .setSmallIcon(R.drawable.ic_stat_tweather)
            .setContentTitle(title(context, alert))
            .setContentText(body.lineSequence().last())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent(context, alert.kind))
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify(alert.kind.notificationId, notification)
            true
        } catch (e: SecurityException) {
            false // POST_NOTIFICATIONS revoked between the check and the post
        }
    }

    /** Localized chrome: `⛈️ Allerta meteo — Milan`. */
    private fun title(context: Context, alert: Alert): String {
        val emoji = alert.condition?.emoji ?: alert.kind.fallbackEmoji
        return context.getString(alert.kind.titleRes, emoji, alert.cityLabel)
    }

    /** English terminal output — "code" per the design's localization rule. */
    private fun terminalBody(alert: Alert, unit: TemperatureUnit): String {
        val time = alert.at?.format(ClockTime)
        fun temp(celsius: Double?) =
            celsius?.let { "${unit.convert(it).roundToInt()}${unit.symbol}" }
        return when (alert.kind) {
            AlertKind.SEVERE ->
                "$ tweather --alert severe\n" +
                    "$time  ${alert.condition?.label}  (wmo ${alert.condition?.wmoCode})"
            AlertKind.PRECIPITATION ->
                "$ tweather --alert precip\n" +
                    "$time  precip_chance: ${alert.precipPct}%"
            AlertKind.DAILY_SUMMARY ->
                "$ tweather --daily\n" +
                    "high ${temp(alert.highC)}  low ${temp(alert.lowC)}  " +
                    "${alert.condition?.label}  precip ${alert.precipPct}%"
        }
    }

    private fun ensureChannel(
        context: Context,
        manager: NotificationManagerCompat,
        kind: AlertKind
    ) {
        // Idempotent: recreating an existing channel is a no-op, so no
        // Application-level registration is needed.
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(kind.channelId, kind.channelImportance)
                .setName(context.getString(kind.channelNameRes))
                .build()
        )
    }

    private fun openAppIntent(context: Context, kind: AlertKind): PendingIntent =
        PendingIntent.getActivity(
            context,
            kind.notificationId,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private val ClockTime = DateTimeFormatter.ofPattern("HH:mm")

    private val AlertKind.channelId: String
        get() = when (this) {
            AlertKind.SEVERE -> "severe_alerts"
            AlertKind.PRECIPITATION -> "precip_warnings"
            AlertKind.DAILY_SUMMARY -> "daily_summary"
        }

    private val AlertKind.notificationId: Int
        get() = when (this) {
            AlertKind.SEVERE -> 1001
            AlertKind.PRECIPITATION -> 1002
            AlertKind.DAILY_SUMMARY -> 1003
        }

    private val AlertKind.channelImportance: Int
        get() = when (this) {
            AlertKind.SEVERE -> NotificationManagerCompat.IMPORTANCE_HIGH
            AlertKind.PRECIPITATION -> NotificationManagerCompat.IMPORTANCE_DEFAULT
            AlertKind.DAILY_SUMMARY -> NotificationManagerCompat.IMPORTANCE_LOW
        }

    private val AlertKind.channelNameRes: Int
        get() = when (this) {
            AlertKind.SEVERE -> R.string.notif_channel_severe
            AlertKind.PRECIPITATION -> R.string.notif_channel_precip
            AlertKind.DAILY_SUMMARY -> R.string.notif_channel_summary
        }

    private val AlertKind.titleRes: Int
        get() = when (this) {
            AlertKind.SEVERE -> R.string.notif_title_severe
            AlertKind.PRECIPITATION -> R.string.notif_title_precip
            AlertKind.DAILY_SUMMARY -> R.string.notif_title_summary
        }

    private val AlertKind.fallbackEmoji: String
        get() = when (this) {
            AlertKind.SEVERE -> "⚠️"
            AlertKind.PRECIPITATION -> "🌧️"
            AlertKind.DAILY_SUMMARY -> "☀️"
        }
}
