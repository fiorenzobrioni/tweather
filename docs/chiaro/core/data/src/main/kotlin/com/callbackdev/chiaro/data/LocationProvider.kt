package com.callbackdev.chiaro.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.annotation.RequiresPermission
import com.callbackdev.chiaro.domain.WeatherException
import com.callbackdev.chiaro.domain.model.Coordinates
import com.callbackdev.chiaro.domain.model.GeoFix
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-shot device position, abstracted so ViewModel tests can fake it. No new
 * dependencies: the platform [LocationManager] + [Geocoder] cover everything on
 * minSdk 33, and staying off play-services keeps the GPL build Google-free.
 */
interface LocationProvider {
    /**
     * Acquires a coarse fix and reverse-geocodes it best-effort. Throws the
     * `WeatherException.Location*` family on failure (terminal `gps::…` messages);
     * geocoding problems never fail the fix — [GeoFix.placeName] just stays null.
     */
    suspend fun currentFix(timeout: Duration = DefaultTimeout): GeoFix

    companion object {
        val DefaultTimeout: Duration = Duration.ofSeconds(15)
    }
}

class AndroidLocationProvider(private val context: Context) : LocationProvider {

    override suspend fun currentFix(timeout: Duration): GeoFix {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw WeatherException.LocationPermissionDenied()
        }
        val manager = context.getSystemService(LocationManager::class.java)
            ?: throw WeatherException.LocationUnavailable()
        if (!manager.isLocationEnabled) throw WeatherException.LocationDisabled()
        val provider = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER
        ).firstOrNull { manager.getProviders(true).contains(it) }
            ?: throw WeatherException.LocationDisabled()

        // null from the consumer = the provider gave up; null from withTimeoutOrNull
        // = no answer in time. Both fall back to the last known position first.
        var gaveUp = false
        val location = try {
            withTimeoutOrNull(timeout.toMillis()) {
                awaitCurrentLocation(manager, provider).also { if (it == null) gaveUp = true }
            } ?: manager.getLastKnownLocation(provider)
        } catch (e: SecurityException) {
            // permission revoked between the check above and the call
            throw WeatherException.LocationPermissionDenied()
        }
            ?: throw if (gaveUp) {
                WeatherException.LocationUnavailable()
            } else {
                WeatherException.LocationTimeout()
            }

        val address = reverseGeocode(location)
        return GeoFix(
            // 2 decimals (~1.1 km): coarse-accuracy scale, and exact under
            // City.cacheKey so cache/history fragment only on real movement.
            coordinates = Coordinates(
                lat = (location.latitude * 100).roundToInt() / 100.0,
                lon = (location.longitude * 100).roundToInt() / 100.0
            ),
            placeName = address?.run { locality ?: subAdminArea ?: subLocality },
            region = address?.adminArea,
            country = address?.countryName
        )
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

    private suspend fun reverseGeocode(location: Location): Address? {
        if (!Geocoder.isPresent()) return null
        return withTimeoutOrNull(GeocodeTimeoutMs) {
            suspendCancellableCoroutine { continuation ->
                Geocoder(context).getFromLocation(
                    location.latitude, location.longitude, 1,
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

    private companion object {
        const val GeocodeTimeoutMs = 5_000L
    }
}
