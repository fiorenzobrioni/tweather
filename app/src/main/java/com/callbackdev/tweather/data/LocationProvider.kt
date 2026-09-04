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
     * The freshest position any enabled provider already holds, within [maxAge].
     *
     * Every provider is asked, not just the one an acquisition would use: fused can
     * be empty on a phone that has just booted while network still holds this
     * morning's fix, and the age is what decides between them — read off
     * [Location.getElapsedRealtimeNanos], which is monotonic and therefore immune to
     * a clock the user (or the network) has just moved.
     */
    @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    private fun lastKnown(manager: LocationManager, maxAge: Duration): Location? {
        val ceiling = maxAge.toNanos()
        return manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { it.ageNanos() <= ceiling }
            .maxByOrNull { it.elapsedRealtimeNanos }
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
     * Rounds first, then geocodes. The 2 decimals (~1.1 km) are the coarse-accuracy
     * scale and map exactly onto `City.cacheKey`, so cache and history fragment only
     * on real movement — and since Fase 20 the geocoder is asked with the rounded pair
     * too: it is a network service on most devices, and handing it the most precise
     * coordinate the app owns while rounding everything else was the one place where
     * the privacy rule leaked.
     */
    private suspend fun Location.toFix(): GeoFix {
        val coordinates = Coordinates(
            lat = (latitude * 100).roundToInt() / 100.0,
            lon = (longitude * 100).roundToInt() / 100.0
        )
        val address = reverseGeocode(coordinates)
        return GeoFix(
            coordinates = coordinates,
            placeName = address?.run { locality ?: subAdminArea ?: subLocality },
            region = address?.adminArea,
            country = address?.countryName
        )
    }

    private suspend fun reverseGeocode(coordinates: Coordinates): Address? {
        if (!Geocoder.isPresent()) return null
        return withTimeoutOrNull(GeocodeTimeoutMs) {
            suspendCancellableCoroutine { continuation ->
                Geocoder(context).getFromLocation(
                    coordinates.lat, coordinates.lon, 1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            continuation.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            continuation.resume(null)
                        }
                    }
                )
            }
        }
    }

    private fun Location.ageNanos(): Long =
        (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos).coerceAtLeast(0L)

    private companion object {
        const val GeocodeTimeoutMs = 5_000L
    }
}
