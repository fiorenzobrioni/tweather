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
import com.callbackdev.tweather.domain.sky.SkyLead
import com.callbackdev.tweather.notifications.SkyAlarmScheduler
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
    /**
     * `notify_default` from `settings.config`: the lead a line uses when it carries
     * none of its own. Off by default, and then no line renders a `--notify` token.
     */
    val defaultLeadMinutes: Int? = null,
    /** The `$ tweather run sky` block, once the command has been confirmed. */
    val dryRun: List<String>? = null,
    /**
     * The file's sentences in the reader's language (Fase 18). Built once from the
     * application's resources and carried on the state, so the document — a pure
     * value — never has to know what a locale is.
     */
    val notes: SkyNotes = SkyNotes.EN
) {
    val document: SkyDocument? = context?.let {
        SkyDocumentBuilder.build(subscriptions, it, defaultLeadMinutes, notes)
    }
}

class SkyViewModel(
    private val store: SkySubscriptionStore,
    cityStore: CityStore,
    private val repository: WeatherRepository,
    private val settingsStore: SettingsStore,
    private val clock: Clock = Clock.systemUTC(),
    /** Re-arms the reminder alarm; no-op in tests, which have no AlarmManager. */
    private val onSubscriptionsChanged: suspend () -> Unit = {},
    /**
     * The file's sentences, already in the reader's language. Built once by the
     * factory from the application's resources: a per-app language change restarts
     * the app, so there is nothing to observe here.
     */
    private val notes: SkyNotes = SkyNotes.EN
) : ViewModel() {

    private val dryRun = MutableStateFlow<List<String>?>(null)

    val uiState: StateFlow<SkyUiState> = combine(
        store.subscriptions,
        cityStore.activeSource,
        settingsStore.settings.map { it.updateFrequencyMin to it.skyNotifyDefaultMin },
        // Every fetch that lands commits to the history, so the existing commit feed
        // doubles as "there is new weather to have an opinion about" — the same
        // signal the home widget already repaints on. This tab never fetches for
        // itself: it reads what the app has.
        repository.observeHistory(limit = 1),
        dryRun
    ) { subscriptions, source, (updateFrequencyMin, defaultLead), _, run ->
        SkyUiState(
            subscriptions = subscriptions,
            context = source.toSkyContext(updateFrequencyMin),
            defaultLeadMinutes = defaultLead,
            dryRun = run,
            notes = notes
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SkyUiState(notes = notes))

    /** True while the file has any line at all — the status bar's count. */
    val jobCount: StateFlow<Int> = store.subscriptions
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun toggleEnabled(subscription: SkySubscription) {
        edit { store.setEnabled(subscription.jobId, !subscription.enabled) }
    }

    fun remove(jobId: String) {
        edit { store.remove(jobId) }
    }

    fun add(jobId: String) {
        edit { store.add(jobId) }
    }

    /**
     * Cycles `off · 15m · 30m · 1h · 3h · 1d` on the line's `--notify` token.
     *
     * Cycles from the lead the line is SHOWING, which is its own when it has one and
     * `notify_default` when it does not: a token that jumped somewhere else on the
     * first tap would be a control disagreeing with the value printed next to it. The
     * result is always written explicitly, so from then on the line has its own.
     */
    fun cycleLead(subscription: SkySubscription) {
        edit {
            val shown = subscription.notifyLeadMinutes ?: uiState.value.defaultLeadMinutes
            store.setNotifyLead(subscription.jobId, SkyLead.ofMinutes(shown).next().minutes)
        }
    }

    /**
     * Every edit re-arms the alarm (Fase 16f): the plan is "the nearest reminder",
     * and adding, removing, commenting out or re-timing a line can change which one
     * that is. Cheap — one DataStore read and one `setAndAllowWhileIdle`.
     */
    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            onSubscriptionsChanged()
        }
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
        dryRun.value = SkyDocumentBuilder.dryRun(document, context, state.notes)
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
                    settingsStore = ServiceLocator.settingsStore(context),
                    onSubscriptionsChanged = { SkyAlarmScheduler.reschedule(context) },
                    notes = skyNotes(context.resources)
                )
            }
        }
    }
}
