package com.callbackdev.tweather.data.local

/**
 * Diff engine for `forecast.diff`: given one city's fetches oldest-first
 * (each carrying the [WeatherSnapshots.flattenForecast] map), emits a revision per
 * fetch that changed at least one field above threshold. Anti-noise rules decided
 * with the committente (Fase 9h):
 *
 * - Thresholds: temperatures 1 °C, precipitation probability 10 points, status any
 *   change — Open-Meteo re-runs its model constantly and a 0.1 °C wiggle is model
 *   noise, not a forecast revision.
 * - The baseline per target date is the last *shown* prediction, not the previous
 *   fetch: a slow drift below threshold accumulates against the baseline until it
 *   crosses, instead of vanishing one sub-threshold step at a time.
 * - A date seen for the first time is a git "new file" (all `+`, null baseline);
 *   a date sliding out of the horizon is silence — the day passing is not a
 *   forecast revision.
 */
object ForecastDiff {

    /** Field order is importance order, like the notification body. */
    private val FIELDS = listOf("status", "high_c", "low_c", "precip_pct")

    private const val TEMP_THRESHOLD_C = 1.0
    private const val PRECIP_THRESHOLD_PCT = 10.0

    data class Fetch(val timestampEpochSeconds: Long, val forecast: Map<String, String>)

    /**
     * One target date's changes inside a revision. [baselineEpochSeconds] is the
     * fetch time of the prediction being replaced, null for a first appearance.
     * [lines] carry bare field keys (`high_c`, not `2026-08-18.high_c`).
     */
    data class Hunk(
        val date: String,
        val dayLabel: String,
        val baselineEpochSeconds: Long?,
        val lines: List<SnapshotDiff.Line>
    )

    /** [fetchIndex] points back into the input list (its entry owns hash/label). */
    data class Revision(val fetchIndex: Int, val hunks: List<Hunk>)

    fun compute(fetches: List<Fetch>): List<Revision> {
        val baselines = mutableMapOf<String, Fetch>()
        return buildList {
            fetches.forEachIndexed { index, fetch ->
                val dates = fetch.forecast.keys.map { it.substringBefore('.') }
                    .distinct().sorted()
                val hunks = dates.mapNotNull { date ->
                    hunkFor(date, dayLabel(dates, date), fetch, baselines)
                }
                if (hunks.isNotEmpty()) add(Revision(index, hunks))
            }
        }
    }

    private fun hunkFor(
        date: String,
        dayLabel: String,
        fetch: Fetch,
        baselines: MutableMap<String, Fetch>
    ): Hunk? {
        val current = fieldsOf(fetch.forecast, date)
        val baseline = baselines[date]
        if (baseline == null) {
            baselines[date] = fetch
            return Hunk(
                date = date,
                dayLabel = dayLabel,
                baselineEpochSeconds = null,
                lines = current.map { (key, value) ->
                    SnapshotDiff.Line(SnapshotDiff.Type.ADDED, key, value)
                }
            )
        }
        val old = fieldsOf(baseline.forecast, date)
        val changedKeys = current.filter { (key, value) -> crossesThreshold(key, old[key], value) }
            .keys
        if (changedKeys.isEmpty()) return null
        // Any crossing resets the whole day's baseline: context lines show the
        // values the next comparison will run against.
        baselines[date] = fetch
        return Hunk(
            date = date,
            dayLabel = dayLabel,
            baselineEpochSeconds = baseline.timestampEpochSeconds,
            lines = buildList {
                current.forEach { (key, value) ->
                    when {
                        key in changedKeys && old[key] != null -> {
                            add(SnapshotDiff.Line(SnapshotDiff.Type.REMOVED, key, old.getValue(key)))
                            add(SnapshotDiff.Line(SnapshotDiff.Type.ADDED, key, value))
                        }
                        key in changedKeys ->
                            add(SnapshotDiff.Line(SnapshotDiff.Type.ADDED, key, value))
                        else ->
                            add(SnapshotDiff.Line(SnapshotDiff.Type.CONTEXT, key, value))
                    }
                }
            }
        )
    }

    /** Known fields in importance order, then any future extras in map order. */
    private fun fieldsOf(forecast: Map<String, String>, date: String): Map<String, String> {
        val bare = forecast.filterKeys { it.startsWith("$date.") }
            .mapKeys { (key, _) -> key.substringAfter('.') }
        return buildMap {
            FIELDS.forEach { field -> bare[field]?.let { put(field, it) } }
            bare.forEach { (key, value) -> putIfAbsent(key, value) }
        }
    }

    private fun crossesThreshold(key: String, old: String?, new: String): Boolean {
        if (old == null) return true
        val threshold = when (key) {
            "high_c", "low_c" -> TEMP_THRESHOLD_C
            "precip_pct" -> PRECIP_THRESHOLD_PCT
            else -> return old != new
        }
        val oldValue = old.toDoubleOrNull()
        val newValue = new.toDoubleOrNull()
        if (oldValue == null || newValue == null) return old != new
        return kotlin.math.abs(newValue - oldValue) >= threshold
    }

    /**
     * The horizon only ever holds tomorrow and the day after (city-local at fetch
     * time), so the earliest date in this fetch is tomorrow — no timezone needed
     * at render time.
     */
    private fun dayLabel(sortedDates: List<String>, date: String): String =
        if (sortedDates.indexOf(date) == 0) "tomorrow" else "in 2 days"
}
