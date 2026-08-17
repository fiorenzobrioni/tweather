package com.callbackdev.tweather.ui.weather

import android.content.res.Resources
import com.callbackdev.tweather.R

/**
 * Localizes the user-facing weather DATA values (conditions, UV, AQI, pollen, moon
 * phases) by mapping the domain's canonical English strings to resources. The domain
 * and the Room history snapshots stay English on purpose — keys/code are English by
 * design, and diffs must not churn when the device language changes. Unknown strings
 * pass through unchanged.
 */
object WeatherTranslations {

    private val byEnglish = mapOf(
        // WMO condition descriptions (WeatherCodes.condition)
        "Clear" to R.string.cond_clear,
        "Mainly Clear" to R.string.cond_mainly_clear,
        "Partly Cloudy" to R.string.cond_partly_cloudy,
        "Overcast" to R.string.cond_overcast,
        "Foggy" to R.string.cond_foggy,
        "Drizzle" to R.string.cond_drizzle,
        "Freezing Drizzle" to R.string.cond_freezing_drizzle,
        "Light Rain" to R.string.cond_light_rain,
        "Rainy" to R.string.cond_rainy,
        "Heavy Rain" to R.string.cond_heavy_rain,
        "Freezing Rain" to R.string.cond_freezing_rain,
        "Light Snow" to R.string.cond_light_snow,
        "Snowy" to R.string.cond_snowy,
        "Heavy Snow" to R.string.cond_heavy_snow,
        "Snow Grains" to R.string.cond_snow_grains,
        "Rain Showers" to R.string.cond_rain_showers,
        "Violent Showers" to R.string.cond_violent_showers,
        "Snow Showers" to R.string.cond_snow_showers,
        "Thunderstorm" to R.string.cond_thunderstorm,
        "Thunderstorm w/ Hail" to R.string.cond_thunderstorm_hail,
        "Unknown" to R.string.cond_unknown,
        // UV descriptions (WeatherCodes.uvDescription, emoji included)
        "Low" to R.string.uv_low,
        "Moderate ☀️" to R.string.uv_moderate,
        "High ☀️" to R.string.uv_high,
        "Very High ☀️" to R.string.uv_very_high,
        "Extreme ☀️" to R.string.uv_extreme,
        // US AQI statuses (WeatherCodes.usAqiStatus)
        "Good ⚪" to R.string.aqi_good,
        "Moderate 🟡" to R.string.aqi_moderate,
        "Unhealthy for Sensitive Groups 🟠" to R.string.aqi_sensitive,
        "Unhealthy 🔴" to R.string.aqi_unhealthy,
        "Very Unhealthy 🟣" to R.string.aqi_very_unhealthy,
        "Hazardous 🟤" to R.string.aqi_hazardous,
        // Pollen levels (PollenLevel.label); "Low" is shared with the UV entry above
        // (same translation), the emoji-less "Moderate"/"High" are pollen-only
        "None" to R.string.pollen_none,
        "Moderate" to R.string.pollen_moderate,
        "High" to R.string.pollen_high,
        // Moon phases (MoonPhase.label)
        "New Moon" to R.string.moon_new,
        "Waxing Crescent" to R.string.moon_waxing_crescent,
        "First Quarter" to R.string.moon_first_quarter,
        "Waxing Gibbous" to R.string.moon_waxing_gibbous,
        "Full Moon" to R.string.moon_full,
        "Waning Gibbous" to R.string.moon_waning_gibbous,
        "Last Quarter" to R.string.moon_last_quarter,
        "Waning Crescent" to R.string.moon_waning_crescent
    )

    /** Translator closing over [resources]; identity for unmapped strings. */
    fun translator(resources: Resources): (String) -> String = { english ->
        byEnglish[english]?.let(resources::getString) ?: english
    }

    /**
     * Like [translator], but tolerant of the rendered `"<description> <emoji>"` form
     * the history snapshots store (`"Overcast ☁️"`, `"Waxing Gibbous 🌔"`): a string
     * with no direct mapping is retried without its trailing emoji token, which is
     * re-appended untouched. Same defensive last-token test as the widget: if it
     * reads as a word (ASCII letters), there is no emoji to split off.
     */
    fun valueTranslator(resources: Resources): (String) -> String {
        val translate = translator(resources)
        return { value ->
            val direct = translate(value)
            if (direct != value) {
                direct
            } else {
                val last = value.substringAfterLast(' ')
                if (last == value || last.any { it in 'A'..'Z' || it in 'a'..'z' }) value
                else "${translate(value.substringBeforeLast(' '))} $last"
            }
        }
    }
}
