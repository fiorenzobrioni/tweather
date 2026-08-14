package com.callbackdev.tweather.notifications

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.callbackdev.tweather.data.AlertStateStore
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.data.SettingsStore
import com.callbackdev.tweather.data.WeatherRepository
import com.callbackdev.tweather.data.local.TweatherDatabase
import com.callbackdev.tweather.data.remote.OpenMeteoAirQualityApi
import com.callbackdev.tweather.data.remote.OpenMeteoForecastApi
import com.callbackdev.tweather.data.remote.OpenMeteoGeocodingApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@RunWith(RobolectricTestRunner::class)
class WeatherSyncWorkerTest {

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

    private fun runWorker(): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<WeatherSyncWorker>(context).build().doWork()
    }

    @Test
    fun `all toggles off - worker succeeds and cancels its own periodic work`() = runBlocking {
        settingsStore.setSevereWeatherAlerts(false)
        settingsStore.setDailySummary(false)
        settingsStore.setPrecipitationWarning(false)
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
    fun `network failure - worker asks for a retry`() {
        // defaults: severe+precip on, notifications enabled (Robolectric default)
        assertEquals(ListenableWorker.Result.retry(), runWorker())
    }
}
