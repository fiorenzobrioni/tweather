package com.callbackdev.tweather.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.domain.sky.SkyJobCatalog
import com.callbackdev.tweather.domain.sky.SkyReminder
import com.callbackdev.tweather.domain.sky.SkyReminderPolicy
import com.callbackdev.tweather.domain.sky.SkyVerdictEngine
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/**
 * A sky reminder has come due (Fase 16f): decide whether it is worth posting, post
 * it if so, and arm the next one either way.
 *
 * Also handles `BOOT_COMPLETED`. Alarms do not survive a reboot and WorkManager's
 * persistence does not extend to them, so without this the reminders would go quiet
 * after a restart and nothing would say why. The periodic sync re-arms too, which
 * makes this a fast path rather than the only one.
 */
class SkyAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        // goAsync, because everything here is a DataStore read and a notification:
        // work measured in milliseconds, but not synchronous.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                handle(appContext, intent)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * The whole of [onReceive] minus the dispatch, so the tests can await it instead
     * of racing a fire-and-forget coroutine. [context] is already the application one.
     */
    internal suspend fun handle(context: Context, intent: Intent) {
        try {
            // A reboot only needs the alarm back; the `finally` below is that.
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) return
            deliver(context, intent)
        } finally {
            // Always re-armed, whatever happened above: an exception that left no
            // alarm behind would end the reminders silently and forever.
            runCatching { SkyAlarmScheduler.reschedule(context) }
        }
    }

    private suspend fun deliver(context: Context, intent: Intent) {
        val jobId = intent.getStringExtra(SkyAlarmScheduler.EXTRA_JOB_ID) ?: return
        val occurrenceSeconds = intent.getLongExtra(SkyAlarmScheduler.EXTRA_OCCURRENCE, 0L)
        if (occurrenceSeconds == 0L) return
        val job = SkyJobCatalog.byId(jobId) ?: return
        val settings = ServiceLocator.settingsStore(context).settings.first()
        if (!settings.skyEnabled) return

        val occurrenceAt = Instant.ofEpochSecond(occurrenceSeconds)
        val reminder = SkyReminder(jobId, Instant.now(), occurrenceAt)
        val state = ServiceLocator.skyAlertStateStore(context)
        // One notification per job per occurrence. The alarm can fire twice — a
        // reboot re-arms one that already went out — and the fingerprint is what
        // makes the second one a no-op.
        if (state.wasPosted(reminder.fingerprint)) return

        val city = SkyAlarmScheduler.activeCity(context) ?: return
        val zone = runCatching { ZoneId.of(city.timezone) }.getOrElse { ZoneId.systemDefault() }
        val report = ServiceLocator.weatherRepository(context).cachedReport(city)
        val now = Instant.now()
        val verdict = if (job.observable) {
            SkyVerdictEngine.evaluate(
                job = job,
                start = occurrenceAt,
                end = null,
                hours = report?.hourly.orEmpty(),
                zone = zone,
                coordinates = city.coordinates,
                dataAge = report?.let {
                    java.time.Duration.between(it.systemInfo.lastSync, now)
                },
                staleAfter = com.callbackdev.tweather.domain.WeatherFreshness
                    .staleAfter(settings.updateFrequencyMin)
            )
        } else {
            null
        }

        if (SkyReminderPolicy.decide(job, verdict, settings.skyNotifyOnFail) !=
            SkyReminderPolicy.Decision.SEND
        ) {
            // Suppressed reminders burn the fingerprint too: the occurrence has been
            // decided on, and re-deciding it at the next re-arm would only produce the
            // same answer a second time.
            state.record(reminder.fingerprint)
            return
        }

        val posted = SkyNotifier.notify(context, jobId, occurrenceAt, zone, verdict, now)
        // Only a successful post burns the fingerprint: a muted channel must not
        // consume the one reminder this occurrence had.
        if (posted) state.record(reminder.fingerprint)
    }
}
