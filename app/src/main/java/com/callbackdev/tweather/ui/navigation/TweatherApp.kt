package com.callbackdev.tweather.ui.navigation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.data.EditorSettings
import com.callbackdev.tweather.data.FirstRun
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.ui.components.EditorNavBar
import com.callbackdev.tweather.ui.components.EditorNavItems
import com.callbackdev.tweather.ui.components.EditorOptions
import com.callbackdev.tweather.ui.components.LocalEditorOptions
import com.callbackdev.tweather.ui.init.InitScreen
import com.callbackdev.tweather.ui.logs.LogsScreen
import com.callbackdev.tweather.ui.search.SearchScreen
import com.callbackdev.tweather.ui.settings.SettingsScreen
import com.callbackdev.tweather.ui.weather.WeatherScreen
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

/**
 * Decides between `$ tweather init` and the workspace — see [CityStore.firstRun].
 * The [FirstRun.Unknown] branch draws an empty surface on purpose: the legacy check
 * of Fase 14b is one DataStore read away, and guessing "pending" for that frame
 * would flash a setup screen at someone who has been using the app for months.
 */
@Composable
fun TweatherApp() {
    val context = LocalContext.current
    val cityStore = remember(context) { ServiceLocator.cityStore(context) }
    val firstRun by remember(cityStore) { cityStore.firstRun }
        .collectAsStateWithLifecycle(initialValue = FirstRun.Unknown)
    // Saveable: choosing "use my position" opens the system permission dialog, and
    // the workspace must still know where the user was headed when it comes back.
    var openCitiesOnStart by rememberSaveable { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (firstRun) {
            FirstRun.Unknown -> Unit
            FirstRun.Pending -> FirstRunSetup(
                cityStore = cityStore,
                onSearchCity = { openCitiesOnStart = true }
            )
            FirstRun.Done -> Workspace(
                openCities = openCitiesOnStart,
                onCitiesOpened = { openCitiesOnStart = false }
            )
        }
    }
}

/** The state around [InitScreen]: the one runtime permission, and the three answers. */
@Composable
private fun FirstRunSetup(cityStore: CityStore, onSearchCity: () -> Unit) {
    val scope = rememberCoroutineScope()
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Enabling also selects GPS as the source (CityStore.setUseGps)
            scope.launch {
                cityStore.setUseGps(true)
                cityStore.markInitDone()
            }
        } else {
            permissionDenied = true
        }
    }
    InitScreen(
        onUseGps = { locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
        onSearchCity = {
            // Flag first, answer second: the workspace composes as soon as the answer
            // lands and has to know it should open cities.json.
            onSearchCity()
            scope.launch { cityStore.markInitDone() }
        },
        onSkip = { scope.launch { cityStore.markInitDone() } },
        permissionDenied = permissionDenied
    )
}

@Composable
private fun Workspace(openCities: Boolean, onCitiesOpened: () -> Unit) {
    val navController = rememberNavController()
    // The editor's HELP.md hint asks for a file on another tab: the flag rides
    // across the tab switch, the Settings screen consumes it (Fase 14d).
    var openHelp by rememberSaveable { mutableStateOf(false) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // "search a city" at first run lands here: the workspace opens on cities.json
    // instead of on an editor the user has nothing to put in yet.
    LaunchedEffect(openCities) {
        if (openCities) {
            navController.navigateToTab(Routes.Search)
            onCitiesOpened()
        }
    }

    // settings.config's editor section, live for every CodeCanvas in the app
    val context = LocalContext.current
    val settingsStore = remember(context) { ServiceLocator.settingsStore(context) }
    val editorSettings by remember(settingsStore) { settingsStore.settings.map { it.editor } }
        .collectAsStateWithLifecycle(initialValue = EditorSettings())

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
                        onOpenCities = { navController.navigateToTab(Routes.Search) },
                        onOpenHelp = {
                            openHelp = true
                            navController.navigateToTab(Routes.Settings)
                        }
                    )
                }
                composable(Routes.Search) {
                    SearchScreen(
                        onCitySelected = { navController.navigateToTab(Routes.Editor) }
                    )
                }
                composable(Routes.Settings) {
                    SettingsScreen(
                        openHelp = openHelp,
                        onHelpOpened = { openHelp = false }
                    )
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

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
