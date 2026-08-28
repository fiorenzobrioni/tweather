package com.callbackdev.tweather.ui.logs

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.callbackdev.tweather.data.local.ForecastDiff
import com.callbackdev.tweather.data.local.SnapshotDiff
import com.callbackdev.tweather.ui.theme.TweatherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class LogsTabsTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis() / 1000

    private val commits = listOf(
        CommitUi(
            hash = "a1b2c3d",
            cityLabel = "Milan, Lombardy",
            author = "sys@tweather.app",
            timestampEpochSeconds = now - 600,
            isInitial = true,
            lines = listOf(
                SnapshotDiff.Line(SnapshotDiff.Type.ADDED, "current.temp_c", "31.0")
            )
        )
    )

    private val revisions = listOf(
        ForecastRevisionUi(
            hash = "a1b2c3d",
            cityLabel = "Milan, Lombardy",
            author = "sys@tweather.app",
            timestampEpochSeconds = now - 600,
            hunks = listOf(
                ForecastDiff.Hunk(
                    date = "2026-08-18",
                    dayLabel = "tomorrow",
                    baselineEpochSeconds = now - 15_000,
                    lines = listOf(
                        SnapshotDiff.Line(SnapshotDiff.Type.REMOVED, "precip_pct", "20"),
                        SnapshotDiff.Line(SnapshotDiff.Type.ADDED, "precip_pct", "70")
                    )
                )
            )
        )
    )

    private fun setContent() = compose.setContent {
        TweatherTheme {
            LogsScreen(commits = commits, revisions = revisions)
        }
    }

    @Test
    fun historyIsTheDefaultTab() {
        setContent()
        compose.onNodeWithText("history.diff").assertIsSelected()
        compose.onNodeWithText("forecast.diff").assertIsNotSelected()
        compose.onNodeWithText("diff --git a/weather_data.json b/weather_data.json")
            .assertExists()
        compose.onNodeWithText("⎇ history").assertExists()
        compose.onNodeWithText("@@ tomorrow @@").assertDoesNotExist()
    }

    @Test
    fun forecastTabShowsRevisionsAndSwitchesTheStatusBar() {
        setContent()
        compose.onNodeWithText("forecast.diff").performClick()
        compose.onNodeWithText("forecast.diff").assertIsSelected()
        compose.onNodeWithText("@@ tomorrow @@").assertExists()
        compose.onNodeWithText("- \"precip_pct\": 20").assertExists()
        compose.onNodeWithText("+ \"precip_pct\": 70").assertExists()
        compose.onNodeWithText("⎇ forecast").assertExists()
        compose.onNodeWithText("1 revisions").assertExists()
        compose.onNodeWithText("diff --git a/weather_data.json b/weather_data.json")
            .assertDoesNotExist()
    }

    @Test
    fun forecastHunkHeadersNameThePerDateFiles() {
        setContent()
        compose.onNodeWithText("forecast.diff").performClick()
        compose.onNodeWithText("+++ b/forecast_2026-08-18.json", substring = true)
            .assertExists()
        compose.onNodeWithText("--- a/forecast_2026-08-18.json", substring = true)
            .assertExists()
    }

    @Test
    fun backToTopAppearsOnlyWhenScrolledAndScrollsBack() {
        val manyCommits = (0 until 40).map { i ->
            CommitUi(
                hash = "hash%03d".format(i),
                cityLabel = "Milan, Lombardy",
                author = "sys@tweather.app",
                timestampEpochSeconds = now - i * 600,
                isInitial = false,
                lines = listOf(
                    SnapshotDiff.Line(SnapshotDiff.Type.REMOVED, "current.temp_c", "$i.0"),
                    SnapshotDiff.Line(SnapshotDiff.Type.ADDED, "current.temp_c", "${i + 1}.0")
                )
            )
        }
        compose.setContent {
            TweatherTheme {
                LogsScreen(commits = manyCommits, revisions = emptyList())
            }
        }
        compose.onNodeWithText("↑ top").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(80)
        compose.onNodeWithText("↑ top").assertExists()
        compose.onNodeWithText("↑ top").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("↑ top").assertDoesNotExist()
        // Back at the top: the first commit header is on screen again
        compose.onNodeWithText("commit hash000 [Milan, Lombardy]").assertExists()
    }

    @Test
    fun emptyForecastShowsItsOwnPlaceholder() {
        compose.setContent {
            TweatherTheme {
                LogsScreen(commits = commits, revisions = emptyList())
            }
        }
        compose.onNodeWithText("forecast.diff").performClick()
        compose.onNodeWithText("// no forecast revisions yet").assertExists()
    }

    /**
     * The Logs under the register rule (Fase 18): the two lines that explain an
     * empty file are sentences and move, while the file names in the tab strip are
     * file names and do not — and neither does `commit`, which is a git noun git
     * itself keeps in every language it ships.
     */
    @Test
    @Config(qualifiers = "it")
    fun emptyFilesExplainThemselvesInItalian() {
        compose.setContent {
            TweatherTheme {
                LogsScreen(commits = emptyList(), revisions = emptyList())
            }
        }
        compose.onNodeWithText("// ancora nessun commit").assertExists()
        compose.onNodeWithText("// aggiorna weather_data.json per registrare il primo").assertExists()
        compose.onNodeWithText("history.diff").assertExists()
    }
}
