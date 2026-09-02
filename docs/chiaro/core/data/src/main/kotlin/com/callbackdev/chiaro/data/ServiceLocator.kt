package com.callbackdev.chiaro.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Room
import com.callbackdev.chiaro.core.data.BuildConfig
import com.callbackdev.chiaro.data.local.ReportDiskCache
import com.callbackdev.chiaro.data.local.ChiaroDatabase
import java.io.File
import com.callbackdev.chiaro.data.remote.OpenMeteoAirQualityApi
import com.callbackdev.chiaro.data.remote.OpenMeteoForecastApi
import com.callbackdev.chiaro.data.remote.OpenMeteoGeocodingApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Hand-rolled DI: the app is small enough that a lazy singleton graph beats a Hilt
 * setup (decision recorded in PLANNING.md Fase 3). ViewModels grab the repository
 * via [weatherRepository] from Fase 4 on.
 */
object ServiceLocator {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var repository: WeatherRepository? = null

    @Volatile
    private var cityStore: CityStore? = null

    @Volatile
    private var searchHistoryStore: SearchHistoryStore? = null

    @Volatile
    private var settingsStore: SettingsStore? = null

    @Volatile
    private var locationProvider: LocationProvider? = null

    @Volatile
    private var alertStateStore: AlertStateStore? = null

    @Volatile
    private var widgetCityStore: WidgetCityStore? = null

    @Volatile
    private var workspaceStore: WorkspaceStore? = null

    @Volatile
    private var ruleStore: RuleStore? = null

    @Volatile
    private var ruleStateStore: RuleStateStore? = null

    @Volatile
    private var skySubscriptionStore: SkySubscriptionStore? = null

    @Volatile
    private var skyAlertStateStore: SkyAlertStateStore? = null

    fun weatherRepository(context: Context): WeatherRepository =
        repository ?: synchronized(this) {
            repository ?: build(context.applicationContext).also { repository = it }
        }

    fun cityStore(context: Context): CityStore =
        cityStore ?: synchronized(this) {
            cityStore ?: CityStore.create(context.applicationContext, json)
                .also { cityStore = it }
        }

    fun searchHistoryStore(context: Context): SearchHistoryStore =
        searchHistoryStore ?: synchronized(this) {
            searchHistoryStore ?: SearchHistoryStore.create(context.applicationContext, json)
                .also { searchHistoryStore = it }
        }

    fun settingsStore(context: Context): SettingsStore =
        settingsStore ?: synchronized(this) {
            settingsStore ?: SettingsStore.create(context.applicationContext)
                .also { settingsStore = it }
        }

    fun locationProvider(context: Context): LocationProvider =
        locationProvider ?: synchronized(this) {
            locationProvider ?: AndroidLocationProvider(context.applicationContext)
                .also { locationProvider = it }
        }

    fun alertStateStore(context: Context): AlertStateStore =
        alertStateStore ?: synchronized(this) {
            alertStateStore ?: AlertStateStore.create(context.applicationContext)
                .also { alertStateStore = it }
        }

    fun widgetCityStore(context: Context): WidgetCityStore =
        widgetCityStore ?: synchronized(this) {
            widgetCityStore ?: WidgetCityStore.create(context.applicationContext)
                .also { widgetCityStore = it }
        }

    fun workspaceStore(context: Context): WorkspaceStore =
        workspaceStore ?: synchronized(this) {
            workspaceStore ?: WorkspaceStore.create(context.applicationContext)
                .also { workspaceStore = it }
        }

    fun ruleStore(context: Context): RuleStore =
        ruleStore ?: synchronized(this) {
            ruleStore ?: RuleStore.create(context.applicationContext, json)
                .also { ruleStore = it }
        }

    fun skySubscriptionStore(context: Context): SkySubscriptionStore =
        skySubscriptionStore ?: synchronized(this) {
            skySubscriptionStore ?: SkySubscriptionStore.create(context.applicationContext, json)
                .also { skySubscriptionStore = it }
        }

    fun skyAlertStateStore(context: Context): SkyAlertStateStore =
        skyAlertStateStore ?: synchronized(this) {
            skyAlertStateStore ?: SkyAlertStateStore.create(context.applicationContext)
                .also { skyAlertStateStore = it }
        }

    fun ruleStateStore(context: Context): RuleStateStore =
        ruleStateStore ?: synchronized(this) {
            ruleStateStore ?: RuleStateStore.create(context.applicationContext)
                .also { ruleStateStore = it }
        }

    /**
     * Test-only: workers resolve dependencies from here, so worker tests swap in
     * temp-file stores/fake repositories. Calling with no arguments resets to
     * lazy real instances (do it in @After — the object outlives the test).
     */
    @VisibleForTesting
    fun overrideForTests(
        repository: WeatherRepository? = null,
        cityStore: CityStore? = null,
        settingsStore: SettingsStore? = null,
        alertStateStore: AlertStateStore? = null,
        widgetCityStore: WidgetCityStore? = null,
        ruleStore: RuleStore? = null,
        ruleStateStore: RuleStateStore? = null,
        skySubscriptionStore: SkySubscriptionStore? = null,
        skyAlertStateStore: SkyAlertStateStore? = null
    ) {
        this.repository = repository
        this.cityStore = cityStore
        this.settingsStore = settingsStore
        this.alertStateStore = alertStateStore
        this.widgetCityStore = widgetCityStore
        this.ruleStore = ruleStore
        this.ruleStateStore = ruleStateStore
        this.skySubscriptionStore = skySubscriptionStore
        this.skyAlertStateStore = skyAlertStateStore
    }

    /**
     * Sent on every API call. Open-Meteo doesn't require it, but rate-limits per IP
     * and reserves the right to block anonymous misbehaving traffic without notice:
     * a named agent with a contact URL turns "block" into "reach out".
     *
     * Handed in by [install] rather than read off BuildConfig: this module is a
     * library and does not have the app's version, which is exactly the point of it
     * being a library.
     */
    @Volatile
    private var userAgent = "chiaro (+https://github.com/fiorenzobrioni/chiaro)"

    @Volatile
    private var historyListener: suspend () -> Unit = {}

    /**
     * Called once from `Application.onCreate`, before anything resolves a
     * dependency. Two things the data layer cannot know about itself: who it says it
     * is upstream, and who wants to hear that new data landed (the widget, in
     * practice). Calling it later still works and only affects the graph built after
     * it, which is why it is called first.
     */
    fun install(userAgent: String, onHistoryCommitted: suspend () -> Unit = {}) {
        this.userAgent = userAgent
        this.historyListener = onHistoryCommitted
    }

    private fun build(appContext: Context): WeatherRepository {
        val okHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", userAgent)
                        .build()
                )
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC)
                    )
                }
            }
            .build()

        fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val database = Room.databaseBuilder(
            appContext,
            ChiaroDatabase::class.java,
            "chiaro.db"
        )
            .addMigrations(
                ChiaroDatabase.MIGRATION_1_2,
                ChiaroDatabase.MIGRATION_2_3,
                ChiaroDatabase.MIGRATION_3_4
            )
            .build()

        return WeatherRepository(
            forecastApi = retrofit(OpenMeteoForecastApi.BASE_URL)
                .create(OpenMeteoForecastApi::class.java),
            airQualityApi = retrofit(OpenMeteoAirQualityApi.BASE_URL)
                .create(OpenMeteoAirQualityApi::class.java),
            geocodingApi = retrofit(OpenMeteoGeocodingApi.BASE_URL)
                .create(OpenMeteoGeocodingApi::class.java),
            historyDao = database.weatherHistoryDao(),
            // Survives process death so cold starts inside the TTL cost zero GETs
            diskCache = ReportDiskCache(File(appContext.filesDir, "report_cache"), json),
            json = json,
            // Every fetch that commits new data notifies whoever installed us — the
            // widget repaint, in the app. The data layer does not know that.
            onHistoryCommitted = { historyListener() }
        )
    }
}
