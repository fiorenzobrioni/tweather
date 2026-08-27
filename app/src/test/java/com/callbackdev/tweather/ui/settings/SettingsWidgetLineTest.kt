package com.callbackdev.tweather.ui.settings

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.callbackdev.tweather.data.AppSettings
import com.callbackdev.tweather.ui.theme.TweatherTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The `widget.bg_opacity_pct` line of settings.config. */
@RunWith(RobolectricTestRunner::class)
class SettingsWidgetLineTest {

    @get:Rule
    val compose = createComposeRule()

    /** Every slot inert but the widget one; named so the 13-arg list can't drift. */
    private fun actions(onCycleWidgetOpacity: () -> Unit = {}) = SettingsActions(
        onLineNumbers = {},
        onWordWrap = {},
        onShowDetails = {},
        onToggleTemperature = {},
        onToggleWindSpeed = {},
        onThemeProfile = {},
        onSevereAlerts = {},
        onDailySummary = {},
        onPrecipWarning = {},
        onUserRules = {},
        onSkyEnabled = {},
        onCycleSkyNotifyDefault = {},
        onSkyNotifyOnFail = {},
        onCycleFrequency = {},
        onCycleWidgetOpacity = onCycleWidgetOpacity,
        onOpenUrl = {},
        onReset = {}
    )

    private fun setScreen(
        settings: AppSettings = AppSettings(),
        onCycleWidgetOpacity: () -> Unit = {}
    ) {
        compose.setContent {
            TweatherTheme {
                SettingsScreen(
                    settings = settings,
                    actions = actions(onCycleWidgetOpacity)
                )
            }
        }
    }

    /** The line sits deep in the LazyColumn: scroll it into view first. */
    private fun onLine(text: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        return compose.onNodeWithText(text).assertExists()
    }

    @Test
    fun defaultOpacityRendersWithCycleHint() {
        setScreen()
        // AppSettings() default opacity is 100
        onLine("\"bg_opacity_pct\": 100  // 100 | 85 | 70 | 50")
    }

    @Test
    fun tapCyclesOpacity() {
        var cycled = false
        setScreen(onCycleWidgetOpacity = { cycled = true })
        onLine("\"bg_opacity_pct\": 100  // 100 | 85 | 70 | 50").performClick()
        assertTrue(cycled)
    }

    @Test
    fun persistedOpacityIsTheRenderedValue() {
        setScreen(AppSettings(widgetOpacityPct = 70))
        onLine("\"bg_opacity_pct\": 70  // 100 | 85 | 70 | 50")
    }
}
