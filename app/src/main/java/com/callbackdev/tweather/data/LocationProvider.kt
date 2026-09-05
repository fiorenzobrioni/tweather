package com.callbackdev.tweather.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import com.callbackdev.tweather.domain.WeatherException
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.GeoFix
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-shot device position, abstracted so ViewModel tests can fake it. No new
 * dependencies: the platform [LocationManager] + [Geocoder] cover everything on
 * minSdk 33, and staying off play-services keeps the GPL build Google-free.
 *
 * The acquisition strategy of Fase 20 came from Chiaro's review of the same file —
 * the two copies were byte-for-byte identical, so all three defects it found were
 * this one's too (see `UPSTREAM.md` in that repo). Keep them in step.
 */
interface LocationProvider {
    /**
     * The device position, reverse-geocoded best-effort.
     *
     * [maxAge] is the whole battery contract (Fase 20). A position the system already
     * holds costs no radio at all, so when one that young exists it IS the answer and
     * nothing is powered up; only past that age is an acquisition worth starting.
     * [LocationProvider.Now] means the reader asked out loud and nothing already
     * known will do.
     *
     * Throws the `WeatherException.Location*` family on failure (terminal `gps::…`
     * messages); geocoding problems never fail the fix — the place fields just stay
     * null.
     */
    suspend fun currentFix(
        maxAge: Duration = SilentMaxAge,
        timeout: Duration = DefaultTimeout
    ): GeoFix

    companion object {
        /** An explicit ask: the pull, the toggle, the tap on the position row. */
        val Now: Duration = Duration.ZERO

        /**
         * How old a known position may be before an automatic path re-acquires. Also
         * the interval the callers gate themselves on, so the two agree by
         * construction.
         */
        val SilentMaxAge: Duration = Duration.ofMinutes(5)

        /** The reader is watching a spinner: worth waiting for. */
        val DefaultTimeout: Duration = Duration.ofSeconds(15)

        /** Nobody is watching, the page already has content: nobody waits fifteen
         * seconds behind something that is already correct. */
        val SilentTimeout: Duration = Duration.ofSeconds(8)

        /**
         * Past this, a last known position is a different trip rather than a stale
         * fix, and answering with it would put the reader in the town they flew home
         * from. Used only as the last resort, after an acquisition failed outright.
         */
        val LastResortMaxAge: Duration = Duration.ofHours(24)
    }
}

class AndroidLocationProvider(private val context: Context) : LocationProvider {

    override suspend fun currentFix(maxAge: Duration, timeout: Duration): GeoFix {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw WeatherException.LocationPermissionDenied()
        }
        val manager = context.getSystemService(LocationManager::class.java)
            ?: throw WeatherException.LocationUnavailable()
        if (!manager.isLocationEnabled) throw WeatherException.LocationDisabled()

        // The cheap answer first, which is also the platform's own advice: a position
        // the system already holds is free and instant, an acquisition is neither. On
        // an automatic path this is what usually answers, and nothing is powered up.
        if (!maxAge.isZero) lastKnown(manager, maxAge)?.let { return it.toFix() }

        val enabled = manager.getProviders(true)
        // Fused first (it answers from whatever another app has already paid for),
        // network second. Raw GPS is last and mostly theoretical: with coarse
        // permission the fix gets fudged to ~2 km anyway, so powering the receiver
        // buys nothing — it stays only for a phone with network location switched off.
        val provider = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER
        ).firstOrNull { enabled.contains(it) }
            ?: throw WeatherException.LocationDisabled()

        // null from the consumer = the provider gave up; null from withTimeoutOrNull
        // = no answer in time. Both fall back to the last known position first.
        var gaveUp = false
        val location = try {
            withTimeoutOrNull(timeout.toMillis()) {
                awaitCurrentLocation(manager, provider).also { if (it == null) gaveUp = true }
            } ?: lastKnown(manager, LocationProvider.LastResortMaxAge)
        } catch (e: SecurityException) {
            // permission revoked between the check above and the call
            throw WeatherException.LocationPermissionDenied()
        }
            ?: throw if (gaveUp) {
                WeatherException.LocationUnavailable()
            } else {
                WeatherException.LocationTimeout()
            }

        return location.toFix()
    }

    /**
     * The best position any enabled provider already holds, within [maxAge].
     *
     * Every provider is asked, not just the one an acquisition would use: fused can be
     * empty on a phone that has just booted while network still holds this morning's
     * fix. What decides between them is NOT which arrived last (Fase 20's answer, and
     * the one that put the reader in the wrong town). The providers do not answer with
     * the same thing — fused hands over a position another app has already paid for,
     * network can hand over the mast the phone is attached to — and coarse permission
     * floors both at 2 km without making the worse one any better, so a cell fix ten
     * seconds old beat a good fix from two minutes ago and answered "Milano" to
     * somebody standing in Segrate. Rank by how far the reader may be from each one by
     * now, which is what both numbers are for.
     */
    @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    private fun lastKnown(manager: LocationManager, maxAge: Duration): Location? {
        val ceiling = maxAge.toNanos()
        return manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { it.ageNanos() <= ceiling }
            .minByOrNull { it.expectedErrorMeters() }
    }

    /** Suspends on [LocationManager.getCurrentLocation]; null when the provider gives up. */
    @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    private suspend fun awaitCurrentLocation(
        manager: LocationManager,
        provider: String
    ): Location? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
            continuation.resume(location)
        }
    }

    /**
     * Rounds what leaves the app; geocodes what the platform handed over.
     *
     * The 2 decimals (~1.1 km) are the coarse-accuracy scale and map exactly onto
     * `City.cacheKey`, so cache and history fragment only on real movement. Fase 20
     * also began handing the rounded pair to the [Geocoder], on the grounds that the
     * most precise coordinate the app owns was going to the one service the app does
     * not control — but under `ACCESS_COARSE_LOCATION` the app owns no precise
     * coordinate to protect: the platform has already quantized the position onto a
     * ~2 km grid, and floored its declared accuracy at 2 km, before the app sees it.
     * Both values therefore name the same cell and the rounding bought no privacy at
     * all. What it did buy was up to 679 m of displacement at these latitudes (555 m
     * of latitude, 390 m of longitude at 45.5°N), which is the width of a small comune
     * — enough to move the point into the fields next to the town, where the geocoder
     * has no town to answer with.
     */
    private suspend fun Location.toFix(): GeoFix {
        // The rounding is the last thing that happens, not the first.
        val place = geocodedPlace(reverseGeocode(latitude, longitude))
        return GeoFix(
            coordinates = Coordinates(
                lat = (latitude * 100).roundToInt() / 100.0,
                lon = (longitude * 100).roundToInt() / 100.0
            ),
            placeName = place.name,
            region = place.region,
            country = place.country
        )
    }

    private fun Location.expectedErrorMeters(): Double =
        expectedErrorMeters(if (hasAccuracy()) accuracy else null, ageNanos())

    /** Every rung the backend answers with, best-effort; empty on any failure. */
    private suspend fun reverseGeocode(lat: Double, lon: Double): List<Address> {
        if (!Geocoder.isPresent()) return emptyList()
        return withTimeoutOrNull(GeocodeTimeoutMs) {
            suspendCancellableCoroutine { continuation ->
                Geocoder(context).getFromLocation(
                    lat, lon, MaxGeocodeResults,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            continuation.resume(addresses.toList())
                        }

                        override fun onError(errorMessage: String?) {
                            continuation.resume(emptyList())
                        }
                    }
                )
            }
        } ?: emptyList()
    }

    private fun Location.ageNanos(): Long =
        (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos).coerceAtLeast(0L)

    private companion object {
        const val GeocodeTimeoutMs = 5_000L

        /**
         * Why the reverse geocode does not ask for one address.
         *
         * The backend answers a point with a ladder of addresses at widening
         * granularity, and for a point that is not on a street the top rung can carry
         * no town at all: in Italy it comes back with the region and the province and
         * nothing in between. Reading only the first rung is what printed "Provincia
         * di Monza e della Brianza" where the reader lives in Cavenago di Brianza.
         */
        const val MaxGeocodeResults = 5
    }
}

/**
 * How far the reader may be from a position by now: what it was worth when it was
 * taken, plus what they can have covered since. Lower is better, and the age term is
 * what keeps a very good fix from yesterday from winning the 24-hour last resort.
 *
 * [accuracyMeters] is null when the position will not say how good it is.
 */
internal fun expectedErrorMeters(accuracyMeters: Float?, ageNanos: Long): Double =
    (accuracyMeters?.toDouble() ?: UnknownAccuracyMeters) +
        ageNanos.coerceAtLeast(0L) / NanosPerSecond * DriftMetersPerSecond

/**
 * The pace at which a position goes out of date: somebody crossing a town, not a
 * motorway. Over `SilentMaxAge` it is worth 3 km, which is the width of the only
 * comparison it has to settle.
 */
private const val DriftMetersPerSecond = 10.0

/** A position that will not say how good it is loses to any that does. */
private const val UnknownAccuracyMeters = 10_000.0

private const val NanosPerSecond = 1_000_000_000.0

/** What reverse geocoding managed to learn about a point. */
internal data class GeocodedPlace(
    val name: String?,
    val region: String?,
    val country: String?
)

/**
 * The place [addresses] describes: the most specific name ANY rung of the ladder
 * knows, never an administrative container standing in for a town.
 *
 * The order is the whole point. A town (`locality`) first; then a quarter or a hamlet
 * (`subLocality`), which is at least somewhere a person can be; and only when no rung
 * knows either, the province (`subAdminArea`) — which used to sit in the MIDDLE of
 * that list, on the first address alone, and therefore won outright every time the
 * nearest address happened to carry no locality. A province is the honest answer when
 * it is the only one there is, and a wrong one when the town is two rungs down.
 */
internal fun geocodedPlace(addresses: List<Address>): GeocodedPlace = GeocodedPlace(
    name = addresses.pick { it.locality }
        ?: addresses.pick { it.subLocality }
        ?: addresses.pick { it.subAdminArea },
    region = addresses.pick { it.adminArea },
    country = addresses.pick { it.countryName }
)

/** The first non-blank [field] on the ladder; blank strings are not answers. */
private fun List<Address>.pick(field: (Address) -> String?): String? =
    firstNotNullOfOrNull { field(it)?.trim()?.takeIf(String::isNotEmpty) }
