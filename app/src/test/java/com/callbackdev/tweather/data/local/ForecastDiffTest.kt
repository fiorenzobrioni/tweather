package com.callbackdev.tweather.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastDiffTest {

    private fun day(
        date: String,
        status: String = "Sunny ☀️",
        high: Double = 30.0,
        low: Double = 18.0,
        precip: Int = 10
    ): Map<String, String> = mapOf(
        "$date.status" to status,
        "$date.high_c" to high.toString(),
        "$date.low_c" to low.toString(),
        "$date.precip_pct" to precip.toString()
    )

    private fun fetch(ts: Long, vararg days: Map<String, String>) =
        ForecastDiff.Fetch(ts, days.fold(emptyMap()) { acc, d -> acc + d })

    @Test
    fun `first fetch is a new file per date, all additions`() {
        val revisions = ForecastDiff.compute(
            listOf(fetch(1000, day("2026-08-18"), day("2026-08-19")))
        )
        assertEquals(1, revisions.size)
        val hunks = revisions.single().hunks
        assertEquals(listOf("2026-08-18", "2026-08-19"), hunks.map { it.date })
        assertEquals(listOf("tomorrow", "in 2 days"), hunks.map { it.dayLabel })
        hunks.forEach { hunk ->
            assertNull(hunk.baselineEpochSeconds)
            assertTrue(hunk.lines.all { it.type == SnapshotDiff.Type.ADDED })
        }
        // Field order is importance order, like the notification body
        assertEquals(
            listOf("status", "high_c", "low_c", "precip_pct"),
            hunks.first().lines.map { it.key }
        )
    }

    @Test
    fun `sub-threshold wiggle produces no revision`() {
        val revisions = ForecastDiff.compute(
            listOf(
                fetch(1000, day("2026-08-18", high = 30.0, precip = 20)),
                fetch(2000, day("2026-08-18", high = 30.9, precip = 29))
            )
        )
        assertEquals(1, revisions.size) // only the new-file revision
        assertEquals(0, revisions.single().fetchIndex)
    }

    @Test
    fun `threshold crossing emits removed-added pair with context`() {
        val revisions = ForecastDiff.compute(
            listOf(
                fetch(1000, day("2026-08-18", high = 31.0, precip = 20)),
                fetch(2000, day("2026-08-18", high = 27.0, precip = 70))
            )
        )
        assertEquals(2, revisions.size)
        val hunk = revisions[1].hunks.single()
        assertEquals(1000L, hunk.baselineEpochSeconds)
        assertEquals(
            listOf(
                SnapshotDiff.Line(SnapshotDiff.Type.CONTEXT, "status", "Sunny ☀️"),
                SnapshotDiff.Line(SnapshotDiff.Type.REMOVED, "high_c", "31.0"),
                SnapshotDiff.Line(SnapshotDiff.Type.ADDED, "high_c", "27.0"),
                SnapshotDiff.Line(SnapshotDiff.Type.CONTEXT, "low_c", "18.0"),
                SnapshotDiff.Line(SnapshotDiff.Type.REMOVED, "precip_pct", "20"),
                SnapshotDiff.Line(SnapshotDiff.Type.ADDED, "precip_pct", "70")
            ),
            hunk.lines
        )
    }

    @Test
    fun `status change always shows`() {
        val revisions = ForecastDiff.compute(
            listOf(
                fetch(1000, day("2026-08-18", status = "Sunny ☀️")),
                fetch(2000, day("2026-08-18", status = "Rain 🌧️"))
            )
        )
        assertEquals(2, revisions.size)
        val changed = revisions[1].hunks.single().lines
            .filter { it.type != SnapshotDiff.Type.CONTEXT }
        assertEquals(listOf("status", "status"), changed.map { it.key })
    }

    @Test
    fun `drift below threshold accumulates against the shown baseline`() {
        // 30.0 → 30.6 (hidden) → 31.2: each step is sub-threshold, the total is not.
        // Comparing against the last SHOWN value, the third fetch must surface it.
        val revisions = ForecastDiff.compute(
            listOf(
                fetch(1000, day("2026-08-18", high = 30.0)),
                fetch(2000, day("2026-08-18", high = 30.6)),
                fetch(3000, day("2026-08-18", high = 31.2))
            )
        )
        assertEquals(2, revisions.size)
        val hunk = revisions[1].hunks.single()
        assertEquals(2, revisions[1].fetchIndex)
        assertEquals(1000L, hunk.baselineEpochSeconds) // vs the baseline, not fetch #2
        val highLines = hunk.lines.filter { it.key == "high_c" }
        assertEquals(
            listOf(
                SnapshotDiff.Line(SnapshotDiff.Type.REMOVED, "high_c", "30.0"),
                SnapshotDiff.Line(SnapshotDiff.Type.ADDED, "high_c", "31.2")
            ),
            highLines
        )
    }

    @Test
    fun `precip threshold is 10 points inclusive`() {
        val below = ForecastDiff.compute(
            listOf(
                fetch(1000, day("2026-08-18", precip = 20)),
                fetch(2000, day("2026-08-18", precip = 29))
            )
        )
        assertEquals(1, below.size)
        val at = ForecastDiff.compute(
            listOf(
                fetch(1000, day("2026-08-18", precip = 20)),
                fetch(2000, day("2026-08-18", precip = 30))
            )
        )
        assertEquals(2, at.size)
    }

    @Test
    fun `date leaving the horizon is silence, date entering is a new file`() {
        val revisions = ForecastDiff.compute(
            listOf(
                fetch(1000, day("2026-08-18"), day("2026-08-19")),
                // next local day: 18 dropped out, 20 entered, 19 unchanged
                fetch(90_000, day("2026-08-19"), day("2026-08-20"))
            )
        )
        assertEquals(2, revisions.size)
        val second = revisions[1]
        assertEquals(listOf("2026-08-20"), second.hunks.map { it.date })
        assertNull(second.hunks.single().baselineEpochSeconds)
        assertTrue(second.hunks.single().lines.all { it.type == SnapshotDiff.Type.ADDED })
    }

    @Test
    fun `the remaining single date reads as tomorrow`() {
        val revisions = ForecastDiff.compute(
            listOf(fetch(1000, day("2026-08-18")))
        )
        assertEquals("tomorrow", revisions.single().hunks.single().dayLabel)
    }
}
