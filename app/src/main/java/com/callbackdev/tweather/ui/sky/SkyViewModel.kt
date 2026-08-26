package com.callbackdev.tweather.ui.sky

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.callbackdev.tweather.data.ActiveSource
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.data.SkySubscription
import com.callbackdev.tweather.data.SkySubscriptionStore
import com.callbackdev.tweather.data.SettingsStore
import com.callbackdev.tweather.data.WeatherRepository
import com.callbackdev.tweather.domain.model.City
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State of `sky.crontab` (Fase 16c): the subscribed lines plus the place they
 * resolve against.
 *
 * [context] is null when there is no location configured (Fase 14b). The schedule
 * needs a latitude and nothing else — no network, no fetch — so this tab is the one
 * that would still work offline, and it is exactly as blank as the editor's other
 * two when the app does not know where you are. An empty schedule with no city would
 * be the first fabricated thing in the module.
 */
data class SkyUiState(
    val subscriptions: List<SkySubscription> = emptyList(),
    val context: SkyContext? = null,
    /** The `$ tweather run sky` block, once the command has been confirmed. */
    val dryRun: List<String>? = null
) {
    val document: SkyDocument? = context?.let { SkyDocumentBuilder.build(subscriptions, it) }
}

class SkyViewModel(
    private val store: SkySubscriptionStore,
    cityStore: CityStore,
    private val repository: WeatherRepository,
    settingsStore: SettingsStore,
    private val clock: Clock = Clock.systemUTC()
) : ViewModel() {

    private val dryRun = MutableStateFlow<List<String>?>(null)

    val uiState: StateFlow<SkyUiState> = combine(
        store.subscriptions,
        cityStore.activeSource,
        settingsStore.settings.map { it.updateFrequencyMin },
        // Every fetch that lands commits to the history, so the existing commit feed
        // doubles as "there is new weather to have an opinion about" — the same
        // signal the home widget already repaints on. This tab never fetches for
        // itself: it reads what the app has.
        repository.observeHistory(limit = 1),
        dryRun
    ) { subscriptions, source, updateFrequencyMin, _, run ->
        SkyUiState(
            subscriptions = subscriptions,
            context = source.toSkyContext(updateFrequencyMin),
            dryRun = run
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SkyUiState())

    /** True while the file has any line at all — the status bar's count. */
    val jobCount: StateFlow<Int> = store.subscriptions
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun toggleEnabled(subscription: SkySubscription) {
        viewModelScope.launch { store.setEnabled(subscription.jobId, !subscription.enabled) }
    }

    fun remove(jobId: String) {
        viewModelScope.launch { store.remove(jobId) }
    }

    fun add(jobId: String) {
        viewModelScope.launch { store.add(jobId) }
    }

    /**
     * `$ tweather run sky`: evaluate every enabled job against the forecast already
     * in hand and print the results inline. Sends nothing, touches no state, writes
     * no run record — the same contract as `$ tweather run rules`.
     */
    fun runSky() {
        val state = uiState.value
        val document = state.document ?: return
        val context = state.context ?: return
        dryRun.value = SkyDocumentBuilder.dryRun(document, context)
    }

    /** Any edit invalidates the block: a dry run is a snapshot, not a live panel. */
    fun clearDryRun() {
        dryRun.value = null
    }

    private suspend fun ActiveSource.toSkyContext(updateFrequencyMin: Int): SkyContext? {
        val city: City = when (this) {
            is ActiveSource.Saved -> city
            is ActiveSource.Gps -> lastFix
            ActiveSource.None -> null
        } ?: return null
        return SkyContext(
            cityLabel = city.label,
            coordinates = city.coordinates,
            // The CITY's zone, falling back to the phone's only when the provider
            // never told us one. A sunrise in Tokyo shown on Rome's clock is the file
            // lying, so the fallback is the last resort and not the default.
            zone = runCatching { ZoneId.of(city.timezone) }.getOrElse { ZoneId.systemDefault() },
            now = clock.instant(),
            report = repository.cachedReport(city),
            updateFrequencyMin = updateFrequencyMin
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val context = this[AndroidViewModelFactory.APPLICATION_KEY]!!
                SkyViewModel(
                    store = ServiceLocator.skySubscriptionStore(context),
                    cityStore = ServiceLocator.cityStore(context),
                    repository = ServiceLocator.weatherRepository(context),
                    settingsStore = ServiceLocator.settingsStore(context)
                )
            }
        }
    }
}
