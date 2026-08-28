package com.callbackdev.tweather.ui.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.callbackdev.tweather.ui.theme.TweatherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The `$ history -c` command that ends cities.json. */
@RunWith(RobolectricTestRunner::class)
class SearchClearHistoryTest {

    @get:Rule
    val compose = createComposeRule()

    private val recents = listOf("Milano, Lombardia", "Torino, Piemonte")

    private var cleared = 0

    private fun setScreen(recents: List<String> = this.recents) {
        compose.setContent {
            TweatherTheme {
                SearchScreen(
                    state = SearchUiState(),
                    recents = recents,
                    onQueryChange = {},
                    onSearchNow = {},
                    onSelect = {},
                    onRecent = {},
                    onClearRecents = { cleared++ }
                )
            }
        }
    }

    private fun onLine(text: String) = compose
        .onNode(hasScrollToNodeAction())
        .performScrollToNode(hasText(text))
        .let { compose.onNodeWithText(text) }

    @Test
    fun `the command is offered only when there is history to clear`() {
        setScreen(recents = emptyList())

        compose.onNodeWithText("$ history -c").assertDoesNotExist()
    }

    @Test
    fun `the first tap arms instead of clearing`() {
        setScreen()

        onLine("$ history -c").performClick()

        assertEquals("clearing must take two taps", 0, cleared)
        onLine("$ history -c  // tap again to confirm").assertIsDisplayed()
    }

    @Test
    fun `the second tap clears`() {
        setScreen()

        onLine("$ history -c").performClick()
        onLine("$ history -c  // tap again to confirm").performClick()

        assertEquals(1, cleared)
    }

    @Test
    fun `the command announces what it touches - the search history`() {
        setScreen()

        // Wording matters here: the line must not read as "this deletes my cities".
        onLine("// clear search history:").assertIsDisplayed()
        onLine("\"Milano, Lombardia\",").assertIsDisplayed()
    }

    /**
     * And it must not read that way in Italian either — which is the whole point of
     * Fase 18: a warning nobody can read warns nobody. The `$` command under it is
     * a command and stays exactly as it is.
     */
    @Test
    @Config(qualifiers = "it")
    fun `the warning is in Italian and the command underneath is not`() {
        setScreen()

        onLine("// cancella la cronologia delle ricerche:").assertIsDisplayed()
        onLine("$ history -c").assertIsDisplayed()
    }
}
