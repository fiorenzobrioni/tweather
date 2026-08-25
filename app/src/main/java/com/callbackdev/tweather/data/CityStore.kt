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

/** What the main screen shows weather for: a saved city, the device position, or
 * nothing at all. */
sealed interface ActiveSource {
    data class Saved(val city: City) : ActiveSource

    /** [lastFix] is the last persisted GPS pseudo-city; null until the first fix. */
    data class Gps(val lastFix: City?) : ActiveSource

    /**
     * No location configured: a fresh install that has not answered `tweather init`
     * yet, or one whose last saved city was removed with GPS off. Before Fase 14b
     * this state could not be represented — an empty list fell back to a seeded
     * Milan, so `cities.json` always listed a city the user had never chosen.
     */
    data object None : ActiveSource
}

/** What the shell must know before it can draw anything — see [CityStore.firstRun]. */
enum class FirstRun {
    /** The legacy check has not run yet in this process: draw nothing, not `init`. */
    Unknown,

    /** `$ tweather init` still owes an answer. */
    Pending,

    /** Answered — with a city, with GPS, or by skipping — or inherited by an upgrade. */
    Done
}

/**
 * Persists the saved-cities list (the Explorer's "files") and the active city as
 * DataStore preferences: the list as a JSON array, the selection as the city id.
 * An empty list is a real state since Fase 14b ([ActiveSource.None]): the store no
 * longer invents [DefaultCity] to have something to show.
 */
class CityStore(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {

    val cities: Flow<List<City>> = dataStore.data.map(::decode).distinctUntilChanged()

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
                val city = cities.firstOrNull { it.id == prefs[ActiveCityId] }
                    ?: cities.firstOrNull()
                city?.let { ActiveSource.Saved(it) } ?: ActiveSource.None
            }
        }
        .distinctUntilChanged()

    /**
     * Whether `$ tweather init` (Fase 14c) still owes an answer. [FirstRun.Unknown]
     * until [migrateFirstRun] has run in this install: the shell must not flash the
     * init screen at someone who has been using the app for months.
     */
    val firstRun: Flow<FirstRun> = dataStore.data
        .map { prefs ->
            when {
                prefs[Migrated] != true -> FirstRun.Unknown
                prefs[InitDone] == true -> FirstRun.Done
                else -> FirstRun.Pending
            }
        }
        .distinctUntilChanged()

    /**
     * Decides once per install whether it predates the empty state, and never runs
     * again. [hasHistory] — any commit in the Logs — is what tells a used install
     * from a fresh one: someone who never opened `cities.json` has nothing in this
     * store either, but has been watching the seeded Milan since the day they
     * installed, and an update must not take it away. Such an install has the seed
     * written for real (it was a fallback, never a stored value) and skips `init`.
     * A genuinely fresh install writes nothing but the marker.
     */
    suspend fun migrateFirstRun(hasHistory: Boolean) {
        dataStore.edit { prefs ->
            if (prefs[Migrated] == true) return@edit
            prefs[Migrated] = true
            val used = hasHistory ||
                prefs[CitiesJson] != null ||
                prefs[ActiveCityId] != null ||
                prefs[UseGps] != null ||
                prefs[GpsCityJson] != null
            if (!used) return@edit
            prefs[InitDone] = true
            if (prefs[CitiesJson] == null) {
                prefs[CitiesJson] = json.encodeToString(listOf(DefaultCity))
            }
        }
    }

    /** The init screen has been answered — skipping it counts as an answer. */
    suspend fun markInitDone() {
        dataStore.edit { it[InitDone] = true }
    }

    /**
     * Adds (or refreshes) [city] and makes it active — the Search screen's flow.
     *
     * A city already in the list is REPLACED, not skipped: the stored record can be
     * older than the geocoding answer the user just tapped. That is what kept a Milano
     * searched in Italian showing as `milan.json` after Fase 13f — same GeoNames id as
     * the seeded "Milan", so the add was a no-op and the English record survived. Same
     * bug for anyone who switches the phone's language and re-adds a city. Its position
     * in the list is kept: re-adding a city is not a reorder.
     */
    suspend fun add(city: City) {
        dataStore.edit { prefs ->
            val cities = decode(prefs)
            val updated = if (cities.any { it.id == city.id }) {
                cities.map { if (it.id == city.id) city else it }
            } else {
                cities + city
            }
            prefs[CitiesJson] = json.encodeToString(updated)
            prefs[ActiveCityId] = city.id
        }
    }

    suspend fun setActive(city: City) {
        dataStore.edit { it[ActiveCityId] = city.id }
    }

    /**
     * Removes [city] — the last one included, since Fase 14b. The old guard existed
     * only because the main screen could not survive without a subject; now an empty
     * `cities.json` is a state the editor can say out loud. Removing the active city
     * activates the first one left, or nothing.
     */
    suspend fun remove(city: City) {
        dataStore.edit { prefs ->
            val remaining = decode(prefs).filterNot { it.id == city.id }
            prefs[CitiesJson] = json.encodeToString(remaining)
            if (prefs[ActiveCityId] == city.id) {
                remaining.firstOrNull()
                    ?.let { prefs[ActiveCityId] = it.id }
                    ?: prefs.remove(ActiveCityId)
            }
        }
    }

    /**
     * Enables/disables GPS as a source in one atomic edit: on also selects it (the
     * user just asked for their position); off falls back to the first saved city,
     * or to [ActiveSource.None] when the list is empty (Fase 14b).
     */
    suspend fun setUseGps(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[UseGps] = enabled
            if (enabled) {
                prefs[ActiveCityId] = GpsCityId
            } else if (prefs[ActiveCityId] == GpsCityId) {
                decode(prefs).firstOrNull()
                    ?.let { prefs[ActiveCityId] = it.id }
                    ?: prefs.remove(ActiveCityId)
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
            ?: emptyList()

    companion object {
        private val CitiesJson = stringPreferencesKey("cities_json")
        private val ActiveCityId = longPreferencesKey("active_city_id")
        private val UseGps = booleanPreferencesKey("use_gps")

        /** Fase 14b: this install has been checked for a pre-14b history. */
        private val Migrated = booleanPreferencesKey("first_run_migrated")

        /** Fase 14c: `$ tweather init` has been answered. */
        private val InitDone = booleanPreferencesKey("init_done")
        private val GpsCityJson = stringPreferencesKey("gps_city_json")

        /**
         * NOT seeded on a fresh install any more (Fase 14b): a city the user never
         * chose is the one thing `cities.json` must not claim. It survives as what
         * [migrateFirstRun] writes for installs that predate the empty state and
         * have been watching it all along. Milan — where the app is developed
         * (deviation from the PRD's New York sample, see PLANNING).
         */
        val DefaultCity = City(
            id = 3_173_435, // GeoNames id, as Open-Meteo geocoding would return
            name = "Milan",
            region = "Lombardy",
            country = "Italy",
            coordinates = Coordinates(45.4643, 9.1895),
            timezone = "Europe/Rome"
        )

        fun create(context: Context, json: Json) = CityStore(context.citiesDataStore, json)
    }
}
