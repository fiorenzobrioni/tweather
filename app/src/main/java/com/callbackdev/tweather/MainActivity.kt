package com.callbackdev.tweather

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.ui.navigation.TweatherApp
import com.callbackdev.tweather.ui.theme.ThemeProfile
import com.callbackdev.tweather.ui.theme.TweatherTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // The app is dark-only (see TweatherTheme), so the system bars must always
        // draw their icons light. enableEdgeToEdge()'s default is SystemBarStyle.auto,
        // which picks the appearance from the *system* dark-mode setting: on a phone
        // in light mode that gave dark icons over the Obsidian background — an
        // invisible status bar. Force the dark style on both bars instead.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        val settingsStore = ServiceLocator.settingsStore(this)
        setContent {
            // Theme switches at runtime with settings.config's "active_profile"
            val profile by remember {
                settingsStore.settings.map { ThemeProfile.fromName(it.themeProfileName) }
            }.collectAsStateWithLifecycle(initialValue = ThemeProfile.Obsidian)
            TweatherTheme(profile = profile) {
                TweatherApp()
            }
        }
    }
}
