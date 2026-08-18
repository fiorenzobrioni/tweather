package com.callbackdev.tweather.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.callbackdev.tweather.data.EditorSettings
import kotlinx.coroutines.flow.map
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.ui.components.EditorNavBar
import com.callbackdev.tweather.ui.components.EditorNavItems
import com.callbackdev.tweather.ui.components.EditorOptions
import com.callbackdev.tweather.ui.components.LocalEditorOptions
import com.callbackdev.tweather.ui.logs.LogsScreen
import com.callbackdev.tweather.ui.search.SearchScreen
import com.callbackdev.tweather.ui.settings.SettingsScreen
import com.callbackdev.tweather.ui.weather.WeatherScreen

/**
 * App shell: NavHost above the editor-style bottom bar, one destination per tab.
 * Since Fase 10b the city list lives inside the Cerca tab (`cities.json`), so the
 * first tab is just the open editor — no nested graph. Tab switches save and
 * restore each stack (`saveState`/`restoreState`). The editor keeps the route name
 * "explorer" even though the tab is now labelled Editor: the route is the nav bar
 * item's selection key and the saved-stack key, never a user-visible string.
 */
object Routes {
    const val Editor = "explorer"
    const val Search = "search"
    const val Settings = "settings"
    const val Logs = "logs"
}

@Composable
fun TweatherApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // settings.config's editor section, live for every CodeCanvas in the app
    val context = LocalContext.current
    val settingsStore = remember(context) { ServiceLocator.settingsStore(context) }
    val editorSettings by remember(settingsStore) { settingsStore.settings.map { it.editor } }
        .collectAsStateWithLifecycle(initialValue = EditorSettings())

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            CompositionLocalProvider(
                LocalEditorOptions provides EditorOptions(
                    showLineNumbers = editorSettings.lineNumbers,
                    wordWrap = editorSettings.wordWrap
                )
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.Editor,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(Routes.Editor) {
                        WeatherScreen(
                            onOpenCities = { navController.navigateToTab(Routes.Search) }
                        )
                    }
                    composable(Routes.Search) {
                        SearchScreen(
                            onCitySelected = { navController.navigateToTab(Routes.Editor) }
                        )
                    }
                    composable(Routes.Settings) {
                        SettingsScreen()
                    }
                    composable(Routes.Logs) {
                        LogsScreen()
                    }
                }
            }
            EditorNavBar(
                items = EditorNavItems.All,
                isSelected = { item ->
                    currentDestination?.hierarchy?.any { it.route == item.route } == true
                },
                onSelect = { navController.navigateToTab(it.route) }
            )
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
