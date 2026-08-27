package com.callbackdev.tweather.ui.sky

import com.callbackdev.tweather.data.SkySubscription
import com.callbackdev.tweather.domain.model.Astronomical
import com.callbackdev.tweather.domain.model.CacheStatus
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.CurrentConditions
import com.callbackdev.tweather.domain.model.HourlyForecast
import com.callbackdev.tweather.domain.model.Location
import com.callbackdev.tweather.domain.model.MoonPhase
import com.callbackdev.tweather.domain.model.Precipitation
import com.callbackdev.tweather.domain.model.SystemInfo
import com.callbackdev.tweather.domain.model.WeatherCondition
import com.callbackdev.tweather.domain.model.WeatherReport
import com.callbackdev.tweather.domain.model.Wind
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The verdicts as the file renders them (Fase 16d): on the row, in the header, and
 * in the `$ tweather run sky` block.
 */
class SkyVerdictRenderTest {

    private val milan = Coordinates(45.4642, 9.19)
    private val rome: ZoneId = ZoneId.of("Europe/Rome")
    private val now: Instant = Instant.parse("2026-08-26T16:30:00Z")

    private fun report(
        cloud: (Int) -> Int,
        rain: (Int) -> Int = { 0 },
        lastSync: Instant = now
    ): WeatherReport {
        val start = now.atZone(rome).toLocalDateTime().truncatedTo(ChronoUnit.HOURS)
        return WeatherReport(
            location = Location("Milan", "Lombardy", "Italy", milan, rome.id, start),
            current = CurrentConditions(
                WeatherCondition(0, "Clear", "☀️"), 20.0, 20.0, 50, 10.0, 10.0, 1013.0,
                3, "Moderate", Wind(5.0, "N", 0, 8.0), Precipitation(0.0, 0)
            ),
            airQuality = null,
            pollen = null,
            astronomical = Astronomical(
                LocalTime.of(6, 37), LocalTime.of(20, 12), MoonPhase.FULL_MOON,
                Duration.ofHours(13)
            ),
            hourly = (0 until 168).map { i ->
                HourlyForecast(
                    start.plusHours(i.toLong()), 20.0,
                    WeatherCondition(0, "Clear", "☀️"), rain(i), cloud(i)
                )
            },
            daily = emptyList(),
            systemInfo = SystemInfo("Open-Meteo API", lastSync, CacheStatus.HIT, 100)
        )
    }

    private fun context(report: WeatherReport?, updateFrequencyMin: Int = 60) = SkyContext(
        cityLabel = "Milan, Lombardy",
        coordinates = milan,
        zone = rome,
        now = now,
        report = report,
        updateFrequencyMin = updateFrequencyMin
    )

    private fun document(
        report: WeatherReport?,
        vararg jobs: String = arrayOf("sun.set", "golden_hour.pm"),
        updateFrequencyMin: Int = 60
    ) = SkyDocumentBuilder.build(
        jobs.map { SkySubscription(it) }, context(report, updateFrequencyMin)
    )

    private fun commentOf(document: SkyDocument, jobId: String) =
        document.rows.first { it.job.id == jobId }.comment

    /**
     * The headline question of the whole module. An earlier cut keyed the verdict off
     * `visibilityDependent` — which governs whether a REMINDER is suppressed, not
     * whether the clouds matter — and left `sun.set` without one.
     */
    @Test
    fun `the sunset carries a verdict, because that is the question`() {
        val comment = commentOf(document(report(cloud = { 8 })), "sun.set")
        assertTrue(comment, comment.contains("✓ pass"))
        assertTrue(comment, comment.contains("cloud 8%"))
    }

    @Test
    fun `the verdict comes straight after the when, before the job's own trivia`() {
        val comment = commentOf(document(report(cloud = { 8 })), "sun.set")
        assertTrue(
            comment,
            comment.indexOf("✓ pass") < comment.indexOf("vs yesterday")
        )
    }

    /**
     * A moment of geometry is not a sight, so it has no verdict: a `✗ fail` on a
     * first quarter would be the file inventing a stake nobody has.
     */
    @Test
    fun `the moments of pure geometry get no verdict at all`() {
        val document = document(report(cloud = { 90 }), "moon.phase", "solstice.winter", "moon.today")
        document.rows.forEach { row ->
            assertEquals("${row.job.id} should have no verdict", null, row.verdict)
            assertFalse(row.comment, row.comment.contains("fail"))
        }
    }

    @Test
    fun `a disabled line has neither comment nor verdict`() {
        val document = SkyDocumentBuilder.build(
            listOf(SkySubscription("sun.set", enabled = false)),
            context(report(cloud = { 8 }))
        )
        val row = document.rows.single()
        assertEquals("", row.comment)
        assertEquals(null, row.verdict)
    }

    @Test
    fun `the header carries the next job's glyph and nothing more of it`() {
        val header = document(report(cloud = { 90 })).header[1]
        assertTrue(header, header.contains("next: golden_hour.pm"))
        assertTrue(header, header.trimEnd().endsWith("✗"))
        // The reasoning stays on the row, where its numbers are.
        assertFalse(header, header.contains("cloud"))
    }

    @Test
    fun `an unknown verdict leaves the header's glyph off rather than guessing one`() {
        val header = document(report = null).header[1]
        assertTrue(header, header.contains("next:"))
        assertFalse(header, header.contains("?"))
    }

    // -------------------------------------------------------------- not knowing

    @Test
    fun `with no fetch the rows say so instead of showing nothing`() {
        val comment = commentOf(document(report = null), "sun.set")
        assertTrue(comment, comment.contains("? unknown (no fetch yet)"))
    }

    @Test
    fun `stale data is unknown, not the last thing the app thought`() {
        // Twice the polling interval is the app's existing staleness rule.
        val old = report(cloud = { 8 }, lastSync = now.minus(Duration.ofHours(3)))
        val comment = commentOf(document(old), "sun.set")
        assertTrue(comment, comment.contains("? unknown (no recent data)"))
        assertFalse("no number survives an unknown", comment.contains("cloud"))
    }

    @Test
    fun `an event past the horizon is unknown and says which kind of unknown`() {
        val comment = commentOf(
            document(report(cloud = { 8 }), "meteor.perseids.peak"), "meteor.perseids.peak"
        )
        assertTrue(comment, comment.contains("? unknown (past the forecast horizon)"))
    }

    /**
     * The reason rides in parentheses, not behind a `//`: on a row the verdict can be
     * followed by the job's own trivia, and `// no fetch yet` with three more words
     * after it reads as a comment that failed to comment.
     */
    @Test
    fun `a verdict reason never swallows the rest of the line`() {
        val comment = commentOf(document(report = null), "sun.set")
        assertFalse(comment, comment.contains("//"))
        assertTrue(comment, comment.endsWith("vs yesterday"))
    }

    // ------------------------------------------------------------ the moon note

    @Test
    fun `a moonlit dark window names the moon and not the clouds`() {
        val comment = commentOf(
            document(report(cloud = { 0 }), "darkness.window"), "darkness.window"
        )
        assertTrue(comment, comment.contains("~ unstable"))
        assertTrue(comment, comment.contains("moon"))
        assertFalse("the sky is clear; do not blame it", comment.contains("cloud"))
    }

    /**
     * `moonless from 23:11` and a MOONLIGHT verdict are the same sentence twice. When
     * the verdict has already named the moon, the suffix stands down.
     */
    @Test
    fun `the moon is not mentioned twice on one line`() {
        val comment = commentOf(
            document(report(cloud = { 0 }), "darkness.window"), "darkness.window"
        )
        assertEquals(1, Regex("moon").findAll(comment).count())
    }

    // -------------------------------------------------------------- the dry run

    @Test
    fun `the dry run lines up every enabled job under itself`() {
        val context = context(report(cloud = { 8 }))
        val document = SkyDocumentBuilder.build(
            listOf("sun.set", "golden_hour.pm", "moon.today").map { SkySubscription(it) }, context
        )
        val lines = SkyDocumentBuilder.dryRun(document, context)
        assertEquals(3, lines.size)
        assertTrue(lines.all { it.startsWith("// ") })
        assertTrue(lines[0], lines[0].contains("sun.set") && lines[0].contains("✓ pass"))
        assertTrue(lines[1], lines[1].contains("19:") && lines[1].contains("..20:"))
        // A job with no verdict prints the FACT it resolved to rather than a window
        // and a dash: `moon.today` used to print `12:00`, the instant its phase is
        // measured at, which means nothing to a reader.
        assertTrue(lines[2], lines[2].contains("lit"))
        assertFalse(lines[2], lines[2].contains("12:00"))
    }

    @Test
    fun `the dry run skips the commented-out lines`() {
        val context = context(report(cloud = { 8 }))
        val document = SkyDocumentBuilder.build(
            listOf(
                SkySubscription("sun.set", enabled = false),
                SkySubscription("golden_hour.pm")
            ),
            context
        )
        val lines = SkyDocumentBuilder.dryRun(document, context)
        assertEquals(1, lines.size)
        assertTrue(lines.single(), lines.single().contains("golden_hour.pm"))
    }

    @Test
    fun `a far event is dated in the dry run rather than shown as a bare clock`() {
        val context = context(report(cloud = { 8 }))
        val document = SkyDocumentBuilder.build(
            listOf(SkySubscription("meteor.perseids.peak")), context
        )
        val line = SkyDocumentBuilder.dryRun(document, context).single()
        assertTrue(line, line.contains("2027-08-"))
    }

    // ------------------------------------------------------------- the footer

    /**
     * The thresholds are printed in the file rather than promoted to
     * `settings.config`: §7 asked that they not be invisible, not that they be
     * adjustable, and the comment channel is where this app puts the facts it knows.
     */
    @Test
    fun `the file prints the thresholds it judges by`() {
        val footer = document(report(cloud = { 8 })).footer.joinToString("\n")
        assertTrue(footer, footer.contains("25%"))
        assertTrue(footer, footer.contains("65%"))
        assertTrue(footer, footer.contains("70%"))
        assertTrue(footer, footer.contains("60%"))
    }

    @Test
    fun `the file says a verdict is a forecast and not an observation`() {
        val footer = document(report(cloud = { 8 })).footer.joinToString("\n")
        assertTrue(footer, footer.contains("not an observation"))
    }
}
