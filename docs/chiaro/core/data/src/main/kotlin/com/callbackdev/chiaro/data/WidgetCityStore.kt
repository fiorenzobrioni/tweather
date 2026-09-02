package com.callbackdev.chiaro.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.widgetCitiesDataStore by preferencesDataStore(name = "widget_cities")

/**
 * Which city each placed home widget shows. A missing entry means "follow the app's
 * active source", which is both the default and what a widget added without
 * configuring gets (`configuration_optional`). Kept out of [CityStore] so pinning a
 * widget never perturbs the app's own selection — and out of [SettingsStore] so it
 * never bumps the visible `// Last modified:` stamp in settings.config.
 */
class WidgetCityStore(private val dataStore: DataStore<Preferences>) {

    /** appWidgetId → pinned city id ([com.callbackdev.chiaro.domain.model.GpsCityId] = the GPS source). */
    val pinned: Flow<Map<Int, Long>> = dataStore.data
        .map { prefs ->
            buildMap {
                prefs.asMap().forEach { (key, value) ->
                    val id = key.name.substringAfter(KeyPrefix, "").toIntOrNull()
                    if (id != null && value is Long) put(id, value)
                }
            }
        }
        .distinctUntilChanged()

    suspend fun current(): Map<Int, Long> = pinned.first()

    suspend fun pin(appWidgetId: Int, cityId: Long) {
        dataStore.edit { it[key(appWidgetId)] = cityId }
    }

    /** Back to following the app's active source. */
    suspend fun unpin(appWidgetId: Int) {
        dataStore.edit { it.remove(key(appWidgetId)) }
    }

    /** Called from the provider's onDeleted so removed widgets leave nothing behind. */
    suspend fun forget(appWidgetIds: IntArray) {
        dataStore.edit { prefs -> appWidgetIds.forEach { prefs.remove(key(it)) } }
    }

    /**
     * Backup restore hands every widget a NEW id, so a pin keyed by the old one would
     * land on whichever widget inherited that number. One edit, and the moves are
     * snapshotted first: old and new sets can overlap (1→2 while 2→3), so removing
     * as we go would clobber a value we still need.
     */
    suspend fun remap(oldIds: IntArray, newIds: IntArray) {
        dataStore.edit { prefs ->
            val moved = oldIds.zip(newIds).mapNotNull { (old, new) ->
                prefs[key(old)]?.let { new to it }
            }
            oldIds.forEach { prefs.remove(key(it)) }
            moved.forEach { (id, cityId) -> prefs[key(id)] = cityId }
        }
    }

    /**
     * Which widgets show the optional sky line (Fase 16e). Per widget, not per app:
     * two widgets can pin two cities, and one of them may be the one somebody put on
     * a screen to watch the sky. Absent means off, which is the default — an existing
     * widget must not change shape because the app updated.
     */
    val skyLine: Flow<Set<Int>> = dataStore.data
        .map { prefs ->
            prefs.asMap().keys
                .mapNotNull { it.name.removePrefix(SkyKeyPrefix).takeIf { n -> n != it.name } }
                .mapNotNull { it.toIntOrNull() }
                .filter { prefs[skyKey(it)] == true }
                .toSet()
        }
        .distinctUntilChanged()

    suspend fun currentSkyLine(): Set<Int> = skyLine.first()

    suspend fun setSkyLine(appWidgetId: Int, enabled: Boolean) {
        dataStore.edit { it[skyKey(appWidgetId)] = enabled }
    }

    private fun key(appWidgetId: Int) = longPreferencesKey("$KeyPrefix$appWidgetId")

    private fun skyKey(appWidgetId: Int) = booleanPreferencesKey("$SkyKeyPrefix$appWidgetId")

    companion object {
        private const val KeyPrefix = "widget_city_"

        private const val SkyKeyPrefix = "widget_sky_"

        fun create(context: Context) = WidgetCityStore(context.widgetCitiesDataStore)
    }
}
