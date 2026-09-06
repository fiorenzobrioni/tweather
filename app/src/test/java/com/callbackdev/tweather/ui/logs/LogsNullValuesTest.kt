package com.callbackdev.tweather.ui.logs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.callbackdev.tweather.data.local.SnapshotDiff
import com.callbackdev.tweather.data.local.WeatherSnapshots
import com.callbackdev.tweather.ui.theme.TweatherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `history.diff` is a diff OF `weather_data.json`, so an absent value has to read the
 * way that file writes one: a bare `null`, not the four letters in quotes.
 *
 * The three keys that can hold one are all real states, not defensive ones — no
 * sunrise above the Arctic circle in June, no precipitation probability from a model
 * that does not carry the field, no AQI when that call failed while the forecast
 * succeeded. Before this they were `nullable.toString()`, which printed
 * `"astronomical.sunrise": "null"`: a sunrise at a time spelled n-u-l-l.
 */
@RunWith(RobolectricTestRunner::class)
class LogsNullValuesTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis() / 1000

    private fun commit(vararg lines: SnapshotDiff.Line) = CommitUi(
        hash = "a1b2c3d",
        cityLabel = "Tromsø, Troms",
        author = "sys@tweather.app",
        timestampEpochSeconds = now - 600,
        isInitial = false,
        lines = lines.toList()
    )

    private fun setContent(commit: CommitUi) = compose.setContent {
        TweatherTheme {
            LogsScreen(commits = listOf(commit), revisions = emptyList())
        }
    }

    @Test
    fun `a value that turned null renders bare on both sides of the change`() {
        setContent(
            commit(
                SnapshotDiff.Line(
                    SnapshotDiff.Type.REMOVED, "astronomical.sunrise", "01:20"
                ),
                SnapshotDiff.Line(
                    SnapshotDiff.Type.ADDED, "astronomical.sunrise", WeatherSnapshots.NullValue
                ),
                SnapshotDiff.Line(
                    SnapshotDiff.Type.CONTEXT, "astronomical.sunset", WeatherSnapshots.NullValue
                )
            )
        )

        compose.onNodeWithText("- \"astronomical.sunrise\": \"01:20\"").assertIsDisplayed()
        compose.onNodeWithText("+ \"astronomical.sunrise\": null").assertIsDisplayed()
        compose.onNodeWithText("  \"astronomical.sunset\": null").assertIsDisplayed()
    }

    @Test
    fun `a genuine string value keeps its quotes`() {
        setContent(
            commit(
                SnapshotDiff.Line(SnapshotDiff.Type.CONTEXT, "current.wind_dir", "NW"),
                SnapshotDiff.Line(
                    SnapshotDiff.Type.CONTEXT, "astronomical.daylight_duration", "10h 52m"
                ),
                SnapshotDiff.Line(SnapshotDiff.Type.ADDED, "current.temp_c", "18.5")
            )
        )

        compose.onNodeWithText("  \"current.wind_dir\": \"NW\"").assertIsDisplayed()
        compose.onNodeWithText("  \"astronomical.daylight_duration\": \"10h 52m\"")
            .assertIsDisplayed()
        // numbers stay bare, as they always did
        compose.onNodeWithText("+ \"current.temp_c\": 18.5").assertIsDisplayed()
    }
}
