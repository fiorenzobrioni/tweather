package com.callbackdev.tweather.ui.logs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.callbackdev.tweather.data.local.ForecastDiff
import com.callbackdev.tweather.data.local.SnapshotDiff
import com.callbackdev.tweather.ui.theme.TweatherTheme
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `forecast.diff`'s hunk headers, reviewed in Fase 28.
 *
 * The file exists to answer "how did the prediction for the SAME future day change",
 * and its headers used to read `@@ tomorrow @@` on every commit — true of the fetch
 * that wrote each one and of nothing afterwards. Two revisions of one day were
 * indistinguishable from two revisions of two different days.
 */
@RunWith(RobolectricTestRunner::class)
class ForecastHunkHeaderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun at(iso: String) = LocalDateTime.parse(iso).toEpochSecond(ZoneOffset.UTC)

    private fun hunk(date: String, baseline: Long?) = ForecastDiff.Hunk(
        date = date,
        baselineEpochSeconds = baseline,
        lines = listOf(SnapshotDiff.Line(SnapshotDiff.Type.ADDED, "high_c", "27.4"))
    )

    /** Two fetches on the 18th, each revising the SAME target day, the 19th. */
    private val revisions = listOf(
        ForecastRevisionUi(
            hash = "a1b2c3d", cityLabel = "Milan, Lombardy", author = "sys@tweather.app",
            timestampEpochSeconds = at("2026-08-18T18:00"),
            hunks = listOf(hunk("2026-08-19", at("2026-08-18T12:00")))
        ),
        ForecastRevisionUi(
            hash = "9f8e7d6", cityLabel = "Milan, Lombardy", author = "sys@tweather.app",
            timestampEpochSeconds = at("2026-08-18T12:00"),
            hunks = listOf(hunk("2026-08-19", at("2026-08-17T22:40")))
        )
    )

    private fun setContent() {
        compose.setContent {
            TweatherTheme { LogsScreen(commits = emptyList(), revisions = revisions) }
        }
        compose.onNodeWithText("forecast.diff").performClick()
    }

    @Test
    fun `the header names the target day, so both revisions of it read as one day`() {
        setContent()

        // Wednesday 19 August 2026 — the same header on both commits, because it is
        // the same day being revised twice.
        compose.onAllNodesWithText("@@ Wed 19 Aug @@").assertCountEquals(2)
    }

    @Test
    @Config(qualifiers = "it")
    fun `the day name follows the reader, the git chrome does not`() {
        setContent()

        compose.onAllNodesWithText("@@ Mer 19 ago @@").assertCountEquals(2)
        compose.onNodeWithText("--- a/forecast_2026-08-19.json (12:00)").assertIsDisplayed()
    }

    /**
     * A baseline from a previous local day says which day, and that month is a name
     * like the weekday four lines under it — pinned English, it printed `Aug 17`
     * above `@@ Mer 19 ago @@`.
     */
    @Test
    fun `a baseline from another day carries a month, in the reader's language`() {
        assertEquals(
            "17 Aug 22:40",
            fetchTimeLabel(
                at("2026-08-17T22:40"), at("2026-08-18T12:00"), ZoneOffset.UTC, Locale.ENGLISH
            )
        )
        assertEquals(
            "17 ago 22:40",
            fetchTimeLabel(
                at("2026-08-17T22:40"), at("2026-08-18T12:00"), ZoneOffset.UTC, Locale.ITALIAN
            )
        )
        // same local day: the clock alone, digits only, no language in it at all
        assertEquals(
            "12:00",
            fetchTimeLabel(
                at("2026-08-18T12:00"), at("2026-08-18T18:00"), ZoneOffset.UTC, Locale.ITALIAN
            )
        )
    }

    @Test
    fun `a first appearance is a new file and still names its day`() {
        compose.setContent {
            TweatherTheme {
                LogsScreen(
                    commits = emptyList(),
                    revisions = listOf(
                        ForecastRevisionUi(
                            hash = "a1b2c3d", cityLabel = "Milan, Lombardy",
                            author = "sys@tweather.app",
                            timestampEpochSeconds = at("2026-08-18T12:00"),
                            hunks = listOf(hunk("2026-08-20", baseline = null))
                        )
                    )
                )
            }
        }
        compose.onNodeWithText("forecast.diff").performClick()

        compose.onNodeWithText("--- /dev/null").assertIsDisplayed()
        compose.onNodeWithText("@@ Thu 20 Aug @@").assertIsDisplayed()
    }
}
