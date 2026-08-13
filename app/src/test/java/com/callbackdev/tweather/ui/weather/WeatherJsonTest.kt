package com.callbackdev.tweather.ui.weather

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherJsonTest {

    private val json = sampleWeatherReport().toDisplayJson()

    @Test
    fun `sections follow the PRD order`() {
        assertEquals(
            listOf(
                "location", "current_conditions", "air_quality", "pollen_report",
                "astronomical", "hourly_forecast", "daily_forecast", "system_info"
            ),
            json.keys.toList()
        )
    }

    @Test
    fun `values are formatted like the full sample`() {
        val location = json.getValue("location").jsonObject
        assertEquals("2023-10-27 14:30", location.getValue("local_time").jsonPrimitive.content)

        val current = json.getValue("current_conditions").jsonObject
        assertEquals("Partly Cloudy ⛅", current.getValue("status").jsonPrimitive.content)
        assertEquals("18.5", current.getValue("temp_c").jsonPrimitive.content)
        assertEquals("NW", current.getValue("wind").jsonObject.getValue("direction").jsonPrimitive.content)

        val astro = json.getValue("astronomical").jsonObject
        assertEquals("07:12", astro.getValue("sunrise").jsonPrimitive.content)
        assertEquals("10h 52m", astro.getValue("daylight_duration").jsonPrimitive.content)
        assertEquals("Waxing Gibbous 🌔", astro.getValue("moon_phase").jsonPrimitive.content)

        val system = json.getValue("system_info").jsonObject
        assertEquals("\"1698413400\"", system.getValue("last_sync").toString()) // string, per sample
        assertEquals("HIT", system.getValue("cache_status").jsonPrimitive.content)
    }

    @Test
    fun `hourly rows carry rounded temps and clock times`() {
        val first = json.getValue("hourly_forecast").jsonArray.first().jsonObject
        assertEquals(
            listOf("time", "temp_c", "status", "precip_chance"),
            first.keys.toList()
        )
        assertEquals("15:00", first.getValue("time").jsonPrimitive.content)
        assertEquals("19", first.getValue("temp_c").jsonPrimitive.content)
    }

    @Test
    fun `daily rows use short english day names`() {
        val first = json.getValue("daily_forecast").jsonArray.first().jsonObject
        assertEquals("Mon", first.getValue("day").jsonPrimitive.content)
        assertEquals("20", first.getValue("high").jsonPrimitive.content)
    }

    @Test
    fun `missing air quality and pollen render as null`() {
        val bare = sampleWeatherReport().copy(airQuality = null, pollen = null).toDisplayJson()
        assertEquals(JsonNull, bare.getValue("air_quality"))
        assertEquals(JsonNull, bare.getValue("pollen_report"))
    }
}
