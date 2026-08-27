package com.callbackdev.tweather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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

/** Read by the alert engine (Fase 9c): each toggle gates one rule in AlertEngine.
 * [userRules] (Fase 11) is the master switch of `alerts.rules`; default true so
 * writing a rule is enough — it only matters once rules exist. */
data class NotificationSettings(
    val severeWeatherAlerts: Boolean = true,
    val dailySummary: Boolean = false,
    val precipitationWarning: Boolean = true,
    val userRules: Boolean = true
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
    /**
     * The sky module (Fase 16c). False removes `sky.crontab` from the editor strip
     * and, from 16e, the sky lines of the README. Default true: the module is four
     * subscribed lines on a fresh install, and a feature that ships switched off is
     * a feature nobody finds.
     */
    val skyEnabled: Boolean = true,
    /**
     * The lead a job added from the catalog starts with (Fase 16f). Minutes, or null
     * for no reminder.
     */
    /**
     * The lead every line of `sky.crontab` uses unless it carries one of its own —
     * NOT a seed copied into a job when it is added. Off by default: a fresh install
     * that switched the module on to read the file must not start notifying for it.
     * Off also means no line renders a `--notify` token, which is why the token is
     * how you discover the file HAS reminders rather than how you learn it does not.
     */
    val skyNotifyDefaultMin: Int? = null,
    /**
     * Send the reminder even when the sky will not allow the event. False by default:
     * a reminder for something you cannot see is noise.
     */
    val skyNotifyOnFail: Boolean = false,
    val updateFrequencyMin: Int = DefaultUpdateFrequencyMin,
    val widgetOpacityPct: Int = DefaultWidgetOpacityPct,
    /** Epoch seconds of the last edit; null until the user changes something. */
    val lastModifiedEpochSeconds: Long? = null
)

/** Foreground cache TTL AND background polling interval (since the alert engine). */
val UpdateFrequencies = listOf(15, 30, 60, 120)

/** 60: right default for a polling interval (decision recorded in PLANNING, Fase 7). */
const val DefaultUpdateFrequencyMin = 60

/** Home-widget background opacity: alpha on the card fill only, the border stays crisp. */
val WidgetOpacities = listOf(100, 85, 70, 50)

const val DefaultWidgetOpacityPct = 100

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
                    precipitationWarning = prefs[PrecipWarning] ?: true,
                    userRules = prefs[UserRules] ?: true
                ),
                themeProfileName = prefs[ThemeProfileName] ?: "Obsidian",
                skyEnabled = prefs[SkyEnabled] ?: true,
                // 0 is how `off` is stored: an Int? preference cannot hold null, and
                // absent must read the same as explicitly switched off.
                skyNotifyDefaultMin = prefs[SkyNotifyDefault]?.takeIf { it > 0 },
                skyNotifyOnFail = prefs[SkyNotifyOnFail] ?: false,
                updateFrequencyMin = (prefs[UpdateFrequencyMin] ?: DefaultUpdateFrequencyMin)
                    .takeIf { it in UpdateFrequencies } ?: DefaultUpdateFrequencyMin,
                widgetOpacityPct = (prefs[WidgetOpacity] ?: DefaultWidgetOpacityPct)
                    .takeIf { it in WidgetOpacities } ?: DefaultWidgetOpacityPct,
                lastModifiedEpochSeconds = prefs[LastModified]
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
    suspend fun setUserRules(enabled: Boolean) = set(UserRules, enabled)
    suspend fun setThemeProfile(name: String) = set(ThemeProfileName, name)
    suspend fun setSkyEnabled(enabled: Boolean) = set(SkyEnabled, enabled)

    /** 0 stands for "off": DataStore has no nullable Int, and absent means default. */
    suspend fun setSkyNotifyDefault(minutes: Int?) = set(SkyNotifyDefault, minutes ?: 0)

    suspend fun setSkyNotifyOnFail(enabled: Boolean) = set(SkyNotifyOnFail, enabled)
    suspend fun setUpdateFrequency(minutes: Int) = set(UpdateFrequencyMin, minutes)
    suspend fun setWidgetOpacity(pct: Int) = set(WidgetOpacity, pct)

    /**
     * `$ git restore settings.config` — clears every stored preference (including
     * the last-modified stamp), so the file reads pristine again.
     */
    suspend fun resetToDefaults() {
        dataStore.edit { it.clear() }
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit {
            it[key] = value
            // settings.config's "// Last modified:" header line
            it[LastModified] = System.currentTimeMillis() / 1000
        }
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
        private val UserRules = booleanPreferencesKey("notif_user_rules")
        private val ThemeProfileName = stringPreferencesKey("theme_profile")
        private val SkyEnabled = booleanPreferencesKey("sky_enabled")
        private val SkyNotifyDefault = intPreferencesKey("sky_notify_default_min")
        private val SkyNotifyOnFail = booleanPreferencesKey("sky_notify_on_fail")
        private val UpdateFrequencyMin = intPreferencesKey("sync_update_frequency_min")
        private val WidgetOpacity = intPreferencesKey("widget_bg_opacity_pct")
        private val LastModified = longPreferencesKey("last_modified_epoch")

        fun create(context: Context) = SettingsStore(context.settingsDataStore)
    }
}
