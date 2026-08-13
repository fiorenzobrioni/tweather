package com.callbackdev.tweather.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.callbackdev.tweather.data.EditorSettings
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.data.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsStore: SettingsStore) : ViewModel() {

    val editorSettings: StateFlow<EditorSettings> = settingsStore.editorSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorSettings())

    fun setLineNumbers(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setLineNumbers(enabled) }
    }

    fun setWordWrap(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setWordWrap(enabled) }
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
