package com.callbackdev.tweather.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.tweather.data.AppSettings
import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.data.WindSpeedUnit
import com.callbackdev.tweather.ui.components.CanvasLine
import com.callbackdev.tweather.ui.components.CodeCanvas
import com.callbackdev.tweather.ui.components.CodeLine
import com.callbackdev.tweather.ui.components.CodeToggle
import com.callbackdev.tweather.ui.components.EditorTab
import com.callbackdev.tweather.ui.components.StatusBarDivider
import com.callbackdev.tweather.ui.components.SyntaxText
import com.callbackdev.tweather.ui.components.TerminalStatusBar
import com.callbackdev.tweather.ui.components.WidgetLine
import com.callbackdev.tweather.ui.components.commentLine
import com.callbackdev.tweather.ui.theme.SyntaxColors
import com.callbackdev.tweather.ui.theme.ThemeProfile
import com.callbackdev.tweather.ui.theme.TweatherTheme

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
    val onCycleFrequency: () -> Unit
)

/**
 * Settings screen: the fake file `settings.config`, mockup format (JSON body with
 * `//` comments). Booleans are [CodeToggle]s, string/number values flip or cycle on
 * tap, theme profiles activate by tapping them in `available_profiles`. Everything
 * persists via DataStore and applies to the app immediately.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
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
            onCycleFrequency = viewModel::cycleUpdateFrequency
        )
    )
}

@Composable
fun SettingsScreen(settings: AppSettings, actions: SettingsActions) {
    val syntax = TweatherTheme.syntax
    val lines = buildSettingsLines(settings, syntax, actions)
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
    actions: SettingsActions
): List<CanvasLine> = buildList {
    add(commentLine("// Tweather Configuration File", syntax))
    add(punctLine("{", 0, syntax))

    add(keyOpenLine("editor", 1, syntax))
    add(toggleLine("line_numbers", settings.editor.lineNumbers, comma = true,
        hint = "// click to toggle", onChange = actions.onLineNumbers))
    add(toggleLine("word_wrap", settings.editor.wordWrap, comma = false,
        onChange = actions.onWordWrap))
    add(punctLine("},", 1, syntax))

    add(keyOpenLine("data", 1, syntax))
    add(toggleLine("show_details", settings.showDetails, comma = false,
        hint = "// full weather_data.json", onChange = actions.onShowDetails))
    add(punctLine("},", 1, syntax))

    add(keyOpenLine("units", 1, syntax))
    add(
        stringValueLine(
            "temperature",
            if (settings.units.temperature == TemperatureUnit.CELSIUS) "celsius" else "fahrenheit",
            comma = true,
            syntax = syntax,
            onClick = actions.onToggleTemperature
        )
    )
    add(
        stringValueLine(
            "wind_speed",
            if (settings.units.windSpeed == WindSpeedUnit.KMH) "km/h" else "mph",
            comma = false,
            syntax = syntax,
            onClick = actions.onToggleWindSpeed
        )
    )
    add(punctLine("},", 1, syntax))

    add(keyOpenLine("theme", 1, syntax))
    add(
        stringValueLine(
            "active_profile", settings.themeProfileName, comma = true, syntax = syntax,
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
                onClick = { actions.onThemeProfile(profile.name) }
            )
        )
    }
    add(punctLine("]", 2, syntax))
    add(punctLine("},", 1, syntax))

    add(keyOpenLine("notifications", 1, syntax))
    add(commentLine("// alert engine ships later; preferences persist now", syntax, indent = 2))
    add(toggleLine("severe_weather_alerts", settings.notifications.severeWeatherAlerts,
        comma = true, onChange = actions.onSevereAlerts))
    add(toggleLine("daily_summary", settings.notifications.dailySummary,
        comma = true, onChange = actions.onDailySummary))
    add(toggleLine("precipitation_warning", settings.notifications.precipitationWarning,
        comma = false, onChange = actions.onPrecipWarning))
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
            onClick = actions.onCycleFrequency
        )
    )
    add(punctLine("}", 1, syntax))

    add(punctLine("}", 0, syntax))
}

/** `"word_wrap": false,` where the boolean is a tappable [CodeToggle]. */
private fun toggleLine(
    key: String,
    value: Boolean,
    comma: Boolean,
    hint: String? = null,
    onChange: (Boolean) -> Unit
): WidgetLine = WidgetLine(indent = 2) {
    val syntax = TweatherTheme.syntax
    Row(verticalAlignment = Alignment.CenterVertically) {
        SyntaxText(
            buildAnnotatedString {
                withStyle(SpanStyle(color = syntax.key)) { append("\"$key\"") }
                withStyle(SpanStyle(color = syntax.comment)) { append(": ") }
            }
        )
        CodeToggle(value = value, onValueChange = onChange)
        SyntaxText(
            buildAnnotatedString {
                if (comma) withStyle(SpanStyle(color = syntax.comment)) { append(",") }
                if (hint != null) {
                    withStyle(SpanStyle(color = syntax.comment.copy(alpha = 0.6f))) {
                        append("  $hint")
                    }
                }
            }
        )
    }
}

/** `"temperature": "celsius",` — the string value flips/cycles on tap. */
private fun stringValueLine(
    key: String,
    value: String,
    comma: Boolean,
    syntax: SyntaxColors,
    onClick: () -> Unit
): CodeLine = CodeLine(
    text = buildAnnotatedString {
        withStyle(SpanStyle(color = syntax.key)) { append("\"$key\"") }
        withStyle(SpanStyle(color = syntax.comment)) { append(": ") }
        withStyle(SpanStyle(color = syntax.string)) { append("\"$value\"") }
        if (comma) withStyle(SpanStyle(color = syntax.comment)) { append(",") }
    },
    indent = 2,
    onClick = onClick
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
            actions = SettingsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        )
    }
}
