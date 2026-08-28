package com.callbackdev.tweather.ui.weather

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.callbackdev.tweather.data.MainEditorFile
import com.callbackdev.tweather.domain.WeatherException
import com.callbackdev.tweather.ui.theme.TweatherTheme
import java.time.Duration
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The main screen's two-file tab bar (Fase 10): `weather_data.json` (default) and
 * the city's `README.md`. Since Fase 10b the way to the city list is the status
 * bar's ⎇ (the branch-switcher), not a pinned tab bar action.
 */
@RunWith(RobolectricTestRunner::class)
class WeatherTabsTest {

    @get:Rule
    val compose = createComposeRule()

    private var helpOpened = 0

    private fun setContent(
        initial: MainEditorFile = MainEditorFile.JSON,
        showHelpHint: Boolean = false
    ) =
        compose.setContent {
            TweatherTheme {
                var active by remember { mutableStateOf(initial) }
                val files = editorFiles(skyEnabled = false)
                WeatherScreen(
                    state = WeatherUiState(report = sampleWeatherReport(), isLoading = false),
                    onRefresh = {},
                    activeFile = active,
                    editorFiles = files,
                    onSelectTab = { active = editorFileAt(it, skyEnabled = false) },
                    showHelpHint = showHelpHint,
                    onOpenHelp = { helpOpened++ }
                )
            }
        }

    /**
     * Fase 14d: one line, on both files, and it opens HELP.md. Not a carousel and
     * not a dialog — the pointer lives in the document it interrupts.
     */
    @Test
    fun `the help hint heads the document and opens the file`() {
        setContent(showHelpHint = true)

        compose.onNodeWithText("// new here? open HELP.md").performClick()

        assertTrue(helpOpened == 1)
    }

    @Test
    fun `without the hint the document starts at its own first line`() {
        setContent()

        compose.onNodeWithText("// new here? open HELP.md").assertDoesNotExist()
    }

    @Test
    fun `the json file is the default tab`() {
        setContent()
        compose.onNodeWithText("weather_data.json").assertIsSelected()
        compose.onNodeWithText("README.md").assertIsNotSelected()
        compose.onNodeWithText("\"location\": {").assertExists()
        compose.onNodeWithText("# New York").assertDoesNotExist()
    }

    @Test
    fun `selecting README shows the markdown summary and hides the json`() {
        setContent()
        compose.onNodeWithText("README.md").performClick()
        compose.onNodeWithText("README.md").assertIsSelected()
        compose.onNodeWithText("# New York").assertExists()
        compose.onNodeWithText("## Current").assertExists()
        compose.onNodeWithText("**18.5°C** · Partly Cloudy ⛅").assertExists()
        compose.onNodeWithText("\"location\": {").assertDoesNotExist()
    }

    @Test
    fun `the ls cities action is gone from the tab bar`() {
        setContent()
        compose.onNodeWithText("$ ls cities/").assertDoesNotExist()
    }

    @Test
    fun `the status bar branch name opens the city list`() {
        var opened = false
        compose.setContent {
            TweatherTheme {
                WeatherScreen(
                    state = WeatherUiState(report = sampleWeatherReport(), isLoading = false),
                    onRefresh = {},
                    onOpenCities = { opened = true }
                )
            }
        }
        compose.onNodeWithText("⎇ New York").performClick()
        assertTrue(opened)
    }

    @Test
    fun `a persisted README selection renders on first frame`() {
        setContent(initial = MainEditorFile.README)
        compose.onNodeWithText("README.md").assertIsSelected()
        compose.onNodeWithText("# New York").assertExists()
    }

    @Test
    fun `loading comments follow the file's comment syntax`() {
        compose.setContent {
            TweatherTheme {
                WeatherScreen(
                    state = WeatherUiState(isLoading = true),
                    onRefresh = {},
                    activeFile = MainEditorFile.README
                )
            }
        }
        // HTML comments, because it is a markdown file — and a sentence inside them
        // since Fase 17, because it is the prose one.
        compose.onNodeWithText("<!-- Updating… -->").assertExists()
    }

    /**
     * Fase 17. `net::ERR_INTERNET_DISCONNECTED` is Chrome's name for "the phone is
     * offline": exactly right in a file that is code, and a vocabulary test in the
     * one document written for somebody who does not read `git` for a living.
     */
    @Test
    fun `the README says the failure in words and the JSON in codes`() {
        val state = WeatherUiState(isLoading = false, error = WeatherException.NoNetwork())
        compose.setContent {
            TweatherTheme {
                var active by remember { mutableStateOf(MainEditorFile.README) }
                val files = editorFiles(skyEnabled = false)
                WeatherScreen(
                    state = state,
                    onRefresh = {},
                    activeFile = active,
                    editorFiles = files,
                    onSelectTab = { active = editorFileAt(it, skyEnabled = false) }
                )
            }
        }
        compose.onNodeWithText("<!-- No connection: the weather could not be updated. -->")
            .assertExists()
        compose.onNodeWithText("<!-- Tap ( ↻ ) to try again. -->").assertExists()

        compose.onNodeWithText("weather_data.json").performClick()
        compose.onNodeWithText(
            "// ERROR: net::ERR_INTERNET_DISCONNECTED — check your connection"
        ).assertExists()
    }

    /**
     * Fase 17: a document the app could not refresh says so, in each file's own
     * register, BEFORE the numbers it is about to print.
     */
    @Test
    fun `a stale document announces its age above itself`() {
        val report = sampleWeatherReport()
        val state = WeatherUiState(
            report = report,
            isLoading = false,
            error = WeatherException.NoNetwork(),
            staleFor = Duration.ofHours(3)
        )
        compose.setContent {
            TweatherTheme {
                var active by remember { mutableStateOf(MainEditorFile.README) }
                val files = editorFiles(skyEnabled = false)
                WeatherScreen(
                    state = state,
                    onRefresh = {},
                    activeFile = active,
                    editorFiles = files,
                    onSelectTab = { active = editorFileAt(it, skyEnabled = false) }
                )
            }
        }
        // 09:30 in America/New_York, the sample's last sync
        compose.onNodeWithText(
            "<!-- Below is the last update that worked, from 09:30 (3 hours ago). -->"
        ).assertExists()
        compose.onNodeWithText("# New York").assertExists()

        compose.onNodeWithText("weather_data.json").performClick()
        compose.onNodeWithText("// stale: last good fetch 3h ago").assertExists()
    }

    /**
     * `weather_data.json`'s state lines under the register rule (Fase 18).
     *
     * The three things that stay are the point: the `//`, which is the file's
     * syntax; the `GET` line, which is the useful form of "it is fetching"; and the
     * file name inside the sentence, which is what the reader would go looking for.
     * `README.md` one tab away says the same two facts in prose, which is where the
     * plain-language reading has lived since Fase 17.
     */
    @Test
    @Config(qualifiers = "it")
    fun theStateLinesSpeakItalianWhileTheMachineLineDoesNot() {
        compose.setContent {
            TweatherTheme {
                WeatherScreen(
                    state = WeatherUiState(isLoading = true),
                    onRefresh = {},
                    activeFile = MainEditorFile.JSON,
                    editorFiles = editorFiles(skyEnabled = false),
                    onSelectTab = {}
                )
            }
        }
        compose.onNodeWithText("// scarico weather_data.json …").assertExists()
        compose.onNodeWithText("// GET https://api.open-meteo.com/v1/forecast").assertExists()
    }

    @Test
    @Config(qualifiers = "it")
    fun theNoLocationStateSpeaksItalianOnTheJsonToo() {
        compose.setContent {
            TweatherTheme {
                WeatherScreen(
                    state = WeatherUiState(noLocation = true, isLoading = false),
                    onRefresh = {},
                    activeFile = MainEditorFile.JSON,
                    editorFiles = editorFiles(skyEnabled = false),
                    onSelectTab = {}
                )
            }
        }
        compose.onNodeWithText("// nessuna posizione configurata").assertExists()
        compose.onNodeWithText("// suggerimento: apri cities.json e cerca una città").assertExists()
    }

}
