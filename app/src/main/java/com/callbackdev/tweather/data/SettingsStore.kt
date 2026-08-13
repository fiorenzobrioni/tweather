package com.callbackdev.tweather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

enum class WindSpeedUnit { KMH, MPH }

data class UnitSettings(
    val temperature: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windSpeed: WindSpeedUnit = WindSpeedUnit.KMH
)

/** Persisted preferences only — no alert engine ships in v1 (see PLANNING). */
data class NotificationSettings(
    val severeWeatherAlerts: Boolean = true,
    val dailySummary: Boolean = false,
    val precipitationWarning: Boolean = true
)

/**
 * Everything `settings.config` edits. [themeProfileName] stays a string here so the
 * data layer doesn't depend on the UI's ThemeProfile enum; the UI maps it safely.
 * [showDetails] default false: hides the technical fields of `weather_data.json`
 * (decided with the user; the compact form also matches the main-screen mockup).
 */
data class AppSettings(
    val editor: EditorSettings = EditorSettings(),
    val showDetails: Boolean = false,
    val units: UnitSettings = UnitSettings(),
    val notifications: NotificationSettings = NotificationSettings(),
    val themeProfileName: String = "Obsidian",
    val updateFrequencyMin: Int = UpdateFrequencies.first()
)

/** Cache TTL choices the sync setting cycles through. */
val UpdateFrequencies = listOf(15, 30, 60)

/** App settings persisted as DataStore preferences. */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.data
        .map { prefs ->
            AppSettings(
                editor = EditorSettings(
                    lineNumbers = prefs[LineNumbers] ?: false,
                    wordWrap = prefs[WordWrap] ?: false
                ),
                showDetails = prefs[ShowDetails] ?: false,
                units = UnitSettings(
                    temperature = enumOrDefault(prefs[Temperature], TemperatureUnit.CELSIUS),
                    windSpeed = enumOrDefault(prefs[WindSpeed], WindSpeedUnit.KMH)
                ),
                notifications = NotificationSettings(
                    severeWeatherAlerts = prefs[SevereAlerts] ?: true,
                    dailySummary = prefs[DailySummary] ?: false,
                    precipitationWarning = prefs[PrecipWarning] ?: true
                ),
                themeProfileName = prefs[ThemeProfileName] ?: "Obsidian",
                updateFrequencyMin = (prefs[UpdateFrequencyMin] ?: UpdateFrequencies.first())
                    .takeIf { it in UpdateFrequencies } ?: UpdateFrequencies.first()
            )
        }
        .distinctUntilChanged()

    suspend fun setLineNumbers(enabled: Boolean) = set(LineNumbers, enabled)
    suspend fun setWordWrap(enabled: Boolean) = set(WordWrap, enabled)
    suspend fun setShowDetails(enabled: Boolean) = set(ShowDetails, enabled)
    suspend fun setTemperatureUnit(unit: TemperatureUnit) = set(Temperature, unit.name)
    suspend fun setWindSpeedUnit(unit: WindSpeedUnit) = set(WindSpeed, unit.name)
    suspend fun setSevereWeatherAlerts(enabled: Boolean) = set(SevereAlerts, enabled)
    suspend fun setDailySummary(enabled: Boolean) = set(DailySummary, enabled)
    suspend fun setPrecipitationWarning(enabled: Boolean) = set(PrecipWarning, enabled)
    suspend fun setThemeProfile(name: String) = set(ThemeProfileName, name)
    suspend fun setUpdateFrequency(minutes: Int) = set(UpdateFrequencyMin, minutes)

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default

    companion object {
        private val LineNumbers = booleanPreferencesKey("editor_line_numbers")
        private val WordWrap = booleanPreferencesKey("editor_word_wrap")
        private val ShowDetails = booleanPreferencesKey("data_show_details")
        private val Temperature = stringPreferencesKey("units_temperature")
        private val WindSpeed = stringPreferencesKey("units_wind_speed")
        private val SevereAlerts = booleanPreferencesKey("notif_severe_alerts")
        private val DailySummary = booleanPreferencesKey("notif_daily_summary")
        private val PrecipWarning = booleanPreferencesKey("notif_precip_warning")
        private val ThemeProfileName = stringPreferencesKey("theme_profile")
        private val UpdateFrequencyMin = intPreferencesKey("sync_update_frequency_min")

        fun create(context: Context) = SettingsStore(context.settingsDataStore)
    }
}
