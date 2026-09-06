package com.callbackdev.tweather.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.callbackdev.tweather.MainActivity
import com.callbackdev.tweather.R
import com.callbackdev.tweather.data.UnitSettings
import com.callbackdev.tweather.domain.model.WeatherReport
import com.callbackdev.tweather.domain.rules.RuleMessages
import com.callbackdev.tweather.domain.rules.RuleTrigger
import com.callbackdev.tweather.domain.rules.RuleVariableKind
import com.callbackdev.tweather.domain.rules.RuleVariables
import java.time.LocalDateTime

/**
 * Renders a fired user rule (Fase 11) as a system notification. Unlike
 * [AlertNotifier]'s JSON bodies, the collapsed text here is the user's own message —
 * their content, in their language, interpolated.
 *
 * The expanded body adds the command that "ran" it (`$ tweather run <name>`) and,
 * since Fase 25, **the rule itself**: its conditions exactly as `alerts.rules` prints
 * them, each with the reading that made it true in the comment channel. That is the
 * question a fired rule raises — why now? — answered in the file's own grammar, and
 * it is why the command line alone was not an unfold worth the gesture.
 *
 * A condition whose variable will not resolve right now (air quality down, an empty
 * window) prints its line with no reading rather than a zero: the engine already
 * refuses to call missing data false, and the body must not undo that.
 *
 * One channel for all rules, one notification id per rule: re-fires of the same rule
 * overwrite, different rules stack.
 */
object RuleNotifier {

    const val CHANNEL_ID = "user_rules"

    /** Fixed ids 1001–1003 belong to the builtin alerts; rules live above 2000. */
    private const val NOTIFICATION_ID_BASE = 2000

    internal fun notificationId(ruleId: Long): Int =
        NOTIFICATION_ID_BASE + (ruleId % 1000).toInt()

    /**
     * Posts the notification; false when notifications are off or the channel is
     * muted — the caller must then NOT record the trigger, so it can retry later.
     */
    fun notify(
        context: Context,
        trigger: RuleTrigger,
        cityLabel: String,
        report: WeatherReport,
        now: LocalDateTime,
        units: UnitSettings
    ): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(
                CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            )
                .setName(context.getString(R.string.notif_channel_rules))
                .build()
        )
        val channel = manager.getNotificationChannelCompat(CHANNEL_ID)
        if (channel?.importance == NotificationManagerCompat.IMPORTANCE_NONE) return false

        val message = RuleMessages.interpolate(trigger.rule.message, trigger, report, now, units)
        val id = notificationId(trigger.rule.id)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tweather)
            .setContentTitle(
                context.getString(R.string.notif_title_rule, trigger.rule.name, cityLabel)
            )
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(expandedBody(trigger, report, now, units, message))
            )
            .setContentIntent(openAppIntent(context, id))
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify(id, notification)
            true
        } catch (e: SecurityException) {
            false // POST_NOTIFICATIONS revoked between the check and the post
        }
    }

    /**
     * `$ tweather run <name>`, the message, then the rule as its file writes it.
     *
     * Internal so the test can read it without a posted notification, like
     * [AlertNotifier]'s two bodies.
     */
    internal fun expandedBody(
        trigger: RuleTrigger,
        report: WeatherReport,
        now: LocalDateTime,
        units: UnitSettings,
        message: String
    ): String {
        val head = "$ tweather run ${trigger.rule.name}\n$message"
        val conditions = trigger.rule.conditions.mapIndexed { index, condition ->
            val variable = RuleVariables.byId(condition.variable)
            val kind = variable?.kind ?: RuleVariableKind.NUMBER
            val line = buildString {
                append(if (index == 0) "if" else "and").append(": ")
                append(variable?.let { RuleVariables.displayId(it, units) } ?: condition.variable)
                // Two spaces around the operator: the same token padding the file uses.
                append("  ").append(condition.op.symbol).append("  ")
                append(RuleVariables.formatValue(kind, condition.threshold, units))
            }
            val reading = variable?.resolve?.invoke(report, now)
                ?.let { RuleVariables.formatValue(kind, it.value, units) }
            if (reading == null) line else "$line  // $reading"
        }
        return if (conditions.isEmpty()) head else "$head\n\n" + conditions.joinToString("\n")
    }

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
