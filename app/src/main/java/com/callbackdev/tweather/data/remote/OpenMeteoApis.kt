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
        @Query("hourly") hourly: String = HOURLY_VARIABLES,
        @Query("daily") daily: String = DAILY_VARIABLES,
        @Query("models") models: String = MODELS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7
    ): ForecastResponseDto

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"

        // "current" conditions are derived from this same hourly series (nearest hour
        // to now) instead of the API's own `current` block, because that block does not
        // support multiple models — see MODEL_PRIORITY below.
        const val HOURLY_VARIABLES =
            "temperature_2m,relative_humidity_2m,apparent_temperature,dew_point_2m,is_day," +
                "precipitation,weather_code,pressure_msl,wind_speed_10m,wind_direction_10m," +
                "wind_gusts_10m,visibility,uv_index,precipitation_probability"
        const val DAILY_VARIABLES =
            "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset," +
                "daylight_duration,precipitation_probability_max,uv_index_max"

        // `models=best_match` alone quietly settles on one fixed regional default per
        // continent rather than genuinely picking the best model per location: verified
        // by direct API calls that it returns exactly icon_eu for Milan and exactly
        // icon_d2 for Paris, never the country's own high-resolution model (a known,
        // still-open behavior: github.com/open-meteo/open-meteo#214). Requesting
        // best_match alongside every national high-res model Open-Meteo publishes and
        // merging per field/per hour (see mergedDoubles/mergedInts in ForecastDto.kt)
        // picks up the ~1-3km local model where the coordinates fall inside its domain,
        // and silently falls back to best_match everywhere else and for variables/days
        // the local model doesn't cover (Open-Meteo omits those fields rather than
        // erroring), so this is a single request that is safe for any location globally.
        val LOCAL_HIGH_RES_MODELS = listOf(
            "italia_meteo_arpae_icon_2i", // Italy, ~2 km
            "meteofrance_arome_france_hd", // France, ~1 km
            "icon_d2", // Germany / Central Europe, ~2 km
            "ukmo_uk_deterministic_2km", // United Kingdom, ~2 km
            "meteoswiss_icon_ch1", // Switzerland, ~1 km
            "knmi_harmonie_arome_netherlands", // Netherlands, ~2 km
            "dmi_harmonie_arome_europe", // Denmark / Northern Europe, ~2 km
            "geosphere_arome_austria", // Austria, ~2.5 km
            "ncep_hrrr_conus", // Continental USA, ~3 km
            "gem_hrdps_continental", // Canada, ~2.5 km
            "jma_msm" // Japan, ~5 km
        )
        const val PRIMARY_MODEL = "best_match"

        // Lookup order for the per-field merge: local high-res models first (whichever
        // one actually covers the coordinates), best_match as the universal fallback.
        val MODEL_PRIORITY = LOCAL_HIGH_RES_MODELS + PRIMARY_MODEL
        val MODELS = MODEL_PRIORITY.joinToString(",")
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
