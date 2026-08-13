package com.callbackdev.tweather.domain

/**
 * Data-layer failures, each carrying a terminal-style message ready for the editor
 * UI (rendered later as `// ERROR: ...` comment lines).
 */
sealed class WeatherException(
    val terminalMessage: String,
    cause: Throwable? = null
) : Exception(terminalMessage, cause) {

    class NoNetwork(cause: Throwable? = null) : WeatherException(
        "net::ERR_INTERNET_DISCONNECTED — check your connection", cause
    )

    class CityNotFound(query: String) : WeatherException(
        "404: location \"$query\" not found in geocoding index"
    )

    class ApiError(val code: Int, cause: Throwable? = null) : WeatherException(
        "http::$code — Open-Meteo request failed", cause
    )

    class Unknown(cause: Throwable) : WeatherException(
        "panic: unexpected error — ${cause.message ?: cause::class.simpleName}", cause
    )
}
