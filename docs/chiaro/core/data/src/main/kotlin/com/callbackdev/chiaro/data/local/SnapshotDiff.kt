package com.callbackdev.chiaro.data.local

/**
 * Diff between two flattened snapshots (see [WeatherSnapshots.flatten]) in git style:
 * a changed key emits the old value as a `-` line followed by the new value as a `+`
 * line; untouched keys are context. With no previous snapshot every line is an
 * addition (git's "new file"). Keys removed by schema changes trail at the end.
 */
object SnapshotDiff {

    enum class Type { CONTEXT, ADDED, REMOVED }

    data class Line(val type: Type, val key: String, val value: String)

    fun compute(previous: Map<String, String>?, current: Map<String, String>): List<Line> =
        buildList {
            current.forEach { (key, value) ->
                val old = previous?.get(key)
                when {
                    previous == null || old == null ->
                        add(Line(Type.ADDED, key, value))
                    old != value -> {
                        add(Line(Type.REMOVED, key, old))
                        add(Line(Type.ADDED, key, value))
                    }
                    else -> add(Line(Type.CONTEXT, key, value))
                }
            }
            previous?.forEach { (key, value) ->
                if (key !in current) add(Line(Type.REMOVED, key, value))
            }
        }
}
