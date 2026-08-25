package com.callbackdev.tweather.data

import com.callbackdev.tweather.data.local.WeatherHistoryDao
import com.callbackdev.tweather.data.local.WeatherHistoryEntry
import com.callbackdev.tweather.data.remote.OpenMeteoAirQualityApi
import com.callbackdev.tweather.data.remote.OpenMeteoForecastApi
import com.callbackdev.tweather.data.remote.OpenMeteoGeocodingApi
import com.callbackdev.tweather.data.remote.dto.GeoResultDto
import com.callbackdev.tweather.data.remote.dto.GeocodingResponseDto
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Fase 13f. `language` is not a display setting on Open-Meteo's geocoding service: it
 * also picks the index the query is matched against, so the hardcoded `en` made an
 * Italian phone spell its own cities in English — "Firenze" came back with the hamlet
 * Firenze Nova alone, "Napoli" with five places that are not Naples.
 */
class SearchLanguageTest {

    private class FakeGeocodingApi : OpenMeteoGeocodingApi {
        var lastLanguage: String? = null
            private set

        override suspend fun search(
            name: String,
            language: String,
            count: Int,
            format: String
        ): GeocodingResponseDto {
            lastLanguage = language
            return GeocodingResponseDto(
                listOf(
                    GeoResultDto(
                        id = 3_176_959,
                        name = "Firenze",
                        latitude = 43.77925,
                        longitude = 11.24626,
                        country = "Italia",
                        countryCode = "IT",
                        admin1 = "Toscana",
                        timezone = "Europe/Rome"
                    )
                )
            )
        }
    }

    /** searchCities never reaches the history; the fetch paths have their own tests. */
    private object UnusedHistoryDao : WeatherHistoryDao {
        override suspend fun insert(entry: WeatherHistoryEntry): Long = 0
        override suspend fun historyFor(cityKey: String, limit: Int) = emptyList<WeatherHistoryEntry>()
        override fun observeLatest(limit: Int): Flow<List<WeatherHistoryEntry>> = emptyFlow()
        override suspend fun prune(keep: Int) = Unit
        override suspend fun setFiredRulesOnLatest(cityKey: String, firedRulesJson: String) = Unit
    }

    private val defaultLocale: Locale = Locale.getDefault()
    private val geocodingApi = FakeGeocodingApi()

    // Never called here, but the constructor wants them: dead URL, like every other test
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://127.0.0.1:1/")
        .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
        .build()

    private fun repository() = WeatherRepository(
        forecastApi = retrofit.create(OpenMeteoForecastApi::class.java),
        airQualityApi = retrofit.create(OpenMeteoAirQualityApi::class.java),
        geocodingApi = geocodingApi,
        historyDao = UnusedHistoryDao
    )

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `the query is matched against the index of the device language`() = runBlocking {
        Locale.setDefault(Locale.ITALY)

        repository().searchCities("Firenze")

        assertEquals("it", geocodingApi.lastLanguage)
    }

    /**
     * Resolved per call, not per instance: the system per-app language picker can
     * change the language while the process (and this repository) lives on.
     */
    @Test
    fun `a language change reaches the next search`() = runBlocking {
        val repository = repository()
        Locale.setDefault(Locale.ITALY)
        repository.searchCities("Firenze")
        assertEquals("it", geocodingApi.lastLanguage)

        Locale.setDefault(Locale.US)
        repository.searchCities("Florence")

        assertEquals("en", geocodingApi.lastLanguage)
    }

    @Test
    fun `the locale gives its language, region and all, and English when it has none`() {
        assertEquals("it", OpenMeteoGeocodingApi.languageOf(Locale.ITALY))
        assertEquals("en", OpenMeteoGeocodingApi.languageOf(Locale.UK))
        assertEquals("fr", OpenMeteoGeocodingApi.languageOf(Locale.CANADA_FRENCH))
        // Unsupported codes are not filtered out: Open-Meteo falls back to English
        // server-side, and a list kept here would only date the app.
        assertEquals("sv", OpenMeteoGeocodingApi.languageOf(Locale.forLanguageTag("sv-SE")))
        assertEquals(
            OpenMeteoGeocodingApi.DEFAULT_LANGUAGE,
            OpenMeteoGeocodingApi.languageOf(Locale.ROOT)
        )
    }
}
