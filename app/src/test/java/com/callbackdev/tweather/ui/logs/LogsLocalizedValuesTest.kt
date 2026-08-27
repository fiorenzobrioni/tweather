package com.callbackdev.tweather.ui.logs

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.callbackdev.tweather.data.local.ForecastDiff
import com.callbackdev.tweather.data.local.SnapshotDiff
import com.callbackdev.tweather.ui.theme.TweatherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Post-9h l10n rule change: weather DATA values in both diff files localize at
 * render time (like main screen/widget/notifications); git chrome, keys and
 * non-weather values stay English. Snapshots in Room stay English either way.
 */
@RunWith(RobolectricTestRunner::class)
class LogsLocalizedValuesTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis() / 1000

    private val commits = listOf(
        CommitUi(
            hash = "a1b2c3d",
            cityLabel = "Milan, Lombardy",
            author = "sys@tweather.app",
            timestampEpochSeconds = now - 600,
            isInitial = false,
            lines = listOf(
                SnapshotDiff.Line(SnapshotDiff.Type.CONTEXT, "location", "Milan, Lombardy"),
                SnapshotDiff.Line(SnapshotDiff.Type.REMOVED, "current.status", "Overcast ☁️"),
                SnapshotDiff.Line(SnapshotDiff.Type.ADDED, "current.status", "Rainy 🌧️"),
                SnapshotDiff.Line(
                    SnapshotDiff.Type.CONTEXT, "astronomical.moon_phase", "Waxing Gibbous 🌔"
                )
            )
        )
    )

    private val revisions = listOf(
        ForecastRevisionUi(
            hash = "a1b2c3d",
            cityLabel = "Milan, Lombardy",
            author = "sys@tweather.app",
            timestampEpochSeconds = now - 600,
            hunks = listOf(
                ForecastDiff.Hunk(
                    date = "2026-08-18",
                    dayLabel = "tomorrow",
                    baselineEpochSeconds = now - 15_000,
                    lines = listOf(
                        SnapshotDiff.Line(SnapshotDiff.Type.REMOVED, "status", "Overcast ☁️"),
                        SnapshotDiff.Line(SnapshotDiff.Type.ADDED, "status", "Rainy 🌧️")
                    )
                )
            )
        )
    )

    private fun setContent() = compose.setContent {
        TweatherTheme {
            LogsScreen(commits = commits, revisions = revisions)
        }
    }

    @Config(qualifiers = "it")
    @Test
    fun historyValuesLocalizeButChromeAndNonWeatherValuesStayEnglish() {
        setContent()
        compose.onNodeWithText("- \"current.status\": \"Coperto ☁️\"").assertExists()
        compose.onNodeWithText("+ \"current.status\": \"Pioggia 🌧️\"").assertExists()
        compose.onNodeWithText("  \"astronomical.moon_phase\": \"Gibbosa crescente 🌔\"")
            .assertExists()
        // Git chrome and non-weather values are code/proper nouns: English
        compose.onNodeWithText("Author: System <sys@tweather.app>").assertExists()
        compose.onNodeWithText("  \"location\": \"Milan, Lombardy\"").assertExists()
    }

    @Config(qualifiers = "it")
    @Test
    fun forecastValuesLocalizeToo() {
        setContent()
        compose.onNodeWithText("forecast.diff").performClick()
        compose.onNodeWithText("- \"status\": \"Coperto ☁️\"").assertExists()
        compose.onNodeWithText("+ \"status\": \"Pioggia 🌧️\"").assertExists()
        compose.onNodeWithText("@@ tomorrow @@").assertExists() // hunk header is code
    }

    @Test
    fun englishLocaleShowsTheCanonicalValues() {
        setContent()
        compose.onNodeWithText("- \"current.status\": \"Overcast ☁️\"").assertExists()
        compose.onNodeWithText("+ \"current.status\": \"Rainy 🌧️\"").assertExists()
    }
}
