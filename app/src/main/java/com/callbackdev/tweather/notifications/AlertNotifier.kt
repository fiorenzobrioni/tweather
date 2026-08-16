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
import com.callbackdev.tweather.ui.weather.WeatherTranslations
import com.callbackdev.tweather.ui.weather.convert
import com.callbackdev.tweather.ui.weather.keySuffix
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Renders an [Alert] as a system notification in the app's own idiom: localized
 * chrome (title) over a JSON object with English keys and localized data values —
 * the same rule `weather_data.json` and the home widget follow, so a condition never
 * reads "Overcast" on an Italian device.
 *
 * The object is folded onto one line for the collapsed notification (which only ever
 * gets one) and pretty-printed under its command line for the expanded one, the way
 * the editor folds and unfolds a node. One channel and one fixed notification id per
 * kind — same-kind alerts overwrite, never stack.
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

        val translate = WeatherTranslations.translator(context.resources)
        val notification = NotificationCompat.Builder(context, alert.kind.channelId)
            .setSmallIcon(R.drawable.ic_stat_tweather)
            .setContentTitle(title(context, alert))
            .setContentText(foldedBody(alert, temperatureUnit, translate))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(expandedBody(alert, temperatureUnit, translate))
            )
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

    /**
     * The collapsed line: `{ "time": "18:00", "status": "Temporale ⛈️", … }`. The
     * system truncates it, hence [fields] ordering — the headline field comes first.
     */
    internal fun foldedBody(
        alert: Alert,
        unit: TemperatureUnit,
        translate: (String) -> String
    ): String = fields(alert, unit, translate)
        .joinToString(", ", prefix = "{ ", postfix = " }") { """"${it.key}": ${it.value}""" }

    /** The expanded body: the command that produced it, then one field per line. */
    internal fun expandedBody(
        alert: Alert,
        unit: TemperatureUnit,
        translate: (String) -> String
    ): String {
        val body = fields(alert, unit, translate)
            .joinToString(",\n") { """  "${it.key}": ${it.value}""" }
        return "${alert.kind.command}\n{\n$body\n}"
    }

    /** One JSON field, [value] already rendered: quoted for strings, bare for numbers. */
    private data class Field(val key: String, val value: String)

    /**
     * Field order is display order, most useful first, because the collapsed line is
     * cut off wherever it runs out of width. Keys mirror `weather_data.json`, unit
     * suffix included (`high_c`/`high_f`), so a notification reads like a fold of the
     * file it came from.
     */
    private fun fields(
        alert: Alert,
        unit: TemperatureUnit,
        translate: (String) -> String
    ): List<Field> = buildList {
        val status = alert.condition?.let {
            Field("status", "${translate(it.description)} ${it.emoji}".quoted())
        }
        when (alert.kind) {
            AlertKind.SEVERE -> {
                alert.at?.let { add(Field("time", it.format(ClockTime).quoted())) }
                status?.let(::add)
                alert.condition?.let { add(Field("wmo_code", it.wmoCode.toString())) }
                alert.precipPct?.let { add(Field("precip_chance", it.toString())) }
            }
            AlertKind.PRECIPITATION -> {
                alert.at?.let { add(Field("time", it.format(ClockTime).quoted())) }
                status?.let(::add)
                alert.precipPct?.let { add(Field("precip_chance", it.toString())) }
            }
            AlertKind.DAILY_SUMMARY -> {
                // no time to anchor it: the condition is the headline of a summary
                status?.let(::add)
                alert.highC.temp(unit)?.let { add(Field("high_${unit.keySuffix}", it)) }
                alert.lowC.temp(unit)?.let { add(Field("low_${unit.keySuffix}", it)) }
                alert.precipPct?.let { add(Field("precip_pct", it.toString())) }
            }
        }
    }

    /** Whole degrees in the user's unit — the unit lives in the key, as in the file. */
    private fun Double?.temp(unit: TemperatureUnit): String? =
        this?.let { unit.convert(it).roundToInt().toString() }

    private fun String.quoted() = "\"$this\""

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
                // SINGLE_TOP: without it CLEAR_TOP rebuilds a launchMode=standard
                // activity, replaying the splash instead of resuming where the user was
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private val ClockTime = DateTimeFormatter.ofPattern("HH:mm")

    /** Terminal shorthand: code, so it stays English like every prompt in the app. */
    private val AlertKind.command: String
        get() = when (this) {
            AlertKind.SEVERE -> "$ tweather --alert severe"
            AlertKind.PRECIPITATION -> "$ tweather --alert precip"
            AlertKind.DAILY_SUMMARY -> "$ tweather --daily"
        }

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
