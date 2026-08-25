package com.callbackdev.tweather.ui.search

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * The `"saved_cities"` array of cities.json (Fase 10b — the old Explorer tree's
 * behavior, ported): pinned GPS entry, active marker, tap-to-activate, `[rm]`.
 */
@RunWith(RobolectricTestRunner::class)
class SearchCitiesSectionTest {

    @get:Rule
    val compose = createComposeRule()

    private val milan = City(1, "Milan", "Lombardy", "Italy", Coordinates(45.46, 9.19), "Europe/Rome")
    private val turin = City(2, "Turin", "Piedmont", "Italy", Coordinates(45.07, 7.69), "Europe/Rome")

    private fun setScreen(
        cities: CitiesUiState,
        onActivate: (City) -> Unit = {},
        onActivateGps: () -> Unit = {},
        onRemove: (City) -> Unit = {}
    ) {
        compose.setContent {
            TweatherTheme {
                SearchScreen(
                    state = SearchUiState(),
                    recents = emptyList(),
                    onQueryChange = {},
                    onSearchNow = {},
                    onSelect = {},
                    onRecent = {},
                    cities = cities,
                    onActivate = onActivate,
                    onActivateGps = onActivateGps,
                    onRemove = onRemove
                )
            }
        }
    }

    @Test
    fun `the gps entry is hidden while gps is off`() {
        setScreen(CitiesUiState(cities = listOf(milan), activeCity = milan))
        compose.onNodeWithText("\"current_location.json\"").assertDoesNotExist()
    }

    @Test
    fun `the gps entry is pinned and selectable while gps is on`() {
        var selected = false
        setScreen(
            CitiesUiState(cities = listOf(milan), activeCity = milan, useGps = true),
            onActivateGps = { selected = true }
        )
        compose.onNodeWithText("\"current_location.json\"").assertExists().performClick()
        assertTrue(selected)
        compose.onNodeWithText(",  // gps").assertExists()
    }

    @Test
    fun `the active marker moves to the gps entry when it is the source`() {
        setScreen(
            CitiesUiState(cities = listOf(milan), useGps = true, gpsActive = true)
        )
        compose.onNodeWithText(",  // active").assertExists()
        compose.onNodeWithText(",  // gps").assertDoesNotExist()
    }

    @Test
    fun `tapping a saved city activates it`() {
        var activated: City? = null
        setScreen(
            CitiesUiState(cities = listOf(milan, turin), activeCity = milan),
            onActivate = { activated = it }
        )
        compose.onNodeWithText("\"turin.json\"").performClick()
        assertEquals(turin, activated)
    }

    @Test
    fun `the active city carries the active comment`() {
        setScreen(CitiesUiState(cities = listOf(milan, turin), activeCity = milan))
        compose.onNodeWithText(",  // active").assertExists()
    }

    /**
     * Fase 14b: the last city is removable like any other. The guard that hid `[rm]`
     * here existed only because the data layer could not represent an empty list.
     */
    @Test
    fun `rm is offered even on the last saved city`() {
        setScreen(CitiesUiState(cities = listOf(milan), activeCity = milan))
        compose.onNodeWithText("[rm]").assertExists()
    }

    @Test
    fun `rm removes its own row's city`() {
        var removed: City? = null
        setScreen(
            CitiesUiState(cities = listOf(milan, turin), activeCity = milan),
            onRemove = { removed = it }
        )
        compose.onAllNodesWithText("[rm]")[1].performClick()
        assertEquals(turin, removed)
    }

    /** Fase 11b: the open file carries the active indicator like every other screen. */
    @Test
    fun `the open file renders as a selected tab`() {
        setScreen(CitiesUiState(cities = listOf(milan), activeCity = milan))
        compose.onNodeWithText("cities.json").assertIsSelected()
    }
}
