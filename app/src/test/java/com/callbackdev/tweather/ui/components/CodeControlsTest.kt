package com.callbackdev.tweather.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.callbackdev.tweather.ui.theme.TweatherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The text-rendered controls (`[x]`/`[ ]`, `true`/`false`) must stay interactive. */
@RunWith(RobolectricTestRunner::class)
class CodeControlsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun checkboxRendersBracketsAndTogglesOnTap() {
        var checked by mutableStateOf(false)
        compose.setContent {
            TweatherTheme {
                CodeCheckbox(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    label = "severe_weather_alerts"
                )
            }
        }

        compose.onNodeWithText("[ ]").assertExists()
        compose.onNode(isToggleable()).assertIsOff()

        compose.onNodeWithText("severe_weather_alerts").performClick()

        compose.onNodeWithText("[x]").assertExists()
        compose.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun toggleRendersBooleanWordAndFlipsOnTap() {
        var value by mutableStateOf(true)
        compose.setContent {
            TweatherTheme {
                CodeToggle(value = value, onValueChange = { value = it })
            }
        }

        compose.onNodeWithText("true").assertExists()
        compose.onNode(isToggleable()).assertIsOn()

        compose.onNodeWithText("true").performClick()

        compose.onNodeWithText("false").assertExists()
        compose.onNode(isToggleable()).assertIsOff()
    }
}
