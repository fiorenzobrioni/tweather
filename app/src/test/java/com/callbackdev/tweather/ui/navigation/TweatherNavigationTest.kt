package com.callbackdev.tweather.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.ui.theme.TweatherTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tab navigation over the real app shell (real NavHost, ViewModels and stores; the
 * weather fetch itself may fail on the JVM — the editor tab renders either way, so
 * the assertions only touch each screen's "file name").
 *
 * Since Fase 14b the shell also decides between `$ tweather init` and the workspace,
 * so the city store is injected per test: the decision has to be this test's input,
 * not whatever a previous test left on disk.
 */
@RunWith(RobolectricTestRunner::class)
class TweatherNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** [used] = an install that predates the empty state, i.e. one with a history. */
    private fun cityStore(used: Boolean): CityStore {
        val store = CityStore(
            PreferenceDataStoreFactory.create(scope = scope) {
                tmp.newFile("cities-${System.nanoTime()}.preferences_pb")
            },
            Json
        )
        // What MainActivity does at startup, before the shell can draw anything
        runBlocking { store.migrateFirstRun(hasHistory = used) }
        ServiceLocator.overrideForTests(cityStore = store)
        return store
    }

    @After
    fun tearDown() {
        ServiceLocator.overrideForTests()
        scope.cancel()
    }

    private fun setApp(used: Boolean = true) {
        cityStore(used)
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
        compose.onNodeWithText("cities.json").assertExists()

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("settings.config").assertExists()

        compose.onNodeWithText("Logs").performClick()
        compose.onNodeWithText("weather_history.diff").assertExists()

        compose.onNodeWithText("Editor").performClick()
        compose.onNodeWithText("weather_data.json").assertExists()
    }

    /** Fase 14c: a fresh install is asked for a location before it gets a workspace. */
    @Test
    fun aFreshInstallLandsOnTweatherInit() {
        setApp(used = false)

        compose.onNodeWithText("tweather init", substring = true).assertExists()
        compose.onNodeWithText("weather_data.json").assertDoesNotExist()
    }

    /** Skipping is an answer: the workspace opens, on the honest empty editor. */
    @Test
    fun skippingInitOpensTheWorkspaceAnyway() {
        val store = cityStore(used = false)
        compose.setContent { TweatherTheme { TweatherApp() } }

        compose.onNodeWithText("> skip").performClick()

        // The answer is a DataStore write, so the swap lands a beat after the tap:
        // waiting for it is the test's job, not something to assert synchronously.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("// no location configured")
                .fetchSemanticsNodes().isNotEmpty()
        }
        // The document itself is the proof: init is gone, and the editor behind it
        // is the honest empty one rather than a city nobody asked for.
        compose.onNodeWithText("tweather init", substring = true).assertDoesNotExist()
    }

    /** Fase 14d: the hint reaches HELP.md, which lives behind the Settings tab. */
    @Test
    fun theHelpHintOpensTheHelpFile() {
        setApp()

        compose.onNodeWithText("// new here? open HELP.md").performClick()

        compose.onNodeWithText("# tweather").assertExists()
    }
}
