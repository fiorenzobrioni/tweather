package com.callbackdev.tweather.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Room
import com.callbackdev.tweather.BuildConfig
import com.callbackdev.tweather.data.local.ReportDiskCache
import com.callbackdev.tweather.data.local.TweatherDatabase
import java.io.File
import com.callbackdev.tweather.data.remote.OpenMeteoAirQualityApi
import com.callbackdev.tweather.data.remote.OpenMeteoForecastApi
import com.callbackdev.tweather.data.remote.OpenMeteoGeocodingApi
import com.callbackdev.tweather.widget.TweatherWidgetUpdater
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
        skySubscriptionStore: SkySubscriptionStore? = null
    ) {
        this.repository = repository
        this.cityStore = cityStore
        this.settingsStore = settingsStore
        this.alertStateStore = alertStateStore
        this.widgetCityStore = widgetCityStore
        this.ruleStore = ruleStore
        this.ruleStateStore = ruleStateStore
        this.skySubscriptionStore = skySubscriptionStore
    }

    /**
     * Sent on every API call. Open-Meteo doesn't require it, but rate-limits per IP
     * and reserves the right to block anonymous misbehaving traffic without notice:
     * a named agent with a contact URL turns "block" into "reach out".
     */
    private val userAgent =
        "tweather/${BuildConfig.VERSION_NAME} (+https://github.com/fiorenzobrioni/tweather)"

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
            TweatherDatabase::class.java,
            "tweather.db"
        )
            .addMigrations(TweatherDatabase.MIGRATION_1_2, TweatherDatabase.MIGRATION_2_3)
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
            // Every fetch that commits new data repaints the home widget, so it
            // needs no polling of its own (no-op when no widget is placed)
            onHistoryCommitted = { TweatherWidgetUpdater.updateAll(appContext) }
        )
    }
}
