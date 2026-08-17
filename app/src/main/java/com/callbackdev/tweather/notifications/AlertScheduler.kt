package com.callbackdev.tweather.notifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.callbackdev.tweather.data.NotificationSettings
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.widget.TweatherWidgetProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Desired-state reconciliation for the single periodic background job — shared by
 * the alert engine and the home widget. Battery choices: no flex window (default =
 * whole period = maximum OS batching freedom), only a CONNECTED constraint, no
 * battery-not-low (it would suppress severe alerts exactly when they matter; flip
 * here if ever reconsidered), backoff only for the no-network edge. Survives
 * reboots via WorkManager itself.
 */
object AlertScheduler {

    const val UNIQUE_NAME = "weather-sync"

    /** Whether the job would post anything: gates the alert evaluation in the worker.
     * User rules (Fase 11) count only while some exist and their master toggle is
     * on — an empty alerts.rules must not keep the phone polling. */
    fun alertsWanted(
        settings: NotificationSettings,
        notificationsEnabled: Boolean,
        hasEnabledRules: Boolean = false
    ): Boolean =
        notificationsEnabled &&
            (
                settings.severeWeatherAlerts || settings.dailySummary ||
                    settings.precipitationWarning || (settings.userRules && hasEnabledRules)
                )

    /**
     * Split out pure so the enqueue-vs-cancel decision is unit-testable. A placed
     * home widget keeps the job alive on its own (Fase 9d): same interval, same
     * single fetch — it just renders instead of notifying.
     */
    fun shouldRun(
        settings: NotificationSettings,
        notificationsEnabled: Boolean,
        hasWidgets: Boolean,
        hasEnabledRules: Boolean = false
    ): Boolean = alertsWanted(settings, notificationsEnabled, hasEnabledRules) || hasWidgets

    suspend fun reconcile(context: Context) {
        val settings = ServiceLocator.settingsStore(context).settings.first()
        val hasEnabledRules =
            ServiceLocator.ruleStore(context).rules.first().any { it.enabled }
        if (shouldRun(
                settings.notifications,
                NotificationManagerCompat.from(context).areNotificationsEnabled(),
                TweatherWidgetProvider.hasWidgets(context),
                hasEnabledRules
            )
        ) {
            val request = PeriodicWorkRequestBuilder<WeatherSyncWorker>(
                settings.updateFrequencyMin.coerceAtLeast(15).toLong(), TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            // UPDATE keeps the periodic cycle on frequency changes; idempotent
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            cancel(context)
        }
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }
}
