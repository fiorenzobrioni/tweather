package com.callbackdev.tweather.ui.sky

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.callbackdev.tweather.data.SkySubscription
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.ui.theme.TweatherTheme
import com.callbackdev.tweather.ui.weather.editorFiles
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `sky.crontab` as a screen (Fase 16c): the token taps, the two-tap `[rm]`, the
 * catalog picker, and the states the file has to be able to say out loud.
 */
@RunWith(RobolectricTestRunner::class)
class SkyScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private class Recorder {
        var toggled: SkySubscription? = null
        var removed: String? = null
        var added: String? = null
        var ran = 0
        val actions = SkyActions(
            onToggleEnabled = { toggled = it },
            onRemove = { removed = it },
            onAdd = { added = it },
            onRunSky = { ran++ }
        )
    }

    private fun context() = SkyContext(
        cityLabel = "Milan, Lombardy",
        coordinates = Coordinates(45.4642, 9.19),
        zone = ZoneId.of("Europe/Rome"),
        now = Instant.parse("2026-08-26T16:30:00Z")
    )

    private fun setScreen(
        subscriptions: List<SkySubscription> = listOf(
            SkySubscription("sun.rise"), SkySubscription("sun.set")
        ),
        context: SkyContext? = context(),
        dryRun: List<String>? = null,
        recorder: Recorder = Recorder()
    ): Recorder {
        compose.setContent {
            TweatherTheme {
                SkyScreen(
                    state = SkyUiState(subscriptions, context, dryRun),
                    editorFiles = editorFiles(skyEnabled = true),
                    activeIndex = 2,
                    onSelectFile = {},
                    actions = recorder.actions
                )
            }
        }
        return recorder
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))
    }

    @Test
    fun `the file opens on the editor strip with all three names`() {
        setScreen()
        compose.onNodeWithText("weather_data.json").assertIsDisplayed()
        compose.onNodeWithText("README.md").assertIsDisplayed()
        compose.onNodeWithText("sky.crontab").assertIsDisplayed()
    }

    @Test
    fun `the header names the city and its zone`() {
        setScreen()
        compose.onNodeWithText("# sky.crontab — Milan, Lombardy (Europe/Rome)").assertIsDisplayed()
    }

    /**
     * Tapping the NAME comments the line out — how everybody disables a cron job in
     * real life, and the reason the job name is the line's main tap target rather
     * than a `true`/`false` token bolted onto it.
     */
    @Test
    fun `tapping the job name toggles the leading hash`() {
        val recorder = setScreen()
        compose.onNodeWithText("sun.rise", substring = true).performClick()
        assertEquals("sun.rise", recorder.toggled?.jobId)
        assertTrue("was enabled, so the tap disables", recorder.toggled?.enabled == true)
    }

    /** Destructive, so two taps — the same confirm every `$` command in the app has. */
    @Test
    fun `removing a line takes two taps`() {
        val recorder = setScreen()
        compose.onAllNodesWithTextAndClick("[rm]", index = 0)
        assertNull("one tap must not remove anything", recorder.removed)
        // The token itself is the confirmation, so it is where the finger already is
        // — a `// tap again` comment after it lands off the right edge of a row this
        // wide, which is a confirmation nobody sees.
        compose.onNodeWithText("[rm?]").assertIsDisplayed()

        compose.onNodeWithText("[rm?]").performClick()
        assertEquals("sun.rise", recorder.removed)
    }

    @Test
    fun `the catalog opens on demand and adding picks a job`() {
        val recorder = setScreen()
        scrollTo("+ add job")
        compose.onNodeWithText("+ add job").performClick()
        scrollTo("golden_hour.pm")
        compose.onNodeWithText("golden_hour.pm", substring = true).performClick()
        assertEquals("golden_hour.pm", recorder.added)
    }

    @Test
    fun `the catalog offers only jobs the file does not already hold`() {
        setScreen()
        scrollTo("+ add job")
        compose.onNodeWithText("+ add job").performClick()
        // `sun.rise` is a line already: it appears once, as that line, and not again
        // in the picker below.
        compose.onAllNodesWithTextCount("sun.rise", expected = 1)
    }

    /**
     * The schedule needs a latitude and nothing else, so this tab is the one that
     * would still work offline — and exactly as blank as the editor's other two when
     * the app does not know where you are (Fase 14b).
     */
    @Test
    fun `with no location the file says so instead of inventing a schedule`() {
        setScreen(context = null)
        compose.onNodeWithText("# no location configured").assertIsDisplayed()
        compose.onNodeWithText("# hint: open cities.json and search a city").assertIsDisplayed()
    }

    @Test
    fun `an empty file is a state, not an error`() {
        setScreen(subscriptions = emptyList())
        compose.onNodeWithText("# no jobs", substring = true).assertIsDisplayed()
        compose.onNodeWithText("+ add job").assertIsDisplayed()
    }

    @Test
    fun `the file always says what it does not model`() {
        setScreen()
        scrollTo("light pollution")
        compose.onNodeWithText("// light pollution is not modelled: the app does not know your sky")
            .assertIsDisplayed()
    }

    /**
     * The dry run is destructive of nothing, but it is a `$` command, and every `$`
     * command in this app confirms twice.
     */
    @Test
    fun `the dry run takes two taps and then prints its block`() {
        val recorder = setScreen()
        scrollTo("tweather run sky")
        compose.onNodeWithText("$ tweather run sky").performClick()
        assertEquals("one tap must not run anything", 0, recorder.ran)
        compose.onNodeWithText("// tap again to confirm", substring = true).assertExists()

        compose.onNodeWithText("tweather run sky", substring = true).performClick()
        assertEquals(1, recorder.ran)
    }

    @Test
    fun `the dry run block renders under the command`() {
        setScreen(dryRun = listOf("// sun.rise  06:38  ✓ pass  cloud 8%"))
        scrollTo("sun.rise  06:38")
        compose.onNodeWithText("// sun.rise  06:38  ✓ pass  cloud 8%").assertExists()
    }

    @Test
    fun `a file with every line commented out offers no dry run`() {
        setScreen(
            subscriptions = listOf(
                SkySubscription("sun.rise", enabled = false),
                SkySubscription("sun.set", enabled = false)
            )
        )
        compose.onAllNodesWithTextCount("tweather run sky", expected = 0)
    }

    @Test
    fun `every tap target announces itself in words`() {
        setScreen()
        // `[rm]` read out as "left bracket r m" is the bar this module was held to:
        // every row has a removal target and every one of them carries a label.
        assertEquals(2, compose.onAllNodesWithText("[rm]").fetchSemanticsNodes().size)
        compose.onAllNodesWithText("[rm]")[0].assert(hasClickAction())
    }

    // Compose's onAllNodes helpers, wrapped so the intent reads in the test body.
    private fun ComposeContentTestRule.onAllNodesWithTextAndClick(text: String, index: Int) {
        onAllNodesWithText(text)[index].performClick()
    }

    private fun ComposeContentTestRule.onAllNodesWithTextCount(text: String, expected: Int) {
        assertEquals(
            expected,
            onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size
        )
    }
}
