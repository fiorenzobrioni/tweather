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
     * every cache hit — the TTL is half of [WeatherFreshness]'s threshold, so a hit
     * cannot be stale.
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
    private val clock: Clock = Clock.systemUTC()
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

    @Volatile
    private var cacheTtl: Duration? = null // null = repository default

    @Volatile
    private var updateFrequencyMin: Int = DefaultUpdateFrequencyMin

    init {
        // The sync setting drives the repository cache TTL on the next load
        viewModelScope.launch {
            settingsStore.settings.collect {
                cacheTtl = Duration.ofMinutes(it.updateFrequencyMin.toLong())
                updateFrequencyMin = it.updateFrequencyMin
            }
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
                val fix = locationProvider.currentFix().toGpsCity()
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

    /** Background re-acquisition behind a just-rendered stale fix (cold start). */
    private fun revalidateFix() {
        gpsJob?.cancel()
        gpsJob = viewModelScope.launch {
            try {
                val fix = locationProvider.currentFix().toGpsCity()
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

    private fun load(city: City, forceRefresh: Boolean, clearReport: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                if (clearReport) WeatherUiState()
                else it.copy(isLoading = true, error = null, noLocation = false)
            }
            try {
                val ttl = cacheTtl
                val report = if (ttl != null) {
                    repository.getWeather(city, forceRefresh, ttl)
                } else {
                    repository.getWeather(city, forceRefresh)
                }
                _uiState.value = documentOf(report)
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
                    skySubscriptionStore = ServiceLocator.skySubscriptionStore(app)
                )
            }
        }
    }
}
