package com.callbackdev.tweather.notifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.callbackdev.tweather.data.ActiveSource
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.domain.AlertEngine
import com.callbackdev.tweather.domain.WeatherException
import com.callbackdev.tweather.domain.model.GpsCityId
import com.callbackdev.tweather.widget.TweatherWidgetProvider
import com.callbackdev.tweather.widget.TweatherWidgetUpdater
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first

/**
 * The single periodic background job (see [AlertScheduler]): fetch weather for
 * the active source, evaluate alerts, notify. Named "sync", not "alerts" — the
 * fetch is the reusable part: the home widget re-renders off the same run, via
 * the repository's history-commit hook. Battery: one fetch (two HTTP GETs —
 * forecast + air quality) for the active source per period, plus one fetch per
 * distinct pinned widget city; a cache HIT is free when the user just used the
 * app. Only the widget's ↻ tap forces a refresh ([KEY_FORCE_REFRESH]), and only
 * for the city that widget shows ([KEY_FORCE_CITY_KEY]).
 */
class WeatherSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = ServiceLocator.settingsStore(context).settings.first()
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val alertsWanted = AlertScheduler.alertsWanted(settings.notifications, notificationsEnabled)
        // Self-heal: nothing left to sync for (no alerts wanted AND no widget placed) —
        // cancel instead of waking up for nothing forever (MainActivity or the widget
        // receiver re-enqueue if conditions return).
        if (!alertsWanted && !TweatherWidgetProvider.hasWidgets(context)) {
            AlertScheduler.cancel(context)
            return Result.success()
        }

        val city = when (val source = ServiceLocator.cityStore(context).activeSource.first()) {
            is ActiveSource.Saved -> source.city
            // Background location is off the table by design: last persisted fix only
            is ActiveSource.Gps -> source.lastFix ?: return Result.success()
        }

        // Only the widget's ↻ forces a refresh; periodic runs stay cache-friendly.
        // The tap names its city so the bypass doesn't fan out to every other one.
        val forceRefresh = inputData.getBoolean(KEY_FORCE_REFRESH, false)
        val forceCityKey = inputData.getString(KEY_FORCE_CITY_KEY)
        fun forces(cacheKey: String): Boolean =
            forceRefresh && (forceCityKey == null || forceCityKey == cacheKey)
        val report = try {
            ServiceLocator.weatherRepository(context).getWeather(
                city,
                forceRefresh = forces(city.cacheKey),
                ttl = Duration.ofMinutes(settings.updateFrequencyMin.toLong())
            )
        } catch (e: WeatherException.NoNetwork) {
            // A failed sync commits nothing, so the repository hook stays silent — but
            // this is exactly when the widget has to stop presenting old numbers as
            // current, so repaint it here to let the stale marker appear.
            TweatherWidgetUpdater.updateAll(context)
            return Result.retry() // captive portal/DNS flap; CONNECTED already gated
        } catch (e: WeatherException) {
            TweatherWidgetUpdater.updateAll(context)
            return Result.success() // next period is at most one interval away
        }

        // A widget-only sync fetches (the repository hook re-renders it) but must
        // never evaluate or post alerts.
        if (alertsWanted) {
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
                // Stable identity, not cacheKey: the GPS pseudo-city's cacheKey moves
                // with the fix (~1.1 km grid) and would re-notify the same storm at
                // every commute leg; ids never move.
                cityKey = if (city.id == GpsCityId) "gps" else city.id.toString()
            )
            alerts.forEach { alert ->
                // Fingerprint burns only on a successful post (muted channel → retry later)
                if (AlertNotifier.notify(context, alert, settings.units.temperature)) {
                    stateStore.record(alert)
                }
            }
        }

        // Widgets pinned to another city have no other producer of history commits:
        // without this their data would only age. One extra GET per distinct pinned
        // city per period, and only while such a widget is actually placed.
        TweatherWidgetUpdater.pinnedCities(context).forEach { pinnedCity ->
            runCatching {
                ServiceLocator.weatherRepository(context).getWeather(
                    pinnedCity,
                    forceRefresh = forces(pinnedCity.cacheKey),
                    ttl = Duration.ofMinutes(settings.updateFrequencyMin.toLong())
                )
            }
        }
        return Result.success()
    }

    companion object {
        /** Input data flag set by the widget's ↻ tap (see TweatherWidgetProvider). */
        const val KEY_FORCE_REFRESH = "force_refresh"

        /** cacheKey of the tapped widget's city; absent = the bypass applies to all
         * (pre-existing enqueues and any tap whose city couldn't be resolved). */
        const val KEY_FORCE_CITY_KEY = "force_city_key"
    }
}
