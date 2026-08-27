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
 * The verdict table of `VISION_SKY.md` §7, boundary by boundary, plus the two
 * overrides (rain, moonlight) and the four ways of not knowing.
 */
class SkyVerdictEngineTest {

    private val milan = Coordinates(45.4642, 9.19)
    private val rome: ZoneId = ZoneId.of("Europe/Rome")
    private val midnight: LocalDateTime = LocalDateTime.parse("2026-08-26T00:00")
    private val fresh: Duration = Duration.ofMinutes(5)
    private val staleAfter: Duration = Duration.ofHours(2)

    private fun hours(count: Int = 48, cloud: (Int) -> Int, rain: (Int) -> Int = { 0 }) =
        (0 until count).map { i ->
            HourlyForecast(
                time = midnight.plusHours(i.toLong()),
                tempC = 20.0,
                condition = WeatherCondition(0, "Clear", "☀️"),
                precipChancePct = rain(i),
                cloudCoverPct = cloud(i)
            )
        }

    private fun at(local: String): Instant =
        LocalDateTime.parse(local).atZone(rome).toInstant()

    private fun evaluate(
        job: SkyJob = SkyJobCatalog.SunSet,
        start: String = "2026-08-26T20:00",
        end: String? = null,
        hours: List<HourlyForecast> = hours(cloud = { 0 }),
        dataAge: Duration? = fresh
    ) = SkyVerdictEngine.evaluate(
        job = job,
        start = at(start),
        end = end?.let { at(it) },
        hours = hours,
        zone = rome,
        coordinates = milan,
        dataAge = dataAge,
        staleAfter = staleAfter
    )

    // ------------------------------------------------------------ the cloud table

    @Test
    fun `the cloud thresholds are inclusive on pass and exclusive on fail`() {
        // ≤ 25 passes, 26..65 is unstable, > 65 fails. The boundaries are asserted
        // rather than the middles: a table is only ever wrong at its edges.
        listOf(
            0 to SkyVerdictKind.PASS,
            25 to SkyVerdictKind.PASS,
            26 to SkyVerdictKind.UNSTABLE,
            65 to SkyVerdictKind.UNSTABLE,
            66 to SkyVerdictKind.FAIL,
            100 to SkyVerdictKind.FAIL
        ).forEach { (cloud, expected) ->
            val verdict = evaluate(hours = hours(cloud = { cloud }))
            assertEquals("at $cloud% cloud", expected, verdict.kind)
            assertEquals("the number used", cloud, verdict.cloudPct)
        }
    }

    /**
     * The number is carried so the file can PRINT it. A verdict whose evidence is
     * invisible is an opinion, and this app does not render opinions.
     */
    @Test
    fun `the verdict carries the numbers it was built from`() {
        val verdict = evaluate(hours = hours(cloud = { 40 }, rain = { 20 }))
        assertEquals(40, verdict.cloudPct)
        assertEquals(20, verdict.precipPct)
    }

    // ----------------------------------------------------------------- the window

    /**
     * `VISION_SKY.md` §7: the window, not the instant. A range job averages the
     * buckets it spans — an event is the whole window, not one minute of it.
     */
    @Test
    fun `a window job averages the hours it spans`() {
        // 19:00 = 10%, 20:00 = 30%, 21:00 = 50% → mean 30 over 19:30..21:30.
        val verdict = evaluate(
            job = SkyJobCatalog.GoldenPm,
            start = "2026-08-26T19:30",
            end = "2026-08-26T21:30",
            hours = hours(cloud = { i -> if (i == 19) 10 else if (i == 20) 30 else 50 })
        )
        assertEquals(30, verdict.cloudPct)
        assertEquals(SkyVerdictKind.UNSTABLE, verdict.kind)
    }

    /**
     * Rain takes the MAX over the window, not the mean: an hour of it inside a
     * two-hour event is not averaged away, it is the thing that ruins the event.
     */
    @Test
    fun `rain over a window is the worst hour, not the average one`() {
        val verdict = evaluate(
            job = SkyJobCatalog.GoldenPm,
            start = "2026-08-26T19:30",
            end = "2026-08-26T21:30",
            hours = hours(cloud = { 0 }, rain = { i -> if (i == 20) 80 else 0 })
        )
        assertEquals(80, verdict.precipPct)
        assertEquals(SkyVerdictKind.FAIL, verdict.kind)
    }

    @Test
    fun `an instant job reads the hour that contains it`() {
        val verdict = evaluate(
            start = "2026-08-26T20:45",
            hours = hours(cloud = { i -> if (i == 20) 10 else 90 })
        )
        assertEquals(10, verdict.cloudPct)
    }

    // -------------------------------------------------------------- the overrides

    @Test
    fun `likely rain fails a clear sky and says so`() {
        val verdict = evaluate(hours = hours(cloud = { 0 }, rain = { 70 }))
        assertEquals(SkyVerdictKind.FAIL, verdict.kind)
        assertEquals(SkyVerdictNote.PRECIPITATION, verdict.note)
        assertEquals("a clear sky is still reported", 0, verdict.cloudPct)
    }

    @Test
    fun `possible rain unsettles a clear sky`() {
        listOf(39 to SkyVerdictKind.PASS, 40 to SkyVerdictKind.UNSTABLE).forEach { (rain, kind) ->
            val verdict = evaluate(hours = hours(cloud = { 0 }, rain = { rain }))
            assertEquals("at $rain% rain", kind, verdict.kind)
        }
    }

    /**
     * The rain threshold that fails an event is the same number the builtin
     * precipitation alert already uses. "It is going to rain" should mean one thing
     * across the app: a sky job disagreeing with a notification about the same hour
     * would be the app arguing with itself.
     */
    @Test
    fun `the failing rain threshold is the app's existing one`() {
        assertEquals(
            com.callbackdev.tweather.domain.AlertEngine.PRECIP_THRESHOLD_PCT,
            SkyVerdictEngine.PRECIP_FAIL_PCT
        )
    }

    /**
     * The moon condition (`VISION_SKY.md` §6), and the one place this module beats a
     * weather app: a shower's peak under a full moon is a failed build under a
     * perfectly clear sky.
     */
    @Test
    fun `a bright moon unsettles a dark-sky job under a clear sky`() {
        // 2026-08-28 is a full moon; at 01:00 it is well up over Milan.
        val verdict = evaluate(
            job = SkyJobCatalog.DarknessWindow,
            start = "2026-08-28T01:00",
            end = "2026-08-28T03:00",
            hours = hours(count = 96, cloud = { 0 })
        )
        assertEquals(SkyVerdictKind.UNSTABLE, verdict.kind)
        assertEquals(SkyVerdictNote.MOONLIGHT, verdict.note)
        assertTrue("moon should be reported bright", (verdict.moonPct ?: 0) >= 60)
        // And the clouds are NOT blamed for it: telling somebody the sky ruined a
        // night the moon ruined is a different lie of the same size.
        assertEquals(0, verdict.cloudPct)
    }

    @Test
    fun `the moon leaves a job that does not need darkness alone`() {
        val verdict = evaluate(
            job = SkyJobCatalog.SunSet,
            start = "2026-08-28T01:00",
            hours = hours(count = 96, cloud = { 0 })
        )
        assertEquals(SkyVerdictKind.PASS, verdict.kind)
        assertNull(verdict.note)
    }

    /**
     * The moon at 1 % and 25° BELOW the horizon over Milan on the night of the new
     * moon: the two conditions the wash-out needs, both absent.
     */
    @Test
    fun `a moon that is down does not wash anything out`() {
        val start = LocalDateTime.parse("2026-08-12T01:00").atZone(rome).toInstant()
        assertTrue("fixture must have the moon down", AstronomyEngine.moonAltitude(start, milan) < 0)
        val verdict = SkyVerdictEngine.evaluate(
            job = SkyJobCatalog.DarknessWindow,
            start = start,
            end = start.plusSeconds(2 * 3600),
            hours = (0 until 48).map {
                HourlyForecast(
                    LocalDateTime.parse("2026-08-12T00:00").plusHours(it.toLong()),
                    20.0, WeatherCondition(0, "Clear", "☀️"), 0, 0
                )
            },
            zone = rome,
            coordinates = milan,
            dataAge = fresh,
            staleAfter = staleAfter
        )
        assertEquals(SkyVerdictKind.PASS, verdict.kind)
    }

    @Test
    fun `the moon can only make a verdict worse, never better`() {
        val verdict = evaluate(
            job = SkyJobCatalog.DarknessWindow,
            start = "2026-08-28T01:00",
            end = "2026-08-28T03:00",
            hours = hours(count = 96, cloud = { 90 })
        )
        assertEquals(SkyVerdictKind.FAIL, verdict.kind)
        assertEquals("the clouds keep the blame", 90, verdict.cloudPct)
    }

    // ------------------------------------------------- the four ways of not knowing

    @Test
    fun `with no fetch at all there is no verdict, and it says which kind of none`() {
        val verdict = evaluate(dataAge = null)
        assertEquals(SkyVerdictKind.UNKNOWN, verdict.kind)
        assertEquals(SkyVerdictNote.NO_DATA, verdict.note)
    }

    /**
     * Stale data does not get to hold an opinion. Printing the last known verdict
     * would be the app answering a question about tonight with what it thought
     * yesterday, in the same words it uses when it knows.
     */
    @Test
    fun `data older than the staleness threshold is unknown, not the last known answer`() {
        assertEquals(
            SkyVerdictKind.PASS,
            evaluate(dataAge = staleAfter, hours = hours(cloud = { 0 })).kind
        )
        val stale = evaluate(dataAge = staleAfter.plusSeconds(1), hours = hours(cloud = { 0 }))
        assertEquals(SkyVerdictKind.UNKNOWN, stale.kind)
        assertEquals(SkyVerdictNote.STALE_DATA, stale.note)
        assertNull("no number survives an unknown", stale.cloudPct)
    }

    /** Past the last hour the forecast covers: `? unknown`, never extrapolated. */
    @Test
    fun `an event past the forecast horizon is unknown and named as such`() {
        val verdict = evaluate(
            start = "2027-08-13T01:00",
            hours = hours(cloud = { 0 })
        )
        assertEquals(SkyVerdictKind.UNKNOWN, verdict.kind)
        assertEquals(SkyVerdictNote.BEYOND_HORIZON, verdict.note)
    }

    /**
     * Exactly on the boundary. The last hour the forecast holds is covered; the one
     * after it is not, and nothing is extrapolated across the edge.
     */
    @Test
    fun `the last hour of the forecast is covered and the next one is not`() {
        val forecast = hours(count = 48, cloud = { 0 })
        val lastHour = forecast.last().time     // 2026-08-27T23:00
        assertEquals(
            SkyVerdictKind.PASS,
            evaluate(start = lastHour.toString(), hours = forecast).kind
        )
        assertEquals(
            SkyVerdictKind.UNKNOWN,
            evaluate(start = lastHour.plusHours(1).toString(), hours = forecast).kind
        )
    }

    @Test
    fun `an empty forecast is unknown rather than a crash`() {
        val verdict = evaluate(hours = emptyList())
        assertEquals(SkyVerdictKind.UNKNOWN, verdict.kind)
        assertEquals(SkyVerdictNote.NO_DATA, verdict.note)
    }

    /**
     * A hole in the middle of the forecast is not the same as its end, and the file
     * says which — one is the provider running out of days, the other is a gap.
     */
    @Test
    fun `a gap inside the forecast is reported as missing coverage`() {
        val forecast = hours(cloud = { 0 }).filterNot { it.time.hour == 20 }
        val verdict = evaluate(start = "2026-08-26T20:30", hours = forecast)
        assertEquals(SkyVerdictKind.UNKNOWN, verdict.kind)
        assertEquals(SkyVerdictNote.NO_COVERAGE, verdict.note)
    }
}
