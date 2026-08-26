package com.callbackdev.tweather.domain.sky

import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.HourlyForecast
import com.callbackdev.tweather.domain.model.WeatherCondition
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which jobs a fetch observed as run (Fase 16e). The window is `(since, now]`, and
 * `since` is the city's previous commit — precisely the last moment the app looked,
 * so the gap between them is everything that happened while it was not looking.
 */
class SkyRunRecorderTest {

    private val milan = Coordinates(45.4642, 9.19)
    private val rome: ZoneId = ZoneId.of("Europe/Rome")

    private fun at(local: String): Instant =
        LocalDateTime.parse(local).atZone(rome).toInstant()

    private fun hours(from: String, count: Int = 48, cloud: Int = 8) =
        (0 until count).map { i ->
            HourlyForecast(
                LocalDateTime.parse(from).plusHours(i.toLong()), 20.0,
                WeatherCondition(0, "Clear", "☀️"), 0, cloud
            )
        }

    private fun record(
        since: String,
        now: String,
        jobs: List<SkyJob> = listOf(SkyJobCatalog.SunSet),
        hours: List<HourlyForecast> = hours("2026-08-26T19:00")
    ) = SkyRunRecorder.runsSince(
        since = at(since),
        now = at(now),
        jobs = jobs,
        zone = rome,
        coordinates = milan,
        hours = hours,
        dataAge = Duration.ZERO,
        staleAfter = Duration.ofHours(2)
    )

    @Test
    fun `a sunset between the two fetches is recorded with its verdict`() {
        // Milan sets at 20:12 on 26 Aug 2026.
        val runs = record(since = "2026-08-26T19:00", now = "2026-08-26T20:24")
        val run = runs.single()
        assertEquals("sun.set", run.jobId)
        assertEquals(SkyVerdictKind.PASS, run.verdict)
        assertEquals(8, run.cloudPct)
    }

    /**
     * `obs` is the distance between the event and the fetch that observed it. It is
     * printed because a verdict resolved from a reading forty minutes away is a
     * weaker claim than one from a reading five minutes away, and hiding that
     * distance would be dishonest.
     */
    @Test
    fun `the run carries how far the observing fetch was from it`() {
        val run = record(since = "2026-08-26T19:00", now = "2026-08-26T20:24").single()
        assertEquals(12, run.obsMinutes)
    }

    @Test
    fun `nothing is recorded when the job did not run in the window`() {
        assertTrue(record(since = "2026-08-26T15:00", now = "2026-08-26T17:00").isEmpty())
    }

    /**
     * A window job counts as run when its window ENDS: the golden hour did not happen
     * at 19:32, it happened between 19:32 and 20:12 and was over at 20:12.
     */
    @Test
    fun `a window job is recorded when its window closes, not when it opens`() {
        val jobs = listOf(SkyJobCatalog.GoldenPm)
        assertTrue(
            "still running at 19:45",
            record("2026-08-26T19:00", "2026-08-26T19:45", jobs).isEmpty()
        )
        val run = record("2026-08-26T19:00", "2026-08-26T20:20", jobs).single()
        assertEquals(SkyJobCatalog.GoldenPm.id, run.jobId)
        // The engine answers to the second and the record keeps it: unlike the
        // snapshot times, this value is written once and never re-diffed, so there is
        // nothing for the seconds to churn.
        val sunset = AstronomyEngine.solarDay(
            java.time.LocalDate.of(2026, 8, 26), rome, milan
        ).sunset!!
        assertEquals("recorded at the window's end", sunset.epochSecond, run.atEpochSeconds)
    }

    /**
     * `– skipped` is the coverage state, straight out of "the app is allowed not to
     * know". If the forecast in hand no longer carries the event's hour, no verdict
     * is invented — and those runs count in no statistic.
     */
    @Test
    fun `a run no fetch came near enough to judge is skipped, not guessed`() {
        // The forecast starts hours after the sunset it is asked about.
        val run = record(
            since = "2026-08-26T19:00",
            now = "2026-08-26T20:24",
            hours = hours("2026-08-27T06:00")
        ).single()
        assertTrue(run.skipped)
        assertNull(run.cloudPct)
        assertNull("a skipped run has no verdict name at all", run.kind)
    }

    @Test
    fun `only observable jobs are recorded`() {
        // A solstice happens at a computed time nobody goes outside to watch, so
        // there is no run to record and no verdict to record it with.
        val runs = SkyRunRecorder.runsSince(
            since = at("2026-12-21T20:00"),
            now = at("2026-12-21T23:00"),
            jobs = listOf(SkyJobCatalog.SolsticeWinter, SkyJobCatalog.MoonPhase),
            zone = rome,
            coordinates = milan,
            hours = hours("2026-12-21T19:00"),
            dataAge = Duration.ZERO,
            staleAfter = Duration.ofHours(2)
        )
        assertTrue(runs.toString(), runs.isEmpty())
    }

    @Test
    fun `several jobs in one window are all recorded`() {
        val runs = record(
            since = "2026-08-26T19:00",
            now = "2026-08-26T21:00",
            jobs = listOf(SkyJobCatalog.SunSet, SkyJobCatalog.GoldenPm, SkyJobCatalog.BluePm)
        )
        assertEquals(
            listOf("sun.set", "golden_hour.pm", "blue_hour.pm").sorted(),
            runs.map { it.jobId }.sorted()
        )
    }

    /**
     * A long gap — a phone off for a day — records the most recent occurrence rather
     * than every one it slept through: the log is what the app OBSERVED, and it did
     * not observe the others.
     */
    @Test
    fun `a long gap records the last occurrence, not a backlog of them`() {
        val runs = record(since = "2026-08-24T19:00", now = "2026-08-26T20:24")
        assertEquals(1, runs.size)
        val sunset = AstronomyEngine.solarDay(
            java.time.LocalDate.of(2026, 8, 26), rome, milan
        ).sunset!!
        assertEquals(sunset.epochSecond, runs.single().atEpochSeconds)
    }
}
