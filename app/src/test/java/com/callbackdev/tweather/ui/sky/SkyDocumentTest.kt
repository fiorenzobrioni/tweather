package com.callbackdev.tweather.ui.sky

import com.callbackdev.tweather.data.SkySubscription
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.sky.SkyJobCatalog
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `sky.crontab` as a document (Fase 16c). Pure: the whole file is asserted without
 * composing anything, which is what [SkyDocumentBuilder] exists for.
 *
 * Most of these are the honesty surface of `VISION_SKY.md` §11 — the cases where the
 * easy implementation prints something plausible and false.
 */
class SkyDocumentTest {

    private val milan = Coordinates(45.4642, 9.19)
    private val rome = ZoneId.of("Europe/Rome")

    private fun context(
        label: String = "Milan, Lombardy",
        coords: Coordinates = milan,
        zone: ZoneId = rome,
        now: String = "2026-08-26T16:30:00Z"
    ) = SkyContext(label, coords, zone, Instant.parse(now))

    private fun subs(vararg ids: String) = ids.map { SkySubscription(it) }

    private fun commentOf(document: SkyDocument, jobId: String) =
        document.rows.first { it.job.id == jobId }.comment

    @Test
    fun `the header names the city, its zone and what fires next`() {
        val document = SkyDocumentBuilder.build(subs("sun.set", "sun.rise"), context())
        assertEquals("# sky.crontab · Milan, Lombardy (Europe/Rome)", document.header.first())
        assertTrue(document.header[1], document.header[1].startsWith("# 2 jobs · next: sun.set in "))
    }

    /**
     * A crontab is a file, not a queue. The lines keep the catalog's order whatever
     * the subscription list's order is and whatever fires first — that is what the
     * header line is for.
     */
    @Test
    fun `rows follow the catalog order, not the subscription order`() {
        val document = SkyDocumentBuilder.build(
            subs("moon.phase", "sun.set", "golden_hour.pm", "sun.rise"), context()
        )
        assertEquals(
            listOf("sun.rise", "sun.set", "golden_hour.pm", "moon.phase"),
            document.rows.map { it.job.id }
        )
        assertEquals(
            document.rows.map { SkyJobCatalog.orderOf(it.job) }.sorted(),
            document.rows.map { SkyJobCatalog.orderOf(it.job) }
        )
    }

    /**
     * Two jobs can resolve to the same instant (a sunset and the golden hour's end
     * are the same moment). Neither collapses and the order does not wobble.
     */
    @Test
    fun `two jobs at the same instant keep a stable order`() {
        val document = SkyDocumentBuilder.build(subs("sun.set", "golden_hour.pm"), context())
        val sunset = commentOf(document, "sun.set").substringBefore(" ")
        val golden = commentOf(document, "golden_hour.pm").substringAfter("..").substringBefore(" ")
        assertEquals("golden hour ends at sunset", sunset, golden)
        assertEquals(listOf("sun.set", "golden_hour.pm"), document.rows.map { it.job.id })
    }

    /**
     * A commented-out line is not evaluated. Not an optimization: a disabled job that
     * still printed a resolved time would be a line claiming to be off while doing
     * the work of being on.
     */
    @Test
    fun `a disabled line keeps its place and drops its comment`() {
        val document = SkyDocumentBuilder.build(
            listOf(SkySubscription("sun.rise", enabled = false), SkySubscription("sun.set")),
            context()
        )
        val disabled = document.rows.first { it.job.id == "sun.rise" }
        assertFalse(disabled.enabled)
        assertEquals("", disabled.comment)
        assertTrue(commentOf(document, "sun.set").isNotEmpty())
        // And it does not count as the next job to fire.
        assertTrue(document.header[1], document.header[1].contains("next: sun.set"))
    }

    @Test
    fun `the catalog offers exactly what the file does not already hold`() {
        val document = SkyDocumentBuilder.build(subs("sun.rise", "sun.set"), context())
        assertEquals(SkyJobCatalog.all.size - 2, document.available.size)
        assertTrue(document.available.none { it.id == "sun.rise" })
        assertTrue(document.available.any { it.id == "golden_hour.pm" })
    }

    // ------------------------------------------------- the honesty surface

    @Test
    fun `polar day is stated with its cause, in words that fit the job asking`() {
        val document = SkyDocumentBuilder.build(
            subs("sun.rise", "sun.set"),
            context("Tromso", Coordinates(69.6492, 18.9553), ZoneId.of("Europe/Oslo"),
                "2026-06-21T10:00:00Z")
        )
        document.rows.forEach { row ->
            assertTrue(row.comment, row.comment.startsWith("∅ not scheduled"))
            assertTrue(row.comment, row.comment.contains("polar day"))
            // Worded the obvious way ("the sun does not set") this reason appeared
            // under `sun.rise` too, where it is nonsense.
            assertTrue(row.comment, row.comment.contains("stays above the horizon"))
        }
    }

    @Test
    fun `polar night is stated with its cause`() {
        val document = SkyDocumentBuilder.build(
            subs("sun.rise"),
            context("Tromso", Coordinates(69.6492, 18.9553), ZoneId.of("Europe/Oslo"),
                "2026-12-21T10:00:00Z")
        )
        assertTrue(document.rows.single().comment.contains("polar night"))
    }

    @Test
    fun `a moonless day says so rather than printing midnight`() {
        // Walk a fortnight; one of those days has no moonrise at this latitude.
        val comments = (1..20).map { day ->
            SkyDocumentBuilder.build(
                subs("moon.rise"),
                context(now = "2026-01-%02dT10:00:00Z".format(day))
            ).rows.single().comment
        }
        val absent = comments.filter { it.startsWith("∅") }
        assertTrue("expected a day with no moonrise in a fortnight", absent.isNotEmpty())
        absent.forEach { assertTrue(it, it.contains("does not do that on this calendar day")) }
        assertTrue("no 00:00 anywhere", comments.none { it.startsWith("00:00") })
    }

    /**
     * The whole file renders in the CITY's zone. A Tokyo sunrise on Rome's clock is
     * the file lying even though the instant behind it is right.
     */
    @Test
    fun `a remote city renders on its own clock and names its zone`() {
        val document = SkyDocumentBuilder.build(
            subs("sun.rise"),
            context("Tokyo", Coordinates(35.6762, 139.6503), ZoneId.of("Asia/Tokyo"),
                "2026-08-25T19:00:00Z")
        )
        assertTrue(document.header.first(), document.header.first().endsWith("(Asia/Tokyo)"))
        assertEquals("05:08", document.rows.single().comment.substringBefore(" "))
    }

    /**
     * The DST note, and the showcase for the `@daily` choice: the recurrence stays
     * true across the switch while every instant it resolves to moves.
     */
    @Test
    fun `the DST switch is named on the day it happens`() {
        val document = SkyDocumentBuilder.build(
            subs("sun.rise"), context(now = "2026-10-25T01:00:00Z")
        )
        val note = document.header.first { it.startsWith("# DST") }
        assertEquals("# DST: the clock falls back 1h on Oct 25", note)
        // The cron field is untouched by any of it — that is the point.
        assertEquals("@daily", document.rows.single().expression)
    }

    @Test
    fun `an ordinary day carries no DST note`() {
        assertTrue(
            SkyDocumentBuilder.build(subs("sun.rise"), context()).header.none {
                it.startsWith("# DST")
            }
        )
    }

    @Test
    fun `equatorial blue hour is rendered as short as it really is`() {
        val document = SkyDocumentBuilder.build(
            subs("blue_hour.pm"),
            context("Quito", Coordinates(-0.1807, -78.4678), ZoneId.of("America/Guayaquil"),
                "2026-03-20T12:00:00Z")
        )
        val (start, end) = document.rows.single().comment.split("..")
        val minutes = end.trim().substringBefore(" ").let { toMinutes(it) } - toMinutes(start)
        assertTrue("blue hour of $minutes minutes", minutes in 5..12)
    }

    @Test
    fun `no location configured renders the same refusal as the other editor tabs`() {
        val state = SkyUiState(subscriptions = subs("sun.rise"), context = null)
        assertEquals(null, state.document)
    }

    // ------------------------------------------------- the derived comments

    @Test
    fun `moon today answers for today, not for the next noon`() {
        // Resolved as "the next occurrence" this read `Aug 27 12:00` at six in the
        // evening: a line called `today` naming tomorrow.
        val comment = SkyDocumentBuilder.build(subs("moon.today"), context()).rows.single().comment
        assertTrue(comment, comment.contains("lit"))
        assertTrue("a phase does not happen at a clock time", comment.none { it == ':' })
    }

    @Test
    fun `the sunrise line carries how far it drifted since yesterday`() {
        val comment = commentOf(
            SkyDocumentBuilder.build(subs("sun.rise"), context(now = "2026-08-26T02:00:00Z")),
            "sun.rise"
        )
        assertTrue(comment, comment.contains("vs yesterday"))
        // Late August in Milan: the sun comes up later every day.
        assertTrue(comment, comment.contains("+"))
    }

    @Test
    fun `the darkness window names the moonless part of the night`() {
        val comment = commentOf(
            SkyDocumentBuilder.build(subs("darkness.window"), context()), "darkness.window"
        )
        assertTrue(comment, comment.contains(".."))
        assertTrue(
            comment,
            comment.contains("moonless") || comment.contains("moon up all night")
        )
    }

    @Test
    fun `an annual job leads with its date and how far off it is`() {
        val comment = commentOf(
            SkyDocumentBuilder.build(subs("solstice.winter"), context()), "solstice.winter"
        )
        assertTrue(comment, comment.startsWith("2026-12-21"))
        assertTrue(comment, Regex("in \\d+d").containsMatchIn(comment))
    }

    @Test
    fun `an event later today needs no date and one tomorrow gets one`() {
        val today = SkyDocumentBuilder.build(subs("sun.set"), context()).rows.single().comment
        assertTrue(today, today.startsWith("20:"))
        val tomorrow = SkyDocumentBuilder.build(subs("sun.rise"), context()).rows.single().comment
        assertTrue(tomorrow, tomorrow.startsWith("Aug 27 "))
    }

    private fun toMinutes(hhmm: String): Int {
        val (h, m) = hhmm.trim().split(":")
        return h.toInt() * 60 + m.toInt()
    }
}
