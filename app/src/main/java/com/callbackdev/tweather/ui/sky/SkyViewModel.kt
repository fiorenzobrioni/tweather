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
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    val context: SkyContext? = null
) {
    val document: SkyDocument? = context?.let { SkyDocumentBuilder.build(subscriptions, it) }
}

class SkyViewModel(
    private val store: SkySubscriptionStore,
    cityStore: CityStore,
    private val clock: Clock = Clock.systemUTC()
) : ViewModel() {

    val uiState: StateFlow<SkyUiState> = combine(
        store.subscriptions,
        cityStore.activeSource
    ) { subscriptions, source ->
        SkyUiState(subscriptions = subscriptions, context = source.toSkyContext())
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

    private fun ActiveSource.toSkyContext(): SkyContext? {
        val city = when (this) {
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
            now = clock.instant()
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val context = this[AndroidViewModelFactory.APPLICATION_KEY]!!
                SkyViewModel(
                    store = ServiceLocator.skySubscriptionStore(context),
                    cityStore = ServiceLocator.cityStore(context)
                )
            }
        }
    }
}
