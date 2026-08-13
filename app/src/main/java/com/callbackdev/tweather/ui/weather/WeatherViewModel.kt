package com.callbackdev.tweather.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.data.WeatherRepository
import com.callbackdev.tweather.domain.WeatherException
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.WeatherReport
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the main screen. [report] survives refresh failures so the last good
 * document stays on screen with the error rendered as comment lines above it.
 */
data class WeatherUiState(
    val report: WeatherReport? = null,
    val isLoading: Boolean = true,
    val error: WeatherException? = null
)

class WeatherViewModel(
    private val repository: WeatherRepository,
    cityStore: CityStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private var city: City? = null
    private var loadJob: Job? = null

    init {
        // Follow the Explorer's selection: every change of active city reloads the
        // document (cache-friendly — an unexpired city comes back as a HIT).
        viewModelScope.launch {
            cityStore.activeCity.collect { active ->
                if (city?.id != active.id) {
                    city = active
                    load(active, forceRefresh = false, clearReport = true)
                }
            }
        }
    }

    /** FAB action: bypasses the cache, so `last_sync` and history advance. */
    fun refresh() {
        city?.let { load(it, forceRefresh = true, clearReport = false) }
    }

    private fun load(city: City, forceRefresh: Boolean, clearReport: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                if (clearReport) WeatherUiState()
                else it.copy(isLoading = true, error = null)
            }
            try {
                val report = repository.getWeather(city, forceRefresh)
                _uiState.value = WeatherUiState(report = report, isLoading = false)
            } catch (e: WeatherException) {
                _uiState.update { it.copy(isLoading = false, error = e) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[AndroidViewModelFactory.APPLICATION_KEY])
                WeatherViewModel(
                    repository = ServiceLocator.weatherRepository(app),
                    cityStore = ServiceLocator.cityStore(app)
                )
            }
        }
    }
}
