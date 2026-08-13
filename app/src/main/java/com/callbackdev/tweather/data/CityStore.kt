package com.callbackdev.tweather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.Coordinates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.citiesDataStore by preferencesDataStore(name = "cities")

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

    private fun decode(prefs: Preferences): List<City> =
        prefs[CitiesJson]
            ?.let { runCatching { json.decodeFromString<List<City>>(it) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(DefaultCity)

    companion object {
        private val CitiesJson = stringPreferencesKey("cities_json")
        private val ActiveCityId = longPreferencesKey("active_city_id")

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
