package com.callbackdev.tweather.ui.weather

import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.data.WindSpeedUnit
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
    fun `hourly rows carry rounded temps and start after the current hour`() {
        val first = json.getValue("hourly_forecast").jsonArray.first().jsonObject
        assertEquals(
            listOf("time", "temp_c", "status", "precip_chance"),
            first.keys.toList()
        )
        // The sample's 15:00 slot is the current hour: current_conditions' job,
        // not the table's (Fase 11f).
        assertEquals("16:00", first.getValue("time").jsonPrimitive.content)
        assertEquals("18", first.getValue("temp_c").jsonPrimitive.content)
    }

    @Test
    fun `daily rows use short english day names`() {
        val first = json.getValue("daily_forecast").jsonArray.first().jsonObject
        assertEquals("Mon", first.getValue("day").jsonPrimitive.content)
        assertEquals("20", first.getValue("high").jsonPrimitive.content)
    }

    @Test
    fun `present air quality and pollen render their sections`() {
        // Regression: the builder's put* return the key's previous value (null), so
        // a `?.let { put... } ?: put(key, JsonNull)` overwrote the section — the UI
        // showed air_quality/pollen_report as always null.
        val aq = json.getValue("air_quality").jsonObject
        assertEquals("42", aq.getValue("aqi_index").jsonPrimitive.content)
        assertEquals("Good ⚪", aq.getValue("status").jsonPrimitive.content)
        assertEquals("8.2", aq.getValue("pollutants").jsonObject.getValue("pm2_5").jsonPrimitive.content)
        val pollen = json.getValue("pollen_report").jsonObject
        assertEquals("High", pollen.getValue("tree").jsonPrimitive.content)
    }

    @Test
    fun `hidden details drop the technical fields`() {
        val compact = sampleWeatherReport()
            .toDisplayJson(options = DisplayOptions(showDetails = false))
        val location = compact.getValue("location").jsonObject
        assertEquals(listOf("city", "local_time"), location.keys.toList())
        val current = compact.getValue("current_conditions").jsonObject
        assertEquals(
            listOf(
                "status", "temp_c", "feels_like_c", "humidity_pct", "uv_index",
                "uv_description", "wind", "precipitation"
            ),
            current.keys.toList()
        )
        assertEquals(
            listOf("speed_kph", "direction"),
            current.getValue("wind").jsonObject.keys.toList()
        )
        assertEquals(
            listOf("aqi_index", "status"),
            compact.getValue("air_quality").jsonObject.keys.toList()
        )
    }

    @Test
    fun `imperial units convert values and rename keys`() {
        val imperial = sampleWeatherReport().toDisplayJson(
            options = DisplayOptions(
                temperature = TemperatureUnit.FAHRENHEIT,
                windSpeed = WindSpeedUnit.MPH
            )
        )
        val current = imperial.getValue("current_conditions").jsonObject
        assertEquals("65.3", current.getValue("temp_f").jsonPrimitive.content) // 18.5°C
        val wind = current.getValue("wind").jsonObject
        assertEquals("7.8", wind.getValue("speed_mph").jsonPrimitive.content) // 12.5 kph
        val firstHour = imperial.getValue("hourly_forecast").jsonArray.first().jsonObject
        assertEquals("64", firstHour.getValue("temp_f").jsonPrimitive.content) // 18°C, 16:00
    }

    @Test
    fun `missing air quality and pollen render as null`() {
        val bare = sampleWeatherReport().copy(airQuality = null, pollen = null).toDisplayJson()
        assertEquals(JsonNull, bare.getValue("air_quality"))
        assertEquals(JsonNull, bare.getValue("pollen_report"))
    }

    @Test
    fun `translate localizes data values and locale the day names`() {
        val translated = sampleWeatherReport().toDisplayJson(
            translate = { if (it == "Partly Cloudy") "Parzialmente nuvoloso" else it },
            locale = java.util.Locale.ITALIAN
        )
        val current = translated.getValue("current_conditions").jsonObject
        assertEquals(
            "Parzialmente nuvoloso ⛅",
            current.getValue("status").jsonPrimitive.content
        )
        // keys stay English (code), only values are localized
        assertEquals("temp_c", current.keys.toList()[1])
        val firstDay = translated.getValue("daily_forecast").jsonArray.first().jsonObject
        assertEquals("Lun", firstDay.getValue("day").jsonPrimitive.content)
    }
}
