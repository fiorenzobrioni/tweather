package com.callbackdev.tweather.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.callbackdev.tweather.data.AppSettings
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.data.SettingsStore
import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.data.UpdateFrequencies
import com.callbackdev.tweather.data.WindSpeedUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsStore: SettingsStore) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setLineNumbers(enabled: Boolean) = save { setLineNumbers(enabled) }
    fun setWordWrap(enabled: Boolean) = save { setWordWrap(enabled) }
    fun setShowDetails(enabled: Boolean) = save { setShowDetails(enabled) }
    fun setSevereWeatherAlerts(enabled: Boolean) = save { setSevereWeatherAlerts(enabled) }
    fun setDailySummary(enabled: Boolean) = save { setDailySummary(enabled) }
    fun setPrecipitationWarning(enabled: Boolean) = save { setPrecipitationWarning(enabled) }
    fun setThemeProfile(name: String) = save { setThemeProfile(name) }

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

    private fun save(block: suspend SettingsStore.() -> Unit) {
        viewModelScope.launch { settingsStore.block() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[AndroidViewModelFactory.APPLICATION_KEY])
                SettingsViewModel(ServiceLocator.settingsStore(app))
            }
        }
    }
}
