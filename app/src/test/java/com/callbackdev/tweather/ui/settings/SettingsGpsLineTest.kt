package com.callbackdev.tweather.ui.settings

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.callbackdev.tweather.data.AppSettings
import com.callbackdev.tweather.ui.theme.TweatherTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The `location.use_gps` line of settings.config across its permission states. */
@RunWith(RobolectricTestRunner::class)
class SettingsGpsLineTest {

    @get:Rule
    val compose = createComposeRule()

    private val noActions = SettingsActions(
        {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}
    )

    private fun setScreen(
        gpsState: GpsLineState,
        gpsDeniedFlash: Boolean = false,
        onGpsLine: () -> Unit = {}
    ) {
        compose.setContent {
            TweatherTheme {
                SettingsScreen(
                    settings = AppSettings(),
                    actions = noActions,
                    gpsState = gpsState,
                    gpsDeniedFlash = gpsDeniedFlash,
                    onGpsLine = onGpsLine
                )
            }
        }
    }

    /** The line sits deep in the LazyColumn: scroll it into view first. */
    private fun onLine(text: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        return compose.onNodeWithText(text).assertExists()
    }

    @Test
    fun offStateRendersHintAndTapFiresAction() {
        var tapped = false
        setScreen(GpsLineState.Off, onGpsLine = { tapped = true })
        onLine("\"use_gps\": false  // tap to enable").performClick()
        assertTrue(tapped)
    }

    @Test
    fun onStateShowsExplorerHint() {
        setScreen(GpsLineState.On)
        onLine("\"use_gps\": true  // current_location.json in explorer")
    }

    @Test
    fun revokedStateKeepsTrueAndShowsError() {
        setScreen(GpsLineState.Revoked)
        onLine("\"use_gps\": true  // ERROR: permission revoked — tap to re-grant")
    }

    @Test
    fun permanentlyDeniedStatePointsToSystemSettings() {
        setScreen(GpsLineState.DeniedPermanently)
        onLine("\"use_gps\": false  // ERROR: denied — open system settings")
    }

    @Test
    fun transientDenialRendersErrorCommentLine() {
        setScreen(GpsLineState.Off, gpsDeniedFlash = true)
        onLine("// ERROR: permission denied — gps stays off")
    }
}
