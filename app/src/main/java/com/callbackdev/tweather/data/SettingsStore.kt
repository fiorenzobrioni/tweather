package com.callbackdev.tweather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Editor behavior toggles from `settings.config`. Defaults follow the mobile
 * mockups: line numbers off (the main-screen mockup hides them on phones — decided
 * with the user to reclaim horizontal space), word wrap off (VS Code's default).
 */
data class EditorSettings(
    val lineNumbers: Boolean = false,
    val wordWrap: Boolean = false
)

/** App settings persisted as DataStore preferences (grows with Fase 7). */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    val editorSettings: Flow<EditorSettings> = dataStore.data
        .map { prefs ->
            EditorSettings(
                lineNumbers = prefs[LineNumbers] ?: false,
                wordWrap = prefs[WordWrap] ?: false
            )
        }
        .distinctUntilChanged()

    suspend fun setLineNumbers(enabled: Boolean) {
        dataStore.edit { it[LineNumbers] = enabled }
    }

    suspend fun setWordWrap(enabled: Boolean) {
        dataStore.edit { it[WordWrap] = enabled }
    }

    companion object {
        private val LineNumbers = booleanPreferencesKey("editor_line_numbers")
        private val WordWrap = booleanPreferencesKey("editor_word_wrap")

        fun create(context: Context) = SettingsStore(context.settingsDataStore)
    }
}
