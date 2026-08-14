package com.callbackdev.tweather.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.callbackdev.tweather.R
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.GpsCityId
import com.callbackdev.tweather.ui.components.EditorTab
import com.callbackdev.tweather.ui.components.TerminalStatusBar
import com.callbackdev.tweather.ui.components.TreeViewItem
import com.callbackdev.tweather.ui.explorer.fileSlug
import com.callbackdev.tweather.ui.theme.ThemeProfile
import com.callbackdev.tweather.ui.theme.TweatherTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Optional widget configuration (`configuration_optional`, so adding a widget never
 * forces this screen): pins one instance to a city instead of following the app's
 * active source. Same file-explorer metaphor as the Explorer tab, minus the tab
 * chrome — the widget is being told which "file" to open.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // Pre-set the result: backing out keeps the widget with its current (or
        // default "follow the app") city rather than cancelling the placement.
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        val cityStore = ServiceLocator.cityStore(this)
        val widgetCityStore = ServiceLocator.widgetCityStore(this)
        val settingsStore = ServiceLocator.settingsStore(this)

        setContent {
            val profile by produceState(ThemeProfile.Obsidian) {
                settingsStore.settings.collect { value = ThemeProfile.fromName(it.themeProfileName) }
            }
            val state by produceState(WidgetConfigState()) {
                val cities = cityStore.cities.first()
                val location = cityStore.locationSettings.first()
                value = WidgetConfigState(
                    cities = cities,
                    gpsAvailable = location.useGps,
                    gpsLabel = location.gpsCity?.name,
                    pinnedCityId = widgetCityStore.current()[appWidgetId]
                )
            }
            TweatherTheme(profile = profile) {
                WidgetConfigScreen(
                    state = state,
                    onFollowApp = { pin(null) },
                    onSelectCity = { pin(it.id) },
                    onSelectGps = { pin(GpsCityId) }
                )
            }
        }
    }

    /** [cityId] null = follow the app's active source. */
    private fun pin(cityId: Long?) {
        val store = ServiceLocator.widgetCityStore(this)
        lifecycleScope.launch {
            if (cityId == null) store.unpin(appWidgetId) else store.pin(appWidgetId, cityId)
            TweatherWidgetUpdater.updateAll(this@WidgetConfigActivity)
            finish()
        }
    }
}

data class WidgetConfigState(
    val cities: List<City> = emptyList(),
    val gpsAvailable: Boolean = false,
    val gpsLabel: String? = null,
    /** null = follow the app's active source (the default). */
    val pinnedCityId: Long? = null
)

@Composable
fun WidgetConfigScreen(
    state: WidgetConfigState,
    onFollowApp: () -> Unit,
    onSelectCity: (City) -> Unit,
    onSelectGps: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Unlike every other screen this one has no TweatherApp/EditorNavBar shell to
        // inset it, and targetSdk 36 means the system draws it edge-to-edge.
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            EditorTab(fileName = "widget.config")
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                TreeViewItem(label = "~/tweather/widget/source/") {
                    ConfigRow(
                        label = "active_file",
                        hint = if (state.pinnedCityId == null) "// selected" else "// follows the app",
                        highlight = state.pinnedCityId == null,
                        onClickLabel = stringResource(R.string.cd_widget_follow_app),
                        onSelect = onFollowApp
                    )
                    if (state.gpsAvailable) {
                        ConfigRow(
                            label = "current_location.json",
                            hint = if (state.pinnedCityId == GpsCityId) "// selected" else "// gps",
                            highlight = state.pinnedCityId == GpsCityId,
                            tertiary = true,
                            onClickLabel = stringResource(R.string.cd_select_gps),
                            onSelect = onSelectGps
                        )
                    }
                    state.cities.forEach { city ->
                        ConfigRow(
                            label = "${city.fileSlug()}.json",
                            hint = if (state.pinnedCityId == city.id) "// selected" else null,
                            highlight = state.pinnedCityId == city.id,
                            onClickLabel = stringResource(R.string.cd_open_city, city.name),
                            onSelect = { onSelectCity(city) }
                        )
                    }
                }
            }
            TerminalStatusBar {
                Text(stringResource(R.string.widget_config_hint))
            }
        }
    }
}

/** A tree leaf: `· milan.json  // selected`. */
@Composable
private fun ConfigRow(
    label: String,
    hint: String?,
    highlight: Boolean,
    onClickLabel: String,
    onSelect: () -> Unit,
    tertiary: Boolean = false
) {
    val syntax = TweatherTheme.syntax
    val style = MaterialTheme.typography.bodySmall
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = onClickLabel) { onSelect() }
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = "·",
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp)
        )
        Text(
            text = label,
            style = style,
            color = when {
                highlight -> MaterialTheme.colorScheme.primary
                tertiary -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
        if (hint != null) {
            Text(text = "  $hint", style = style, color = syntax.comment)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun WidgetConfigScreenPreview() {
    TweatherTheme {
        WidgetConfigScreen(
            state = WidgetConfigState(
                cities = listOf(com.callbackdev.tweather.data.CityStore.DefaultCity),
                gpsAvailable = true
            ),
            onFollowApp = {},
            onSelectCity = {},
            onSelectGps = {}
        )
    }
}
