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
import kotlinx.coroutines.delay
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
        // Enqueued here rather than in the coroutine below: the fetch is what the tap
        // is actually asking for, and it must not wait behind a render.
        val tapped = intent.action == ACTION_REFRESH
        if (tapped) enqueueManualSync(context, intent.getStringExtra(EXTRA_CITY_KEY))
        val render = needsRender
        val reconcile = needsReconcile
        val forget = deleted
        val remap = restored
        if (!tapped && !render && !reconcile && forget == null && remap == null) return

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
                when {
                    tapped -> acknowledgeTap(context)
                    render -> TweatherWidgetUpdater.updateAll(context)
                }
            } catch (e: Exception) {
                // An unhandled throw here would crash the app from a broadcast; the
                // widget simply keeps whatever it was showing.
            } finally {
                pendingResult?.finish()
            }
        }
    }

    /**
     * The answer to the ↻ tap the fetch itself cannot give: it lands seconds later, so
     * the repaint the tap used to trigger was pixel-identical to no tap at all — same
     * numbers, same glyph — and on the sizes most people place `# last_sync` is cut
     * anyway. So the glyph itself wears the tap, on every tier, and comes back on the
     * first repaint that follows: the history commit the fetch writes, the worker's own
     * repaint when the sync fails, or [BusyWindowMs] here, whichever comes first.
     *
     * That window is a ceiling, not a wait — it is what covers the tap nothing else
     * answers: offline, the CONNECTED constraint holds the job enqueued for as long as
     * it takes, and a widget left wearing `…` for good would be a worse lie than
     * numbers that did not move.
     */
    private suspend fun acknowledgeTap(context: Context) {
        TweatherWidgetUpdater.updateAll(context, syncing = true)
        delay(BusyWindowMs)
        TweatherWidgetUpdater.updateAll(context)
    }

    companion object {
        const val ACTION_REFRESH = "com.callbackdev.tweather.widget.REFRESH"

        /**
         * How long the ↻ stays in its working form when nothing else takes it out of
         * it. Long enough to outlast an ordinary fetch (two GETs behind an expedited
         * job), far short of the budget a background broadcast may hold with goAsync.
         */
        private const val BusyWindowMs = 5_000L

        /** cacheKey of the city the tapped instance shows (set by [WidgetRenderer]). */
        const val EXTRA_CITY_KEY = "com.callbackdev.tweather.widget.EXTRA_CITY_KEY"

        /** Distinct from the periodic job so a tap never disturbs its cycle. */
        const val MANUAL_SYNC_NAME = "weather-sync-manual"

        fun hasWidgets(context: Context): Boolean =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, TweatherWidgetProvider::class.java))
                .isNotEmpty()

        /**
         * ↻ tap: one fetch, cache bypassed only for [forceCityKey] — the city the
         * tapped widget shows; every other city keeps its TTL (null forces all, the
         * legacy contract). KEEP swallows tap-spam, so a tap on a second widget while
         * one sync is pending piggybacks on it instead of forcing its own city.
         * Expedited is safe without `getForegroundInfo` on minSdk 33 — the
         * foreground-service fallback is a pre-S requirement.
         */
        fun enqueueManualSync(context: Context, forceCityKey: String? = null) {
            val request = OneTimeWorkRequestBuilder<WeatherSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setInputData(
                    workDataOf(
                        WeatherSyncWorker.KEY_FORCE_REFRESH to true,
                        WeatherSyncWorker.KEY_FORCE_CITY_KEY to forceCityKey
                    )
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(MANUAL_SYNC_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
