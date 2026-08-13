package com.callbackdev.tweather.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.callbackdev.tweather.ui.theme.TweatherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tab navigation over the real app shell (real NavHost, ViewModels and stores; the
 * weather fetch itself may fail on the JVM — the editor tab renders either way, so
 * the assertions only touch each screen's "file name").
 */
@RunWith(RobolectricTestRunner::class)
class TweatherNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setApp() {
        compose.setContent {
            TweatherTheme {
                TweatherApp()
            }
        }
    }

    @Test
    fun startDestinationIsTheWeatherEditor() {
        setApp()
        compose.onNodeWithText("weather_data.json").assertExists()
    }

    @Test
    fun bottomBarSwitchesBetweenTheFourFiles() {
        setApp()

        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithText("search_query.json").assertExists()

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("settings.config").assertExists()

        compose.onNodeWithText("Logs").performClick()
        compose.onNodeWithText("weather_history.diff").assertExists()

        compose.onNodeWithText("Explorer").performClick()
        compose.onNodeWithText("weather_data.json").assertExists()
    }
}
