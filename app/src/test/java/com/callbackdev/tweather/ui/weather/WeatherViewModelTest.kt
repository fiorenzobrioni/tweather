package com.callbackdev.tweather.ui.weather

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.data.LocationProvider
import com.callbackdev.tweather.data.SettingsStore
import com.callbackdev.tweather.data.WeatherRepository
import com.callbackdev.tweather.data.WorkspaceStore
import com.callbackdev.tweather.data.local.TweatherDatabase
import com.callbackdev.tweather.data.remote.OpenMeteoAirQualityApi
import com.callbackdev.tweather.data.remote.OpenMeteoForecastApi
import com.callbackdev.tweather.data.remote.OpenMeteoGeocodingApi
import com.callbackdev.tweather.domain.WeatherException
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.GeoFix
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * GPS orchestration tests. The network side is a Retrofit instance pointed at an
 * unreachable port, so every fetch fails fast as [WeatherException.NoNetwork] —
 * which doubles as proof that the ViewModel attempted a load for the given source.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WeatherViewModelTest {

    private class FakeLocationProvider(
        @Volatile var fix: () -> GeoFix
    ) : LocationProvider {
        @Volatile
        var calls = 0

        override suspend fun currentFix(timeout: Duration): GeoFix {
            calls++
            return fix()
        }
    }

    @get:Rule
    val tmp = TemporaryFolder()

    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var cityStore: CityStore
    private lateinit var settingsStore: SettingsStore
    private lateinit var workspaceStore: WorkspaceStore
    private lateinit var repository: WeatherRepository
    private lateinit var database: TweatherDatabase

    private val milanFix = GeoFix(Coordinates(45.46, 9.19), "Milano", "Lombardia", "Italy")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        cityStore = CityStore(
            PreferenceDataStoreFactory.create(scope = storeScope) {
                tmp.newFile("cities-${System.nanoTime()}.preferences_pb")
            },
            json
        )
        settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(scope = storeScope) {
                tmp.newFile("settings-${System.nanoTime()}.preferences_pb")
            }
        )
        workspaceStore = WorkspaceStore(
            PreferenceDataStoreFactory.create(scope = storeScope) {
                tmp.newFile("workspace-${System.nanoTime()}.preferences_pb")
            }
        )
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TweatherDatabase::class.java
        ).allowMainThreadQueries().build()
        val retrofit = Retrofit.Builder()
            .baseUrl("http://127.0.0.1:1/") // nothing listens: instant NoNetwork
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        repository = WeatherRepository(
            forecastApi = retrofit.create(OpenMeteoForecastApi::class.java),
            airQualityApi = retrofit.create(OpenMeteoAirQualityApi::class.java),
            geocodingApi = retrofit.create(OpenMeteoGeocodingApi::class.java),
            historyDao = database.weatherHistoryDao(),
            json = json
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
        storeScope.cancel()
    }

    private fun viewModel(provider: LocationProvider) =
        WeatherViewModel(repository, cityStore, settingsStore, provider, workspaceStore)

    private fun awaitState(
        viewModel: WeatherViewModel,
        predicate: (WeatherUiState) -> Boolean
    ): WeatherUiState = runBlocking {
        withTimeout(10_000) { viewModel.uiState.first(predicate) }
    }

    @Test
    fun `location failure surfaces as a gps error, not a crash`() {
        runBlocking { cityStore.setUseGps(true) }
        val vm = viewModel(
            FakeLocationProvider { throw WeatherException.LocationPermissionDenied() }
        )
        val state = awaitState(vm) { it.error != null }
        assertTrue(state.error is WeatherException.LocationPermissionDenied)
        assertFalse(state.acquiringFix)
        assertFalse(state.isLoading)
    }

    @Test
    fun `a fix is persisted and drives the fetch`() {
        runBlocking { cityStore.setUseGps(true) }
        val vm = viewModel(FakeLocationProvider { milanFix })
        // NoNetwork (unreachable Retrofit) proves the load ran for the acquired fix
        val state = awaitState(vm) { it.error is WeatherException.NoNetwork }
        assertFalse(state.acquiringFix)
        val persisted = runBlocking { cityStore.locationSettings.first().gpsCity }
        assertEquals("Milano", persisted?.name)
        assertEquals(Coordinates(45.46, 9.19), persisted?.coordinates)
    }

    @Test
    fun `refresh with gps source re-acquires the position`() {
        runBlocking { cityStore.setUseGps(true) }
        val provider = FakeLocationProvider { milanFix }
        val vm = viewModel(provider)
        awaitState(vm) { it.error is WeatherException.NoNetwork }
        val callsAfterFirstLoad = provider.calls
        vm.refresh()
        awaitState(vm) { !it.isLoading }
        assertTrue(provider.calls > callsAfterFirstLoad)
    }

    @Test
    fun `saved city stays the source while gps is off`() {
        // Fase 14b: the saved city is the test's own precondition now, not a seed
        runBlocking { cityStore.add(CityStore.DefaultCity) }
        val provider = FakeLocationProvider { milanFix }
        val vm = viewModel(provider)
        awaitState(vm) { it.error is WeatherException.NoNetwork }
        assertEquals(0, provider.calls)
    }

    /**
     * Fase 14b: no city, no GPS — the document says so instead of spinning forever on
     * a fetch it cannot make, and the FAB goes away with it (WeatherScreen).
     */
    @Test
    fun `with nothing configured the editor reports no location`() {
        val provider = FakeLocationProvider { milanFix }
        val vm = viewModel(provider)
        val state = awaitState(vm) { it.noLocation }
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNull(state.report)
        assertEquals(0, provider.calls)
    }

    /** The moment a city is added the empty state must lift, not linger. */
    @Test
    fun `adding a city clears the no-location state`() {
        val vm = viewModel(FakeLocationProvider { milanFix })
        awaitState(vm) { it.noLocation }

        runBlocking { cityStore.add(CityStore.DefaultCity) }

        assertFalse(awaitState(vm) { !it.noLocation }.noLocation)
    }
}
