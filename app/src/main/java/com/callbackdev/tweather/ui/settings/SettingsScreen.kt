package com.callbackdev.tweather.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.tweather.BuildConfig
import com.callbackdev.tweather.R
import com.callbackdev.tweather.data.AppSettings
import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.data.WindSpeedUnit
import com.callbackdev.tweather.ui.components.CanvasLine
import com.callbackdev.tweather.ui.components.CodeCanvas
import com.callbackdev.tweather.ui.components.CodeLine
import com.callbackdev.tweather.ui.components.EditorTab
import com.callbackdev.tweather.ui.components.StatusBarDivider
import com.callbackdev.tweather.ui.components.TerminalStatusBar
import com.callbackdev.tweather.ui.components.commentLine
import com.callbackdev.tweather.ui.theme.SyntaxColors
import com.callbackdev.tweather.ui.theme.ThemeProfile
import com.callbackdev.tweather.ui.theme.TweatherTheme
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Everything the settings file can change, bundled for [buildSettingsLines]. */
class SettingsActions(
    val onLineNumbers: (Boolean) -> Unit,
    val onWordWrap: (Boolean) -> Unit,
    val onShowDetails: (Boolean) -> Unit,
    val onToggleTemperature: () -> Unit,
    val onToggleWindSpeed: () -> Unit,
    val onThemeProfile: (String) -> Unit,
    val onSevereAlerts: (Boolean) -> Unit,
    val onDailySummary: (Boolean) -> Unit,
    val onPrecipWarning: (Boolean) -> Unit,
    val onCycleFrequency: () -> Unit,
    val onOpenUrl: (String) -> Unit
)

/**
 * Settings screen: the fake file `settings.config`, mockup format (JSON body with
 * `//` comments). Every editable value is a plain tappable code line (booleans flip,
 * strings/numbers cycle) so word wrap works on them like on any other line; theme
 * profiles also activate by tapping them in `available_profiles`. Everything
 * persists via DataStore and applies to the app immediately.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    SettingsScreen(
        settings = settings,
        actions = SettingsActions(
            onLineNumbers = viewModel::setLineNumbers,
            onWordWrap = viewModel::setWordWrap,
            onShowDetails = viewModel::setShowDetails,
            onToggleTemperature = viewModel::toggleTemperatureUnit,
            onToggleWindSpeed = viewModel::toggleWindSpeedUnit,
            onThemeProfile = viewModel::setThemeProfile,
            onSevereAlerts = viewModel::setSevereWeatherAlerts,
            onDailySummary = viewModel::setDailySummary,
            onPrecipWarning = viewModel::setPrecipitationWarning,
            onCycleFrequency = viewModel::cycleUpdateFrequency,
            onOpenUrl = uriHandler::openUri
        )
    )
}

@Composable
fun SettingsScreen(settings: AppSettings, actions: SettingsActions) {
    val syntax = TweatherTheme.syntax
    val resources = LocalContext.current.resources
    val lines = buildSettingsLines(
        settings, syntax, actions,
        changeLabel = { key -> resources.getString(R.string.cd_change_setting, key) },
        openLabel = { name -> resources.getString(R.string.cd_open_link, name) }
    )
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            EditorTab(fileName = "settings.config")
            CodeCanvas(
                lines = lines,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            )
            TerminalStatusBar {
                Text("⎇ config")
                StatusBarDivider()
                Text("rw")
                Spacer(Modifier.weight(1f))
                Text("UTF-8")
            }
        }
    }
}

private fun buildSettingsLines(
    settings: AppSettings,
    syntax: SyntaxColors,
    actions: SettingsActions,
    changeLabel: (String) -> String,
    openLabel: (String) -> String
): List<CanvasLine> = buildList {
    add(commentLine("// Tweather Configuration File", syntax))
    settings.lastModifiedEpochSeconds?.let { epoch ->
        // mockup line 2; appears once the user edits something for the first time
        val stamp = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(epoch))
        add(commentLine("// Last modified: $stamp", syntax))
    }
    add(punctLine("{", 0, syntax))

    add(keyOpenLine("editor", 1, syntax))
    add(boolLine("line_numbers", settings.editor.lineNumbers, comma = true,
        hint = "// click to toggle", syntax = syntax,
        onClickLabel = changeLabel("line_numbers")) {
        actions.onLineNumbers(!settings.editor.lineNumbers)
    })
    add(boolLine("word_wrap", settings.editor.wordWrap, comma = false, syntax = syntax,
        onClickLabel = changeLabel("word_wrap")) {
        actions.onWordWrap(!settings.editor.wordWrap)
    })
    add(punctLine("},", 1, syntax))

    add(keyOpenLine("data", 1, syntax))
    add(boolLine("show_details", settings.showDetails, comma = false,
        hint = "// full weather_data.json", syntax = syntax,
        onClickLabel = changeLabel("show_details")) {
        actions.onShowDetails(!settings.showDetails)
    })
    add(punctLine("},", 1, syntax))

    add(keyOpenLine("units", 1, syntax))
    add(
        stringValueLine(
            "temperature",
            if (settings.units.temperature == TemperatureUnit.CELSIUS) "celsius" else "fahrenheit",
            comma = true,
            syntax = syntax,
            onClickLabel = changeLabel("temperature"),
            onClick = actions.onToggleTemperature
        )
    )
    add(
        stringValueLine(
            "wind_speed",
            if (settings.units.windSpeed == WindSpeedUnit.KMH) "km/h" else "mph",
            comma = false,
            syntax = syntax,
            onClickLabel = changeLabel("wind_speed"),
            onClick = actions.onToggleWindSpeed
        )
    )
    add(punctLine("},", 1, syntax))

    add(keyOpenLine("theme", 1, syntax))
    add(
        stringValueLine(
            "active_profile", settings.themeProfileName, comma = true, syntax = syntax,
            onClickLabel = changeLabel("active_profile"),
            onClick = {
                val entries = ThemeProfile.entries
                val current = ThemeProfile.fromName(settings.themeProfileName)
                actions.onThemeProfile(entries[(entries.indexOf(current) + 1) % entries.size].name)
            }
        )
    )
    add(keyOpenLine("available_profiles", 2, syntax, bracket = "["))
    ThemeProfile.entries.forEachIndexed { i, profile ->
        val isActive = profile.name == settings.themeProfileName
        add(
            CodeLine(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = syntax.string)) { append("\"${profile.name}\"") }
                    if (i != ThemeProfile.entries.lastIndex) {
                        withStyle(SpanStyle(color = syntax.comment)) { append(",") }
                    }
                    if (isActive) {
                        withStyle(SpanStyle(color = syntax.comment)) { append("  // active") }
                    }
                },
                indent = 3,
                onClick = { actions.onThemeProfile(profile.name) },
                onClickLabel = changeLabel("active_profile")
            )
        )
    }
    add(punctLine("]", 2, syntax))
    add(punctLine("},", 1, syntax))

    add(keyOpenLine("notifications", 1, syntax))
    add(commentLine("// alert engine ships later; preferences persist now", syntax, indent = 2))
    add(boolLine("severe_weather_alerts", settings.notifications.severeWeatherAlerts,
        comma = true, syntax = syntax,
        onClickLabel = changeLabel("severe_weather_alerts")) {
        actions.onSevereAlerts(!settings.notifications.severeWeatherAlerts)
    })
    add(boolLine("daily_summary", settings.notifications.dailySummary,
        comma = true, syntax = syntax,
        onClickLabel = changeLabel("daily_summary")) {
        actions.onDailySummary(!settings.notifications.dailySummary)
    })
    add(boolLine("precipitation_warning", settings.notifications.precipitationWarning,
        comma = false, syntax = syntax,
        onClickLabel = changeLabel("precipitation_warning")) {
        actions.onPrecipWarning(!settings.notifications.precipitationWarning)
    })
    add(punctLine("},", 1, syntax))

    add(keyOpenLine("sync", 1, syntax))
    add(
        CodeLine(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = syntax.key)) { append("\"update_frequency_min\"") }
                withStyle(SpanStyle(color = syntax.comment)) { append(": ") }
                withStyle(SpanStyle(color = syntax.number)) {
                    append(settings.updateFrequencyMin.toString())
                }
                withStyle(SpanStyle(color = syntax.comment)) { append("  // 15 | 30 | 60") }
            },
            indent = 2,
            onClick = actions.onCycleFrequency,
            onClickLabel = changeLabel("update_frequency_min")
        )
    )
    add(punctLine("},", 1, syntax))

    // Read-only About block; the attribution/license lines open the related site.
    add(keyOpenLine("about", 1, syntax))
    add(stringValueLine("app_name", "tweather", comma = true, syntax = syntax))
    add(stringValueLine("version", BuildConfig.VERSION_NAME, comma = true, syntax = syntax))
    add(stringValueLine("developer", "Callback Dev", comma = true, syntax = syntax))
    add(stringValueLine("copyright", "© 2026 Fiorenzo Brioni", comma = true, syntax = syntax))
    add(
        stringValueLine(
            "license", "GPL-3.0", comma = true, syntax = syntax,
            onClickLabel = openLabel("license"),
            onClick = { actions.onOpenUrl("https://www.gnu.org/licenses/gpl-3.0.html") }
        )
    )
    add(keyOpenLine("credits", 2, syntax))
    add(
        stringValueLine(
            "weather_data", "Open-Meteo.com", comma = true, syntax = syntax, indent = 3,
            hint = "// CC BY 4.0",
            onClickLabel = openLabel("Open-Meteo"),
            onClick = { actions.onOpenUrl("https://open-meteo.com/") }
        )
    )
    add(
        stringValueLine(
            "font", "JetBrains Mono", comma = false, syntax = syntax, indent = 3,
            hint = "// SIL OFL 1.1",
            onClickLabel = openLabel("JetBrains Mono"),
            onClick = { actions.onOpenUrl("https://www.jetbrains.com/lp/mono/") }
        )
    )
    add(punctLine("}", 2, syntax))
    add(punctLine("}", 1, syntax))

    add(punctLine("}", 0, syntax))
}

/**
 * `"word_wrap": false,  // hint` — a plain [CodeLine] (so it word-wraps like any
 * other line, unlike a widget Row) whose whole line toggles the boolean on tap.
 */
private fun boolLine(
    key: String,
    value: Boolean,
    comma: Boolean,
    syntax: SyntaxColors,
    hint: String? = null,
    onClickLabel: String? = null,
    onToggle: () -> Unit
): CodeLine = CodeLine(
    text = buildAnnotatedString {
        withStyle(SpanStyle(color = syntax.key)) { append("\"$key\"") }
        withStyle(SpanStyle(color = syntax.comment)) { append(": ") }
        withStyle(SpanStyle(color = syntax.number)) { append(value.toString()) }
        if (comma) withStyle(SpanStyle(color = syntax.comment)) { append(",") }
        if (hint != null) {
            withStyle(SpanStyle(color = syntax.comment.copy(alpha = 0.6f))) {
                append("  $hint")
            }
        }
    },
    indent = 2,
    onClick = onToggle,
    onClickLabel = onClickLabel
)

/**
 * `"temperature": "celsius",` — the string value flips/cycles (or opens a link)
 * on tap; with no [onClick] it is a plain read-only line (About block).
 */
private fun stringValueLine(
    key: String,
    value: String,
    comma: Boolean,
    syntax: SyntaxColors,
    indent: Int = 2,
    hint: String? = null,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null
): CodeLine = CodeLine(
    text = buildAnnotatedString {
        withStyle(SpanStyle(color = syntax.key)) { append("\"$key\"") }
        withStyle(SpanStyle(color = syntax.comment)) { append(": ") }
        withStyle(SpanStyle(color = syntax.string)) { append("\"$value\"") }
        if (comma) withStyle(SpanStyle(color = syntax.comment)) { append(",") }
        if (hint != null) {
            withStyle(SpanStyle(color = syntax.comment.copy(alpha = 0.6f))) {
                append("  $hint")
            }
        }
    },
    indent = indent,
    onClick = onClick,
    onClickLabel = onClickLabel
)

private fun punctLine(text: String, indent: Int, syntax: SyntaxColors) =
    CodeLine(AnnotatedString(text, SpanStyle(color = syntax.comment)), indent)

private fun keyOpenLine(key: String, indent: Int, syntax: SyntaxColors, bracket: String = "{") =
    CodeLine(
        buildAnnotatedString {
            withStyle(SpanStyle(color = syntax.key)) { append("\"$key\"") }
            withStyle(SpanStyle(color = syntax.comment)) { append(": $bracket") }
        },
        indent
    )

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 700)
@Composable
private fun SettingsScreenPreview() {
    TweatherTheme {
        SettingsScreen(
            settings = AppSettings(),
            actions = SettingsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        )
    }
}
