package com.callbackdev.tweather.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.callbackdev.tweather.R
import com.callbackdev.tweather.data.AlertStateStore
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.data.SettingsStore
import com.callbackdev.tweather.data.WeatherRepository
import com.callbackdev.tweather.data.local.TweatherDatabase
import com.callbackdev.tweather.data.remote.OpenMeteoAirQualityApi
import com.callbackdev.tweather.data.remote.OpenMeteoForecastApi
import com.callbackdev.tweather.data.remote.OpenMeteoGeocodingApi
import com.callbackdev.tweather.notifications.AlertScheduler
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

/**
 * The receiver side of Fase 9d: the ↻ broadcast and the "is a widget placed?"
 * question that keeps the shared periodic job alive on its own. Same sandbox as
 * WeatherSyncWorkerTest (temp-file DataStores, in-memory Room, dead base URL):
 * a bound widget makes the provider render, and rendering resolves the graph.
 */
@RunWith(RobolectricTestRunner::class)
class TweatherWidgetProviderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var settingsStore: SettingsStore
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
            .baseUrl("http://127.0.0.1:1/") // unreachable: no test may hit the network
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        ServiceLocator.overrideForTests(
            repository = WeatherRepository(
                forecastApi = retrofit.create(OpenMeteoForecastApi::class.java),
                airQualityApi = retrofit.create(OpenMeteoAirQualityApi::class.java),
                geocodingApi = retrofit.create(OpenMeteoGeocodingApi::class.java),
                historyDao = database.weatherHistoryDao(),
                json = json
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
            )
        )
    }

    @After
    fun tearDown() {
        ServiceLocator.overrideForTests() // back to lazy real instances
        database.close()
        scope.cancel()
    }

    /**
     * Robolectric's binding API. It registers the instance under
     * `ComponentName(context, TweatherWidgetProvider::class)` — exactly the
     * component [TweatherWidgetProvider.hasWidgets] queries — and dispatches
     * ENABLED + UPDATE to a fresh provider instance, so the real placement path
     * (async render and reconcile) runs alongside the assertions.
     */
    private fun bindWidget(): Int =
        shadowOf(AppWidgetManager.getInstance(context))
            .createWidget(TweatherWidgetProvider::class.java, R.layout.widget_tweather_medium)

    private fun workStatesFor(uniqueName: String): List<WorkInfo.State> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(uniqueName).get()
            .map { it.state }

    private fun turnEveryNotificationOff() {
        runBlocking {
            settingsStore.setSevereWeatherAlerts(false)
            settingsStore.setDailySummary(false)
            settingsStore.setPrecipitationWarning(false)
        }
    }

    @Test
    fun `the refresh broadcast enqueues the manual sync job`() {
        TweatherWidgetProvider().onReceive(context, Intent(TweatherWidgetProvider.ACTION_REFRESH))

        assertTrue(
            workStatesFor(TweatherWidgetProvider.MANUAL_SYNC_NAME).isNotEmpty()
        )
    }

    @Test
    fun `tap spam stays one job - the manual sync is unique and KEEPs`() {
        val provider = TweatherWidgetProvider()
        provider.onReceive(context, Intent(TweatherWidgetProvider.ACTION_REFRESH))
        provider.onReceive(context, Intent(TweatherWidgetProvider.ACTION_REFRESH))

        assertEquals(1, workStatesFor(TweatherWidgetProvider.MANUAL_SYNC_NAME).size)
    }

    @Test
    fun `an unrelated broadcast enqueues nothing`() {
        TweatherWidgetProvider().onReceive(context, Intent(Intent.ACTION_TIME_TICK))

        assertEquals(
            emptyList<WorkInfo.State>(),
            workStatesFor(TweatherWidgetProvider.MANUAL_SYNC_NAME)
        )
    }

    @Test
    fun `hasWidgets follows the bound instances`() {
        assertFalse(TweatherWidgetProvider.hasWidgets(context))

        bindWidget()

        assertTrue(TweatherWidgetProvider.hasWidgets(context))
    }

    @Test
    fun `notifications all off but a widget placed - the periodic job survives`() {
        turnEveryNotificationOff()
        bindWidget()

        runBlocking { AlertScheduler.reconcile(context) }

        assertTrue(
            "a placed widget alone must keep weather-sync alive",
            workStatesFor(AlertScheduler.UNIQUE_NAME).any { !it.isFinished }
        )
    }

    @Test
    fun `notifications all off and no widget - the periodic job is cancelled`() {
        // Defaults (severe + precip on) enqueue it first, so there is something to lose
        runBlocking { AlertScheduler.reconcile(context) }
        assertTrue(workStatesFor(AlertScheduler.UNIQUE_NAME).any { !it.isFinished })

        turnEveryNotificationOff()
        runBlocking { AlertScheduler.reconcile(context) }

        assertEquals(
            emptyList<WorkInfo.State>(),
            workStatesFor(AlertScheduler.UNIQUE_NAME).filter { !it.isFinished }
        )
    }
}
