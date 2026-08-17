package com.callbackdev.tweather.domain

import com.callbackdev.tweather.data.NotificationSettings
import com.callbackdev.tweather.domain.model.WeatherCondition
import com.callbackdev.tweather.domain.model.WeatherReport
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class AlertKind { SEVERE, PRECIPITATION, DAILY_SUMMARY }

/**
 * One notification-worthy finding. Domain data stays canonical English; the
 * notifier localizes the chrome (title) at render time, per the l10n rule.
 */
data class Alert(
    val kind: AlertKind,
    /** Dedup key persisted after a successful notify (see [AlertState]). */
    val fingerprint: String,
    val cityLabel: String,
    val condition: WeatherCondition? = null,
    /** Triggering hour, in the city's timezone (SEVERE, PRECIPITATION). */
    val at: LocalDateTime? = null,
    val precipPct: Int? = null,
    val highC: Double? = null,
    val lowC: Double? = null
)

/**
 * Recently notified fingerprints — what keeps hourly polling from re-notifying.
 * Severe and precipitation are per-kind sets rather than single slots: their
 * fingerprints embed the city, so a single slot would be clobbered every time the
 * evaluated city changes (alternating saved cities), re-notifying events already
 * notified. The daily summary is one per date across all cities, so a bare date
 * is enough.
 */
data class AlertState(
    val severeFingerprints: Set<String> = emptySet(),
    val precipFingerprints: Set<String> = emptySet(),
    val summaryDate: LocalDate? = null
)

/**
 * Pure alert evaluation: no clocks, no Android, no I/O — everything injected so
 * the rules are table-testable. `now` must be in the report's local timezone
 * (the worker derives it from `report.location.timezone`, never the device zone).
 */
object AlertEngine {

    /**
     * Hazard classes for severe weather. The bucket doubles as the fingerprint
     * component: a 95→96 evolution is the same storm, THUNDER→SNOW is news.
     * Keyed on the WMO code — descriptions collapse distinct codes.
     */
    enum class SevereBucket { THUNDER, ICE, RAIN, SNOW }

    val SevereCodes: Map<Int, SevereBucket> = mapOf(
        95 to SevereBucket.THUNDER, 96 to SevereBucket.THUNDER, 99 to SevereBucket.THUNDER,
        56 to SevereBucket.ICE, 57 to SevereBucket.ICE,
        66 to SevereBucket.ICE, 67 to SevereBucket.ICE,
        65 to SevereBucket.RAIN, 82 to SevereBucket.RAIN,
        75 to SevereBucket.SNOW, 86 to SevereBucket.SNOW
    )

    /** Long enough to warn before an evening storm seen at a morning poll. */
    const val SEVERE_LOOKAHEAD_HOURS = 12L

    /** "Take the umbrella" horizon — actionable, not noise. */
    const val PRECIP_LOOKAHEAD_HOURS = 6L
    const val PRECIP_THRESHOLD_PCT = 70

    /** The 12:00 cap stops a "today's summary" from landing in the evening. */
    val SummaryWindowStart: LocalTime = LocalTime.of(6, 0)
    val SummaryWindowEnd: LocalTime = LocalTime.of(12, 0)

    fun evaluate(
        report: WeatherReport,
        settings: NotificationSettings,
        state: AlertState,
        now: LocalDateTime,
        cityKey: String
    ): List<Alert> = buildList {
        val severe = if (settings.severeWeatherAlerts) {
            findSevere(report, state, now, cityKey)
        } else {
            null
        }
        severe?.let(::add)
        // A severe alert already covers its own rain — don't notify twice
        if (settings.precipitationWarning && severe == null) {
            findPrecipitation(report, state, now, cityKey)?.let(::add)
        }
        if (settings.dailySummary) {
            findDailySummary(report, state, now)?.let(::add)
        }
    }

    private fun findSevere(
        report: WeatherReport,
        state: AlertState,
        now: LocalDateTime,
        cityKey: String
    ): Alert? {
        val end = now.plusHours(SEVERE_LOOKAHEAD_HOURS)
        val hit = report.hourly.firstOrNull { hour ->
            !hour.time.isBefore(now) && !hour.time.isAfter(end) &&
                hour.condition.wmoCode in SevereCodes
        } ?: return null
        val bucket = SevereCodes.getValue(hit.condition.wmoCode)
        val fingerprint = "$cityKey:sev:${bucket.name}:${hit.time.toLocalDate()}"
        if (fingerprint in state.severeFingerprints) return null
        return Alert(
            kind = AlertKind.SEVERE,
            fingerprint = fingerprint,
            cityLabel = report.location.city,
            condition = hit.condition,
            at = hit.time,
            precipPct = hit.precipChancePct
        )
    }

    private fun findPrecipitation(
        report: WeatherReport,
        state: AlertState,
        now: LocalDateTime,
        cityKey: String
    ): Alert? {
        val end = now.plusHours(PRECIP_LOOKAHEAD_HOURS)
        val hit = report.hourly.firstOrNull { hour ->
            !hour.time.isBefore(now) && !hour.time.isAfter(end) &&
                hour.precipChancePct >= PRECIP_THRESHOLD_PCT
        } ?: return null
        // Half-day bucket: at most two rain warnings per day per city
        val halfDay = if (hit.time.hour < 12) "AM" else "PM"
        val fingerprint = "$cityKey:pre:${hit.time.toLocalDate()}:$halfDay"
        if (fingerprint in state.precipFingerprints) return null
        return Alert(
            kind = AlertKind.PRECIPITATION,
            fingerprint = fingerprint,
            cityLabel = report.location.city,
            condition = hit.condition,
            at = hit.time,
            precipPct = hit.precipChancePct
        )
    }

    private fun findDailySummary(
        report: WeatherReport,
        state: AlertState,
        now: LocalDateTime
    ): Alert? {
        val time = now.toLocalTime()
        if (time < SummaryWindowStart || time > SummaryWindowEnd) return null
        if (state.summaryDate == now.toLocalDate()) return null
        val today = report.daily.firstOrNull() ?: return null
        return Alert(
            kind = AlertKind.DAILY_SUMMARY,
            // ISO date: AlertStateStore stores it straight into summaryDate
            fingerprint = now.toLocalDate().toString(),
            cityLabel = report.location.city,
            condition = today.condition,
            precipPct = today.precipPct,
            highC = today.highC,
            lowC = today.lowC
        )
    }
}
