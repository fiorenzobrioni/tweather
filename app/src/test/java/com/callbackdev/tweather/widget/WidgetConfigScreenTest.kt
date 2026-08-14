package com.callbackdev.tweather.widget

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.ui.theme.TweatherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The per-widget city picker (`widget.config`). Only the stateless screen is exercised:
 * the activity around it just loads the stores and writes the pin, and the store itself
 * is covered by WidgetCityStoreTest.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetConfigScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val milan = CityStore.DefaultCity

    // Two words on purpose: the row label is a slug of the city name, not the name
    private val newYork = City(
        id = 5_128_581,
        name = "New York",
        region = "New York",
        country = "United States",
        coordinates = Coordinates(40.7143, -74.006),
        timezone = "America/New_York"
    )

    /** Hints carry the two leading spaces the row draws before the comment. */
    private val selected = "  // selected"

    private fun setScreen(
        state: WidgetConfigState = WidgetConfigState(cities = listOf(milan, newYork)),
        onFollowApp: () -> Unit = {},
        onSelectCity: (City) -> Unit = {},
        onSelectGps: () -> Unit = {}
    ) {
        compose.setContent {
            TweatherTheme {
                WidgetConfigScreen(
                    state = state,
                    onFollowApp = onFollowApp,
                    onSelectCity = onSelectCity,
                    onSelectGps = onSelectGps
                )
            }
        }
    }

    /** A row merges its dot, file name and comment into the one clickable node, so the
     * file name alone both finds the row and is the thing to click. */
    private fun onRow(fileName: String): SemanticsNodeInteraction =
        compose.onNodeWithText(fileName).assertExists()

    @Test
    fun defaultStateFollowsTheAppAndListsEverySavedCity() {
        setScreen()

        compose.onNode(hasText("active_file") and hasText(selected)).assertExists()
        // Exactly one source can be selected, so the marker must not be duplicated
        compose.onAllNodesWithText(selected).assertCountEquals(1)

        onRow("milan.json")
        onRow("new_york.json")
        // GPS is off in the default state: there is no pseudo-city to pin
        compose.onNodeWithText("current_location.json").assertDoesNotExist()
    }

    @Test
    fun tappingACityRowPinsThatCity() {
        var picked: City? = null
        setScreen(onSelectCity = { picked = it })

        onRow("new_york.json").performClick()

        assertEquals(newYork, picked)
    }

    @Test
    fun tappingActiveFileGoesBackToFollowingTheApp() {
        var followed = false
        setScreen(
            state = WidgetConfigState(cities = listOf(milan), pinnedCityId = milan.id),
            onFollowApp = { followed = true }
        )

        onRow("active_file").performClick()

        assertTrue(followed)
    }

    @Test
    fun theGpsRowIsOfferedAndPinnableWhileGpsIsOn() {
        var gpsPinned = false
        setScreen(
            state = WidgetConfigState(
                cities = listOf(milan),
                gpsAvailable = true,
                gpsLabel = "Turin"
            ),
            onSelectGps = { gpsPinned = true }
        )

        compose.onNodeWithText("  // gps").assertExists()
        onRow("current_location.json").performClick()

        assertTrue(gpsPinned)
    }

    @Test
    fun aPinnedCityTakesTheSelectedMarkerFromActiveFile() {
        setScreen(
            state = WidgetConfigState(
                cities = listOf(milan, newYork),
                pinnedCityId = newYork.id
            )
        )

        compose.onNode(hasText("new_york.json") and hasText(selected)).assertExists()
        compose.onAllNodesWithText(selected).assertCountEquals(1)
        // active_file stays tappable but explains what it does instead of claiming the pin
        compose.onNode(hasText("active_file") and hasText("  // follows the app")).assertExists()
    }
}
