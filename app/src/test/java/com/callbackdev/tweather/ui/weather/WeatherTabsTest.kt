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
import com.callbackdev.tweather.ui.theme.TweatherTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
                WeatherScreen(
                    state = WeatherUiState(report = sampleWeatherReport(), isLoading = false),
                    onRefresh = {},
                    activeFile = active,
                    onSelectFile = { active = it },
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
        compose.onNodeWithText("<!-- fetching README.md … -->").assertExists()
    }
}
