package com.callbackdev.tweather.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.callbackdev.tweather.data.ActiveSource
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.data.LocationProvider
import com.callbackdev.tweather.data.MainEditorFile
import com.callbackdev.tweather.data.PowerSaveState
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.data.DefaultUpdateFrequencyMin
import com.callbackdev.tweather.data.SettingsStore
import com.callbackdev.tweather.data.SkySubscriptionStore
import com.callbackdev.tweather.data.WeatherRepository
import com.callbackdev.tweather.data.WorkspaceStore
import com.callbackdev.tweather.domain.WeatherException
import com.callbackdev.tweather.domain.WeatherFreshness
import com.callbackdev.tweather.domain.WeatherRecency
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.WeatherReport
import com.callbackdev.tweather.domain.model.toGpsCity
import com.callbackdev.tweather.ui.sky.SkyContext
import com.callbackdev.tweather.ui.sky.SkyReadme
import com.callbackdev.tweather.ui.sky.SkySummary
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the main screen. [report] survives refresh failures so the last good
 * document stays on screen with the error rendered as comment lines above it.
 */
data class WeatherUiState(
    val report: WeatherReport? = null,
    val isLoading: Boolean = true,
    val error: WeatherException? = null,
    /** True while waiting for a GPS fix (rendered as its own comment line). */
    val acquiringFix: Boolean = false,
    /**
     * No location configured at all (Fase 14b): not an error and not a load — there
     * is simply nothing to fetch, and the document says so instead of staying blank.
     */
    val noLocation: Boolean = false,
    /**
     * How far behind [report] is, when the app has decided it is no longer current
     * (Fase 17): `null` while it counts as fresh, which is every successful fetch and
     * every cache hit — the TTL is [WeatherFreshness.ProviderResolution] and the
     * shortest threshold this object can return is twice it, so a hit cannot be stale.
     *
     * Non-null means the document below the error lines is the last fetch that
     * worked, and both renderers say so before printing a single number.
     */
    val staleFor: Duration? = null
)

class WeatherViewModel(
    private val repository: WeatherRepository,
    private val cityStore: CityStore,
    settingsStore: SettingsStore,
    private val locationProvider: LocationProvider,
    private val workspaceStore: WorkspaceStore,
    private val skySubscriptionStore: SkySubscriptionStore,
    private val clock: Clock = Clock.systemUTC(),
    private val powerSave: PowerSaveState = PowerSaveState.Off
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    /** How the document renders: detail level and units, straight from Settings. */
    val displayOptions: StateFlow<DisplayOptions> = settingsStore.settings
        .map {
            DisplayOptions(
                showDetails = it.showDetails,
                temperature = it.units.temperature,
                windSpeed = it.units.windSpeed
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DisplayOptions())

    /**
     * The main tab bar's active file, persisted as editor workspace state (Fase
     * 10): like a real editor, the app reopens on the file you left it on.
     * Eagerly so a persisted README selection lands before the first frame.
     */
    val activeFile: StateFlow<MainEditorFile> = workspaceStore.mainActiveFile
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainEditorFile.JSON)

    /**
     * `sky.enabled` from `settings.config` (Fase 16c): whether the strip draws a
     * third tab at all. Eagerly like [activeFile] — a tab that appears one frame
     * after the others reads as a glitch, and the strip's width jumping is worse.
     */
    val skyEnabled: StateFlow<Boolean> = settingsStore.settings
        .map { it.skyEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun selectFile(file: MainEditorFile) {
        viewModelScope.launch { workspaceStore.setMainActiveFile(file) }
    }

    /** Fase 14d: the `HELP.md` pointer, until it is used or dismissed. */
    val showHelpHint: StateFlow<Boolean> = workspaceStore.helpHintDismissed
        .map { !it }
        // Eagerly like activeFile: a hint that appears one frame late reads as a glitch
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun dismissHelpHint() {
        viewModelScope.launch { workspaceStore.dismissHelpHint() }
    }

    /**
     * What the sky adds to `README.md` (Fase 16e), or null when the module is off.
     *
     * Built here rather than inside the document so the README stays a pure
     * rendering of a report plus a summary — and so the SAME summary can be compared
     * against `sky.crontab` in a test, which is the agreement rule of `VISION_SKY.md`
     * §9.1 turned into an assertion.
     */
    val skySummary: StateFlow<SkySummary?> = combine(
        uiState,
        skyEnabled,
        skySubscriptionStore.subscriptions
    ) { state, enabled, subscriptions ->
        val report = state.report
        if (!enabled || report == null) return@combine null
        val zone = runCatching { ZoneId.of(report.location.timezone) }
            .getOrElse { ZoneId.systemDefault() }
        SkyReadme.summarize(
            SkyContext(
                cityLabel = report.location.city,
                coordinates = report.location.coordinates,
                zone = zone,
                now = clock.instant(),
                report = report,
                updateFrequencyMin = updateFrequencyMin
            ),
            subscriptions
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var city: City? = null
    private var loadJob: Job? = null
    private var gpsJob: Job? = null
    private var isGpsSource = false

    /**
     * Identity of what's on screen: id AND cacheKey, so a GPS fix that moves under
     * the stable sentinel id still triggers a reload (id alone would miss it).
     */
    private var currentKey: String? = null

    /**
     * The BACKGROUND polling interval, read for one purpose only: how old a report
     * has to be before the document says so ([WeatherFreshness.isStale]).
     *
     * It used to drive the repository's cache TTL as well (Fase 25 took that away) —
     * which quietly made a battery setting decide how old `## Current` may be with
     * the reader looking straight at it: an hour by default, two at the top of the
     * range. The TTL is the provider's own resolution now and lives in the
     * repository; this stays what its name says.
     */
    @Volatile
    private var updateFrequencyMin: Int = DefaultUpdateFrequencyMin

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { updateFrequencyMin = it.updateFrequencyMin }
        }
        // Follow the Explorer's selection: every change of active source reloads
        // the document (cache-friendly — an unexpired city comes back as a HIT).
        viewModelScope.launch {
            cityStore.activeSource.collect { active ->
                isGpsSource = active is ActiveSource.Gps
                when (active) {
                    is ActiveSource.Saved -> {
                        gpsJob?.cancel()
                        switchTo(active.city)
                    }
                    ActiveSource.None -> {
                        // Nothing to fetch and nothing to wait for: cancel whatever
                        // the previous source left running and let the editor say it.
                        gpsJob?.cancel()
                        loadJob?.cancel()
                        currentKey = null
                        city = null
                        _uiState.value = WeatherUiState(isLoading = false, noLocation = true)
                    }
                    is ActiveSource.Gps -> {
                        val lastFix = active.lastFix
                        if (lastFix == null) {
                            // First selection ever: nothing to show until a fix lands
                            if (currentKey != GpsPendingKey) {
                                currentKey = GpsPendingKey
                                city = null
                                loadJob?.cancel()
                                acquireAndLoad(forceRefresh = false)
                            }
                        } else if (switchTo(lastFix)) {
                            // Stale-while-revalidate: the persisted fix renders now,
                            // the real position catches up in the background.
                            revalidateFix()
                        }
                    }
                }
            }
        }
    }

    /** Loads [target] if it isn't what's on screen already; true when it loaded. */
    private fun switchTo(target: City): Boolean {
        if (currentKey == target.sourceKey) return false
        currentKey = target.sourceKey
        city = target
        load(target, forceRefresh = false, clearReport = true)
        return true
    }

    /** FAB action: bypasses the cache, so `last_sync` and history advance. GPS
     * source re-acquires the position first — that's what "refresh" means there. */
    fun refresh() {
        if (isGpsSource) {
            acquireAndLoad(forceRefresh = true)
        } else {
            city?.let { load(it, forceRefresh = true, clearReport = false) }
        }
    }

    /**
     * The editor came back to the foreground (Fase 25).
     *
     * Nothing used to happen here at all: the document was built once, at the load
     * that produced it, and then aged on screen — an app left open at 09:00 and
     * unlocked at 11:00 still printed 09:00's `## Current`, still trimmed
     * `## Next hours` against a clock two hours slow, and had not even recomputed
     * whether to say `// stale`. Fifteen minutes past the last fetch this re-reads;
     * inside them it is a cache HIT that costs no network and still rebuilds the
     * document against the real now, which is half of what was missing.
     *
     * Deliberately SILENT — no `// fetching…`, no spinning FAB: an automatic read has
     * nothing to announce, and announcing it on every unlock would make the document
     * jump twice for something the reader did not ask for. Chiaro's `userRefreshing`
     * draws the same line for the same reason.
     *
     * Does nothing before the first document lands, and nothing while a load is in
     * flight: cold start already fetches, and the first ON_RESUME arrives right on
     * top of it.
     *
     * A GPS source is re-read at its LAST FIX and never re-acquires the position:
     * that is what the FAB means there ([refresh]) and what `revalidateFix` does once
     * per selection. Taking a fix on every unlock is the cost the whole location
     * strategy is written to avoid.
     *
     * Under battery saver the NETWORK half is dropped and the other half is not, and
     * the split is the point. A fetch nobody asked for out loud is exactly the work
     * that mode exists to postpone — and the FAB is still one tap away, because an
     * explicit request is never quietly ignored. But rebuilding the document against
     * the real clock costs no radio and no disk, only the arithmetic of
     * [WeatherRecency.trim] and [WeatherFreshness], and skipping THAT would leave the
     * editor printing hours that are over and staying silent about being behind.
     * Saving battery is not a licence to let the file lie: under saver the reader gets
     * the same data, correctly trimmed, with `// stale` when it is due.
     */
    fun onResumed() {
        if (loadJob?.isActive == true || gpsJob?.isActive == true) return
        val state = _uiState.value
        if (state.report == null) return
        if (powerSave.isOn()) {
            _uiState.value = documentOf(state.report, state.error)
            return
        }
        city?.let { load(it, forceRefresh = false, clearReport = false, announce = false) }
    }

    /**
     * Fresh fix, then fetch. [currentKey] is set before [CityStore.updateGpsCity]
     * so the resulting flow emission is a no-op in the collector (no double load).
     */
    private fun acquireAndLoad(forceRefresh: Boolean) {
        gpsJob?.cancel()
        gpsJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, acquiringFix = true, error = null, noLocation = false)
            }
            try {
                // The reader asked out loud (first selection, or the FAB): nothing
                // the system already holds will do.
                val fix = locationProvider.currentFix(maxAge = LocationProvider.Now)
                    .toGpsCity()
                currentKey = fix.sourceKey
                city = fix
                _uiState.update { it.copy(acquiringFix = false) }
                cityStore.updateGpsCity(fix)
                load(fix, forceRefresh, clearReport = false)
            } catch (e: WeatherException) {
                _uiState.update { it.copy(isLoading = false, acquiringFix = false, error = e) }
            }
        }
    }

    /**
     * Background re-acquisition behind a just-rendered stale fix (cold start).
     *
     * Nobody is watching this one, so since Fase 20 it asks for a position that may
     * be a few minutes old — which the system usually already has, at no cost in
     * radio — and waits eight seconds rather than fifteen for one it does not.
     */
    private fun revalidateFix() {
        gpsJob?.cancel()
        gpsJob = viewModelScope.launch {
            try {
                val fix = locationProvider.currentFix(
                    maxAge = LocationProvider.SilentMaxAge,
                    timeout = LocationProvider.SilentTimeout
                ).toGpsCity()
                if (fix.sourceKey != currentKey) {
                    currentKey = fix.sourceKey
                    city = fix
                    cityStore.updateGpsCity(fix)
                    load(fix, forceRefresh = false, clearReport = true)
                } else {
                    cityStore.updateGpsCity(fix) // reverse geocode may improve the name
                }
            } catch (e: WeatherException) {
                // Keep the stale report; surface the error once the load settled
                loadJob?.join()
                _uiState.update { it.copy(error = e) }
            }
        }
    }

    /**
     * [announce] false is the silent read of [onResumed]: the state is left exactly as
     * it is until the new document replaces it, so the FAB does not spin and the two
     * `// fetching…` lines never appear. It also leaves any error line standing until
     * this attempt has something better to say about it.
     */
    private fun load(
        city: City,
        forceRefresh: Boolean,
        clearReport: Boolean,
        announce: Boolean = true
    ) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (announce) {
                _uiState.update {
                    if (clearReport) WeatherUiState()
                    else it.copy(isLoading = true, error = null, noLocation = false)
                }
            }
            try {
                _uiState.value = documentOf(repository.getWeather(city, forceRefresh))
            } catch (e: WeatherException) {
                _uiState.value = documentOf(lastKnown(city), e)
            }
        }
    }

    /**
     * The document to show when a fetch just failed (Fase 17).
     *
     * What is already on screen wins — a failed manual refresh has never blanked the
     * page. What changed is the OTHER case: a cold start or a city switch with no
     * network used to leave `README.md` with two comment lines and nothing else, on a
     * phone that had a full week of forecast sitting in [ReportDiskCache]. The home
     * widget had this right since Fase 9d (it keeps its last snapshot and marks it
     * `# stale`); the editor, with a whole screen to explain itself in, threw the data
     * away.
     *
     * Null when there is genuinely nothing to show: no entry, or one whose forecast no
     * longer reaches the present, which is the honest expiry of a cached report and is
     * read off the data itself ([WeatherRecency.coversNow]) rather than off a constant.
     */
    private suspend fun lastKnown(city: City): WeatherReport? =
        _uiState.value.report
            ?: repository.cachedReport(city)?.takeIf { WeatherRecency.coversNow(it, clock.instant()) }

    /**
     * A report as the editor renders it: trimmed to the hours and days that have not
     * already happened, and carrying its age when it is no longer current.
     *
     * The trim runs on EVERY report, not only on a recovered one, because it is a
     * no-op for a fetch that just landed and a genuine fix for a cache hit: with
     * `update_frequency_min = 120` a hit can be 119 minutes old, and `## Next hours`
     * opened with two hours that were over.
     */
    private fun documentOf(report: WeatherReport?, error: WeatherException? = null): WeatherUiState {
        if (report == null) return WeatherUiState(isLoading = false, error = error)
        val now = clock.instant()
        val lastSync = report.systemInfo.lastSync
        return WeatherUiState(
            report = WeatherRecency.trim(report, now),
            isLoading = false,
            error = error,
            staleFor = Duration.between(lastSync, now)
                .takeIf { WeatherFreshness.isStale(lastSync, updateFrequencyMin, now) }
        )
    }

    companion object {
        private const val GpsPendingKey = "gps:pending"

        private val City.sourceKey: String get() = "$id:$cacheKey"

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[AndroidViewModelFactory.APPLICATION_KEY])
                WeatherViewModel(
                    repository = ServiceLocator.weatherRepository(app),
                    cityStore = ServiceLocator.cityStore(app),
                    settingsStore = ServiceLocator.settingsStore(app),
                    locationProvider = ServiceLocator.locationProvider(app),
                    workspaceStore = ServiceLocator.workspaceStore(app),
                    skySubscriptionStore = ServiceLocator.skySubscriptionStore(app),
                    powerSave = PowerSaveState.of(app)
                )
            }
        }
    }
}
