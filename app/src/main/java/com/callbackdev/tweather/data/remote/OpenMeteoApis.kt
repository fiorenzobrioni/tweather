package com.callbackdev.tweather.data.remote

import com.callbackdev.tweather.data.remote.dto.AirQualityResponseDto
import com.callbackdev.tweather.data.remote.dto.ForecastResponseDto
import com.callbackdev.tweather.data.remote.dto.GeocodingResponseDto
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
        @Query("forecast_days") forecastDays: Int = 7
    ): ForecastResponseDto

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
        const val CURRENT_VARIABLES =
            "temperature_2m,relative_humidity_2m,apparent_temperature,dew_point_2m," +
                "is_day,precipitation,weather_code,pressure_msl,wind_speed_10m," +
                "wind_direction_10m,wind_gusts_10m,visibility,cloud_cover,uv_index"
        // visibility + cloud_cover are not displayed: they repair `weather_code`, whose
        // fog is unreliable in both directions (Fase 13c) — see WeatherReportMapper.
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
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): GeocodingResponseDto

    companion object {
        const val BASE_URL = "https://geocoding-api.open-meteo.com/"
    }
}
