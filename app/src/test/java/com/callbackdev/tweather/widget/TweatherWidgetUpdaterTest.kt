package com.callbackdev.tweather.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.callbackdev.tweather.R
import com.callbackdev.tweather.data.AlertStateStore
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.data.SettingsStore
import com.callbackdev.tweather.data.WeatherRepository
import com.callbackdev.tweather.data.WidgetCityStore
import com.callbackdev.tweather.data.local.TweatherDatabase
import com.callbackdev.tweather.data.local.WeatherHistoryEntry
import com.callbackdev.tweather.data.local.WeatherSnapshots
import com.callbackdev.tweather.data.remote.OpenMeteoAirQualityApi
import com.callbackdev.tweather.data.remote.OpenMeteoForecastApi
import com.callbackdev.tweather.data.remote.OpenMeteoGeocodingApi
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.GeoFix
import com.callbackdev.tweather.domain.model.GpsCityId
import com.callbackdev.tweather.domain.model.toGpsCity
import java.time.Instant
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
 * Per-widget city (Fase 9d): two instances of the same provider must be able to show
 * two different cities at the same time. The rule lives in
 * [TweatherWidgetUpdater.resolveCity], but the only honest observation point is the
 * view the launcher would get, so every test drives the real graph — same sandbox as
 * TweatherWidgetProviderTest (temp-file DataStores, in-memory Room, dead base URL) —
 * and reads the rendered widget back through Robolectric's AppWidgetManager shadow.
 */
@RunWith(RobolectricTestRunner::class)
class TweatherWidgetUpdaterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var cityStore: CityStore
    private lateinit var widgetCityStore: WidgetCityStore
    private lateinit var database: TweatherDatabase

    // Milan is CityStore.DefaultCity: the active source unless a test moves it
    private val milan = CityStore.DefaultCity
    private val turin = City(3_165_524, "Turin", "Piedmont", "Italy",
        Coordinates(45.0703, 7.6869), "Europe/Rome")
    private val gpsCity = GeoFix(Coordinates(43.77, 11.26), "Florence", null, "Italy").toGpsCity()

    /**
     * City name + rendered temperature, one pair per seeded city. Both are readable in
     * every tier (`Location: "Milan, Lombardy"` / `Temp: 21°C` on medium and large, the
     * bare name and temperature on small), and the temperatures are far enough apart
     * that none of them is a substring of another.
     */
    private val seeded = listOf("Milan" to "21°C", "Turin" to "-7°C", "Florence" to "33°C")

    @Before
    fun setUp() {
        // Placing a widget reconciles the shared job (AlertScheduler); without a test
        // WorkManager that path throws inside the provider's broadcast coroutine.
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        database = Room.inMemoryDatabaseBuilder(context, TweatherDatabase::class.java)
            .allowMainThreadQueries().build()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl("http://127.0.0.1:1/") // unreachable: the widget renders persisted data only
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        cityStore = CityStore(
            PreferenceDataStoreFactory.create(scope = scope) {
                tmp.newFile("cities-${System.nanoTime()}.preferences_pb")
            },
            json
        )
        // Fase 14b: nothing is seeded any more, so the fixture states its own
        // precondition instead of inheriting one from the data layer.
        runBlocking { cityStore.add(milan) }
        widgetCityStore = WidgetCityStore(
            PreferenceDataStoreFactory.create(scope = scope) {
                tmp.newFile("widget-cities-${System.nanoTime()}.preferences_pb")
            }
        )
        ServiceLocator.overrideForTests(
            repository = WeatherRepository(
                forecastApi = retrofit.create(OpenMeteoForecastApi::class.java),
                airQualityApi = retrofit.create(OpenMeteoAirQualityApi::class.java),
                geocodingApi = retrofit.create(OpenMeteoGeocodingApi::class.java),
                historyDao = database.weatherHistoryDao(),
                json = json
            ),
            cityStore = cityStore,
            settingsStore = SettingsStore(
                PreferenceDataStoreFactory.create(scope = scope) {
                    tmp.newFile("settings-${System.nanoTime()}.preferences_pb")
                }
            ),
            alertStateStore = AlertStateStore(
                PreferenceDataStoreFactory.create(scope = scope) {
                    tmp.newFile("alerts-${System.nanoTime()}.preferences_pb")
                }
            ),
            widgetCityStore = widgetCityStore
        )
    }

    @After
    fun tearDown() {
        ServiceLocator.overrideForTests() // back to lazy real instances
        database.close()
        scope.cancel()
    }

    // --- sandbox ---

    /**
     * Writes a history "commit" straight through the DAO, the way a fetch would. Only
     * the keys the widget binds are stored: the rest of WeatherSnapshots.flatten adds
     * nothing to a question about *which* city got rendered.
     */
    private suspend fun seedHistory(city: City, location: String, tempC: String) {
        val snapshot = Json.encodeToString(
            mapOf(
                "location" to location,
                "current.status" to "Clear ☀️",
                "current.temp_c" to tempC,
                "current.humidity_pct" to "50"
            )
        )
        // Fresh on purpose: an aged commit would add the stale marker to the very lines
        // these tests read, and staleness has its own test (WidgetContentBuilderTest).
        val timestamp = Instant.now().epochSecond
        database.weatherHistoryDao().insert(
            WeatherHistoryEntry(
                cityKey = city.cacheKey,
                cityLabel = city.label,
                hash = WeatherSnapshots.commitHash(city.cacheKey, timestamp.toString(), snapshot),
                author = "sys@tweather.app",
                timestampEpochSeconds = timestamp,
                snapshotJson = snapshot
            )
        )
    }

    /** Milan active with Turin saved next to it — the starting point of every test. */
    private fun seedTwoCities() = runBlocking {
        seedHistory(milan, "Milan, Lombardy", "21.4")
        seedHistory(turin, "Turin, Piedmont", "-7.2")
        cityStore.add(turin) // add() also activates, so hand the app back to Milan
        cityStore.setActive(milan)
    }

    /**
     * Places one instance the way the launcher would: Robolectric registers it under
     * `ComponentName(context, TweatherWidgetProvider::class)` — the component
     * [TweatherWidgetUpdater] queries — and dispatches ENABLED + UPDATE to a fresh
     * provider, whose own render then runs asynchronously (see [awaitFirstPaint]).
     */
    private fun bindWidget(): Int =
        shadowOf(AppWidgetManager.getInstance(context))
            .createWidget(TweatherWidgetProvider::class.java, R.layout.widget_tweather_medium)
            .also { awaitFirstPaint(it) }

    /**
     * That provider-side render reads the stores as they are *at bind time*, so a test
     * that pins a city right after binding would race it. Waiting for the paint to land
     * makes the ordering explicit: whatever the widget shows afterwards is what our own
     * updateAll put there. Best effort — our call is the last writer either way.
     */
    private fun awaitFirstPaint(appWidgetId: Int) {
        val deadline = System.currentTimeMillis() + 10_000
        // "°" reaches the view only through a rendered temperature (or the "--°"
        // placeholder); no widget layout carries it as static text.
        while (System.currentTimeMillis() < deadline && '°' !in renderedText(appWidgetId)) {
            Thread.sleep(10)
        }
    }

    private fun pin(appWidgetId: Int, cityId: Long) = runBlocking {
        widgetCityStore.pin(appWidgetId, cityId)
    }

    private fun updateAll() = runBlocking { TweatherWidgetUpdater.updateAll(context) }

    // --- reading the rendered widget ---

    /**
     * Every string the launcher would show for [appWidgetId]. The whole text tree, not
     * single ids: the updater always pushes the sizes map, and a host that applies it
     * without a size (Robolectric's, and any launcher before it measures) lands on the
     * smallest tier, where the city lives in `widget_location` instead of `widget_line1`.
     */
    private fun renderedText(appWidgetId: Int): String {
        val root = shadowOf(AppWidgetManager.getInstance(context)).getViewFor(appWidgetId)
        return buildString { appendTexts(root) }
    }

    private fun StringBuilder.appendTexts(view: View) {
        if (view is TextView) append(view.text).append('\n')
        if (view is ViewGroup) repeat(view.childCount) { appendTexts(view.getChildAt(it)) }
    }

    /**
     * [city] is showing and no other seeded city is — "renders the right one" is only
     * worth asserting together with "and not the wrong one".
     */
    private fun assertRenders(appWidgetId: Int, city: String) {
        val text = renderedText(appWidgetId)
        val temp = seeded.first { it.first == city }.second
        assertTrue("widget $appWidgetId should show $city $temp, rendered:\n$text",
            city in text && temp in text)
        seeded.filterNot { it.first == city }.forEach { (other, otherTemp) ->
            assertFalse("widget $appWidgetId leaked $other, rendered:\n$text",
                other in text || otherTemp in text)
        }
    }

    // --- resolution ---

    @Test
    fun `an unpinned widget follows the app's active city`() {
        seedTwoCities()

        val widget = bindWidget()
        updateAll()

        assertRenders(widget, "Milan")
    }

    @Test
    fun `a pinned widget keeps its city while its unpinned neighbour follows the app`() {
        seedTwoCities()

        val follower = bindWidget()
        val pinned = bindWidget()
        pin(pinned, turin.id)
        updateAll()

        // The whole point of the feature: one render pass, two cities, same provider
        assertRenders(pinned, "Turin")
        assertRenders(follower, "Milan")
    }

    @Test
    fun `a widget pinned to the gps source shows the persisted fix`() {
        seedTwoCities()
        runBlocking {
            seedHistory(gpsCity, "Florence, Tuscany", "33.0")
            cityStore.setUseGps(true) // enabling also selects it, so re-select Milan
            cityStore.updateGpsCity(gpsCity)
            cityStore.setActive(milan)
        }

        val follower = bindWidget()
        val pinned = bindWidget()
        pin(pinned, GpsCityId)
        updateAll()

        assertRenders(pinned, "Florence")
        assertRenders(follower, "Milan")
    }

    @Test
    fun `a pin to a city the user has since removed falls back to the active one`() {
        seedTwoCities()

        val widget = bindWidget()
        pin(widget, turin.id)
        runBlocking { cityStore.remove(turin) }
        updateAll()

        // Turin's history is still in Room, so a stale pin would visibly win here
        assertRenders(widget, "Milan")
    }

    @Test
    fun `updateAll with nothing placed does nothing at all`() {
        seedTwoCities()
        assertEquals(
            0,
            AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, TweatherWidgetProvider::class.java)
            ).size
        )
        // The tripwire: a render pass reads settings and queries Room, and this database
        // is closed — the call can only survive by returning before any of that.
        database.close()

        updateAll()
    }
}
