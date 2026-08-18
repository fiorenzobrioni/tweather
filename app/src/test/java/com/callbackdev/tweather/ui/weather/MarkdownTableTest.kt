package com.callbackdev.tweather.ui.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pipe tables as a formatter would write them (Fase 11c): columns padded to their
 * widest cell, numbers right-aligned with the real `---:` marker, and emoji parked
 * against the right edge of their cell so that a glyph nobody can measure in
 * character cells still leaves the following pipe in column.
 */
class MarkdownTableTest {

    private val columns = listOf(
        TableColumn("Day"),
        TableColumn("High", TableAlign.RIGHT),
        TableColumn("Status")
    )

    private val rows = listOf(
        listOf(TableCell("Mon"), TableCell("20°"), TableCell("Sunny", "☀️")),
        listOf(TableCell("Tue"), TableCell("8°"), TableCell("Partly Cloudy", "⛅"))
    )

    @Test
    fun `every column is padded to its widest cell`() {
        assertEquals(
            listOf(
                "| Day | High | Status           |",
                "| --- | ---: | ---------------- |",
                "| Mon |  20° | Sunny         ☀️ |",
                "| Tue |   8° | Partly Cloudy ⛅ |"
            ),
            markdownTable(columns, rows)
        )
    }

    @Test
    fun `an emoji is measured as two cells, never by its UTF-16 length`() {
        // One glyph each, but 2, 1 and 3 UTF-16 units: measuring the rendered string
        // would stagger the column by the width of the encoding.
        val emojis = listOf("☀️", "⛅", "🌧️")
        val lines = markdownTable(
            columns = listOf(TableColumn("Status")),
            rows = emojis.map { listOf(TableCell("Rain", it)) }
        ).drop(2)

        val offsets = lines.mapIndexed { i, line -> line.indexOf(emojis[i]) }
        assertEquals(listOf(offsets.first(), offsets.first(), offsets.first()), offsets)
    }

    @Test
    fun `an icon-only cell hugs the right edge of its column`() {
        assertEquals(
            listOf(
                "| Status |",
                "| -----: |",
                "|     ☀️ |"
            ),
            markdownTable(
                columns = listOf(TableColumn("Status", TableAlign.RIGHT)),
                rows = listOf(listOf(TableCell.icon("☀️")))
            )
        )
    }

    @Test
    fun `a narrow column still writes a legal separator`() {
        val lines = markdownTable(
            columns = listOf(TableColumn("A"), TableColumn("B", TableAlign.RIGHT)),
            rows = listOf(listOf(TableCell("b"), TableCell("1")))
        )
        assertEquals("| --- | --: |", lines[1])
        assertEquals("| b   |   1 |", lines[2])
    }

    @Test
    fun `text is left and numbers are right within the same table`() {
        val body = markdownTable(columns, rows).drop(2)
        assertTrue(body.all { it.startsWith("| Mon") || it.startsWith("| Tue") })
        assertTrue(body.all { "|  20° |" in it || "|   8° |" in it })
    }
}
