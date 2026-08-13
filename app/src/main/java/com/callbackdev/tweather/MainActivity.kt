package com.callbackdev.tweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
