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
import androidx.lifecycle.lifecycleScope
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.notifications.AlertScheduler
import com.callbackdev.tweather.ui.navigation.TweatherApp
import com.callbackdev.tweather.ui.theme.ThemeProfile
import com.callbackdev.tweather.ui.theme.TweatherTheme
import com.callbackdev.tweather.widget.TweatherWidgetUpdater
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
        // Single owner of background-work reconciliation: covers app start, every
        // notification toggle flip, frequency changes, `git restore` resets and
        // (Fase 11) alerts.rules edits — the first enabled rule arms the job, the
        // last one removed lets it self-cancel. (The permission-grant path in
        // Settings reconciles explicitly — a grant doesn't mutate DataStore, so
        // this flow wouldn't fire.)
        lifecycleScope.launch {
            combine(
                settingsStore.settings.map { it.notifications to it.updateFrequencyMin },
                ServiceLocator.ruleStore(this@MainActivity).rules
                    .map { rules -> rules.any { it.enabled } }
            ) { settings, hasEnabledRules -> settings to hasEnabledRules }
                .distinctUntilChanged()
                .collect { AlertScheduler.reconcile(this@MainActivity) }
        }
        // Fase 14b: decides once whether this install predates the empty state, and
        // must land before the shell can tell `init` from a returning user.
        lifecycleScope.launch {
            val hasHistory = ServiceLocator.weatherRepository(this@MainActivity).hasAnyHistory()
            ServiceLocator.cityStore(this@MainActivity).migrateFirstRun(hasHistory)
        }
        // Widget re-renders that no fetch would trigger: theme, units, opacity and
        // active-city changes. (New data repaints it from the repository hook.)
        lifecycleScope.launch {
            combine(
                settingsStore.settings.map {
                    Triple(it.themeProfileName, it.units, it.widgetOpacityPct)
                },
                ServiceLocator.cityStore(this@MainActivity).activeSource
            ) { appearance, source -> appearance to source }
                .distinctUntilChanged()
                .collect { TweatherWidgetUpdater.updateAll(this@MainActivity) }
        }
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
