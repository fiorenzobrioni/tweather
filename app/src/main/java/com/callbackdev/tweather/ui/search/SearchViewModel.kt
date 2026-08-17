package com.callbackdev.tweather.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.data.SearchHistoryStore
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.data.WeatherRepository
import com.callbackdev.tweather.domain.WeatherException
import com.callbackdev.tweather.domain.model.City
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Results start flowing from this query length (the geocoding API's own minimum). */
private const val MIN_QUERY_LENGTH = 2
private const val DEBOUNCE_MS = 400L

data class SearchUiState(
    val query: String = "",
    val results: List<City> = emptyList(),
    val isSearching: Boolean = false,
    val error: WeatherException? = null
)

class SearchViewModel(
    private val repository: WeatherRepository,
    private val cityStore: CityStore,
    private val historyStore: SearchHistoryStore,
    private val savedState: SavedStateHandle
) : ViewModel() {

    // The query survives process death (the one screen state not derivable from a
    // DataStore); restoring it re-feeds queryFlow, so the results come back too.
    private val _uiState = MutableStateFlow(SearchUiState(query = savedState[KEY_QUERY] ?: ""))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val recentSearches: StateFlow<List<String>> = historyStore.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val queryFlow = MutableStateFlow(_uiState.value.query)
    private var searchJob: Job? = null

    /** Query already launched by [searchNow]; its own debounce echo is skipped once. */
    private var immediateQuery: String? = null

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            queryFlow
                .map { it.trim() }
                .debounce(DEBOUNCE_MS)
                .collect { query ->
                    // searchNow already ran exactly this query: re-running would cancel
                    // its in-flight request and pay the debounce again. Any other
                    // emission clears the marker — the query has moved on.
                    val alreadyRunning = query == immediateQuery
                    immediateQuery = null
                    if (!alreadyRunning) runSearch(query)
                }
        }
    }

    fun onQueryChange(query: String) {
        savedState[KEY_QUERY] = query
        _uiState.update { it.copy(query = query) }
        queryFlow.value = query
    }

    /** IME search action / recent-search tap: skips the debounce. */
    fun searchNow(query: String = _uiState.value.query) {
        savedState[KEY_QUERY] = query
        _uiState.update { it.copy(query = query) }
        queryFlow.value = query
        immediateQuery = query.trim()
        runSearch(query.trim())
    }

    /**
     * Search result tapped: save + activate the city, remember the search. The query
     * and its results are cleared because the search is finished — the city is now a
     * file in the Explorer. Leaving them would mean coming back to a stale query and
     * stale results that have to be deleted by hand before searching again.
     */
    fun select(city: City) {
        viewModelScope.launch {
            cityStore.add(city)
            historyStore.add(city.label)
        }
        searchJob?.cancel()
        savedState[KEY_QUERY] = ""
        immediateQuery = null
        queryFlow.value = ""
        _uiState.value = SearchUiState()
    }

    /** `$ history -c` — drops the recent searches only; saved cities are not history. */
    fun clearRecentSearches() {
        viewModelScope.launch { historyStore.clear() }
    }

    private fun runSearch(query: String) {
        searchJob?.cancel()
        if (query.length < MIN_QUERY_LENGTH) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false, error = null) }
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, error = null) }
            try {
                val results = repository.searchCities(query)
                _uiState.update { it.copy(results = results, isSearching = false) }
            } catch (e: WeatherException) {
                _uiState.update {
                    it.copy(results = emptyList(), isSearching = false, error = e)
                }
            }
        }
    }

    companion object {
        private const val KEY_QUERY = "search_query"

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[AndroidViewModelFactory.APPLICATION_KEY])
                SearchViewModel(
                    repository = ServiceLocator.weatherRepository(app),
                    cityStore = ServiceLocator.cityStore(app),
                    historyStore = ServiceLocator.searchHistoryStore(app),
                    savedState = createSavedStateHandle()
                )
            }
        }
    }
}
