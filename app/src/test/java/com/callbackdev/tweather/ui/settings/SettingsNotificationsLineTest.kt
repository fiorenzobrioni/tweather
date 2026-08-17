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

/** The dynamic status line of settings.config's `notifications` block. */
@RunWith(RobolectricTestRunner::class)
class SettingsNotificationsLineTest {

    @get:Rule
    val compose = createComposeRule()

    private val noActions = SettingsActions(
        {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}
    )

    private fun setScreen(
        notifState: NotifLineState,
        onNotifLine: () -> Unit = {}
    ) {
        compose.setContent {
            TweatherTheme {
                SettingsScreen(
                    settings = AppSettings(),
                    actions = noActions,
                    notifState = notifState,
                    onNotifLine = onNotifLine
                )
            }
        }
    }

    private fun onLine(text: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        return compose.onNodeWithText(text).assertExists()
    }

    @Test
    fun armedStateShowsPollingInterval() {
        setScreen(NotifLineState.Armed)
        // AppSettings() default frequency is 60
        onLine("// polling every 60 min")
    }

    @Test
    fun disabledStateShowsAlertsDisabled() {
        setScreen(NotifLineState.Disabled)
        onLine("// alerts disabled")
    }

    @Test
    fun missingPermissionIsTappableError() {
        var tapped = false
        setScreen(NotifLineState.MissingPermission, onNotifLine = { tapped = true })
        onLine("// ERROR: notifications permission missing — tap to grant").performClick()
        assertTrue(tapped)
    }

    @Test
    fun permanentlyDeniedPointsToSystemSettings() {
        setScreen(NotifLineState.DeniedPermanently)
        onLine("// ERROR: denied — open system settings")
    }
}
