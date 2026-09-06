package com.callbackdev.tweather.notifications

import com.callbackdev.tweather.domain.AlertEngine
import com.callbackdev.tweather.domain.model.HourlyForecast
import java.time.LocalDateTime

/**
 * The stretch of hours an alert is actually about, read off the same forecast the
 * engine judged (Fase 25).
 *
 * An [com.callbackdev.tweather.domain.Alert] carries ONE hour — the first that
 * crossed the threshold — because that is all its fingerprint needs. A reader
 * deciding whether to move a bike ride needs the rest of the run: when it lets up,
 * how bad it gets at its worst, what the thermometer does meanwhile. Those are the
 * fields the expanded notification unfolds under the folded object; nothing in them
 * is computed for the occasion, they are the very hours already in the report.
 *
 * Chiaro grew this first (its Fase 6b) and this is deliberately the same arithmetic
 * on the same domain types, so the two apps cannot come to disagree about the shape
 * of one storm — the register they say it in is where they differ, and that is the
 * only place they should.
 */
data class AlertWindow(
    /** First hour of the run. */
    val start: LocalDateTime,
    /** Last hour of the run — equal to [start] when it is one hour long. */
    val end: LocalDateTime,
    val peakPrecipPct: Int,
    val peakPrecipAt: LocalDateTime,
    val lowC: Double,
    val highC: Double,
    /**
     * The run reached the end of the forecast rather than a calm hour, so [end] is
     * where the DATA stops, not where the weather does. The notification prints
     * `"to": null` for it: naming an end the forecast never showed would be the one
     * claim in the object the reader cannot check.
     */
    val openEnded: Boolean
) {
    val singleHour: Boolean get() = start == end
}

/** Pure: no clock, no resources, no Android — the notifier does the rendering. */
object AlertDetails {

    /** The run of severe hours containing [at], by the engine's own bucket table. */
    fun severeWindow(hours: List<HourlyForecast>, at: LocalDateTime): AlertWindow? =
        window(hours, at) { it.condition.wmoCode in AlertEngine.SevereCodes }

    /** The run of hours at or above the warning threshold containing [at]. */
    fun rainWindow(
        hours: List<HourlyForecast>,
        at: LocalDateTime,
        thresholdPct: Int = AlertEngine.PRECIP_THRESHOLD_PCT
    ): AlertWindow? = window(hours, at) { it.precipChancePct >= thresholdPct }

    /**
     * The maximal run of consecutive hours around [at] for which [holds] is true.
     * Null when [at] is not among [hours] at all — a cached report whose hours have
     * elapsed can outlive the alert built from it, and no window is the honest
     * answer there.
     */
    private fun window(
        hours: List<HourlyForecast>,
        at: LocalDateTime,
        holds: (HourlyForecast) -> Boolean
    ): AlertWindow? {
        val index = hours.indexOfFirst { it.time == at }.takeIf { it >= 0 } ?: return null
        if (!holds(hours[index])) return null
        var first = index
        while (first > 0 && holds(hours[first - 1])) first--
        var last = index
        while (last < hours.lastIndex && holds(hours[last + 1])) last++
        val run = hours.subList(first, last + 1)
        val peak = run.maxBy { it.precipChancePct }
        return AlertWindow(
            start = run.first().time,
            end = run.last().time,
            peakPrecipPct = peak.precipChancePct,
            peakPrecipAt = peak.time,
            lowC = run.minOf { it.tempC },
            highC = run.maxOf { it.tempC },
            openEnded = last == hours.lastIndex
        )
    }
}
