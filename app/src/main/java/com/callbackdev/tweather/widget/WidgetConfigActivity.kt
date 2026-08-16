package com.callbackdev.tweather.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.callbackdev.tweather.R
import com.callbackdev.tweather.data.AppSettings
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.GpsCityId
import com.callbackdev.tweather.ui.components.CanvasLine
import com.callbackdev.tweather.ui.components.CodeCanvas
import com.callbackdev.tweather.ui.components.CodeLine
import com.callbackdev.tweather.ui.components.EditorOptions
import com.callbackdev.tweather.ui.components.EditorTab
import com.callbackdev.tweather.ui.components.LocalEditorOptions
import com.callbackdev.tweather.ui.components.StatusBarDivider
import com.callbackdev.tweather.ui.components.StatusBarText
import com.callbackdev.tweather.ui.components.TerminalStatusBar
import com.callbackdev.tweather.ui.components.commentLine
import com.callbackdev.tweather.ui.components.keyOpenLine
import com.callbackdev.tweather.ui.components.punctLine
import com.callbackdev.tweather.ui.components.stringValueLine
import com.callbackdev.tweather.ui.explorer.fileSlug
import com.callbackdev.tweather.ui.theme.SyntaxColors
import com.callbackdev.tweather.ui.theme.ThemeProfile
import com.callbackdev.tweather.ui.theme.TweatherTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Optional widget configuration (`configuration_optional`, so adding a widget never
 * forces this screen): pins one instance to a city instead of following the app's
 * active source. Rendered as the fake file `widget.config`, same format as
 * `settings.config` — the tab promises a config file, so the body is one.
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
            val settings by produceState(AppSettings()) {
                settingsStore.settings.collect { value = it }
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
            TweatherTheme(profile = ThemeProfile.fromName(settings.themeProfileName)) {
                // This Activity is outside the TweatherApp shell, so it has to hand
                // its own CodeCanvas the editor section of settings.config.
                CompositionLocalProvider(
                    LocalEditorOptions provides EditorOptions(
                        showLineNumbers = settings.editor.lineNumbers,
                        wordWrap = settings.editor.wordWrap
                    )
                ) {
                    WidgetConfigScreen(
                        state = state,
                        onFollowApp = { pin(null) },
                        onSelectCity = { pin(it.id) },
                        onSelectGps = { pin(GpsCityId) }
                    )
                }
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

/** The file name of the pinned source, or null while the widget follows the app. */
private fun WidgetConfigState.pinnedFile(): String? = when {
    pinnedCityId == null -> null
    pinnedCityId == GpsCityId -> if (gpsAvailable) GpsFile else null
    else -> cities.firstOrNull { it.id == pinnedCityId }?.let { "${it.fileSlug()}.json" }
}

private const val FollowAppFile = "active_file"
private const val GpsFile = "current_location.json"

@Composable
fun WidgetConfigScreen(
    state: WidgetConfigState,
    onFollowApp: () -> Unit,
    onSelectCity: (City) -> Unit,
    onSelectGps: () -> Unit
) {
    val syntax = TweatherTheme.syntax
    val selectedColor = MaterialTheme.colorScheme.primary
    // Design system: tertiary is reserved for global constants like "Current Location"
    val gpsColor = MaterialTheme.colorScheme.tertiary
    val resources = LocalContext.current.resources
    val followLabel = stringResource(R.string.cd_widget_follow_app)
    val gpsLabel = stringResource(R.string.cd_select_gps)

    val lines = remember(state, syntax, selectedColor, gpsColor, resources) {
        buildWidgetConfigLines(
            state = state,
            syntax = syntax,
            selectedColor = selectedColor,
            gpsColor = gpsColor,
            followAppLabel = followLabel,
            gpsClickLabel = gpsLabel,
            cityClickLabel = { resources.getString(R.string.cd_widget_pin, it.name) },
            onFollowApp = onFollowApp,
            onSelectGps = onSelectGps,
            onSelectCity = onSelectCity
        )
    }

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
            CodeCanvas(
                lines = lines,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            )
            TerminalStatusBar {
                StatusBarText("⎇ widget")
                StatusBarDivider()
                StatusBarText("rw")
                Spacer(Modifier.weight(1f))
                StatusBarText(
                    stringResource(R.string.status_sources, state.sourceCount())
                )
            }
        }
    }
}

/** `active_file` plus every pinnable city (and the GPS pseudo-city when enabled). */
private fun WidgetConfigState.sourceCount(): Int =
    1 + cities.size + if (gpsAvailable) 1 else 0

/**
 * The `widget.config` buffer. `"source"` is read-only — tapping a source in
 * `available_sources` pins it and closes the screen, so there is nothing to cycle
 * through; the list is the control, like `available_profiles` in settings.config.
 *
 * Only three `//` annotations survive, one per job: the affordance (`tap to pin`),
 * the state (`selected`) and the one file name that cannot explain itself
 * (`active_file`). Anything the value, the file name or the color already says is
 * not repeated in a comment.
 */
private fun buildWidgetConfigLines(
    state: WidgetConfigState,
    syntax: SyntaxColors,
    selectedColor: Color,
    gpsColor: Color,
    followAppLabel: String,
    gpsClickLabel: String,
    cityClickLabel: (City) -> String,
    onFollowApp: () -> Unit,
    onSelectGps: () -> Unit,
    onSelectCity: (City) -> Unit
): List<CanvasLine> = buildList {
    val pinnedFile = state.pinnedFile()

    add(commentLine("// Tweather Widget Configuration", syntax))
    add(punctLine("{", 0, syntax))

    // No "widget" block around this: the file is already widget.config, so a wrapper
    // would only cost every line one indent level.
    add(
        stringValueLine(
            key = "source",
            value = pinnedFile ?: FollowAppFile,
            comma = true,
            syntax = syntax,
            indent = 1
        )
    )
    add(keyOpenLine("available_sources", 1, syntax, bracket = "[", hint = "// tap to pin"))

    // One entry per pinnable source; the last one carries no comma, JSON style.
    val entries = buildList {
        add(
            SourceEntry(
                file = FollowAppFile,
                hint = if (pinnedFile == null) "// selected" else "// follows the app",
                selected = pinnedFile == null,
                clickLabel = followAppLabel,
                onClick = onFollowApp
            )
        )
        if (state.gpsAvailable) {
            add(
                SourceEntry(
                    file = GpsFile,
                    // No "// gps" annotation: the file name already says it, and the
                    // tertiary color is what the design system uses to mark it
                    hint = "// selected".takeIf { pinnedFile == GpsFile },
                    selected = pinnedFile == GpsFile,
                    gps = true,
                    clickLabel = gpsClickLabel,
                    onClick = onSelectGps
                )
            )
        }
        state.cities.forEach { city ->
            val file = "${city.fileSlug()}.json"
            add(
                SourceEntry(
                    file = file,
                    hint = "// selected".takeIf { file == pinnedFile },
                    selected = file == pinnedFile,
                    clickLabel = cityClickLabel(city),
                    onClick = { onSelectCity(city) }
                )
            )
        }
    }
    entries.forEachIndexed { i, entry ->
        add(
            CodeLine(
                text = buildAnnotatedString {
                    val color = when {
                        entry.selected -> selectedColor
                        entry.gps -> gpsColor
                        else -> syntax.string
                    }
                    withStyle(SpanStyle(color = color)) { append("\"${entry.file}\"") }
                    if (i != entries.lastIndex) {
                        withStyle(SpanStyle(color = syntax.comment)) { append(",") }
                    }
                    if (entry.hint != null) {
                        withStyle(SpanStyle(color = syntax.comment.copy(alpha = 0.6f))) {
                            append("  ${entry.hint}")
                        }
                    }
                },
                indent = 2,
                onClick = entry.onClick,
                onClickLabel = entry.clickLabel
            )
        )
    }

    add(punctLine("]", 1, syntax))
    add(punctLine("}", 0, syntax))
}

/** One tappable row of `available_sources`. */
private data class SourceEntry(
    val file: String,
    val hint: String?,
    val selected: Boolean,
    val gps: Boolean = false,
    val clickLabel: String,
    val onClick: () -> Unit
)

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
