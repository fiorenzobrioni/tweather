package com.callbackdev.tweather.ui.logs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.data.UnitSettings
import com.callbackdev.tweather.data.WindSpeedUnit
import com.callbackdev.tweather.data.local.ForecastDiff
import com.callbackdev.tweather.data.local.SnapshotDiff
import com.callbackdev.tweather.ui.theme.TweatherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Fase 28: the Logs were the last surface still printing metric while
 * `weather_data.json`, `README.md`, the widget and the notifications all converted.
 * The snapshots stay metric — a diff must never churn because a setting moved — so
 * both files convert at render time and rename the key with the value, exactly the
 * way the JSON tab does (`temp_c` → `temp_f`).
 */
@RunWith(RobolectricTestRunner::class)
class LogsUnitsTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis() / 1000

    private val commits = listOf(
        CommitUi(
            hash = "a1b2c3d",
            cityLabel = "Milan, Lombardy",
            author = "sys@tweather.app",
            timestampEpochSeconds = now - 600,
            baselineEpochSeconds = now - 4_200,
            lines = listOf(
                SnapshotDiff.Line(SnapshotDiff.Type.REMOVED, "current.temp_c", "18.5"),
                SnapshotDiff.Line(SnapshotDiff.Type.ADDED, "current.temp_c", "21.0"),
                SnapshotDiff.Line(SnapshotDiff.Type.CONTEXT, "current.wind_kph", "24.0"),
                SnapshotDiff.Line(SnapshotDiff.Type.CONTEXT, "current.humidity_pct", "54")
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
                    baselineEpochSeconds = now - 15_000,
                    lines = listOf(
                        SnapshotDiff.Line(SnapshotDiff.Type.REMOVED, "high_c", "31.0"),
                        SnapshotDiff.Line(SnapshotDiff.Type.ADDED, "high_c", "27.4"),
                        SnapshotDiff.Line(SnapshotDiff.Type.CONTEXT, "precip_pct", "70")
                    )
                )
            )
        )
    )

    private fun setContent(units: UnitSettings) = compose.setContent {
        TweatherTheme {
            LogsScreen(commits = commits, revisions = revisions, units = units)
        }
    }

    @Test
    fun `the default units print exactly what the snapshot stores`() {
        setContent(UnitSettings())

        compose.onNodeWithText("- \"current.temp_c\": 18.5").assertIsDisplayed()
        compose.onNodeWithText("+ \"current.temp_c\": 21.0").assertIsDisplayed()
        compose.onNodeWithText("  \"current.wind_kph\": 24.0").assertIsDisplayed()
    }

    @Test
    fun `fahrenheit and mph convert the values and rename the keys`() {
        setContent(UnitSettings(TemperatureUnit.FAHRENHEIT, WindSpeedUnit.MPH))

        compose.onNodeWithText("- \"current.temp_f\": 65.3").assertIsDisplayed()
        compose.onNodeWithText("+ \"current.temp_f\": 69.8").assertIsDisplayed()
        compose.onNodeWithText("  \"current.wind_mph\": 14.9").assertIsDisplayed()
        // a percentage is the same number in every unit system
        compose.onNodeWithText("  \"current.humidity_pct\": 54").assertIsDisplayed()
    }

    @Test
    fun `the forecast file converts too`() {
        setContent(UnitSettings(TemperatureUnit.FAHRENHEIT, WindSpeedUnit.MPH))
        compose.onNodeWithText("forecast.diff").performClick()

        compose.onNodeWithText("- \"high_f\": 87.8").assertIsDisplayed()
        compose.onNodeWithText("+ \"high_f\": 81.3").assertIsDisplayed()
        compose.onNodeWithText("  \"precip_pct\": 70").assertIsDisplayed()
        // the hunk header names the target day and does not move with the units
        compose.onNodeWithText("@@ Tue 18 Aug @@").assertIsDisplayed()
    }
}
