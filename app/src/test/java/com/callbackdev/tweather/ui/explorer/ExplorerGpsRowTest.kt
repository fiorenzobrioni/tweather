package com.callbackdev.tweather.ui.explorer

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.ui.theme.TweatherTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The pinned `current_location.json` leaf of the Explorer tree. */
@RunWith(RobolectricTestRunner::class)
class ExplorerGpsRowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setScreen(state: ExplorerUiState, onSelectGps: () -> Unit = {}) {
        compose.setContent {
            TweatherTheme {
                ExplorerScreen(
                    state = state,
                    onSelect = {},
                    onRemove = {},
                    onAddCity = {},
                    onSelectGps = onSelectGps
                )
            }
        }
    }

    @Test
    fun hiddenWhileGpsIsOff() {
        setScreen(ExplorerUiState(cities = listOf(CityStore.DefaultCity)))
        compose.onNodeWithText("current_location.json").assertDoesNotExist()
    }

    @Test
    fun visibleAndSelectableWhileGpsIsOn() {
        var selected = false
        setScreen(
            ExplorerUiState(cities = listOf(CityStore.DefaultCity), useGps = true),
            onSelectGps = { selected = true }
        )
        compose.onNodeWithText("current_location.json").assertExists().performClick()
        assertTrue(selected)
        compose.onNodeWithText("  // gps").assertExists()
    }

    @Test
    fun activeMarkerReplacesGpsComment() {
        setScreen(
            ExplorerUiState(
                cities = listOf(CityStore.DefaultCity),
                useGps = true,
                gpsActive = true
            )
        )
        compose.onNodeWithText("  // active").assertExists()
        compose.onNodeWithText("  // gps").assertDoesNotExist()
    }
}
