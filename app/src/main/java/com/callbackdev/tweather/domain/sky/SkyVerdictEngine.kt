package com.callbackdev.tweather.domain.sky

import com.callbackdev.tweather.domain.AlertEngine
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.HourlyForecast
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** The verdict vocabulary, shared with the series: `✓ pass`, `~ unstable`, `✗ fail`. */
enum class SkyVerdictKind(val glyph: String, val word: String) {
    PASS("✓", "pass"),
    UNSTABLE("~", "unstable"),
    FAIL("✗", "fail"),

    /**
     * The app is allowed not to know. Never an optimistic guess and never a blank
     * cell: the reason travels with it.
     */
    UNKNOWN("?", "unknown")
}

/** What decided the verdict, when it was not the cloud number on its own. */
enum class SkyVerdictNote {
    /** Rain is likely enough to matter, whatever the sky is doing. */
    PRECIPITATION,

    /** A bright moon is up: the sky is clear and still not dark (`VISION_SKY.md` §6). */
    MOONLIGHT,

    /** The event is past the last hour the forecast covers. */
    BEYOND_HORIZON,

    /** No fetch has landed yet — a fresh install, or an editor tab never opened. */
    NO_DATA,

    /** The last fetch is old enough that it is no longer a claim about now. */
    STALE_DATA,

    /** There is a forecast, but no hour of it covers this event. */
    NO_COVERAGE
}

/**
 * The verdict on one resolved occurrence: whether the sky will let the job run.
 *
 * [cloudPct] and [precipPct] are the numbers the verdict was built from, carried so
 * the file can PRINT them. `VISION_SKY.md` §7 asks for exactly that — a verdict whose
 * evidence is invisible is an opinion, and this app does not render opinions.
 */
data class SkyVerdict(
    val kind: SkyVerdictKind,
    val cloudPct: Int? = null,
    val precipPct: Int? = null,
    val note: SkyVerdictNote? = null,
    /** Illumination of the moon at the event, 0..100, when [note] is MOONLIGHT. */
    val moonPct: Int? = null
) {
    val isKnown: Boolean get() = kind != SkyVerdictKind.UNKNOWN
}

/**
 * Resolved event + hourly forecast + moon → verdict (Fase 16d).
 *
 * Pure, like every other engine in this app: no clock, no Android, no repository. It
 * is handed the hours the app already has and answers about one occurrence.
 *
 * **A verdict is a forecast, not an observation.** The app never sees the sky. A
 * `✓ pass` means the reported cloud cover for those hours was 8 %, and the file says
 * so next to it — it never means "you saw it". Verdicts are recomputed on read and
 * never frozen: the one shown for a future event is the current forecast's opinion
 * and will change.
 *
 * **The thresholds are constants here and printed in the file**, not keys in
 * `settings.config`. The requirement (`VISION_SKY.md` §7) was that they not be
 * invisible, and `sky.crontab`'s comment channel — where this app puts the facts it
 * knows — discharges that at a tenth of the settings surface.
 */
object SkyVerdictEngine {

    /** At or below: the sky is clear enough to call it a pass. */
    const val CLOUD_PASS_PCT = 25

    /** Above: a fail. Between the two: unstable. */
    const val CLOUD_FAIL_PCT = 65

    /** Rain probability that makes an otherwise clear window unstable. */
    const val PRECIP_UNSTABLE_PCT = 40

    /**
     * Rain probability that fails the event outright. Deliberately the same number
     * the builtin precipitation alert already uses: "it is going to rain" should mean
     * one thing across the app, and a sky job disagreeing with a notification about
     * the same hour would be the app arguing with itself.
     */
    const val PRECIP_FAIL_PCT = AlertEngine.PRECIP_THRESHOLD_PCT

    /**
     * Illumination above which a moon that is UP washes out a dark-sky event. Sixty
     * per cent is a bit past first quarter — the point where the light stops being
     * something you can observe around.
     */
    const val MOON_WASH_PCT = 60

    /**
     * The verdict on [job]'s occurrence running from [start] to [end] (null for an
     * instant job).
     *
     * [dataAge] is null when there is no report at all, which is a different `?` from
     * a report too old to trust — the file says which.
     */
    fun evaluate(
        job: SkyJob,
        start: Instant,
        end: Instant?,
        hours: List<HourlyForecast>,
        zone: ZoneId,
        coordinates: Coordinates,
        dataAge: Duration?,
        staleAfter: Duration
    ): SkyVerdict {
        if (dataAge == null) return SkyVerdict(SkyVerdictKind.UNKNOWN, note = SkyVerdictNote.NO_DATA)
        // Stale data does not get to hold an opinion. Printing the last known verdict
        // would be the app answering a question about tonight with what it thought
        // yesterday, in the same words it uses when it knows.
        if (dataAge > staleAfter) {
            return SkyVerdict(SkyVerdictKind.UNKNOWN, note = SkyVerdictNote.STALE_DATA)
        }

        val window = window(start, end, hours, zone)
            ?: return SkyVerdict(SkyVerdictKind.UNKNOWN, note = horizonNote(start, hours, zone))

        // Mean over the window for cloud (the event is the whole window, not one
        // minute of it) and MAX for rain: an hour of it inside a two-hour window is
        // not averaged away, it is the thing that ruins the event.
        val clouds = window.mapNotNull { it.cloudCoverPct }
        if (clouds.isEmpty()) {
            return SkyVerdict(SkyVerdictKind.UNKNOWN, note = SkyVerdictNote.NO_COVERAGE)
        }
        val cloudPct = clouds.average().roundToInt()
        val precipPct = window.maxOf { it.precipChancePct }

        val fromWeather = when {
            precipPct >= PRECIP_FAIL_PCT ->
                SkyVerdict(SkyVerdictKind.FAIL, cloudPct, precipPct, SkyVerdictNote.PRECIPITATION)
            cloudPct > CLOUD_FAIL_PCT -> SkyVerdict(SkyVerdictKind.FAIL, cloudPct, precipPct)
            precipPct >= PRECIP_UNSTABLE_PCT ->
                SkyVerdict(SkyVerdictKind.UNSTABLE, cloudPct, precipPct, SkyVerdictNote.PRECIPITATION)
            cloudPct > CLOUD_PASS_PCT -> SkyVerdict(SkyVerdictKind.UNSTABLE, cloudPct, precipPct)
            else -> SkyVerdict(SkyVerdictKind.PASS, cloudPct, precipPct)
        }
        return if (job.needsDarkness) withMoon(fromWeather, start, end, coordinates) else fromWeather
    }

    /**
     * The moon condition (`VISION_SKY.md` §6), and the one place this module is
     * genuinely more useful than a weather app: a Geminid peak under a full moon is a
     * failed build under a perfectly clear sky. It can only make a verdict WORSE, and
     * when it does the note names the moon — telling somebody the clouds ruined a
     * night the moon ruined would be a different lie of the same size.
     */
    private fun withMoon(
        verdict: SkyVerdict,
        start: Instant,
        end: Instant?,
        coordinates: Coordinates
    ): SkyVerdict {
        if (verdict.kind == SkyVerdictKind.FAIL) return verdict
        // Sampled across the window, not at its start: a moon that sets an hour in
        // leaves most of the night usable, and one that rises does the opposite.
        val samples = samplesOf(start, end)
        val up = samples.filter { AstronomyEngine.moonAltitude(it, coordinates) > 0 }
        if (up.isEmpty()) return verdict
        val illumination = up.maxOf { AstronomyEngine.moonIllumination(it).illuminatedFraction }
        val moonPct = (illumination * 100).roundToInt()
        if (moonPct < MOON_WASH_PCT) return verdict
        return verdict.copy(
            kind = SkyVerdictKind.UNSTABLE,
            note = SkyVerdictNote.MOONLIGHT,
            moonPct = moonPct
        )
    }

    private fun samplesOf(start: Instant, end: Instant?): List<Instant> {
        if (end == null || !end.isAfter(start)) return listOf(start)
        val step = Duration.between(start, end).dividedBy(MOON_SAMPLES.toLong() - 1)
        return List(MOON_SAMPLES) { start.plus(step.multipliedBy(it.toLong())) }
    }

    /**
     * The hourly buckets the event spans: the one containing an instant, or every
     * bucket from the start's hour to the end's for a window.
     */
    private fun window(
        start: Instant,
        end: Instant?,
        hours: List<HourlyForecast>,
        zone: ZoneId
    ): List<HourlyForecast>? {
        if (hours.isEmpty()) return null
        val from = start.atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS)
        val to = (end ?: start).atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS)
        return hours
            .filter { !it.time.isBefore(from) && !it.time.isAfter(to) }
            .takeIf { it.isNotEmpty() }
    }

    /**
     * Why a window came back empty. Past the last hour is the honest, common case —
     * a solstice five months out, a shower next year — and it reads differently from
     * a forecast with a hole in it.
     */
    private fun horizonNote(
        start: Instant,
        hours: List<HourlyForecast>,
        zone: ZoneId
    ): SkyVerdictNote {
        val last = hours.lastOrNull() ?: return SkyVerdictNote.NO_DATA
        val at = start.atZone(zone).toLocalDateTime()
        return if (at.isAfter(last.time)) SkyVerdictNote.BEYOND_HORIZON
        else SkyVerdictNote.NO_COVERAGE
    }

    /** Points sampled across a window when asking where the moon is. */
    private const val MOON_SAMPLES = 5
}
