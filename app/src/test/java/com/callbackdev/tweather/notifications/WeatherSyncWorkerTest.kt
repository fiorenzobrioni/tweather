package com.callbackdev.tweather.notifications

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.callbackdev.tweather.R
import com.callbackdev.tweather.data.AlertStateStore
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.data.RuleStateStore
import com.callbackdev.tweather.data.RuleStore
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.data.SettingsStore
import com.callbackdev.tweather.data.WeatherRepository
import com.callbackdev.tweather.data.local.TweatherDatabase
import com.callbackdev.tweather.data.remote.OpenMeteoAirQualityApi
import com.callbackdev.tweather.data.remote.OpenMeteoForecastApi
import com.callbackdev.tweather.data.remote.OpenMeteoGeocodingApi
import com.callbackdev.tweather.widget.TweatherWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@RunWith(RobolectricTestRunner::class)
class WeatherSyncWorkerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var settingsStore: SettingsStore
    private lateinit var ruleStore: RuleStore
    private lateinit var database: TweatherDatabase

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(scope = scope) {
                tmp.newFile("settings-${System.nanoTime()}.preferences_pb")
            }
        )
        database = Room.inMemoryDatabaseBuilder(context, TweatherDatabase::class.java)
            .allowMainThreadQueries().build()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl("http://127.0.0.1:1/") // unreachable: getWeather → NoNetwork
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        ServiceLocator.overrideForTests(
            repository = WeatherRepository(
                forecastApi = retrofit.create(OpenMeteoForecastApi::class.java),
                airQualityApi = retrofit.create(OpenMeteoAirQualityApi::class.java),
                geocodingApi = retrofit.create(OpenMeteoGeocodingApi::class.java),
                historyDao = database.weatherHistoryDao(),
                json = json
                // onHistoryCommitted left at its no-op default: the widget render is
                // TweatherWidgetUpdater's business, not the worker's
            ),
            cityStore = CityStore(
                PreferenceDataStoreFactory.create(scope = scope) {
                    tmp.newFile("cities-${System.nanoTime()}.preferences_pb")
                },
                json
            ),
            settingsStore = settingsStore,
            alertStateStore = AlertStateStore(
                PreferenceDataStoreFactory.create(scope = scope) {
                    tmp.newFile("alerts-${System.nanoTime()}.preferences_pb")
                }
            ),
            ruleStore = RuleStore(
                PreferenceDataStoreFactory.create(scope = scope) {
                    tmp.newFile("rules-${System.nanoTime()}.preferences_pb")
                },
                json
            ).also { ruleStore = it },
            ruleStateStore = RuleStateStore(
                PreferenceDataStoreFactory.create(scope = scope) {
                    tmp.newFile("rule-state-${System.nanoTime()}.preferences_pb")
                }
            )
        )
    }

    @After
    fun tearDown() {
        ServiceLocator.overrideForTests() // back to lazy real instances
        database.close()
        scope.cancel()
    }

    private fun runWorker(input: Data = Data.EMPTY): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<WeatherSyncWorker>(context).setInputData(input).build().doWork()
    }

    /**
     * The only way to make [TweatherWidgetProvider.hasWidgets] true on Robolectric.
     * It also replays ENABLED/UPDATE on a throwaway provider instance, whose
     * `goAsync` blocks fire and forget on their own scope — irrelevant here, the
     * assertions below never depend on them.
     */
    private fun placeWidget() {
        shadowOf(AppWidgetManager.getInstance(context))
            .createWidget(TweatherWidgetProvider::class.java, R.layout.widget_tweather_medium)
    }

    @Test
    fun `all toggles off - worker succeeds and cancels its own periodic work`() = runBlocking {
        settingsStore.setSevereWeatherAlerts(false)
        settingsStore.setDailySummary(false)
        settingsStore.setPrecipitationWarning(false)
        // no widget placed either, so the self-heal has nothing left to sync for
        // pre-existing periodic work to cancel
        AlertScheduler.reconcile(context) // toggles off → this is already a cancel
        assertEquals(ListenableWorker.Result.success(), runWorker())
        val info = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(AlertScheduler.UNIQUE_NAME).get()
        assertEquals(
            emptyList<WorkInfo.State>(),
            info.map { it.state }.filter { !it.isFinished }
        )
    }

    @Test
    fun `all toggles off but a widget is placed - the job survives and still fetches`() =
        runBlocking {
            settingsStore.setSevereWeatherAlerts(false)
            settingsStore.setDailySummary(false)
            settingsStore.setPrecipitationWarning(false)
            placeWidget()
            AlertScheduler.reconcile(context) // widget alone → enqueue, not cancel

            // retry means it walked past the self-heal branch and reached the fetch:
            // the widget must keep being fed even with every notification off
            assertEquals(ListenableWorker.Result.retry(), runWorker())

            val states = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(AlertScheduler.UNIQUE_NAME).get().map { it.state }
            assertTrue(
                "periodic work should still be alive, was $states",
                states.contains(WorkInfo.State.ENQUEUED)
            )
            assertFalse(
                "worker cancelled itself with a widget placed",
                states.contains(WorkInfo.State.CANCELLED)
            )
        }

    @Test
    fun `all toggles off but a user rule exists - the job survives and still fetches`() =
        runBlocking {
            settingsStore.setSevereWeatherAlerts(false)
            settingsStore.setDailySummary(false)
            settingsStore.setPrecipitationWarning(false)
            ruleStore.add() // one enabled rule in alerts.rules
            AlertScheduler.reconcile(context) // rules alone → enqueue, not cancel

            // retry = it walked past the self-heal branch and reached the fetch
            assertEquals(ListenableWorker.Result.retry(), runWorker())

            val states = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(AlertScheduler.UNIQUE_NAME).get().map { it.state }
            assertTrue(
                "periodic work should still be alive, was $states",
                states.contains(WorkInfo.State.ENQUEUED)
            )
        }

    @Test
    fun `network failure - worker asks for a retry`() {
        // defaults: severe+precip on, notifications enabled (Robolectric default)
        assertEquals(ListenableWorker.Result.retry(), runWorker())
    }

    @Test
    fun `widget refresh tap - the force-refresh input data does not derail the run`() {
        // The cache bypass itself isn't observable here (an unreachable API never
        // fills the cache), so what this pins down is the input-data contract
        // between TweatherWidgetProvider's ↻ and the worker.
        assertEquals(
            ListenableWorker.Result.retry(),
            runWorker(workDataOf(WeatherSyncWorker.KEY_FORCE_REFRESH to true))
        )
    }
}
