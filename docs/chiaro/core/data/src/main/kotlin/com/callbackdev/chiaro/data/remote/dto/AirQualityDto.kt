package com.callbackdev.chiaro.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AirQualityResponseDto(
    val current: AirQualityCurrentDto
)

// Everything nullable: coverage varies by region (pollen is Europe-only) and the
// report must degrade gracefully instead of failing to parse.
@Serializable
data class AirQualityCurrentDto(
    val time: String,
    @SerialName("us_aqi") val usAqi: Int? = null,
    @SerialName("pm2_5") val pm25: Double? = null,
    @SerialName("pm10") val pm10: Double? = null,
    @SerialName("ozone") val ozone: Double? = null,
    @SerialName("nitrogen_dioxide") val no2: Double? = null,
    @SerialName("sulphur_dioxide") val so2: Double? = null,
    @SerialName("carbon_monoxide") val co: Double? = null,
    @SerialName("grass_pollen") val grassPollen: Double? = null,
    @SerialName("birch_pollen") val birchPollen: Double? = null,
    @SerialName("alder_pollen") val alderPollen: Double? = null,
    @SerialName("olive_pollen") val olivePollen: Double? = null,
    @SerialName("ragweed_pollen") val ragweedPollen: Double? = null,
    @SerialName("mugwort_pollen") val mugwortPollen: Double? = null
)
