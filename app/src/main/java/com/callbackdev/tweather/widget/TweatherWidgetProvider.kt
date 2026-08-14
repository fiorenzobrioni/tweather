package com.callbackdev.tweather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.notifications.AlertScheduler
import com.callbackdev.tweather.notifications.WeatherSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The `tweather --now` home widget. Deliberately passive on battery: no
 * `updatePeriodMillis`, no polling of its own — it renders persisted data and is
 * refreshed whenever a fetch commits (the shared "weather-sync" job or the app in
 * foreground). The ↻ glyph is the only user-initiated fetch.
 */
class TweatherWidgetProvider : AppWidgetProvider() {

    // The hooks only record what the broadcast needs; the actual suspend work runs
    // once in onReceive. goAsync() is consume-once, and a single broadcast can hit
    // two hooks (ACTION_APPWIDGET_ENABLE_AND_UPDATE → onEnabled + onUpdate), where a
    // second goAsync() would return null.
    private var needsRender = false
    private var needsReconcile = false
    private var deleted: IntArray? = null
    private var restored: Pair<IntArray, IntArray>? = null

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        needsRender = true
    }

    /**
     * Resize is deliberately NOT handled: the sizes map exists so the host re-picks
     * the right tier itself, in-process. Pushing a fresh RemoteViews from here raced
     * that — the host applied our update against the size it still had on record, so
     * shrinking a widget could leave the taller transcript in place, clipped.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) = Unit

    /** First instance placed: the background job may now be wanted (see [AlertScheduler]). */
    override fun onEnabled(context: Context) {
        needsReconcile = true
    }

    /** Last instance removed: with notifications off there is nothing left to sync for. */
    override fun onDisabled(context: Context) {
        needsReconcile = true
    }

    /** Removed instances must not leave their pinned city behind in DataStore. */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        deleted = appWidgetIds
    }

    /**
     * Restore gives the same widget a new id while our pins are keyed by the old one
     * (the DataStore file rides along in the backup), so they must be re-keyed or a
     * widget would inherit whichever pin happened to reuse its number.
     */
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        restored = oldWidgetIds to newWidgetIds
    }

    override fun onReceive(context: Context, intent: Intent) {
        needsRender = false
        needsReconcile = false
        deleted = null
        restored = null
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            enqueueManualSync(context)
            needsRender = true // paint the freshly-tapped state; the fetch lands later
        }
        val render = needsRender
        val reconcile = needsReconcile
        val forget = deleted
        val remap = restored
        if (!render && !reconcile && forget == null && remap == null) return

        // Nullable despite the platform signature: goAsync() only returns a result
        // while a real broadcast is being dispatched. The work still has to run.
        val pendingResult: BroadcastReceiver.PendingResult? = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val store = ServiceLocator.widgetCityStore(context)
                // before the render: RESTORED is followed by onUpdate in the same broadcast
                remap?.let { (old, new) -> store.remap(old, new) }
                forget?.let { store.forget(it) }
                if (reconcile) AlertScheduler.reconcile(context)
                if (render) TweatherWidgetUpdater.updateAll(context)
            } catch (e: Exception) {
                // An unhandled throw here would crash the app from a broadcast; the
                // widget simply keeps whatever it was showing.
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.callbackdev.tweather.widget.REFRESH"

        /** Distinct from the periodic job so a tap never disturbs its cycle. */
        const val MANUAL_SYNC_NAME = "weather-sync-manual"

        fun hasWidgets(context: Context): Boolean =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, TweatherWidgetProvider::class.java))
                .isNotEmpty()

        /**
         * ↻ tap: one fetch, cache bypassed (a manual refresh must actually refresh).
         * KEEP swallows tap-spam. Expedited is safe without `getForegroundInfo` on
         * minSdk 33 — the foreground-service fallback is a pre-S requirement.
         */
        fun enqueueManualSync(context: Context) {
            val request = OneTimeWorkRequestBuilder<WeatherSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setInputData(workDataOf(WeatherSyncWorker.KEY_FORCE_REFRESH to true))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(MANUAL_SYNC_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
