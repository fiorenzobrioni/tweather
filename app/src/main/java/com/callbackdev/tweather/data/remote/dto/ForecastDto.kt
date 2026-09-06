package com.callbackdev.tweather.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForecastResponseDto(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val current: CurrentDto,
    val hourly: HourlyDto,
    val daily: DailyDto
)

@Serializable
data class CurrentDto(
    val time: String,
    @SerialName("temperature_2m") val temperatureC: Double,
    @SerialName("relative_humidity_2m") val humidityPct: Int,
    @SerialName("apparent_temperature") val apparentTemperatureC: Double,
    @SerialName("dew_point_2m") val dewPointC: Double,
    @SerialName("is_day") val isDay: Int,
    @SerialName("precipitation") val precipitationMm: Double,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("pressure_msl") val pressureMslHpa: Double,
    @SerialName("wind_speed_10m") val windSpeedKph: Double,
    @SerialName("wind_direction_10m") val windDirectionDeg: Int,
    @SerialName("wind_gusts_10m") val windGustsKph: Double,
    /**
     * Nullable since Fase 26, like [HourlyDto]'s. Never seen absent — probed on twelve
     * places including McMurdo, mid-Pacific, Everest and Svalbard — but `visibility`
     * is a model-dependent field, and a non-nullable one here means a model that stops
     * carrying it fails the WHOLE fetch, current block, forecast and all. The hourly
     * DTO has always tolerated it; there was no reason for the two to disagree.
     */
    @SerialName("visibility") val visibilityM: Double? = null,
    @SerialName("cloud_cover") val cloudCoverPct: Int,
    @SerialName("uv_index") val uvIndex: Double
)

@Serializable
data class HourlyDto(
    val time: List<String>,
    @SerialName("temperature_2m") val temperatureC: List<Double>,
    @SerialName("weather_code") val weatherCode: List<Int>,
    /**
     * Millimetres in the hour. Defaulted so a `ReportDiskCache` entry written before
     * Fase 26 still deserializes: an offline phone must not lose its week of forecast
     * to an app update, and the daily code degrades to its hour-count rule when the
     * amounts are not there.
     */
    @SerialName("precipitation") val precipitationMm: List<Double> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbabilityPct: List<Int?>,
    @SerialName("is_day") val isDay: List<Int>,
    @SerialName("visibility") val visibilityM: List<Double?>,
    @SerialName("cloud_cover") val cloudCoverPct: List<Int>
)

@Serializable
data class DailyDto(
    val time: List<String>,
    @SerialName("weather_code") val weatherCode: List<Int>,
    @SerialName("temperature_2m_max") val temperatureMaxC: List<Double>,
    @SerialName("temperature_2m_min") val temperatureMinC: List<Double>,
    val sunrise: List<String>,
    val sunset: List<String>,
    @SerialName("daylight_duration") val daylightDurationSec: List<Double>,
    @SerialName("precipitation_probability_max") val precipitationProbabilityMaxPct: List<Int?>,
    @SerialName("uv_index_max") val uvIndexMax: List<Double>
)
