package com.callbackdev.tweather.data.remote.dto

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The multi-model merge is the point of requesting `models=best_match,<local high-res
 * models>` (see OpenMeteoForecastApi.MODEL_PRIORITY): a local model's value should win
 * whenever it's present, falling back per index to best_match otherwise.
 */
class ForecastDtoMergeTest {

    @Test
    fun `local high-res model value wins over best_match when both present`() {
        val obj = buildJsonObject {
            put("time", JsonArray(listOf(JsonPrimitive("t0"), JsonPrimitive("t1"))))
            put(
                "temperature_2m_best_match",
                JsonArray(listOf(JsonPrimitive(20.0), JsonPrimitive(21.0)))
            )
            put(
                "temperature_2m_italia_meteo_arpae_icon_2i",
                JsonArray(listOf(JsonPrimitive(19.5), JsonPrimitive(20.5)))
            )
        }
        assertEquals(listOf(19.5, 20.5), obj.mergedDoubles("temperature_2m", 2))
    }

    @Test
    fun `null local value falls back to best_match for that index only`() {
        val obj = buildJsonObject {
            put(
                "temperature_2m_best_match",
                JsonArray(listOf(JsonPrimitive(20.0), JsonPrimitive(21.0), JsonPrimitive(22.0)))
            )
            // Beyond the local model's 3-day forecast horizon, Open-Meteo nulls out
            // the remaining slots rather than shortening the array (verified live).
            put(
                "temperature_2m_italia_meteo_arpae_icon_2i",
                JsonArray(listOf(JsonPrimitive(19.5), JsonNull, JsonNull))
            )
        }
        assertEquals(listOf(19.5, 21.0, 22.0), obj.mergedDoubles("temperature_2m", 3))
    }

    @Test
    fun `field entirely absent for a model falls back to best_match`() {
        // Outside a local model's geographic domain, Open-Meteo omits its fields
        // entirely rather than returning nulls (verified live against Paris).
        val obj = buildJsonObject {
            put(
                "temperature_2m_best_match",
                JsonArray(listOf(JsonPrimitive(20.0)))
            )
        }
        assertEquals(listOf(20.0), obj.mergedDoubles("temperature_2m", 1))
    }

    @Test
    fun `priority order picks the first local model with data over later ones`() {
        // meteofrance_arome_france_hd sits earlier than icon_d2 in MODEL_PRIORITY.
        val obj = buildJsonObject {
            put("temperature_2m_best_match", JsonArray(listOf(JsonPrimitive(20.0))))
            put(
                "temperature_2m_meteofrance_arome_france_hd",
                JsonArray(listOf(JsonPrimitive(18.0)))
            )
            put("temperature_2m_icon_d2", JsonArray(listOf(JsonPrimitive(19.0))))
        }
        assertEquals(listOf(18.0), obj.mergedDoubles("temperature_2m", 1))
    }

    @Test
    fun `mergedNullableInts preserves a genuine null when no model has data`() {
        val obj = buildJsonObject {
            put(
                "precipitation_probability_best_match",
                JsonArray(listOf(JsonNull, JsonPrimitive(40)))
            )
        }
        assertEquals(listOf(null, 40), obj.mergedNullableInts("precipitation_probability", 2))
    }

    @Test
    fun `bestMatchInts ignores a disagreeing local model, unlike mergedInts`() {
        // weather_code is deliberately never merged with a local model (see
        // ForecastDto.kt): a single raw high-res run can disagree sharply with reality
        // on a categorical, fast-changing field like cloud cover.
        val obj = buildJsonObject {
            put("weather_code_best_match", JsonArray(listOf(JsonPrimitive(2))))
            put(
                "weather_code_italia_meteo_arpae_icon_2i",
                JsonArray(listOf(JsonPrimitive(3)))
            )
        }
        assertEquals(listOf(2), obj.bestMatchInts("weather_code", 1))
        assertEquals(listOf(3), obj.mergedInts("weather_code", 1)) // what mergedInts would pick instead
    }

    @Test
    fun `timeSeries reads the unsuffixed shared time array`() {
        val obj = buildJsonObject {
            put("time", JsonArray(listOf(JsonPrimitive("2026-08-13T00:00"), JsonPrimitive("2026-08-13T01:00"))))
        }
        assertEquals(listOf("2026-08-13T00:00", "2026-08-13T01:00"), obj.timeSeries())
    }
}
