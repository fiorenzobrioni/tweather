package com.callbackdev.tweather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.GpsCityId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.citiesDataStore by preferencesDataStore(name = "cities")

/** `settings.config`'s `location` section plus the last persisted GPS fix. */
data class LocationSettings(val useGps: Boolean, val gpsCity: City?)

/** What the main screen shows weather for: a saved city or the device position. */
sealed interface ActiveSource {
    data class Saved(val city: City) : ActiveSource

    /** [lastFix] is the last persisted GPS pseudo-city; null until the first fix. */
    data class Gps(val lastFix: City?) : ActiveSource
}

/**
 * Persists the saved-cities list (the Explorer's "files") and the active city as
 * DataStore preferences: the list as a JSON array, the selection as the city id.
 * An empty store falls back to [DefaultCity], so [activeCity] always emits.
 */
class CityStore(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {

    val cities: Flow<List<City>> = dataStore.data.map(::decode).distinctUntilChanged()

    val activeCity: Flow<City> = dataStore.data
        .map { prefs ->
            val cities = decode(prefs)
            cities.firstOrNull { it.id == prefs[ActiveCityId] } ?: cities.first()
        }
        .distinctUntilChanged()

    val locationSettings: Flow<LocationSettings> = dataStore.data
        .map { prefs -> LocationSettings(prefs[UseGps] ?: false, decodeGpsCity(prefs)) }
        .distinctUntilChanged()

    /** GPS is the source only while enabled AND selected (sentinel [GpsCityId]). */
    val activeSource: Flow<ActiveSource> = dataStore.data
        .map { prefs ->
            if (prefs[UseGps] == true && prefs[ActiveCityId] == GpsCityId) {
                ActiveSource.Gps(decodeGpsCity(prefs))
            } else {
                val cities = decode(prefs)
                ActiveSource.Saved(
                    cities.firstOrNull { it.id == prefs[ActiveCityId] } ?: cities.first()
                )
            }
        }
        .distinctUntilChanged()

    /** Adds (or re-uses) [city] and makes it active — the Search screen's flow. */
    suspend fun add(city: City) {
        dataStore.edit { prefs ->
            val cities = decode(prefs)
            if (cities.none { it.id == city.id }) {
                prefs[CitiesJson] = json.encodeToString(cities + city)
            }
            prefs[ActiveCityId] = city.id
        }
    }

    suspend fun setActive(city: City) {
        dataStore.edit { it[ActiveCityId] = city.id }
    }

    /**
     * Removes [city]; the last remaining city can't be removed (the main screen
     * always needs a subject). Removing the active city activates the first left.
     */
    suspend fun remove(city: City) {
        dataStore.edit { prefs ->
            val remaining = decode(prefs).filterNot { it.id == city.id }
            if (remaining.isEmpty()) return@edit
            prefs[CitiesJson] = json.encodeToString(remaining)
            if (prefs[ActiveCityId] == city.id) prefs[ActiveCityId] = remaining.first().id
        }
    }

    /**
     * Enables/disables GPS as a source in one atomic edit: on also selects it
     * (the user just asked for their position); off falls back to the first saved
     * city — always present thanks to [remove]'s last-city guard.
     */
    suspend fun setUseGps(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[UseGps] = enabled
            if (enabled) {
                prefs[ActiveCityId] = GpsCityId
            } else if (prefs[ActiveCityId] == GpsCityId) {
                prefs[ActiveCityId] = decode(prefs).first().id
            }
        }
    }

    /** Selects GPS from the Explorer; no-op while the toggle is off. */
    suspend fun setActiveGps() {
        dataStore.edit { prefs ->
            if (prefs[UseGps] == true) prefs[ActiveCityId] = GpsCityId
        }
    }

    /** Upserts the persisted GPS pseudo-city; never touches the saved list. */
    suspend fun updateGpsCity(city: City) {
        require(city.id == GpsCityId) { "not the GPS pseudo-city: ${city.id}" }
        dataStore.edit { it[GpsCityJson] = json.encodeToString(city) }
    }

    private fun decodeGpsCity(prefs: Preferences): City? =
        prefs[GpsCityJson]?.let { runCatching { json.decodeFromString<City>(it) }.getOrNull() }

    private fun decode(prefs: Preferences): List<City> =
        prefs[CitiesJson]
            ?.let { runCatching { json.decodeFromString<List<City>>(it) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(DefaultCity)

    companion object {
        private val CitiesJson = stringPreferencesKey("cities_json")
        private val ActiveCityId = longPreferencesKey("active_city_id")
        private val UseGps = booleanPreferencesKey("use_gps")
        private val GpsCityJson = stringPreferencesKey("gps_city_json")

        /** The PRD's sample city, seeded on first run so the app opens on data. */
        val DefaultCity = City(
            id = 5_128_581, // GeoNames id, as Open-Meteo geocoding would return
            name = "New York",
            region = "NY",
            country = "USA",
            coordinates = Coordinates(40.7128, -74.0060),
            timezone = "America/New_York"
        )

        fun create(context: Context, json: Json) = CityStore(context.citiesDataStore, json)
    }
}
