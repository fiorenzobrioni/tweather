package com.callbackdev.tweather.ui.sky

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.callbackdev.tweather.data.SkySubscription
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.ui.components.EditorOptions
import com.callbackdev.tweather.ui.components.LocalEditorOptions
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
import org.robolectric.annotation.Config

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
        var cycledLead: SkySubscription? = null
        val actions = SkyActions(
            onToggleEnabled = { toggled = it },
            onRemove = { removed = it },
            onAdd = { added = it },
            onRunSky = { ran++ },
            onCycleLead = { cycledLead = it }
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
        defaultLeadMinutes: Int? = null,
        recorder: Recorder = Recorder(),
        wordWrap: Boolean = false
    ): Recorder {
        compose.setContent {
            TweatherTheme {
                CompositionLocalProvider(
                    LocalEditorOptions provides EditorOptions(wordWrap = wordWrap)
                ) {
                    SkyScreen(
                        state = SkyUiState(subscriptions, context, defaultLeadMinutes, dryRun),
                        editorFiles = editorFiles(skyEnabled = true),
                        activeIndex = 2,
                        onSelectFile = {},
                        actions = recorder.actions
                    )
                }
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
        compose.onNodeWithText("# sky.crontab · Milan, Lombardy (Europe/Rome)").assertIsDisplayed()
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

    /**
     * The seam on the file the sky module opens with (Fase 18). The `#` is what a
     * crontab comments with and never translates; the two sentences under it do,
     * and `cities.json` inside one of them is a file name and comes through.
     */
    @Test
    @Config(qualifiers = "it")
    fun `with no location it says so in Italian, file name included`() {
        setScreen(context = null)
        compose.onNodeWithText("# nessuna posizione configurata").assertIsDisplayed()
        compose.onNodeWithText("# suggerimento: apri cities.json e cerca una città")
            .assertIsDisplayed()
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

    /**
     * The `--notify` token, rendered since Fase 16f. Before the reminders existed it
     * was deliberately absent: a token promising something the app could not send
     * would have been the first thing this module lied about.
     */
    @Test
    fun `a job with a reminder shows its notify token, and it cycles on tap`() {
        val recorder = setScreen(
            subscriptions = listOf(
                SkySubscription("sun.rise"),
                SkySubscription("sun.set", notifyLeadMinutes = 30)
            )
        )
        compose.onNodeWithText("--notify=30m", substring = true).assertIsDisplayed()
        compose.onNodeWithText("--notify=30m", substring = true).performClick()
        assertEquals("sun.set", recorder.cycledLead?.jobId)
    }

    /**
     * A file where nobody set a reminder does not pay a column for the possibility.
     * `notify_default` is off out of the box, so this is what a fresh install shows.
     */
    @Test
    fun `a file with no reminders shows no notify column at all`() {
        setScreen()
        compose.onAllNodesWithTextCount("--notify", expected = 0)
    }

    /**
     * The escape from the dead end this nearly shipped as. Every line stores its lead
     * as null until somebody taps one, so if `notify_default` were only a seed value
     * copied into newly added jobs, a file of seeded lines would render no token —
     * and a reminder could never be switched on at all. `notify_default` is instead
     * the lead a line uses when it carries none of its own: one setting, and the
     * whole file grows the token.
     */
    @Test
    fun `notify_default puts the token on every line that has no lead of its own`() {
        setScreen(defaultLeadMinutes = 30)
        compose.onAllNodesWithTextCount("--notify=30m", expected = 2)
    }

    /** A line's own lead wins over the default, which is what an override is. */
    @Test
    fun `a line with its own lead ignores notify_default`() {
        setScreen(
            subscriptions = listOf(
                SkySubscription("sun.rise"),
                SkySubscription("sun.set", notifyLeadMinutes = 60)
            ),
            defaultLeadMinutes = 30
        )
        compose.onAllNodesWithTextCount("--notify=30m", expected = 1)
        compose.onAllNodesWithTextCount("--notify=1h", expected = 1)
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
    // --- man (Fase 23) -----------------------------------------------------

    /**
     * The whole point of the feature in one test: a reader who does not know what
     * `zodiacal.pm` is can reach a page that tells them, from the list where they
     * were being asked to choose it.
     */
    @Test
    fun `the catalog explains a job before asking you to pick it`() {
        setScreen()
        scrollTo("+ add job")
        compose.onNodeWithText("+ add job").performClick()
        scrollTo("zodiacal.pm")

        compose.onAllNodesWithText("[man]").onFirst().performClick()

        // A man page, not the file: its header, its sections, and no crontab row.
        compose.onNodeWithText("(7)", substring = true).assertIsDisplayed()
        compose.onNodeWithText("NAME").assertIsDisplayed()
        compose.onNodeWithText("DESCRIPTION").assertIsDisplayed()
    }

    @Test
    fun `the manual index reaches every job, including the ones already in the file`() {
        setScreen()
        scrollTo("$ man sky")
        compose.onNodeWithText("$ man sky").performClick()

        compose.onNodeWithText("SKY(7)").assertIsDisplayed()
        // `sun.rise` is a subscription, so the picker never offers it — the index is
        // the only way to its page, which is why the index exists.
        scrollTo("sun.rise")
        compose.onAllNodesWithText("sun.rise", substring = true).onFirst().performClick()
        compose.onNodeWithText("SUN.RISE(7)").assertIsDisplayed()
    }

    @Test
    fun `see also walks from one page to the next`() {
        setScreen()
        scrollTo("$ man sky")
        compose.onNodeWithText("$ man sky").performClick()
        scrollTo("golden_hour.pm")
        compose.onAllNodesWithText("golden_hour.pm", substring = true).onFirst().performClick()

        compose.onNodeWithText("GOLDEN_HOUR.PM(7)").assertIsDisplayed()
        scrollTo("SEE ALSO")
        compose.onAllNodesWithText("blue_hour.pm", substring = true).onFirst().performClick()

        compose.onNodeWithText("BLUE_HOUR.PM(7)").assertIsDisplayed()
    }

    @Test
    fun `quit gives the file back`() {
        setScreen()
        scrollTo("$ man sky")
        compose.onNodeWithText("$ man sky").performClick()
        compose.onNodeWithText("SKY(7)").assertIsDisplayed()

        compose.onNodeWithText("[q] quit").performClick()

        compose.onNodeWithText("sky.crontab").assertIsDisplayed()
    }

    /** A `[man]` tap must never be mistaken for "add this line to my file". */
    @Test
    fun `reading about a job does not subscribe to it`() {
        val recorder = setScreen()
        scrollTo("+ add job")
        compose.onNodeWithText("+ add job").performClick()
        scrollTo("[man]")

        compose.onAllNodesWithText("[man]").onFirst().performClick()

        assertNull(recorder.added)
    }

    // --- word_wrap (Fase 23b) ----------------------------------------------

    /**
     * With wrapping on there is no horizontal pan to escape into, and a crontab row
     * is five columns in one Row that cannot itself wrap: the comment was being
     * squeezed into a one-character ribbon down the right edge. It gets a line of its
     * own instead, which is where a long crontab line has always put its comment.
     */
    @Test
    fun `with word_wrap on the comment takes a line of its own`() {
        setScreen(wordWrap = true)

        // Still one row per job, and the resolved instant is still on screen — just
        // no longer fighting for width with the name.
        compose.onNodeWithText("sun.rise", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("#", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun `with word_wrap off the row keeps its columns`() {
        setScreen(wordWrap = false)

        // One node carrying both the name and its comment is the single-Row layout.
        compose.onNodeWithText("sun.rise", substring = true).assertIsDisplayed()
    }

    /**
     * The index is a two-column table, so it must not wrap even when the paragraphs
     * above it do — that was the ragged hanging-indent list the committente caught on
     * device.
     */
    @Test
    fun `the manual index is a table and its rows do not wrap`() {
        setScreen()
        scrollTo("$ man sky")
        compose.onNodeWithText("$ man sky").performClick()
        scrollTo("sun.rise")

        // Padded to the catalog's longest id, so every name starts in the same column.
        val text = compose.onAllNodesWithText("sun.rise", substring = true).onFirst()
            .fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
            .joinToString("") { it.text }
        assertTrue("the index row is not padded into columns: '$text'", text.contains("   "))
    }
}
