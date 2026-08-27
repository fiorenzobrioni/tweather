package com.callbackdev.tweather.notifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.callbackdev.tweather.data.ActiveSource
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.domain.AlertEngine
import com.callbackdev.tweather.domain.WeatherException
import com.callbackdev.tweather.domain.WeatherFreshness
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.GpsCityId
import com.callbackdev.tweather.domain.model.WeatherReport
import com.callbackdev.tweather.domain.sky.SkyJobCatalog
import com.callbackdev.tweather.domain.sky.SkyRunRecorder
import com.callbackdev.tweather.domain.rules.RuleEngine
import com.callbackdev.tweather.widget.TweatherWidgetProvider
import com.callbackdev.tweather.widget.TweatherWidgetUpdater
import java.time.Duration
import java.time.Instant
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
        val enabledRules = ServiceLocator.ruleStore(context).rules.first().filter { it.enabled }
        val alertsWanted = AlertScheduler.alertsWanted(
            settings.notifications, notificationsEnabled, enabledRules.isNotEmpty()
        )
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
            // Nothing configured yet (Fase 14b): no city to fetch, and no widget pin
            // can resolve either — a pin points at a saved city, and there are none.
            ActiveSource.None -> return Result.success()
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
            // Stable identity, not cacheKey: the GPS pseudo-city's cacheKey moves
            // with the fix (~1.1 km grid) and would re-notify the same storm at
            // every commute leg; ids never move.
            val cityKey = if (city.id == GpsCityId) "gps" else city.id.toString()

            val stateStore = ServiceLocator.alertStateStore(context)
            val alerts = AlertEngine.evaluate(
                report = report,
                settings = settings.notifications,
                state = stateStore.state.first(),
                now = now,
                cityKey = cityKey
            )
            alerts.forEach { alert ->
                // Fingerprint burns only on a successful post (muted channel → retry later)
                if (AlertNotifier.notify(context, alert, settings.units.temperature)) {
                    stateStore.record(alert)
                }
            }

            // User rules (Fase 11): same fetch, same clock, zero extra battery.
            if (settings.notifications.userRules && enabledRules.isNotEmpty()) {
                val ruleStateStore = ServiceLocator.ruleStateStore(context)
                val evaluation = RuleEngine.evaluate(
                    rules = enabledRules,
                    report = report,
                    state = ruleStateStore.state.first(),
                    now = now,
                    cityKey = cityKey
                )
                // Re-arm regardless of what posts: false is false
                ruleStateStore.unlatch(evaluation.unlatch)
                val fired = mutableListOf<String>()
                evaluation.triggers.forEach { trigger ->
                    val posted = RuleNotifier.notify(
                        context, trigger, report.location.city, report, now, settings.units
                    )
                    if (posted) {
                        ruleStateStore.record(trigger)
                        fired += trigger.rule.name
                    }
                }
                // The Logs' check lines: this fetch's commit lists what fired
                if (fired.isNotEmpty()) {
                    ServiceLocator.weatherRepository(context).recordFiredRules(city, fired)
                }
            }
        }

        // The sky module (Fase 16e). Deliberately OUTSIDE the `alertsWanted` gate:
        // recording is not notifying, and a sky run is a fact about what happened
        // whether or not this install has notifications on at all. Zero extra
        // battery — same fetch, same clock, and the schedule is local arithmetic.
        if (settings.skyEnabled) {
            recordSkyRuns(context, city, report, settings.updateFrequencyMin)
            // Re-arm on every sync (Fase 16f). The receiver arms the next reminder
            // when one fires, so this is a safety net rather than the main path: an
            // alarm lost to a force-stop or a cleared task comes back at the next
            // fetch instead of never.
            SkyAlarmScheduler.reschedule(context)
        }

        // Widgets pinned to another city have no other producer of history commits:
        // without this their data would only age. One extra fetch (two GETs) per
        // distinct pinned city per period, and only while such a widget is placed.
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

    /**
     * Which sky jobs ran since this city's previous commit, attached to the one this
     * fetch just wrote.
     *
     * The previous commit IS the "since": it is precisely the last moment the app
     * looked at this city, so the window between them is everything that happened
     * while it was not looking. On the very first commit there is no previous one and
     * nothing to have missed, so nothing is recorded — an install does not acquire a
     * history of sunsets it was not installed for.
     */
    private suspend fun recordSkyRuns(
        context: Context,
        city: City,
        report: WeatherReport,
        updateFrequencyMin: Int
    ) {
        val subscriptions = ServiceLocator.skySubscriptionStore(context).subscriptions.first()
        val jobs = subscriptions.filter { it.enabled }.mapNotNull { SkyJobCatalog.byId(it.jobId) }
        if (jobs.isEmpty()) return
        val repository = ServiceLocator.weatherRepository(context)
        val history = repository.historyFor(city, limit = 2)
        val previous = history.getOrNull(1) ?: return
        val zone = runCatching { ZoneId.of(report.location.timezone) }
            .getOrDefault(ZoneId.systemDefault())
        val runs = SkyRunRecorder.runsSince(
            since = Instant.ofEpochSecond(previous.timestampEpochSeconds),
            now = report.systemInfo.lastSync,
            jobs = jobs,
            zone = zone,
            coordinates = city.coordinates,
            hours = report.hourly,
            dataAge = Duration.ZERO,
            staleAfter = WeatherFreshness.staleAfter(updateFrequencyMin)
        )
        repository.recordSkyRuns(city, runs)
    }

    companion object {
        /** Input data flag set by the widget's ↻ tap (see TweatherWidgetProvider). */
        const val KEY_FORCE_REFRESH = "force_refresh"

        /** cacheKey of the tapped widget's city; absent = the bypass applies to all
         * (pre-existing enqueues and any tap whose city couldn't be resolved). */
        const val KEY_FORCE_CITY_KEY = "force_city_key"
    }
}
