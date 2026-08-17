package com.callbackdev.tweather.ui.logs

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.callbackdev.tweather.data.local.SnapshotDiff
import com.callbackdev.tweather.ui.theme.TweatherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Weather CI check lines in `weather_history.diff` (Fase 11). */
@RunWith(RobolectricTestRunner::class)
class LogsFiredRulesTest {

    @get:Rule
    val compose = createComposeRule()

    private fun commit(firedRules: List<String>) = CommitUi(
        hash = "a1b2c3d",
        cityLabel = "Milan, Lombardy",
        author = "sys@tweather.app",
        timestampEpochSeconds = System.currentTimeMillis() / 1000 - 600,
        isInitial = false,
        lines = listOf(SnapshotDiff.Line(SnapshotDiff.Type.CONTEXT, "current.temp_c", "18.5")),
        firedRules = firedRules
    )

    @Test
    fun `fired rules render as check lines, silent commits show none`() {
        compose.setContent {
            TweatherTheme {
                LogsScreen(
                    commits = listOf(commit(listOf("umbrella", "sunscreen"))),
                    revisions = emptyList()
                )
            }
        }
        compose.onNodeWithText("✓ rule \"umbrella\" fired").assertExists()
        compose.onNodeWithText("✓ rule \"sunscreen\" fired").assertExists()
    }

    @Test
    fun `a commit without fired rules has no check lines`() {
        compose.setContent {
            TweatherTheme {
                LogsScreen(commits = listOf(commit(emptyList())), revisions = emptyList())
            }
        }
        compose.onNodeWithText("✓ rule \"umbrella\" fired").assertDoesNotExist()
    }
}
