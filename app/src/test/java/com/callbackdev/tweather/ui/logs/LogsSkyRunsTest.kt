package com.callbackdev.tweather.ui.logs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
     * `assertExists`, not `assertIsDisplayed`, and the difference is a real finding:
     * the three names together are 53 monospace characters, which is wider than a
     * 360dp strip. The bar has scrolled horizontally since it was written and, since
     * Fase 16c, brings the ACTIVE tab into view — so the file is reachable with a
     * swipe and lands in view once selected — but it is not on screen at rest. Two
     * `.diff` names already filled that strip; the third file is what made it
     * obvious. Recorded in PLANNING rather than hidden behind a laxer assertion.
     */
    @Test
    fun `the strip grows a third file when the module is on`() {
        setScreen()
        compose.onNodeWithText("weather_history.diff").assertIsDisplayed()
        compose.onNodeWithText("weather_forecast.diff").assertExists()
        compose.onNodeWithText("sky_runs.log").assertExists()
    }

    @Test
    fun `with the module off the strip is two files again`() {
        setScreen(skyEnabled = false)
        compose.onNodeWithText("sky_runs.log").assertDoesNotExist()
    }

    /**
     * The same run, seen from the commit. `weather_history.diff` answers "what
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
        // Scrolled to first, because that is what a user does: the strip is wider
        // than the screen (see above), so the tab has to be brought into reach
        // before it can be tapped.
        compose.onNodeWithText("sky_runs.log").performScrollTo().performClick()
        compose.onNodeWithText("20:12", substring = true).assertIsDisplayed()
        compose.onNodeWithText("obs +12m", substring = true).assertIsDisplayed()
    }
}
