package com.callbackdev.tweather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.searchHistoryDataStore by preferencesDataStore(name = "search_history")

/**
 * The `recent_searches` array of the Search screen: most recent first, deduplicated,
 * capped at [MAX_ENTRIES]. Stored as a JSON string array in DataStore.
 */
class SearchHistoryStore(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {

    val recentSearches: Flow<List<String>> = dataStore.data
        .map { prefs ->
            prefs[RecentsJson]
                ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
                .orEmpty()
        }
        .distinctUntilChanged()

    suspend fun add(term: String) {
        val clean = term.trim()
        if (clean.isEmpty()) return
        dataStore.edit { prefs ->
            val current = prefs[RecentsJson]
                ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
                .orEmpty()
            val updated = (listOf(clean) + current.filterNot { it.equals(clean, ignoreCase = true) })
                .take(MAX_ENTRIES)
            prefs[RecentsJson] = json.encodeToString(updated)
        }
    }

    /**
     * `$ history -c` — forgets what was searched for. Deliberately narrow: the saved
     * cities are the user's files, not history, and live in [CityStore] where the
     * Explorer's own `[rm]` removes them one by one.
     */
    suspend fun clear() {
        dataStore.edit { it.remove(RecentsJson) }
    }

    companion object {
        const val MAX_ENTRIES = 5
        private val RecentsJson = stringPreferencesKey("recent_searches_json")

        fun create(context: Context, json: Json) =
            SearchHistoryStore(context.searchHistoryDataStore, json)
    }
}
