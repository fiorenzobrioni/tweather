package com.callbackdev.tweather.data

import android.content.Context
import androidx.room.Room
import com.callbackdev.tweather.BuildConfig
import com.callbackdev.tweather.data.local.TweatherDatabase
import com.callbackdev.tweather.data.remote.OpenMeteoAirQualityApi
import com.callbackdev.tweather.data.remote.OpenMeteoForecastApi
import com.callbackdev.tweather.data.remote.OpenMeteoGeocodingApi
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

    private fun build(appContext: Context): WeatherRepository {
        val okHttp = OkHttpClient.Builder()
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
        ).build()

        return WeatherRepository(
            forecastApi = retrofit(OpenMeteoForecastApi.BASE_URL)
                .create(OpenMeteoForecastApi::class.java),
            airQualityApi = retrofit(OpenMeteoAirQualityApi.BASE_URL)
                .create(OpenMeteoAirQualityApi::class.java),
            geocodingApi = retrofit(OpenMeteoGeocodingApi.BASE_URL)
                .create(OpenMeteoGeocodingApi::class.java),
            historyDao = database.weatherHistoryDao(),
            json = json
        )
    }
}
