package com.callbackdev.tweather.ui.logs

import com.callbackdev.tweather.domain.sky.SkyRun
import com.callbackdev.tweather.domain.sky.SkyVerdictKind
import com.callbackdev.tweather.ui.components.CodeLine
import com.callbackdev.tweather.ui.theme.ObsidianSyntax
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `sky_runs.log` (Fase 16e): a journal transcript, not a diff. It records outcomes,
 * not changes, and calling it a diff would be the same kind of lie the crontab
 * avoided when it refused to write a fixed minute field.
 */
class SkyRunsLogTest {

    private val rome: ZoneId = ZoneId.of("Europe/Rome")

    private fun at(local: String): Long =
        LocalDateTime.parse(local).atZone(rome).toInstant().epochSecond

    private fun row(
        jobId: String,
        at: String,
        kind: SkyVerdictKind? = SkyVerdictKind.PASS,
        cloud: Int? = 8,
        obs: Long = 12
    ) = SkyRunsLog.Row(
        SkyRun(jobId, at(at), kind?.name, cloud, obs),
        observedAtEpochSeconds = at(at) + obs * 60
    )

    private fun render(vararg rows: SkyRunsLog.Row): List<String> =
        SkyRunsLog.build(rows.toList(), rome, ObsidianSyntax)
            .filterIsInstance<CodeLine>()
            .map { it.text.text }

    @Test
    fun `an empty log says what would fill it rather than nothing`() {
        val lines = render()
        assertTrue(lines.toString(), lines.any { it.contains("no runs recorded yet") })
        assertTrue(lines.any { it.contains("first time a fetch sees it has passed") })
    }

    @Test
    fun `a run prints its time, job, verdict, evidence and how far the fetch was`() {
        val line = render(row("sun.set", "2026-08-26T20:12")).first { it.contains("sun.set") }
        assertTrue(line, line.startsWith("20:12"))
        assertTrue(line, line.contains("✓ pass"))
        assertTrue(line, line.contains("cloud   8%"))
        assertTrue(line, line.contains("obs +12m"))
    }

    @Test
    fun `runs are grouped by day, newest first, with a summary per day`() {
        val lines = render(
            row("sun.set", "2026-08-24T20:16"),
            row("sun.set", "2026-08-26T20:12"),
            row("golden_hour.pm", "2026-08-26T20:12", SkyVerdictKind.UNSTABLE, cloud = 45)
        )
        assertEquals("Aug 26", lines.first().removePrefix("# "))
        assertTrue(lines.any { it == "# 1 passed · 1 unstable" })
        assertTrue(lines.any { it == "# 1 passed" })
        assertTrue("the older day comes second", lines.indexOf("# Aug 24") > lines.indexOf("# Aug 26"))
    }

    /**
     * `– skipped` is the coverage state: no fetch came near enough to the event to
     * have an opinion, so none was invented. Those runs count in no statistic — the
     * day's summary must not quietly fold them into anything.
     */
    @Test
    fun `a skipped run is named and counted apart from the judged ones`() {
        val lines = render(
            row("sun.set", "2026-08-26T20:12"),
            row("sun.rise", "2026-08-26T06:37", kind = null, cloud = null, obs = 95)
        )
        val skipped = lines.first { it.contains("sun.rise") }
        assertTrue(skipped, skipped.contains("– skipped"))
        assertTrue("no evidence is invented for it", !skipped.contains("cloud"))
        assertTrue(lines.toString(), lines.any { it == "# 1 passed · 1 skipped" })
    }

    @Test
    fun `an unknown verdict is a skipped run, not a fourth outcome`() {
        val line = render(row("sun.set", "2026-08-26T20:12", SkyVerdictKind.UNKNOWN, cloud = null))
            .first { it.contains("sun.set") }
        assertTrue(line, line.contains("– skipped"))
    }

    @Test
    fun `a failed run reads as a failed one`() {
        val line = render(row("sun.set", "2026-08-26T20:12", SkyVerdictKind.FAIL, cloud = 92))
            .first { it.contains("sun.set") }
        assertTrue(line, line.contains("✗ fail"))
        assertTrue(line, line.contains("cloud  92%"))
    }

    @Test
    fun `the columns line up whatever the job names are`() {
        val lines = render(
            row("sun.set", "2026-08-26T20:12"),
            row("meteor.eta_aquariids.peak", "2026-08-26T03:00")
        ).filter { it.contains("obs") }
        assertEquals(2, lines.size)
        // Same start column for the verdict on every row: the file is aligned to
        // itself, like the crontab.
        assertEquals(
            lines.map { it.indexOf("✓") }.distinct().size,
            1
        )
    }
}
