package com.callbackdev.tweather.ui.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.domain.model.City
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExplorerUiState(
    val cities: List<City> = emptyList(),
    val activeCity: City? = null
)

class ExplorerViewModel(private val cityStore: CityStore) : ViewModel() {

    val uiState: StateFlow<ExplorerUiState> =
        combine(cityStore.cities, cityStore.activeCity, ::ExplorerUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExplorerUiState())

    fun select(city: City) {
        viewModelScope.launch { cityStore.setActive(city) }
    }

    fun remove(city: City) {
        viewModelScope.launch { cityStore.remove(city) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[AndroidViewModelFactory.APPLICATION_KEY])
                ExplorerViewModel(ServiceLocator.cityStore(app))
            }
        }
    }
}
