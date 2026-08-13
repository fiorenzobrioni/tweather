package com.callbackdev.tweather.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.data.WeatherRepository
import com.callbackdev.tweather.domain.WeatherException
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.WeatherReport
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
    private val city: City = DefaultCity
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    init {
        load(forceRefresh = false)
    }

    /** FAB action: bypasses the cache, so `last_sync` and history advance. */
    fun refresh() = load(forceRefresh = true)

    private fun load(forceRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val report = repository.getWeather(city, forceRefresh)
                _uiState.update { WeatherUiState(report = report, isLoading = false) }
            } catch (e: WeatherException) {
                _uiState.update { it.copy(isLoading = false, error = e) }
            }
        }
    }

    companion object {
        /**
         * The PRD's sample city, hard-wired until Fase 5 adds the Explorer with a
         * persisted, user-selectable city list.
         */
        val DefaultCity = City(
            id = 5_128_581, // GeoNames id, as Open-Meteo geocoding would return
            name = "New York",
            region = "NY",
            country = "USA",
            coordinates = Coordinates(40.7128, -74.0060),
            timezone = "America/New_York"
        )

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[AndroidViewModelFactory.APPLICATION_KEY])
                WeatherViewModel(ServiceLocator.weatherRepository(app))
            }
        }
    }
}
