package com.callbackdev.tweather.ui.logs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.callbackdev.tweather.data.local.SnapshotDiff
import com.callbackdev.tweather.domain.sky.SkyRun
import com.callbackdev.tweather.domain.sky.SkyVerdictKind
import com.callbackdev.tweather.ui.theme.TweatherTheme
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Logs strip's third file (Fase 16e), and the check lines the same runs leave on
 * the commit that observed them — one store, two views.
 */
@RunWith(RobolectricTestRunner::class)
class LogsSkyRunsTest {

    @get:Rule
    val compose = createComposeRule()

    private val at = LocalDateTime.parse("2026-08-26T20:12")
        .atZone(ZoneId.systemDefault()).toInstant().epochSecond

    private val run = SkyRun("sun.set", at, SkyVerdictKind.PASS.name, 8, 12)

    private val commit = CommitUi(
        hash = "a1b2c3d",
        cityLabel = "Milan, Lombardy",
        author = "sys@tweather.app",
        timestampEpochSeconds = at + 720,
        isInitial = false,
        lines = listOf(SnapshotDiff.Line(SnapshotDiff.Type.CONTEXT, "current.temp_c", "20.0")),
        skyRuns = listOf(run)
    )

    private fun setScreen(skyEnabled: Boolean = true) {
        compose.setContent {
            TweatherTheme {
                LogsScreen(
                    commits = listOf(commit),
                    revisions = emptyList(),
                    skyRuns = listOf(SkyRunsLog.Row(run, commit.timestampEpochSeconds)),
                    skyEnabled = skyEnabled
                )
            }
        }
    }

    /**
     * All three DISPLAYED, not merely present — which is the point of Fase 16f's
     * rename. With `weather_history.diff` and `weather_forecast.diff` the strip was
     * 53 monospace characters wide and the third tab sat entirely off-screen at every
     * phone width, so the file could only be found by somebody who already knew it
     * was there. Measured at 320dp, 360dp and 411dp.
     */
    @Test
    fun `the strip grows a third file, and all three fit on the screen`() {
        setScreen()
        compose.onNodeWithText("history.diff").assertIsDisplayed()
        compose.onNodeWithText("forecast.diff").assertIsDisplayed()
        compose.onNodeWithText("sky_runs.log").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `the third file fits even on the narrowest phone`() {
        setScreen()
        compose.onNodeWithText("sky_runs.log").assertIsDisplayed()
    }

    @Test
    fun `with the module off the strip is two files again`() {
        setScreen(skyEnabled = false)
        compose.onNodeWithText("sky_runs.log").assertDoesNotExist()
    }

    /**
     * The same run, seen from the commit. `history.diff` answers "what
     * changed" and gets a check line; `sky_runs.log` answers "what the sky did" and
     * gets a row. Neither can disagree with the other, because there is one column
     * behind both.
     */
    @Test
    fun `the run leaves a check line on the commit that observed it`() {
        setScreen()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("sun.set", substring = true))
        compose.onNodeWithText("✓ sun.set ran clear").assertIsDisplayed()
    }

    @Test
    fun `the third tab opens on the journal`() {
        setScreen()
        compose.onNodeWithText("sky_runs.log").performClick()
        compose.onNodeWithText("20:12", substring = true).assertIsDisplayed()
        compose.onNodeWithText("obs +12m", substring = true).assertIsDisplayed()
    }
}
