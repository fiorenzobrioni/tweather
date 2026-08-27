package com.callbackdev.tweather.ui.weather

import android.content.res.Resources
import com.callbackdev.tweather.R
import com.callbackdev.tweather.domain.WeatherException
import java.time.Duration

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

/**
 * What `README.md` says about ITSELF — the loading line, a failed refresh, data the
 * app could not update (Fase 17).
 *
 * The same facts `weather_data.json` prints as `// ERROR: net::ERR_INTERNET_DISCONNECTED`,
 * in a language. The JSON keeps the code because a JSON file IS code and the error
 * string is genuinely useful there; the README is the app's one prose surface, and
 * `net::ERR_INTERNET_DISCONNECTED` in the middle of it asks somebody who does not read
 * Chrome's dialect to learn it in order to be told the phone is offline.
 *
 * [WeatherException.terminalMessage] stays exactly as it is: it is what the other
 * three surfaces (JSON, `cities.json`, `settings.config`) render, and this is a
 * second reading of the same value, not a replacement.
 */
object WeatherStateProse {

    /** The failure as a sentence. Total over [WeatherException]. */
    fun error(resources: Resources, error: WeatherException): String = when (error) {
        is WeatherException.NoNetwork -> resources.getString(R.string.readme_err_offline)
        is WeatherException.ApiError -> resources.getString(R.string.readme_err_service, error.code)
        is WeatherException.CityNotFound -> resources.getString(R.string.readme_err_city)
        is WeatherException.LocationPermissionDenied ->
            resources.getString(R.string.readme_err_gps_permission)
        is WeatherException.LocationDisabled -> resources.getString(R.string.readme_err_gps_off)
        is WeatherException.LocationTimeout -> resources.getString(R.string.readme_err_gps_timeout)
        is WeatherException.LocationUnavailable ->
            resources.getString(R.string.readme_err_gps_unavailable)
        // Deliberately does NOT print the cause: `panic: unexpected error — …` is a
        // stack-trace fragment, which is exactly the register this file does not use.
        is WeatherException.Unknown -> resources.getString(R.string.readme_err_unknown)
    }

    /**
     * How old, in words: `3 hours ago`, `un minuto fa`, `ieri`.
     *
     * Coarsest unit that still says something — nobody needs `2 hours and 14 minutes`
     * to decide whether to trust a temperature. Plurals are real plurals: `1 ore fa`
     * is the kind of thing that makes a reader stop trusting the rest of the page.
     */
    fun age(resources: Resources, age: Duration): String {
        val minutes = age.toMinutes().coerceAtLeast(1)
        return when {
            minutes < 60 ->
                resources.getQuantityString(R.plurals.age_minutes, minutes.toInt(), minutes.toInt())
            minutes < 60 * 24 -> {
                val hours = (minutes / 60).toInt()
                resources.getQuantityString(R.plurals.age_hours, hours, hours)
            }
            else -> {
                val days = (minutes / (60 * 24)).toInt()
                resources.getQuantityString(R.plurals.age_days, days, days)
            }
        }
    }
}
