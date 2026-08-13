package com.callbackdev.tweather.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import com.callbackdev.tweather.ui.theme.TweatherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CodeCanvasTest {

    @get:Rule
    val compose = createComposeRule()

    private val lines = listOf(
        CodeLine(AnnotatedString("{")),
        CodeLine(AnnotatedString("\"active\": true"), indent = 1),
        CodeLine(AnnotatedString("}"))
    )

    @Test
    fun gutterShowsLineNumbersWhenEnabled() {
        compose.setContent {
            TweatherTheme {
                CodeCanvas(lines = lines, options = EditorOptions(showLineNumbers = true))
            }
        }
        compose.onNodeWithText("1").assertExists()
        compose.onNodeWithText("2").assertExists()
        compose.onNodeWithText("3").assertExists()
    }

    @Test
    fun gutterIsHiddenByDefault() {
        compose.setContent {
            TweatherTheme {
                CodeCanvas(lines = lines) // mobile default: no line numbers
            }
        }
        compose.onNodeWithText("1").assertDoesNotExist()
    }

    @Test
    fun tappableCodeLineInvokesItsOnClick() {
        var clicks = 0
        compose.setContent {
            TweatherTheme {
                CodeCanvas(
                    lines = listOf(
                        CodeLine(AnnotatedString("\"word_wrap\": false"), onClick = { clicks++ })
                    )
                )
            }
        }
        compose.onNodeWithText("\"word_wrap\": false").performClick()
        assertEquals(1, clicks)
    }
}
