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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Desired-state reconciliation for the single periodic background job. Battery
 * choices: no flex window (default = whole period = maximum OS batching freedom),
 * only a CONNECTED constraint, no battery-not-low (it would suppress severe
 * alerts exactly when they matter; flip here if ever reconsidered), backoff only
 * for the no-network edge. Survives reboots via WorkManager itself.
 */
object AlertScheduler {

    const val UNIQUE_NAME = "weather-sync"

    /** Split out pure so the enqueue-vs-cancel decision is unit-testable. */
    fun shouldRun(settings: NotificationSettings, notificationsEnabled: Boolean): Boolean =
        notificationsEnabled &&
            (settings.severeWeatherAlerts || settings.dailySummary || settings.precipitationWarning)

    suspend fun reconcile(context: Context) {
        val settings = ServiceLocator.settingsStore(context).settings.first()
        if (shouldRun(settings.notifications, NotificationManagerCompat.from(context).areNotificationsEnabled())) {
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
