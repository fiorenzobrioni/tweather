package com.callbackdev.chiaro.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.workspaceDataStore by preferencesDataStore(name = "workspace")

/**
 * The files open in the main editor's tab bar: two since Fase 10, three since 16c.
 * [SKY] is only reachable while `sky.enabled` is true — the enum keeps the value
 * either way, because a user who switches the module off and back on should find the
 * tab they left open, not the one the app picked for them.
 */
enum class MainEditorFile { JSON, README, SKY }

/**
 * Editor workspace state — what a real editor keeps in its session, not in its
 * settings: the last active file of the main screen survives app restarts like
 * VS Code reopening yesterday's tab. Deliberately its own DataStore instead of a
 * [SettingsStore] key: `$ git restore settings.config` must not close your tab,
 * and workspace state has no line in the settings.config file.
 */
class WorkspaceStore(private val dataStore: DataStore<Preferences>) {

    val mainActiveFile: Flow<MainEditorFile> = dataStore.data
        .map { prefs ->
            prefs[MainActiveFile]
                ?.let { name -> MainEditorFile.entries.firstOrNull { it.name == name } }
                ?: MainEditorFile.JSON
        }
        .distinctUntilChanged()

    suspend fun setMainActiveFile(file: MainEditorFile) {
        dataStore.edit { it[MainActiveFile] = file.name }
    }

    /**
     * The one-shot `HELP.md` pointer in the editor (Fase 14d), shown until it is
     * used or dismissed. Workspace state, and deliberately NOT a `settings.config`
     * toggle: a switch for something that happens once would spend the rest of the
     * app's life sitting on `false` in a file the user reads, and
     * `$ tweather reset settings` would bring the hint back to someone who has been
     * using tweather for a year. The way to see the help again is the file itself,
     * which never goes anywhere.
     */
    val helpHintDismissed: Flow<Boolean> = dataStore.data
        .map { it[HelpHintDismissed] ?: false }
        .distinctUntilChanged()

    suspend fun dismissHelpHint() {
        dataStore.edit { it[HelpHintDismissed] = true }
    }

    companion object {
        private val MainActiveFile = stringPreferencesKey("main_active_file")
        private val HelpHintDismissed = booleanPreferencesKey("help_hint_dismissed")

        fun create(context: Context) = WorkspaceStore(context.workspaceDataStore)
    }
}
