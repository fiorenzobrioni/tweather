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
    @SerialName("visibility") val visibilityM: Double,
    @SerialName("uv_index") val uvIndex: Double
)

@Serializable
data class HourlyDto(
    val time: List<String>,
    @SerialName("temperature_2m") val temperatureC: List<Double>,
    @SerialName("weather_code") val weatherCode: List<Int>,
    @SerialName("precipitation_probability") val precipitationProbabilityPct: List<Int?>,
    @SerialName("is_day") val isDay: List<Int>
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
