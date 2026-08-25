package com.callbackdev.tweather.ui.init

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.callbackdev.tweather.ui.theme.TweatherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `$ tweather init` (Fase 14c): three answers, and every one of them has to be an
 * answer — including `skip`, which is what lets the honest empty editor of Fase 14b
 * be somewhere the user chose to be.
 */
@RunWith(RobolectricTestRunner::class)
class InitScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var gps = 0
    private var search = 0
    private var skip = 0

    private fun setScreen(permissionDenied: Boolean = false) {
        compose.setContent {
            TweatherTheme {
                InitScreen(
                    onUseGps = { gps++ },
                    onSearchCity = { search++ },
                    onSkip = { skip++ },
                    permissionDenied = permissionDenied
                )
            }
        }
    }

    @Test
    fun `the command and the three ways out are on screen`() {
        setScreen()

        compose.onNodeWithText("tweather init", substring = true).assertExists()
        compose.onNodeWithText("use my position", substring = true).assertExists()
        compose.onNodeWithText("search a city", substring = true).assertExists()
        compose.onNodeWithText("skip", substring = true).assertExists()
    }

    @Test
    fun `each choice reports itself once`() {
        setScreen()

        compose.onNodeWithText("> use my position").performClick()
        compose.onNodeWithText("> search a city").performClick()
        compose.onNodeWithText("> skip").performClick()

        assertEquals(1, gps)
        assertEquals(1, search)
        assertEquals(1, skip)
    }

    /** A denied permission must not dead-end the screen: the other ways out stay. */
    @Test
    fun `a denied permission is said out loud and leaves the other choices`() {
        setScreen(permissionDenied = true)

        compose.onNodeWithText("permission denied", substring = true).assertExists()
        compose.onNodeWithText("> search a city").performClick()
        assertTrue(search == 1)
    }

    @Test
    fun `the setup session is the only open file`() {
        setScreen()

        compose.onNodeWithText(SetupFile).assertExists()
    }
}
