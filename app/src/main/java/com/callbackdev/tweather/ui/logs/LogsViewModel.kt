package com.callbackdev.tweather.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.data.WeatherRepository
import com.callbackdev.tweather.data.local.ForecastDiff
import com.callbackdev.tweather.data.local.SnapshotDiff
import com.callbackdev.tweather.data.local.WeatherHistoryEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

/** One history entry ready to render: header data plus its diff lines. */
data class CommitUi(
    val hash: String,
    val cityLabel: String,
    val author: String,
    val timestampEpochSeconds: Long,
    val isInitial: Boolean,
    val lines: List<SnapshotDiff.Line>
)

/**
 * One `weather_forecast.diff` revision ready to render. Same hash as the fetch's
 * history commit: in git terms one commit touched both files.
 */
data class ForecastRevisionUi(
    val hash: String,
    val cityLabel: String,
    val author: String,
    val timestampEpochSeconds: Long,
    val hunks: List<ForecastDiff.Hunk>
)

class LogsViewModel(repository: WeatherRepository, private val json: Json) : ViewModel() {

    val commits: StateFlow<List<CommitUi>> = repository.observeHistory()
        .map(::buildCommits)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val revisions: StateFlow<List<ForecastRevisionUi>> = repository.observeHistory()
        .map { buildForecastRevisions(it, json) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** [entries] arrive newest-first; each diffs against the next OLDER same-city one. */
    private fun buildCommits(entries: List<WeatherHistoryEntry>): List<CommitUi> =
        entries.mapIndexed { index, entry ->
            val previous = entries.subList(index + 1, entries.size)
                .firstOrNull { it.cityKey == entry.cityKey }
            val current = decode(entry.snapshotJson, json)
            val old = previous?.snapshotJson?.let { decode(it, json) }
            CommitUi(
                hash = entry.hash,
                cityLabel = entry.cityLabel,
                author = entry.author,
                timestampEpochSeconds = entry.timestampEpochSeconds,
                isInitial = old == null,
                lines = SnapshotDiff.compute(old, current)
            )
        }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[AndroidViewModelFactory.APPLICATION_KEY])
                LogsViewModel(
                    repository = ServiceLocator.weatherRepository(app),
                    json = Json
                )
            }
        }
    }
}

/**
 * [entries] arrive newest-first, possibly interleaved across cities; each city's
 * fetches run through [ForecastDiff] oldest-first (its baseline tracking needs
 * chronological order), then all cities' revisions merge newest-first. Rows from
 * before the `forecast_json` column (null) can't take part in any comparison and
 * are skipped: the first fetch after the update becomes the "new file" baseline.
 * Internal so tests exercise it without Room or a ViewModel.
 */
internal fun buildForecastRevisions(
    entries: List<WeatherHistoryEntry>,
    json: Json
): List<ForecastRevisionUi> =
    entries.filter { it.forecastJson != null }
        .groupBy { it.cityKey }
        .values
        .flatMap { cityEntries ->
            val oldestFirst = cityEntries.asReversed()
            val fetches = oldestFirst.map { entry ->
                ForecastDiff.Fetch(
                    timestampEpochSeconds = entry.timestampEpochSeconds,
                    forecast = decode(entry.forecastJson.orEmpty(), json)
                )
            }
            ForecastDiff.compute(fetches).map { revision ->
                val entry = oldestFirst[revision.fetchIndex]
                ForecastRevisionUi(
                    hash = entry.hash,
                    cityLabel = entry.cityLabel,
                    author = entry.author,
                    timestampEpochSeconds = entry.timestampEpochSeconds,
                    hunks = revision.hunks
                )
            }
        }
        .sortedByDescending { it.timestampEpochSeconds }

private fun decode(snapshotJson: String, json: Json): Map<String, String> =
    runCatching { json.decodeFromString<Map<String, String>>(snapshotJson) }
        .getOrDefault(emptyMap())
