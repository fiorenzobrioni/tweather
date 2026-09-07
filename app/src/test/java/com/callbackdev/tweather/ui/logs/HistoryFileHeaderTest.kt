package com.callbackdev.tweather.ui.logs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import com.callbackdev.tweather.data.local.WeatherHistoryEntry
import com.callbackdev.tweather.ui.theme.TweatherTheme
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `history.diff` opens each commit with the `---`/`+++` pair `forecast.diff` has used
 * since Fase 9h, in place of the bare `diff --git` it carried until Fase 28c.
 *
 * The line that left named the file and said nothing else. The pair names it twice and
 * puts the two TIMES beside the sides they belong to — and the far one is a fact the
 * reader had no other way to reach: this file diffs against the previous commit of the
 * SAME city, which with two cities interleaved is not the row above it.
 */
@RunWith(RobolectricTestRunner::class)
class HistoryFileHeaderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun ts(iso: String) = LocalDateTime.parse(iso).toEpochSecond(ZoneOffset.UTC)

    private fun snapshot(temp: String) =
        Json.encodeToString(mapOf("current.temp_c" to temp))

    private fun entry(id: Long, city: String, label: String, at: String, temp: String) =
        WeatherHistoryEntry(
            id = id, cityKey = city, cityLabel = label, hash = "h%05d".format(id),
            author = "sys@tweather.app", timestampEpochSeconds = ts(at),
            snapshotJson = snapshot(temp)
        )

    /** Newest-first, as `observeHistory` hands them over. Milan, NY, NY, Milan. */
    private val entries = listOf(
        entry(4, "45:9", "Milan, Lombardy", "2026-08-18T14:30", "19.5"),
        entry(3, "40:-74", "New York, NY", "2026-08-18T14:15", "22.0"),
        entry(2, "40:-74", "New York, NY", "2026-08-18T13:15", "21.0"),
        entry(1, "45:9", "Milan, Lombardy", "2026-08-17T23:40", "18.2")
    )

    private fun setContent() = compose.setContent {
        TweatherTheme {
            LogsScreen(commits = buildCommits(entries, Json), revisions = emptyList())
        }
    }

    @Test
    fun `the baseline is the previous fetch of the same city, not the row above`() {
        val commits = buildCommits(entries, Json)

        // Milan's newest is diffed against Milan's own previous — fifteen hours back —
        // while the two rows between them belong to New York.
        assertEquals(ts("2026-08-17T23:40"), commits[0].baselineEpochSeconds)
        assertEquals(ts("2026-08-18T13:15"), commits[1].baselineEpochSeconds)
        // the oldest of each city has nothing behind it
        assertNull(commits[2].baselineEpochSeconds)
        assertNull(commits[3].baselineEpochSeconds)
    }

    @Test
    fun `the pair prints both ends, and says when the far one is another day`() {
        setContent()

        compose.onNodeWithText("--- a/weather_data.json (17 Aug 23:40)").assertIsDisplayed()
        compose.onNodeWithText("+++ b/weather_data.json (14:30)").assertIsDisplayed()
        // the ordinary case, an hour apart on the same day: the clock alone
        compose.onNodeWithText("--- a/weather_data.json (13:15)").assertIsDisplayed()
        compose.onNodeWithText("+++ b/weather_data.json (14:15)").assertIsDisplayed()
    }

    @Test
    fun `a first commit is a new file the way git writes one`() {
        setContent()
        // the oldest commits are below the fold of a four-commit file
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("--- /dev/null"))

        compose.onAllNodesWithText("--- /dev/null").onFirst().assertIsDisplayed()
        // `new file mode 100644` left with `diff --git`: it belongs to that line's
        // extended header block, and `--- /dev/null` already says new file.
        compose.onNodeWithText("new file mode 100644").assertDoesNotExist()
        compose.onNodeWithText("diff --git", substring = true).assertDoesNotExist()
    }
}
