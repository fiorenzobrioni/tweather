package com.callbackdev.tweather.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.callbackdev.tweather.data.AppSettings
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.data.SettingsStore
import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.data.UpdateFrequencies
import com.callbackdev.tweather.data.WidgetOpacities
import com.callbackdev.tweather.data.WindSpeedUnit
import com.callbackdev.tweather.data.WorkspaceStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val cityStore: CityStore,
    private val workspaceStore: WorkspaceStore
) : ViewModel() {

    /**
     * The editor's `HELP.md` hint has done its job once the file has been opened —
     * by any route, not only by tapping the hint (Fase 14d).
     */
    fun markHelpSeen() {
        viewModelScope.launch { workspaceStore.dismissHelpHint() }
    }

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** `location.use_gps` lives in CityStore so gps↔city transitions stay atomic. */
    val useGps: StateFlow<Boolean> = cityStore.locationSettings
        .map { it.useGps }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setUseGps(enabled: Boolean) {
        viewModelScope.launch { cityStore.setUseGps(enabled) }
    }

    fun setLineNumbers(enabled: Boolean) = save { setLineNumbers(enabled) }
    fun setWordWrap(enabled: Boolean) = save { setWordWrap(enabled) }
    fun setShowDetails(enabled: Boolean) = save { setShowDetails(enabled) }
    fun setSevereWeatherAlerts(enabled: Boolean) = save { setSevereWeatherAlerts(enabled) }
    fun setDailySummary(enabled: Boolean) = save { setDailySummary(enabled) }
    fun setPrecipitationWarning(enabled: Boolean) = save { setPrecipitationWarning(enabled) }
    fun setUserRules(enabled: Boolean) = save { setUserRules(enabled) }
    fun setSkyEnabled(enabled: Boolean) = save { setSkyEnabled(enabled) }
    fun setThemeProfile(name: String) = save { setThemeProfile(name) }

    /** `$ git restore settings.config` — also turns gps back off (its default). */
    fun resetToDefaults() {
        save { resetToDefaults() }
        viewModelScope.launch { cityStore.setUseGps(false) }
    }

    fun toggleTemperatureUnit() = save {
        setTemperatureUnit(
            if (this@SettingsViewModel.settings.value.units.temperature == TemperatureUnit.CELSIUS) {
                TemperatureUnit.FAHRENHEIT
            } else {
                TemperatureUnit.CELSIUS
            }
        )
    }

    fun toggleWindSpeedUnit() = save {
        setWindSpeedUnit(
            if (this@SettingsViewModel.settings.value.units.windSpeed == WindSpeedUnit.KMH) {
                WindSpeedUnit.MPH
            } else {
                WindSpeedUnit.KMH
            }
        )
    }

    /** Cycles 15 → 30 → 60 → 15 minutes. */
    fun cycleUpdateFrequency() = save {
        val current = this@SettingsViewModel.settings.value.updateFrequencyMin
        val index = UpdateFrequencies.indexOf(current)
        setUpdateFrequency(UpdateFrequencies[(index + 1) % UpdateFrequencies.size])
    }

    /** Cycles 100 → 85 → 70 → 50 → 100 percent. */
    fun cycleWidgetOpacity() = save {
        val current = this@SettingsViewModel.settings.value.widgetOpacityPct
        val index = WidgetOpacities.indexOf(current)
        setWidgetOpacity(WidgetOpacities[(index + 1) % WidgetOpacities.size])
    }

    private fun save(block: suspend SettingsStore.() -> Unit) {
        viewModelScope.launch { settingsStore.block() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[AndroidViewModelFactory.APPLICATION_KEY])
                SettingsViewModel(
                    settingsStore = ServiceLocator.settingsStore(app),
                    cityStore = ServiceLocator.cityStore(app),
                    workspaceStore = ServiceLocator.workspaceStore(app)
                )
            }
        }
    }
}
