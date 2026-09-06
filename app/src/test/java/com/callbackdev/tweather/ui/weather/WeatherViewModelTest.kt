package com.callbackdev.tweather.ui.weather

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.data.LocationProvider
import com.callbackdev.tweather.data.PowerSaveState
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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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

    /** A clock the test moves by hand, so "the document was rebuilt against the real
     * now" is an assertion rather than a hope about wall-clock drift. */
    private class TestClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
        fun advance(by: Duration) { now = now.plus(by) }
    }

    @get:Rule
    val tmp = TemporaryFolder()

    /** Every request that actually left the app, cache hits excluded by construction. */
    @Volatile
    private var httpCalls = 0

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
        val counting = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    httpCalls++ // counted before it fails: the point is that it tried
                    chain.proceed(chain.request())
                }
            )
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("http://127.0.0.1:1/") // nothing listens: instant NoNetwork
            .client(counting)
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

    /**
     * The view models built here own coroutines this class cannot join: `viewModelScope`
     * is not exposed, and a load whose last statement has already set the state can
     * still be a few instructions from finishing on the Main test dispatcher.
     * `resetMain()` refuses while another thread is inside it — `Dispatchers.Main is
     * used concurrently with setting it`, which is what this class was failing with
     * about one run in twenty, in whichever test happened to end with a fetch in
     * flight.
     *
     * So it waits instead of asserting the timing: those coroutines have nothing left
     * to do, only a return to make.
     */
    @After
    fun tearDown() {
        runBlocking {
            withTimeout(5_000) {
                while (runCatching { Dispatchers.resetMain() }.isFailure) delay(5)
            }
        }
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

    private fun viewModel(
        provider: LocationProvider,
        clock: Clock = Clock.systemUTC(),
        powerSave: PowerSaveState = PowerSaveState.Off
    ) = WeatherViewModel(
        repository, cityStore, settingsStore, provider, workspaceStore, skyStore,
        clock, powerSave
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

    // -------------------------------------------------- Fase 25: coming back to it

    /** Waits until at least [target] requests have left the app. */
    private fun awaitHttp(target: Int): Int = runBlocking {
        withTimeout(10_000) {
            while (httpCalls < target) delay(5)
            httpCalls
        }
    }

    /**
     * `ON_RESUME`, retried until the view model is the one that answered.
     *
     * [WeatherViewModel.onResumed] declines while a load is still in flight, on purpose:
     * a fetch already on its way will produce the document anyway. But landing on the
     * document is not the same as that job being over — `load()` sets the state as its
     * very last statement and the coroutine completes a few instructions later, on
     * another thread — so resuming the instant [awaitState] returned was racing the tail
     * of a job that had nothing left to show. It lost about one run in three on a loaded
     * machine, and once on CI, where the assertion then read a `staleFor` the resume had
     * never been allowed to recompute. Retrying is also the truthful reading of the
     * scenario: nobody comes back to an app two hours later and lands inside a fetch
     * started two hours ago.
     *
     * Called with the clock already moved, so a resume that takes is visible: the
     * comparison is structural rather than by identity because a `MutableStateFlow`
     * conflates — assigning a value that `equals` the current one leaves the old
     * instance in place, and `!==` would have been a signal that can never fire.
     */
    private fun awaitResume(
        viewModel: WeatherViewModel,
        from: WeatherUiState
    ): WeatherUiState = runBlocking {
        withTimeout(10_000) {
            var state = viewModel.uiState.value
            while (state == from) {
                viewModel.onResumed()
                delay(5)
                state = viewModel.uiState.value
            }
            state
        }
    }

    /**
     * Battery saver drops the NETWORK half of the resume re-read and keeps the other
     * half, and both halves are asserted here.
     *
     * The second phase is what makes the first one mean anything: with saver off the
     * same call does reach the network, so the counter standing still under saver is
     * a decision and not a broken instrument.
     */
    @Test
    fun `battery saver skips the resume fetch but still rebuilds against the clock`() {
        val city = CityStore.DefaultCity
        val zone = ZoneId.of(city.timezone!!)
        seedDiskCache(city, ageHours = 3)
        runBlocking { cityStore.add(city) }

        var saving = false
        val clock = TestClock(Instant.now())
        val vm = viewModel(
            FakeLocationProvider { milanFix },
            clock = clock,
            powerSave = { saving }
        )
        val landed = awaitState(vm) { it.report != null && !it.isLoading }
        val staleBefore = landed.staleFor!!
        val firstHourBefore = landed.report!!.hourly.first().time
        val callsBefore = httpCalls

        // Two hours later, under saver.
        saving = true
        clock.advance(Duration.ofHours(2))
        val saved = awaitResume(vm, from = landed)

        assertEquals("saver must not spend a request", callsBefore, httpCalls)
        assertNotNull("and must not blank the document either", saved.report)
        // The clock moved and the document knows: it says it is two hours further
        // behind, and it no longer opens with two hours that are over.
        assertTrue(
            "staleFor: ${saved.staleFor}",
            saved.staleFor!! >= staleBefore.plusHours(2)
        )
        assertEquals(firstHourBefore.plusHours(2), saved.report!!.hourly.first().time)
        assertEquals(
            clock.now.atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS),
            saved.report!!.hourly.first().time
        )

        // Saver off: the very same call goes to the network.
        saving = false
        vm.onResumed()
        assertTrue(awaitHttp(callsBefore + 1) > callsBefore)
    }

    /** The FAB is an explicit request and is never quietly ignored. */
    @Test
    fun `battery saver does not silence the refresh the reader asked for`() {
        val city = CityStore.DefaultCity
        seedDiskCache(city, ageHours = 3)
        runBlocking { cityStore.add(city) }

        val vm = viewModel(FakeLocationProvider { milanFix }, powerSave = { true })
        awaitState(vm) { it.report != null && !it.isLoading }
        val callsBefore = httpCalls

        vm.refresh()
        assertTrue(awaitHttp(callsBefore + 1) > callsBefore)
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
