package com.callbackdev.chiaro.domain

import com.callbackdev.chiaro.domain.model.PollenLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherCodesTest {

    @Test
    fun `clear sky is sun by day and moon by night`() {
        assertEquals("Clear ☀️", WeatherCodes.condition(0, isDay = true).label)
        assertEquals("Clear 🌙", WeatherCodes.condition(0, isDay = false).label)
    }

    @Test
    fun `wmo codes map to sample-style labels`() {
        assertEquals("Partly Cloudy ⛅", WeatherCodes.condition(2, isDay = true).label)
        assertEquals("Overcast ☁️", WeatherCodes.condition(3, isDay = true).label)
        assertEquals("Rainy 🌧️", WeatherCodes.condition(63, isDay = true).label)
        assertEquals("Thunderstorm ⛈️", WeatherCodes.condition(95, isDay = false).label)
        assertEquals("Unknown ❓", WeatherCodes.condition(42, isDay = true).label)
    }

    @Test
    fun `wind degrees map to 16-point compass`() {
        assertEquals("N", WeatherCodes.windCompass(0))
        assertEquals("NW", WeatherCodes.windCompass(310)) // sample value
        assertEquals("E", WeatherCodes.windCompass(90))
        assertEquals("SSW", WeatherCodes.windCompass(202))
        assertEquals("N", WeatherCodes.windCompass(355))
        assertEquals("N", WeatherCodes.windCompass(360))
    }

    @Test
    fun `us aqi status matches sample`() {
        assertEquals("Good ⚪", WeatherCodes.usAqiStatus(42)) // sample value
        assertEquals("Moderate 🟡", WeatherCodes.usAqiStatus(56))
        assertEquals("Hazardous 🟤", WeatherCodes.usAqiStatus(400))
    }

    @Test
    fun `uv description matches sample`() {
        assertEquals("Moderate ☀️", WeatherCodes.uvDescription(4)) // sample value
        assertEquals("Low", WeatherCodes.uvDescription(1))
        assertEquals("Extreme ☀️", WeatherCodes.uvDescription(12))
    }

    @Test
    fun `pollen grains map to coarse levels`() {
        assertNull(WeatherCodes.pollenLevel(null))
        assertEquals(PollenLevel.NONE, WeatherCodes.pollenLevel(0.0))
        assertEquals(PollenLevel.LOW, WeatherCodes.pollenLevel(7.1))
        assertEquals(PollenLevel.MODERATE, WeatherCodes.pollenLevel(50.0))
        assertEquals(PollenLevel.HIGH, WeatherCodes.pollenLevel(250.0))
    }
}
