package com.callbackdev.tweather.domain.model

import java.util.Locale
import kotlin.math.abs

/**
 * Reserved id for the GPS pseudo-city (`current_location.json`). GeoNames ids from
 * the geocoding API are always positive, so it can never collide with a saved city.
 */
const val GpsCityId = -1L

/**
 * One-shot device position fix. Coordinates arrive already rounded to 2 decimals
 * (~1.1 km) so they map 1:1 onto [City.cacheKey] — history and cache fragment only
 * on real movement, never on float noise. Place fields are best-effort reverse
 * geocoding and stay null when unavailable.
 */
data class GeoFix(
    val coordinates: Coordinates,
    val placeName: String?,
    val region: String?,
    val country: String?
)

/** The GPS pseudo-city rendered by the editor; never stored in the saved list. */
fun GeoFix.toGpsCity(): City = City(
    id = GpsCityId,
    name = placeName ?: coordinates.gpsLabel,
    region = region,
    country = country,
    coordinates = coordinates,
    timezone = null // the forecast API resolves timezone=auto from the coordinates
)

/** `"45.46N 9.19E"` — display name fallback when reverse geocoding fails. */
val Coordinates.gpsLabel: String
    get() = String.format(
        Locale.US,
        "%.2f%s %.2f%s",
        abs(lat), if (lat >= 0) "N" else "S",
        abs(lon), if (lon >= 0) "E" else "W"
    )
