package com.callbackdev.tweather.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.callbackdev.tweather.data.ActiveSource
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.sky.SkyJobCatalog
import com.callbackdev.tweather.domain.sky.SkyLead
import com.callbackdev.tweather.domain.sky.SkyReminder
import com.callbackdev.tweather.domain.sky.SkyReminderPlanner
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * Arms the next sky reminder (Fase 16f).
 *
 * **The only new scheduling primitive in the app, and it is deliberately inexact.**
 * `setAndAllowWhileIdle`, never `setExact*`, never `SCHEDULE_EXACT_ALARM`: battery is
 * a feature and a sunset is not an alarm clock. `setWindow` would not do — under Doze
 * a plain window alarm is deferred to the next maintenance window, which can be hours
 * away, and the reminder would arrive after dark. The cost is a drift of about ten
 * minutes, which is exactly why the shortest selectable lead is fifteen (see
 * [SkyLead]) and why the settings line says the reminder is approximate.
 *
 * **One alarm at a time.** The nearest reminder is armed; when it fires,
 * [SkyAlarmReceiver] posts it (or declines to) and arms the next. A queue of alarms
 * would buy nothing — the plan is recomputed on every fetch anyway — and would cost a
 * cancel-and-rearm of the whole queue every time the user tapped a `--notify` token.
 *
 * **Only the active city.** A city pinned to a widget does not schedule reminders;
 * `HELP.md` says so rather than letting it be a surprise.
 */
object SkyAlarmScheduler {

    const val ACTION_FIRE = "com.callbackdev.tweather.SKY_REMINDER"
    const val EXTRA_JOB_ID = "job_id"
    const val EXTRA_OCCURRENCE = "occurrence_epoch_s"

    /**
     * Recomputes the plan and arms (or clears) the alarm. Called after every fetch,
     * after every edit to `sky.crontab`, when a reminder fires, and on boot.
     */
    suspend fun reschedule(context: Context) {
        val settings = ServiceLocator.settingsStore(context).settings.first()
        val alarms = context.getSystemService<AlarmManager>() ?: return
        val plan = if (settings.skyEnabled) plan(context) else null
        if (plan == null) {
            alarms.cancel(pendingIntent(context, null))
            return
        }
        // FLAG_UPDATE_CURRENT on one fixed request code: arming the next reminder
        // replaces the previous alarm rather than adding to it, so the app can never
        // accumulate a queue it forgot about.
        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            plan.fireAt.toEpochMilli(),
            pendingIntent(context, plan)
        )
    }

    /** The next reminder for the active city, or null when there is nothing to arm. */
    suspend fun plan(context: Context, now: Instant = Instant.now()): SkyReminder? {
        val city = activeCity(context) ?: return null
        val subscriptions = ServiceLocator.skySubscriptionStore(context).subscriptions.first()
        // `notify_default` resolved exactly as `sky.crontab` renders it: the file and
        // the alarm must not be able to disagree about which lines have a reminder.
        val defaultLead = ServiceLocator.settingsStore(context)
            .settings.first().skyNotifyDefaultMin
        val jobs = subscriptions
            .filter { it.enabled }
            .mapNotNull { subscription ->
                SkyJobCatalog.byId(subscription.jobId)?.let {
                    it to SkyLead.ofMinutes(subscription.notifyLeadMinutes ?: defaultLead)
                }
            }
        if (jobs.isEmpty()) return null
        val zone = runCatching { ZoneId.of(city.timezone) }.getOrElse { ZoneId.systemDefault() }
        return SkyReminderPlanner.next(jobs, now, zone, city.coordinates)
    }

    suspend fun activeCity(context: Context): City? =
        when (val source = ServiceLocator.cityStore(context).activeSource.first()) {
            is ActiveSource.Saved -> source.city
            // The last persisted fix, like every other background path: there is no
            // background location in this app.
            is ActiveSource.Gps -> source.lastFix
            ActiveSource.None -> null
        }

    private fun pendingIntent(context: Context, reminder: SkyReminder?): PendingIntent {
        val intent = Intent(context, SkyAlarmReceiver::class.java).setAction(ACTION_FIRE)
        reminder?.let {
            intent.putExtra(EXTRA_JOB_ID, it.jobId)
            intent.putExtra(EXTRA_OCCURRENCE, it.occurrenceAt.epochSecond)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** One code, because there is one alarm. */
    private const val REQUEST_CODE = 0x5C1
}
