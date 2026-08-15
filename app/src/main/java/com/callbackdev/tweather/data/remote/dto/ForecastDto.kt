package com.callbackdev.tweather.data.remote.dto

import com.callbackdev.tweather.data.remote.OpenMeteoForecastApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * `hourly`/`daily` are kept as raw [JsonObject] rather than a fixed set of properties:
 * requesting multiple `models` (see [OpenMeteoForecastApi.MODEL_PRIORITY]) makes
 * Open-Meteo suffix every field per model (`temperature_2m_best_match`,
 * `temperature_2m_italia_meteo_arpae_icon_2i`, ...) instead of one fixed key set. The
 * `mergedDoubles`/`mergedInts`/etc. helpers below read across those suffixed fields,
 * per hour/day, preferring the first model in priority order that has a non-null value.
 */
@Serializable
data class ForecastResponseDto(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val hourly: JsonObject,
    val daily: JsonObject
)

/** The unsuffixed `time` array shared by every model in a block. */
fun JsonObject.timeSeries(): List<String> =
    (this["time"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()

private fun JsonObject.mergedElementAt(variable: String, index: Int): JsonElement? {
    for (model in OpenMeteoForecastApi.MODEL_PRIORITY) {
        val value = (this["${variable}_$model"] as? JsonArray)?.getOrNull(index)
        if (value != null && value != JsonNull) return value
    }
    return null
}

fun JsonObject.mergedDoubles(variable: String, size: Int): List<Double> =
    (0 until size).map { mergedElementAt(variable, it)?.jsonPrimitive?.doubleOrNull ?: 0.0 }

fun JsonObject.mergedInts(variable: String, size: Int): List<Int> =
    (0 until size).map { mergedElementAt(variable, it)?.jsonPrimitive?.intOrNull ?: 0 }

fun JsonObject.mergedNullableInts(variable: String, size: Int): List<Int?> =
    (0 until size).map { mergedElementAt(variable, it)?.jsonPrimitive?.intOrNull }

fun JsonObject.mergedStrings(variable: String, size: Int): List<String> =
    (0 until size).map { mergedElementAt(variable, it)?.jsonPrimitive?.contentOrNull ?: "" }

/**
 * `weather_code` is deliberately read from best_match only, never merged with a local
 * model: it's a categorical read of cloud cover / precipitation type, which is far more
 * volatile hour-to-hour than a continuous field like temperature, and a single raw
 * high-res model's cell can disagree sharply with reality at a given instant. Verified
 * live for Milan: italia_meteo_arpae_icon_2i reported "overcast" (weather_code 3, 100%
 * cloud cover) while best_match reported "partly cloudy" (weather_code 2, 90%) for the
 * same hour, and best_match already blends multiple sources for exactly this kind of
 * field rather than trusting one raw model run.
 */
fun JsonObject.bestMatchInts(variable: String, size: Int): List<Int> {
    val values = this["${variable}_${OpenMeteoForecastApi.PRIMARY_MODEL}"] as? JsonArray
    return (0 until size).map { values?.getOrNull(it)?.jsonPrimitive?.intOrNull ?: 0 }
}
