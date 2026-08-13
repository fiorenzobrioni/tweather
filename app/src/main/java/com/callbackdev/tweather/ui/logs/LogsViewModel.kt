package com.callbackdev.tweather.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.data.WeatherRepository
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

class LogsViewModel(repository: WeatherRepository, private val json: Json) : ViewModel() {

    val commits: StateFlow<List<CommitUi>> = repository.observeHistory()
        .map(::buildCommits)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** [entries] arrive newest-first; each diffs against the next OLDER same-city one. */
    private fun buildCommits(entries: List<WeatherHistoryEntry>): List<CommitUi> =
        entries.mapIndexed { index, entry ->
            val previous = entries.subList(index + 1, entries.size)
                .firstOrNull { it.cityKey == entry.cityKey }
            val current = decode(entry.snapshotJson)
            val old = previous?.snapshotJson?.let(::decode)
            CommitUi(
                hash = entry.hash,
                cityLabel = entry.cityLabel,
                author = entry.author,
                timestampEpochSeconds = entry.timestampEpochSeconds,
                isInitial = old == null,
                lines = SnapshotDiff.compute(old, current)
            )
        }

    private fun decode(snapshotJson: String): Map<String, String> =
        runCatching { json.decodeFromString<Map<String, String>>(snapshotJson) }
            .getOrDefault(emptyMap())

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
