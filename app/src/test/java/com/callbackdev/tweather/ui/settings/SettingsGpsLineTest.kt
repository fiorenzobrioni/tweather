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
import org.robolectric.annotation.Config

/** The `location.use_gps` line of settings.config across its permission states. */
@RunWith(RobolectricTestRunner::class)
class SettingsGpsLineTest {

    @get:Rule
    val compose = createComposeRule()

    private val noActions = SettingsActions(
        {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}
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

    /**
     * The hint used to end `in explorer` — a tab nobody can see. The first tab lost
     * that name in Fase 11b and only its nav route kept it, so the line was pointing
     * at a word that is not on screen anywhere. The translation pass of Fase 18 is
     * what walked past it; the entry lives in `cities.json`, and that is the file
     * the reader is actually looking for.
     */
    @Test
    fun onStateNamesTheFileTheEntryLivesIn() {
        setScreen(GpsLineState.On)
        onLine("\"use_gps\": true  // current_location.json in cities.json")
    }

    /**
     * `"use_gps"` is a key and `false` is its value, so the left of the line does
     * not move; the hint beside it is a sentence and does (Fase 18).
     */
    @Test
    @Config(qualifiers = "it")
    fun theHintSpeaksItalianWhileTheKeyDoesNot() {
        setScreen(GpsLineState.Off)
        onLine("\"use_gps\": false  // tocca per attivare")
    }

    @Test
    @Config(qualifiers = "it")
    fun theGpsErrorKeepsItsLevel() {
        setScreen(GpsLineState.Revoked)
        onLine("\"use_gps\": true  // ERROR: permesso revocato — tocca per riconcederlo")
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
