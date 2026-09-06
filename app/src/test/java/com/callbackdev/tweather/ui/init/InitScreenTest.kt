package com.callbackdev.tweather.ui.init

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.tweather.R
import com.callbackdev.tweather.ui.theme.ObsidianSyntax
import com.callbackdev.tweather.ui.theme.TweatherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `$ tweather init` (Fase 14c): three answers, and every one of them has to be an
 * answer — including `skip`, which is what lets the honest empty editor of Fase 14b
 * be somewhere the user chose to be.
 *
 * Since Fase 27 the transcript prints itself, so every test about its *content*
 * asks for the still version first — which is not a testing trick but the screen a
 * phone with "Remove animations" on actually gets, and therefore worth asserting.
 * The two tests at the bottom are about the animation itself.
 */
@RunWith(RobolectricTestRunner::class)
// A phone, not Robolectric's default 320x470 handset: the transcript keeps its last
// line in sight, so on a screen too short for it the top of the session has honestly
// scrolled away and these assertions would be measuring a device nobody ships.
@Config(qualifiers = "w360dp-h740dp")
class InitScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var gps = 0
    private var search = 0
    private var skip = 0

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun animations(on: Boolean) {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            if (on) 1f else 0f
        )
    }

    @Before
    fun stillByDefault() = animations(on = false)

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

    /**
     * The screen says what the app IS before it says what it needs. The four `#`
     * lines are the whole reason the transcript is worth printing rather than
     * pasting, so one of them is held here.
     */
    @Test
    fun `the session introduces the app before asking for anything`() {
        setScreen()

        compose.onNodeWithText("code editor", substring = true).assertExists()
        compose.onNodeWithText("open-meteo.com", substring = true).assertExists()
    }

    // ---- the animation -----------------------------------------------------

    /**
     * "Remove animations" is not a slower animation: it is no animation. The whole
     * transcript, choices included, has to be there on the frame the screen opens
     * — not a fade later, which is what a half-hearted implementation would leave.
     */
    @Test
    fun `with animations off the transcript is whole on the first frame`() {
        animations(on = false)
        compose.mainClock.autoAdvance = false
        setScreen()
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("> skip").assertExists()
        compose.onNodeWithText("code editor", substring = true).assertExists()
    }

    /**
     * Tap-to-skip: the touch that ends the printing lands on the transcript, never
     * on a choice. Somebody impatient enough to tap is not somebody who wanted to
     * turn the GPS on by accident.
     */
    @Test
    fun `a tap ends the printing without answering the question`() {
        animations(on = true)
        compose.mainClock.autoAdvance = false
        setScreen()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("> skip").assertDoesNotExist()

        compose.onRoot().performTouchInput { down(center); up() }
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("> skip").assertExists()
        assertEquals(0, gps)
        assertEquals(0, search)
        assertEquals(0, skip)
    }

    /**
     * The budget, in both languages — and a **range** since Fase 27b.
     *
     * The ceiling is the old reason: the copy is free to grow, but not past the point
     * where a first-run screen starts costing the reader time. This is the test to
     * argue with before the intro becomes a carousel by accretion.
     *
     * The floor is the device's answer, and it has moved twice. At five hundred
     * characters a second the whole session was over in a second and a half, and that
     * does not read as writing — it reads as a flicker, which is the "fuffa" the
     * animation exists to avoid. "Under two seconds" was the guard that had allowed
     * it, so the guard grew a second end. Then the shape turned out to matter more
     * than the rate: the answers are prompts too, so they are typed at the command's
     * speed and wait after themselves, and the session became a conversation with
     * turns instead of one command followed by everything at once. Italian is the
     * longer of the two languages and both are checked.
     */
    private val budgetMs = 4_000L..8_000L

    @Test
    fun `the whole session prints inside its budget`() {
        assertTrue("English: ${sessionMs(context)}ms", sessionMs(context) in budgetMs)
    }

    @Test
    @Config(qualifiers = "+it")
    fun `the italian session prints inside its budget too`() {
        assertTrue("Italian: ${sessionMs(context)}ms", sessionMs(context) in budgetMs)
    }

    private fun sessionMs(context: Context): Long = Typist(
        buildInitScript(
            syntax = ObsidianSyntax,
            intro = context.getString(R.string.init_intro),
            files = context.getString(R.string.init_files),
            privacy = context.getString(R.string.init_privacy),
            ask = context.getString(R.string.init_ask),
            gps = context.getString(R.string.init_option_gps),
            gpsNote = context.getString(R.string.init_option_gps_note),
            search = context.getString(R.string.init_option_search),
            searchNote = context.getString(R.string.init_option_search_note),
            skip = context.getString(R.string.init_option_skip),
            skipNote = context.getString(R.string.init_option_skip_note),
            denied = null,
            onUseGps = {},
            onSearchCity = {},
            onSkip = {}
        )
    ).totalMs
}
