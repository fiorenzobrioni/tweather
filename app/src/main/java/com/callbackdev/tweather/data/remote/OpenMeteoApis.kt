package com.callbackdev.tweather.data.remote

import com.callbackdev.tweather.data.remote.dto.AirQualityResponseDto
import com.callbackdev.tweather.data.remote.dto.ForecastResponseDto
import com.callbackdev.tweather.data.remote.dto.GeocodingResponseDto
import java.util.Locale
import retrofit2.http.GET
import retrofit2.http.Query

// Open-Meteo (https://open-meteo.com): free, no API key. The three services live on
// separate hosts, hence three Retrofit instances sharing one OkHttp client.

interface OpenMeteoForecastApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_VARIABLES,
        @Query("hourly") hourly: String = HOURLY_VARIABLES,
        @Query("daily") daily: String = DAILY_VARIABLES,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = FORECAST_DAYS
    ): ForecastResponseDto

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"

        /**
         * Days of forecast requested, and therefore `× 24` hourly values returned.
         * A constant since Fase 16a because the mapper's hourly window is derived
         * from it: the two used to be a 7 here and a 25 there, which is how the app
         * ended up parsing a week of hours and keeping one day of them.
         */
        const val FORECAST_DAYS = 7
        const val CURRENT_VARIABLES =
            "temperature_2m,relative_humidity_2m,apparent_temperature,dew_point_2m," +
                "is_day,precipitation,weather_code,pressure_msl,wind_speed_10m," +
                "wind_direction_10m,wind_gusts_10m,visibility,cloud_cover,uv_index"
        // visibility + cloud_cover are never displayed: they repair `weather_code`,
        // whose fog is unreliable in both directions (Fase 13c) — see
        // WeatherReportMapper. Hourly cloud_cover has a second reader since Fase 16a:
        // it is carried into the domain for the sky module's verdicts. Still not a
        // rendered field, and still fetched for the same price as before.
        const val HOURLY_VARIABLES =
            "temperature_2m,weather_code,precipitation_probability,is_day," +
                "visibility,cloud_cover"
        const val DAILY_VARIABLES =
            "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset," +
                "daylight_duration,precipitation_probability_max,uv_index_max"
    }
}

interface OpenMeteoAirQualityApi {
    @GET("v1/air-quality")
    suspend fun current(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_VARIABLES,
        @Query("timezone") timezone: String = "auto"
    ): AirQualityResponseDto

    companion object {
        const val BASE_URL = "https://air-quality-api.open-meteo.com/"
        const val CURRENT_VARIABLES =
            "us_aqi,pm2_5,pm10,ozone,nitrogen_dioxide,sulphur_dioxide,carbon_monoxide," +
                "grass_pollen,birch_pollen,alder_pollen,olive_pollen,ragweed_pollen," +
                "mugwort_pollen"
    }
}

interface OpenMeteoGeocodingApi {
    /**
     * [language] is not a display setting: it also picks the index the query is matched
     * against, so it decides what the user can even find. Hardcoded to `en` until Fase
     * 13f, which is why an Italian phone had to spell its own cities in English —
     * "Firenze" returned only the hamlet Firenze Nova and "Napoli" five places that are
     * not Naples. It is passed per call by `WeatherRepository.searchCities` and has no
     * default here on purpose: a caller cannot fall back to English by distraction.
     */
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("language") language: String,
        @Query("count") count: Int = 10,
        @Query("format") format: String = "json"
    ): GeocodingResponseDto

    companion object {
        const val BASE_URL = "https://geocoding-api.open-meteo.com/"

        /** What a locale with no language of its own searches in. */
        const val DEFAULT_LANGUAGE = "en"

        /**
         * The ISO code [search] wants for [locale]. Handed over as it is: Open-Meteo
         * supports nine languages and falls back to English server-side for anything
         * else, so filtering here would only date the app the day it supports a tenth.
         */
        fun languageOf(locale: Locale): String =
            locale.language.lowercase(Locale.ROOT).ifBlank { DEFAULT_LANGUAGE }
    }
}
