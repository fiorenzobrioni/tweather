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
import com.callbackdev.tweather.data.UnitSettings
import com.callbackdev.tweather.domain.Alert
import com.callbackdev.tweather.domain.AlertKind
import com.callbackdev.tweather.domain.model.WeatherCondition
import com.callbackdev.tweather.domain.model.WeatherReport
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
 * **Folding hides children, not whitespace** (Fase 25). The collapsed notification
 * gets one line, so it gets the headline object folded onto it, exactly as before.
 * The expanded one gets that same object with its nested nodes OPEN: the run of
 * hours the alert is really about, the worst of it, what the thermometer does
 * meanwhile, and where the reader is standing right now. Until this phase the two
 * bodies were the same four fields laid out twice — pulling a notification open gave
 * back what it already said, in taller form, which is not what unfolding a node does
 * in any editor. Chiaro made the same split for the same reason (its Fase 6b) and
 * this is that content in this app's register: English keys, localized values.
 *
 * Nothing in the expanded object is invented. The window comes from [AlertDetails]
 * reading the very hours [com.callbackdev.tweather.domain.AlertEngine] judged, and a
 * node whose data the report does not carry is not written at all — with one
 * deliberate exception, `window.to`, which is `null` when the run reaches the end of
 * the forecast: there the absence IS the fact, the way the file writes `null` for a
 * section the sky did not fill.
 *
 * One channel and one fixed notification id per kind — same-kind alerts overwrite,
 * never stack.
 */
object AlertNotifier {

    /**
     * Posts the notification; false when notifications are disabled or the kind's
     * channel is muted — the caller must then NOT burn the alert's fingerprint,
     * so the alert can still fire if the user re-enables the channel.
     */
    fun notify(
        context: Context,
        alert: Alert,
        /**
         * The report the alert was evaluated on — what the expanded object unfolds.
         * The [Alert] alone cannot supply it: it carries the one hour its fingerprint
         * needs, and the rest of the story is in the forecast behind it.
         */
        report: WeatherReport,
        units: UnitSettings
    ): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel(context, manager, alert.kind)
        val channel = manager.getNotificationChannelCompat(alert.kind.channelId)
        if (channel?.importance == NotificationManagerCompat.IMPORTANCE_NONE) return false

        val translate = WeatherTranslations.translator(context.resources)
        val notification = NotificationCompat.Builder(context, alert.kind.channelId)
            .setSmallIcon(R.drawable.ic_stat_tweather)
            .setContentTitle(title(context, alert))
            .setContentText(foldedBody(alert, units.temperature, translate))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(expandedBody(alert, report, units, translate))
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
     * system truncates it, hence [headline]'s ordering — the headline field comes
     * first — and hence its CONTENT: the folded node shows a summary, never the
     * children.
     */
    internal fun foldedBody(
        alert: Alert,
        unit: TemperatureUnit,
        translate: (String) -> String
    ): String = headline(alert, unit, translate)
        .joinToString(", ", prefix = "{ ", postfix = " }") { """"${it.key}": ${it.value.fold()}""" }

    /**
     * The expanded body: the command that produced it, then the same object with its
     * nested nodes open — one field per line, children indented under their key.
     */
    internal fun expandedBody(
        alert: Alert,
        report: WeatherReport,
        units: UnitSettings,
        translate: (String) -> String
    ): String {
        val fields = headline(alert, units.temperature, translate) +
            unfolded(alert, report, units, translate)
        return "${alert.kind.command}\n${render(fields, indent = "")}"
    }

    /** A JSON value: a rendered scalar, or a node with children of its own. */
    private sealed interface Value {
        /** Already rendered: quoted for strings, bare for numbers, `null` for null. */
        data class Scalar(val text: String) : Value
        data class Node(val fields: List<Field>) : Value
    }

    private data class Field(val key: String, val value: Value)

    /** What a folded node shows in one line: `{…}` for children, the value itself
     * otherwise. Only ever reached from [foldedBody], which has no children to show. */
    private fun Value.fold(): String = when (this) {
        is Value.Scalar -> text
        is Value.Node -> "{…}"
    }

    /**
     * Pretty-prints [fields] as a JSON object, recursing into nested nodes. [indent]
     * is what the CLOSING brace and the children sit at — the opening one follows its
     * key on the line above and is never indented itself.
     */
    private fun render(fields: List<Field>, indent: String): String {
        val inner = "$indent  "
        val body = fields.joinToString(",\n") { field ->
            val value = when (val v = field.value) {
                is Value.Scalar -> v.text
                is Value.Node -> render(v.fields, inner)
            }
            """$inner"${field.key}": $value"""
        }
        return "{\n$body\n$indent}"
    }

    /**
     * The fields the collapsed line carries, in display order — most useful first,
     * because that line is cut off wherever it runs out of width. Keys mirror
     * `weather_data.json`, unit suffix included (`high_c`/`high_f`), so a
     * notification reads like a fold of the file it came from.
     */
    private fun headline(
        alert: Alert,
        unit: TemperatureUnit,
        translate: (String) -> String
    ): List<Field> = buildList {
        val status = alert.condition?.let { field("status", it.status(translate)) }
        when (alert.kind) {
            AlertKind.SEVERE -> {
                alert.at?.let { add(field("time", it.format(ClockTime).quoted())) }
                status?.let(::add)
                alert.condition?.let { add(field("wmo_code", it.wmoCode.toString())) }
                alert.precipPct?.let { add(field("precip_chance", it.toString())) }
            }
            AlertKind.PRECIPITATION -> {
                alert.at?.let { add(field("time", it.format(ClockTime).quoted())) }
                status?.let(::add)
                alert.precipPct?.let { add(field("precip_chance", it.toString())) }
            }
            AlertKind.DAILY_SUMMARY -> {
                // no time to anchor it: the condition is the headline of a summary
                status?.let(::add)
                alert.highC?.let { add(field("high_${unit.keySuffix}", it.temp(unit))) }
                alert.lowC?.let { add(field("low_${unit.keySuffix}", it.temp(unit))) }
                alert.precipPct?.let { add(field("precip_pct", it.toString())) }
            }
        }
    }

    /**
     * What opens under the headline (Fase 25) — the reason the expanded body is worth
     * pulling open.
     *
     * An hour-anchored alert unfolds the RUN it belongs to and the present; the daily
     * summary has no run, so it unfolds the day's own facts instead. Order is
     * worth-first here too: the system cuts a long body at the bottom.
     */
    private fun unfolded(
        alert: Alert,
        report: WeatherReport,
        units: UnitSettings,
        translate: (String) -> String
    ): List<Field> = buildList {
        val window = alert.at?.let {
            when (alert.kind) {
                AlertKind.SEVERE -> AlertDetails.severeWindow(report.hourly, it)
                AlertKind.PRECIPITATION -> AlertDetails.rainWindow(report.hourly, it)
                AlertKind.DAILY_SUMMARY -> null
            }
        }
        window?.let { add(field("window", it.node(units.temperature))) }
        add(field("current_conditions", report.currentNode(units, translate)))
        if (alert.kind == AlertKind.DAILY_SUMMARY) {
            // Both ends or neither: above the Arctic circle in June there is no
            // sunrise to have, and half a pair says less than nothing.
            val sunrise = report.astronomical.sunrise
            val sunset = report.astronomical.sunset
            if (sunrise != null && sunset != null) {
                add(
                    field(
                        "astronomical",
                        node(
                            field("sunrise", sunrise.format(ClockTime).quoted()),
                            field("sunset", sunset.format(ClockTime).quoted())
                        )
                    )
                )
            }
            report.daily.firstOrNull()?.let { today ->
                add(field("uv_index_max", today.uvIndexMax.toString()))
                add(field("uv_description", translate(today.uvDescription).quoted()))
            }
            report.airQuality?.let { air ->
                add(
                    field(
                        "air_quality",
                        node(
                            field("aqi_index", air.aqiIndex.toString()),
                            field("status", translate(air.status).quoted())
                        )
                    )
                )
            }
        }
    }

    /** The run of hours the alert belongs to, as `weather_data.json` would key it. */
    private fun AlertWindow.node(unit: TemperatureUnit): Value = node(
        buildList {
            add(field("from", start.format(ClockTime).quoted()))
            // `null`, not the last hour the data happens to hold: the run reached the
            // end of the forecast, so the weather's own end was never shown.
            add(field("to", if (openEnded) "null" else end.format(ClockTime).quoted()))
            if (peakPrecipPct > 0) {
                add(field("peak_precip_chance", peakPrecipPct.toString()))
                add(field("peak_at", peakPrecipAt.format(ClockTime).quoted()))
            }
            add(field("low_${unit.keySuffix}", lowC.temp(unit)))
            add(field("high_${unit.keySuffix}", highC.temp(unit)))
        }
    )

    /**
     * Where the reader is standing when the notification arrives — the alert is about
     * a later hour, and the present is what makes it read as a change.
     *
     * The wind sits INSIDE this node rather than beside the window, and the nesting is
     * the whole point: the hourly forecast carries no wind, so this is the reading at
     * the moment of posting. Printed one level up, under a storm window, it would read
     * as the storm's wind.
     */
    private fun WeatherReport.currentNode(
        units: UnitSettings,
        translate: (String) -> String
    ): Value = node(
        field("temp_${units.temperature.keySuffix}", current.tempC.temp(units.temperature)),
        field("status", current.condition.status(translate)),
        field(
            "wind",
            node(
                field(
                    "speed_${units.windSpeed.keySuffix}",
                    units.windSpeed.convert(current.wind.speedKph).roundToInt().toString()
                ),
                field("direction", current.wind.directionCompass.quoted())
            )
        )
    )

    private fun field(key: String, rendered: String) = Field(key, Value.Scalar(rendered))
    private fun field(key: String, value: Value) = Field(key, value)
    private fun node(vararg fields: Field): Value = Value.Node(fields.toList())
    private fun node(fields: List<Field>): Value = Value.Node(fields)

    /** The localized description with its emoji, quoted — the file's `status`. */
    private fun WeatherCondition.status(translate: (String) -> String): String =
        "${translate(description)} $emoji".quoted()

    /** Whole degrees in the user's unit — the unit lives in the key, as in the file. */
    private fun Double.temp(unit: TemperatureUnit): String =
        unit.convert(this).roundToInt().toString()

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
