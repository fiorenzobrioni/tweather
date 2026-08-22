package com.callbackdev.tweather.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.data.RuleStateStore
import com.callbackdev.tweather.data.RuleStore
import com.callbackdev.tweather.data.SettingsStore
import com.callbackdev.tweather.data.WeatherRepository
import com.callbackdev.tweather.data.local.ReportDiskCache
import com.callbackdev.tweather.data.local.TweatherDatabase
import com.callbackdev.tweather.data.remote.OpenMeteoAirQualityApi
import com.callbackdev.tweather.data.remote.OpenMeteoForecastApi
import com.callbackdev.tweather.data.remote.OpenMeteoGeocodingApi
import com.callbackdev.tweather.data.remote.dto.CurrentDto
import com.callbackdev.tweather.data.remote.dto.DailyDto
import com.callbackdev.tweather.data.remote.dto.ForecastResponseDto
import com.callbackdev.tweather.data.remote.dto.HourlyDto
import com.callbackdev.tweather.domain.rules.RuleOp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
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
 * `$ tweather run rules` end to end, no network: the repository reads a primed
 * [ReportDiskCache] entry (the cold-start path), so the whole dry run — fetch,
 * check, interpolation — executes exactly as on a device. Born from a real
 * on-device crash the pure unit tests could not see.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RulesDryRunEndToEndTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var database: TweatherDatabase
    private lateinit var ruleStore: RuleStore
    private lateinit var viewModel: RulesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TweatherDatabase::class.java
        ).allowMainThreadQueries().build()

        val retrofit = Retrofit.Builder()
            .baseUrl("http://127.0.0.1:1/") // unreachable: the disk cache must serve
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val diskCache = ReportDiskCache(tmp.newFolder("report_cache"), json)
        val cityStore = CityStore(
            PreferenceDataStoreFactory.create(scope = scope) {
                tmp.newFile("cities.preferences_pb")
            },
            json
        )
        // Prime the cache for the default city (Milan) with data around real "now"
        runBlocking {
            diskCache.write(
                CityStore.DefaultCity.cacheKey,
                ReportDiskCache.Entry(
                    fetchedAtEpochMs = System.currentTimeMillis(),
                    responseTimeMs = 42,
                    forecast = forecastAroundNow(),
                    airQuality = null
                )
            )
        }
        ruleStore = RuleStore(
            PreferenceDataStoreFactory.create(scope = scope) {
                tmp.newFile("rules.preferences_pb")
            },
            json
        )
        viewModel = RulesViewModel(
            ruleStore = ruleStore,
            ruleStateStore = RuleStateStore(
                PreferenceDataStoreFactory.create(scope = scope) {
                    tmp.newFile("rule-state.preferences_pb")
                }
            ),
            settingsStore = SettingsStore(
                PreferenceDataStoreFactory.create(scope = scope) {
                    tmp.newFile("settings.preferences_pb")
                }
            ),
            cityStore = cityStore,
            repository = WeatherRepository(
                forecastApi = retrofit.create(OpenMeteoForecastApi::class.java),
                airQualityApi = retrofit.create(OpenMeteoAirQualityApi::class.java),
                geocodingApi = retrofit.create(OpenMeteoGeocodingApi::class.java),
                historyDao = database.weatherHistoryDao(),
                diskCache = diskCache,
                json = json
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
        scope.cancel()
    }

    /** A plausible Open-Meteo response whose hourly slots straddle the real now,
     * precip probability 30% everywhere — so `< 60` fires and `>= 60` passes. */
    private fun forecastAroundNow(): ForecastResponseDto {
        val zone = ZoneId.of("Europe/Rome")
        val nowLocal = ZonedDateTime.now(zone).toLocalDateTime()
        val start = nowLocal.truncatedTo(ChronoUnit.HOURS).minusHours(2)
        val count = 30
        return ForecastResponseDto(
            latitude = 45.46,
            longitude = 9.19,
            timezone = "Europe/Rome",
            current = CurrentDto(
                time = nowLocal.truncatedTo(ChronoUnit.MINUTES).toString(),
                temperatureC = 18.5,
                humidityPct = 54,
                apparentTemperatureC = 17.2,
                dewPointC = 9.0,
                isDay = 1,
                precipitationMm = 0.0,
                weatherCode = 2,
                pressureMslHpa = 1015.2,
                windSpeedKph = 12.5,
                windDirectionDeg = 310,
                windGustsKph = 18.0,
                visibilityM = 16100.0,
                cloudCoverPct = 60,
                uvIndex = 4.0
            ),
            hourly = HourlyDto(
                time = List(count) { start.plusHours(it.toLong()).toString() },
                temperatureC = List(count) { 18.0 },
                weatherCode = List(count) { 2 },
                precipitationProbabilityPct = List(count) { 30 },
                isDay = List(count) { 1 },
                visibilityM = List(count) { 16_100.0 },
                cloudCoverPct = List(count) { 60 }
            ),
            daily = DailyDto(
                time = List(3) { LocalDate.now(zone).plusDays(it.toLong()).toString() },
                weatherCode = List(3) { 2 },
                temperatureMaxC = List(3) { 24.0 },
                temperatureMinC = List(3) { 14.0 },
                sunrise = List(3) { LocalDateTime.of(LocalDate.now(zone), java.time.LocalTime.of(6, 30)).toString() },
                sunset = List(3) { LocalDateTime.of(LocalDate.now(zone), java.time.LocalTime.of(20, 15)).toString() },
                daylightDurationSec = List(3) { 49_500.0 },
                precipitationProbabilityMaxPct = List(3) { 30 },
                uvIndexMax = List(3) { 6.0 }
            )
        )
    }

    private fun awaitDryRun(): DryRunUi = runBlocking {
        withTimeout(10_000) {
            viewModel.dryRun.first { it != null && it != DryRunUi.Running }!!
        }
    }

    @Test
    fun `a firing rule produces an interpolated notify line, not a crash`() = runBlocking {
        ruleStore.add() // template: next_6h.precip_chance_max >= 60 → passes at 30%
        val template = ruleStore.rules.first().single()
        // `>=` → `<` with a suspend write this test can *await*. It used to go through
        // viewModel.cycleOp() and then wait for the change to surface on the store's
        // flow (`first { op == LT }`, 10s budget): cycleOp is fire-and-forget on
        // viewModelScope, so that wait polled a DataStore round-trip driven by nobody,
        // and on a loaded CI runner it hung once and reddened the gate (ago 2026).
        // The op cycle is not what this test is about — it is covered by
        // RulesScreenTest — here it is setup, and setup must be deterministic.
        ruleStore.update(
            template.copy(
                conditions = listOf(template.conditions.single().copy(op = RuleOp.LT))
            )
        )
        assertEquals(RuleOp.LT, ruleStore.rules.first().single().conditions.single().op)

        viewModel.runRules()
        val done = awaitDryRun()
        assertTrue("expected Done, was $done", done is DryRunUi.Done)
        val result = (done as DryRunUi.Done).results.getValue(template.id)
        assertTrue("expected Fires, was $result", result is DryRunResult.Fires)
        val message = (result as DryRunResult.Fires).message
        assertTrue(message, message.startsWith("Take an umbrella — 30% rain at "))
    }

    @Test
    fun `a passing rule reports pass`() = runBlocking {
        ruleStore.add() // >= 60 against 30% → passes
        viewModel.runRules()
        val done = awaitDryRun()
        assertTrue("expected Done, was $done", done is DryRunUi.Done)
        assertEquals(
            DryRunResult.Passes,
            (done as DryRunUi.Done).results.values.single()
        )
    }
}
