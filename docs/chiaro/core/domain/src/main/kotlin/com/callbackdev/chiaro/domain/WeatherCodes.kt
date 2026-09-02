package com.callbackdev.chiaro.domain

import com.callbackdev.chiaro.domain.model.PollenLevel
import com.callbackdev.chiaro.domain.model.WeatherCondition

/**
 * Single source of truth for turning raw Open-Meteo values into the labeled,
 * emoji-decorated strings the editor UI renders (icons are Unicode emoji, per the
 * design system — no image assets).
 */
object WeatherCodes {

    /** WMO weather interpretation code (Open-Meteo `weather_code`) → condition. */
    fun condition(wmoCode: Int, isDay: Boolean): WeatherCondition {
        val (description, emoji) = when (wmoCode) {
            0 -> if (isDay) "Clear" to "☀️" else "Clear" to "🌙"
            1 -> if (isDay) "Mainly Clear" to "🌤️" else "Mainly Clear" to "🌙"
            2 -> "Partly Cloudy" to "⛅"
            3 -> "Overcast" to "☁️"
            45, 48 -> "Foggy" to "🌫️"
            51, 53, 55 -> "Drizzle" to "🌦️"
            56, 57 -> "Freezing Drizzle" to "🌧️"
            61 -> "Light Rain" to "🌧️"
            63 -> "Rainy" to "🌧️"
            65 -> "Heavy Rain" to "🌧️"
            66, 67 -> "Freezing Rain" to "🌧️"
            71 -> "Light Snow" to "🌨️"
            73 -> "Snowy" to "🌨️"
            75 -> "Heavy Snow" to "❄️"
            77 -> "Snow Grains" to "❄️"
            80, 81 -> "Rain Showers" to "🌦️"
            82 -> "Violent Showers" to "🌧️"
            85, 86 -> "Snow Showers" to "🌨️"
            95 -> "Thunderstorm" to "⛈️"
            96, 99 -> "Thunderstorm w/ Hail" to "⛈️"
            else -> "Unknown" to "❓"
        }
        return WeatherCondition(wmoCode, description, emoji)
    }

    /** UV index → descriptive label, e.g. `"Moderate ☀️"` like the sample. */
    fun uvDescription(uvIndex: Int): String = when {
        uvIndex <= 2 -> "Low"
        uvIndex <= 5 -> "Moderate ☀️"
        uvIndex <= 7 -> "High ☀️"
        uvIndex <= 10 -> "Very High ☀️"
        else -> "Extreme ☀️"
    }

    /** US AQI → status label (sample: 42 → `"Good ⚪"`). */
    fun usAqiStatus(aqi: Int): String = when {
        aqi <= 50 -> "Good ⚪"
        aqi <= 100 -> "Moderate 🟡"
        aqi <= 150 -> "Unhealthy for Sensitive Groups 🟠"
        aqi <= 200 -> "Unhealthy 🔴"
        aqi <= 300 -> "Very Unhealthy 🟣"
        else -> "Hazardous 🟤"
    }

    private val COMPASS_POINTS = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    )

    /** Wind direction in degrees → 16-point compass label (310 → `"NW"`). */
    fun windCompass(degree: Int): String {
        val normalized = ((degree % 360) + 360) % 360
        val index = ((normalized + 11.25) / 22.5).toInt() % COMPASS_POINTS.size
        return COMPASS_POINTS[index]
    }

    /**
     * Pollen concentration (grains/m³) → coarse level; null in, null out (Open-Meteo
     * pollen is Europe-only).
     */
    fun pollenLevel(grainsPerM3: Double?): PollenLevel? = when {
        grainsPerM3 == null -> null
        grainsPerM3 < 1.0 -> PollenLevel.NONE
        grainsPerM3 < 30.0 -> PollenLevel.LOW
        grainsPerM3 < 100.0 -> PollenLevel.MODERATE
        else -> PollenLevel.HIGH
    }
}
