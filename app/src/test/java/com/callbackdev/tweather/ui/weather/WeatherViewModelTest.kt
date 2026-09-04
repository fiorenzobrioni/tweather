package com.callbackdev.tweather.ui.weather

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.data.LocationProvider
import com.callbackdev.tweather.data.SettingsStore
import com.callbackdev.tweather.data.SkySubscriptionStore
import com.callbackdev.tweather.data.WeatherRepository
import com.callbackdev.tweather.data.WorkspaceStore
import com.callbackdev.tweather.data.local.ReportDiskCache
import com.callbackdev.tweather.data.local.TweatherDatabase
import com.callbackdev.tweather.data.remote.OpenMeteoAirQualityApi
import com.callbackdev.tweather.data.remote.OpenMeteoForecastApi
import com.callbackdev.tweather.data.remote.OpenMeteoGeocodingApi
import com.callbackdev.tweather.domain.WeatherException
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.GeoFix
import com.callbackdev.tweather.domain.model.toGpsCity
import com.callbackdev.tweather.data.remote.dto.CurrentDto
import com.callbackdev.tweather.data.remote.dto.DailyDto
import com.callbackdev.tweather.data.remote.dto.ForecastResponseDto
import com.callbackdev.tweather.data.remote.dto.HourlyDto
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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
import org.junit.Assert.assertNotNull
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

        @Volatile
        var lastMaxAge: Duration? = null

        override suspend fun currentFix(maxAge: Duration, timeout: Duration): GeoFix {
            calls++
            lastMaxAge = maxAge
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
    private lateinit var diskCache: ReportDiskCache
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
        diskCache = ReportDiskCache(tmp.newFolder("reports-${System.nanoTime()}"), json)
        repository = WeatherRepository(
            forecastApi = retrofit.create(OpenMeteoForecastApi::class.java),
            airQualityApi = retrofit.create(OpenMeteoAirQualityApi::class.java),
            geocodingApi = retrofit.create(OpenMeteoGeocodingApi::class.java),
            historyDao = database.weatherHistoryDao(),
            diskCache = diskCache,
            json = json
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
        storeScope.cancel()
    }

    private val skyStore by lazy {
        SkySubscriptionStore(
            PreferenceDataStoreFactory.create(scope = storeScope) {
                tmp.newFile("sky-${System.nanoTime()}.preferences_pb")
            },
            json
        )
    }

    private fun viewModel(provider: LocationProvider) =
        WeatherViewModel(
            repository, cityStore, settingsStore, provider, workspaceStore, skyStore
        )

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

    /**
     * Fase 20. The FAB is a gesture and gets a real acquisition; the cold-start
     * revalidation behind an already-rendered fix takes whatever the system already
     * holds, which usually costs no radio at all. Same call, different contract, and
     * the difference IS the battery saving.
     */
    @Test
    fun `the reader's refresh and the silent revalidation ask for different things`() {
        runBlocking {
            cityStore.setUseGps(true)
            cityStore.updateGpsCity(milanFix.toGpsCity())
        }
        val provider = FakeLocationProvider { milanFix }
        val vm = viewModel(provider)
        // Cold start behind a persisted fix: the revalidation, and it is patient.
        awaitState(vm) { it.error is WeatherException.NoNetwork }
        assertEquals(LocationProvider.SilentMaxAge, provider.lastMaxAge)

        vm.refresh()
        awaitState(vm) { !it.isLoading }
        assertEquals(LocationProvider.Now, provider.lastMaxAge)
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

    // ------------------------------------------------- Fase 17: the offline fallback

    /**
     * Writes a fetch of [ageHours] ago into the disk cache for [city], shaped like the
     * real thing: a week of hourly rows opening at the fetch's own hour, seven daily
     * ones opening on its day.
     */
    private fun seedDiskCache(city: com.callbackdev.tweather.domain.model.City, ageHours: Long) {
        val zone = ZoneId.of(city.timezone!!)
        val fetchedAt = Instant.now().minus(Duration.ofHours(ageHours))
        val local = fetchedAt.atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS)
        val hours = 24 * 7
        val days = 7
        runBlocking {
            diskCache.write(
                city.cacheKey,
                ReportDiskCache.Entry(
                    fetchedAtEpochMs = fetchedAt.toEpochMilli(),
                    responseTimeMs = 120,
                    forecast = ForecastResponseDto(
                        latitude = city.coordinates.lat,
                        longitude = city.coordinates.lon,
                        timezone = zone.id,
                        current = CurrentDto(
                            time = local.toString(),
                            temperatureC = 21.0, humidityPct = 60, apparentTemperatureC = 21.0,
                            dewPointC = 12.0, isDay = 1, precipitationMm = 0.0, weatherCode = 0,
                            pressureMslHpa = 1013.0, windSpeedKph = 5.0, windDirectionDeg = 0,
                            windGustsKph = 8.0, visibilityM = 20_000.0, cloudCoverPct = 0,
                            uvIndex = 3.0
                        ),
                        hourly = HourlyDto(
                            time = List(hours) { local.plusHours(it.toLong()).toString() },
                            temperatureC = List(hours) { 20.0 },
                            weatherCode = List(hours) { 0 },
                            precipitationProbabilityPct = List(hours) { 0 },
                            isDay = List(hours) { 1 },
                            visibilityM = List(hours) { 20_000.0 },
                            cloudCoverPct = List(hours) { 0 }
                        ),
                        daily = DailyDto(
                            time = List(days) { local.toLocalDate().plusDays(it.toLong()).toString() },
                            weatherCode = List(days) { 0 },
                            temperatureMaxC = List(days) { 28.0 },
                            temperatureMinC = List(days) { 18.0 },
                            sunrise = List(days) { local.toLocalDate().atTime(6, 7).toString() },
                            sunset = List(days) { local.toLocalDate().atTime(19, 52).toString() },
                            daylightDurationSec = List(days) { 49_500.0 },
                            precipitationProbabilityMaxPct = List(days) { 0 },
                            uvIndexMax = List(days) { 6.0 }
                        )
                    ),
                    airQuality = null
                )
            )
        }
    }

    /**
     * The screenshot that started this: a cold start with no network showed two comment
     * lines and nothing else, on a phone holding a full week of forecast. The home
     * widget had never done that — it keeps its last snapshot and marks it `# stale`.
     */
    @Test
    fun `a cold start with no network falls back to the last fetch that worked`() {
        val city = CityStore.DefaultCity
        seedDiskCache(city, ageHours = 3)
        runBlocking { cityStore.add(city) }

        val state = awaitState(viewModel(FakeLocationProvider { milanFix })) {
            it.error is WeatherException.NoNetwork && !it.isLoading
        }

        assertNotNull("the document must survive the failed fetch", state.report)
        assertNotNull("and say that it is behind", state.staleFor)
        assertTrue(state.staleFor!!.toMinutes() >= 179)
        // Trimmed: the three hours that are over are not "next hours"
        val firstHour = state.report!!.hourly.first().time
        val nowLocal = LocalDateTime.now(ZoneId.of(city.timezone!!)).truncatedTo(ChronoUnit.HOURS)
        assertEquals(nowLocal, firstHour)
        assertEquals(LocalDate.now(ZoneId.of(city.timezone!!)), state.report!!.daily.first().date)
    }

    /**
     * The expiry is read off the data, not off a constant: past the forecast horizon
     * every section of the document would be about a week that is over.
     */
    @Test
    fun `a fetch older than its own forecast is not shown at all`() {
        val city = CityStore.DefaultCity
        seedDiskCache(city, ageHours = 24 * 8)
        runBlocking { cityStore.add(city) }

        val state = awaitState(viewModel(FakeLocationProvider { milanFix })) {
            it.error is WeatherException.NoNetwork && !it.isLoading
        }

        assertNull(state.report)
        assertNull(state.staleFor)
    }

    /** Nothing cached at all: the editor says what happened and nothing more. */
    @Test
    fun `with no cached fetch the error stands alone`() {
        runBlocking { cityStore.add(CityStore.DefaultCity) }

        val state = awaitState(viewModel(FakeLocationProvider { milanFix })) {
            it.error is WeatherException.NoNetwork && !it.isLoading
        }

        assertNull(state.report)
        assertNull(state.staleFor)
    }
}
