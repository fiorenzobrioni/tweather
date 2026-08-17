package com.callbackdev.tweather.widget

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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
 * The per-widget city picker (`widget.config`), rendered as a config file in the
 * `settings.config` format. Only the stateless screen is exercised: the activity
 * around it just loads the stores and writes the pin, and the store itself is
 * covered by WidgetCityStoreTest.
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

    private fun setScreen(
        state: WidgetConfigState = WidgetConfigState(cities = listOf(milan)),
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

    /**
     * One `available_sources` entry: the whole JSON line is a single text node (a
     * [com.callbackdev.tweather.ui.components.CodeLine]), so the exact line is both
     * the way to find the row and the thing to click. [comma] is false on the last
     * entry of the array, JSON style.
     */
    private fun entry(file: String, comma: Boolean = true, hint: String? = null): String =
        "\"$file\"" + (if (comma) "," else "") + (hint?.let { "  $it" } ?: "")

    private fun onEntry(text: String): SemanticsNodeInteraction =
        compose.onNodeWithText(text).assertExists()

    @Test
    fun theBufferIsAConfigFileNotAFileTree() {
        setScreen()

        compose.onNodeWithText("// Tweather Widget Configuration").assertExists()
        compose.onNodeWithText("{").assertExists()
        compose.onNodeWithText("\"available_sources\": [  // tap to pin").assertExists()
        compose.onNodeWithText("]").assertExists()
        // No "widget" wrapper block: the file is already widget.config
        compose.onNodeWithText("\"widget\": {").assertDoesNotExist()
    }

    @Test
    fun defaultStateFollowsTheAppAndListsEverySavedCity() {
        setScreen(state = WidgetConfigState(cities = listOf(milan, newYork)))

        compose.onNodeWithText("\"source\": \"active_file\",").assertExists()
        onEntry(entry("active_file", hint = "// selected"))
        // Exactly one source can be selected, so the marker must not be duplicated
        compose.onAllNodes(hasText("// selected", substring = true)).assertCountEquals(1)

        onEntry(entry("milan.json"))
        onEntry(entry("new_york.json", comma = false))
        // GPS is off in the default state: there is no pseudo-city to pin
        compose.onNodeWithText("current_location.json", substring = true).assertDoesNotExist()
    }

    @Test
    fun tappingACityRowPinsThatCity() {
        var picked: City? = null
        setScreen(
            state = WidgetConfigState(cities = listOf(milan, newYork)),
            onSelectCity = { picked = it }
        )

        onEntry(entry("new_york.json", comma = false)).performClick()

        assertEquals(newYork, picked)
    }

    @Test
    fun tappingActiveFileGoesBackToFollowingTheApp() {
        var followed = false
        setScreen(
            state = WidgetConfigState(
                cities = listOf(milan),
                pinnedCityId = milan.id
            ),
            onFollowApp = { followed = true }
        )

        onEntry(entry("active_file", hint = "// follows the app")).performClick()

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

        onEntry(entry("current_location.json")).performClick()

        assertTrue(gpsPinned)
    }

    @Test
    fun aPinnedCityTakesTheSelectedMarkerFromActiveFileAndBecomesTheSource() {
        setScreen(
            state = WidgetConfigState(
                cities = listOf(milan, newYork),
                pinnedCityId = newYork.id
            )
        )

        compose.onNodeWithText("\"source\": \"new_york.json\",").assertExists()
        onEntry(entry("new_york.json", comma = false, hint = "// selected"))
        compose.onAllNodes(hasText("// selected", substring = true)).assertCountEquals(1)
        // active_file stays tappable but explains what it does instead of claiming the pin
        onEntry(entry("active_file", hint = "// follows the app"))
    }

    @Test
    fun homonymCitiesRenderDistinctFilesAndOnlyThePinnedOneIsSelected() {
        val springfieldIl = City(1L, "Springfield", "Illinois", "United States", Coordinates(39.8, -89.6), null)
        val springfieldMo = City(2L, "Springfield", "Missouri", "United States", Coordinates(37.2, -93.3), null)
        setScreen(
            state = WidgetConfigState(
                cities = listOf(springfieldIl, springfieldMo),
                pinnedCityId = springfieldMo.id
            )
        )

        compose.onNodeWithText("\"source\": \"springfield_missouri.json\",").assertExists()
        onEntry(entry("springfield_illinois.json"))
        onEntry(entry("springfield_missouri.json", comma = false, hint = "// selected"))
        // Selection is by id: the twin slug must not inherit the marker
        compose.onAllNodes(hasText("// selected", substring = true)).assertCountEquals(1)
    }

    @Test
    fun aPinnedCityThatIsNoLongerSavedFallsBackToFollowingTheApp() {
        setScreen(
            state = WidgetConfigState(
                cities = listOf(milan),
                pinnedCityId = 999_999L
            )
        )

        compose.onNodeWithText("\"source\": \"active_file\",").assertExists()
        onEntry(entry("active_file", hint = "// selected"))
    }
}
