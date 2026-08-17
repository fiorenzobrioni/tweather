package com.callbackdev.tweather.ui.logs

import com.callbackdev.tweather.data.local.SnapshotDiff
import com.callbackdev.tweather.data.local.WeatherHistoryEntry
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [buildForecastRevisions] turns the interleaved history flow into per-city revisions. */
class ForecastRevisionsTest {

    private val json = Json

    private fun entry(
        id: Long,
        cityKey: String,
        ts: Long,
        forecast: Map<String, String>?,
        hash: String = "hash$id"
    ) = WeatherHistoryEntry(
        id = id,
        cityKey = cityKey,
        cityLabel = cityKey.uppercase(),
        hash = hash,
        author = "sys@tweather.app",
        timestampEpochSeconds = ts,
        snapshotJson = "{}",
        forecastJson = forecast?.let { json.encodeToString(it) }
    )

    private fun day(date: String, high: Double) = mapOf(
        "$date.status" to "Sunny ☀️",
        "$date.high_c" to high.toString(),
        "$date.low_c" to "18.0",
        "$date.precip_pct" to "10"
    )

    @Test
    fun `cities diff independently and merge newest-first`() {
        // Newest-first, interleaved: milan's change must diff against milan's
        // previous fetch even with rome's in between.
        val entries = listOf(
            entry(4, "milan", 4000, day("2026-08-18", high = 27.0)),
            entry(3, "rome", 3000, day("2026-08-18", high = 33.0)),
            entry(2, "milan", 2000, day("2026-08-18", high = 31.0)),
            entry(1, "rome", 1000, day("2026-08-18", high = 33.4))
        )
        val revisions = buildForecastRevisions(entries, json)
        // milan: new file + change; rome: new file only (0.4 °C is noise)
        assertEquals(listOf(4000L, 2000L, 1000L), revisions.map { it.timestampEpochSeconds })
        val milanChange = revisions.first()
        assertEquals("MILAN", milanChange.cityLabel)
        assertEquals("hash4", milanChange.hash)
        val highLines = milanChange.hunks.single().lines.filter { it.key == "high_c" }
        assertEquals(
            listOf(
                SnapshotDiff.Line(SnapshotDiff.Type.REMOVED, "high_c", "31.0"),
                SnapshotDiff.Line(SnapshotDiff.Type.ADDED, "high_c", "27.0")
            ),
            highLines
        )
    }

    @Test
    fun `rows from before the forecast column are skipped, not treated as empty`() {
        // A pre-migration row must not turn the next real fetch into a bogus diff:
        // the first row WITH a forecast becomes the new-file baseline.
        val entries = listOf(
            entry(2, "milan", 2000, day("2026-08-18", high = 31.0)),
            entry(1, "milan", 1000, forecast = null)
        )
        val revisions = buildForecastRevisions(entries, json)
        assertEquals(1, revisions.size)
        assertTrue(revisions.single().hunks.single().lines
            .all { it.type == SnapshotDiff.Type.ADDED })
    }

    @Test
    fun `no revisions from unchanged forecasts`() {
        val entries = listOf(
            entry(2, "milan", 2000, day("2026-08-18", high = 31.0)),
            entry(1, "milan", 1000, day("2026-08-18", high = 31.0))
        )
        val revisions = buildForecastRevisions(entries, json)
        assertEquals(1, revisions.size) // just the initial new-file revision
        assertEquals(1000L, revisions.single().timestampEpochSeconds)
    }
}
