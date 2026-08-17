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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The main screen's two-file tab bar (Fase 10): `weather_data.json` (default) and
 * the city's `README.md`, with the `$ ls cities/` action still pinned right.
 */
@RunWith(RobolectricTestRunner::class)
class WeatherTabsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setContent(initial: MainEditorFile = MainEditorFile.JSON) =
        compose.setContent {
            TweatherTheme {
                var active by remember { mutableStateOf(initial) }
                WeatherScreen(
                    state = WeatherUiState(report = sampleWeatherReport(), isLoading = false),
                    onRefresh = {},
                    activeFile = active,
                    onSelectFile = { active = it }
                )
            }
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
    fun `the ls cities action survives on both tabs`() {
        setContent()
        compose.onNodeWithText("$ ls cities/").assertExists()
        compose.onNodeWithText("README.md").performClick()
        compose.onNodeWithText("$ ls cities/").assertExists()
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
