package com.callbackdev.tweather.notifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.callbackdev.tweather.data.ActiveSource
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.domain.AlertEngine
import com.callbackdev.tweather.domain.WeatherException
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first

/**
 * The single periodic background job (see [AlertScheduler]): fetch weather for
 * the active source, evaluate alerts, notify. Named "sync", not "alerts" — the
 * fetch is the reusable part (the future home widget hooks into the same run).
 * Battery: at most one HTTP GET per period, and a free cache HIT when the user
 * just used the app (never forceRefresh from here).
 */
class WeatherSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = ServiceLocator.settingsStore(context).settings.first()
        // Self-heal: toggles all off or permission revoked from system settings —
        // cancel instead of waking up for nothing forever (MainActivity re-enqueues
        // on the next app open if conditions return).
        if (!AlertScheduler.shouldRun(
                settings.notifications,
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            )
        ) {
            AlertScheduler.cancel(context)
            return Result.success()
        }

        val city = when (val source = ServiceLocator.cityStore(context).activeSource.first()) {
            is ActiveSource.Saved -> source.city
            // Background location is off the table by design: last persisted fix only
            is ActiveSource.Gps -> source.lastFix ?: return Result.success()
        }

        val report = try {
            ServiceLocator.weatherRepository(context).getWeather(
                city,
                forceRefresh = false,
                ttl = Duration.ofMinutes(settings.updateFrequencyMin.toLong())
            )
        } catch (e: WeatherException.NoNetwork) {
            return Result.retry() // captive portal/DNS flap; CONNECTED already gated
        } catch (e: WeatherException) {
            return Result.success() // next period is at most one interval away
        }

        // Alert rules run in the CITY's timezone, not the device's
        val zone = runCatching { ZoneId.of(report.location.timezone) }
            .getOrDefault(ZoneId.systemDefault())
        val now = ZonedDateTime.now(zone).toLocalDateTime()

        val stateStore = ServiceLocator.alertStateStore(context)
        val alerts = AlertEngine.evaluate(
            report = report,
            settings = settings.notifications,
            state = stateStore.state.first(),
            now = now,
            cityKey = city.cacheKey
        )
        alerts.forEach { alert ->
            // Fingerprint burns only on a successful post (muted channel → retry later)
            if (AlertNotifier.notify(context, alert, settings.units.temperature)) {
                stateStore.record(alert)
            }
        }
        return Result.success()
    }
}
