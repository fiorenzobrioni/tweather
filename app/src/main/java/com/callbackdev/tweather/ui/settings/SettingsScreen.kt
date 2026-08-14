package com.callbackdev.tweather.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import kotlinx.coroutines.delay
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
    val onOpenUrl: (String) -> Unit,
    val onReset: () -> Unit
)

/** How the `"use_gps"` line renders and reacts; derived in the stateful wrapper. */
enum class GpsLineState {
    /** Feature off; tap asks for the permission (if needed) and enables. */
    Off,

    /** Feature on and permitted; tap disables. */
    On,

    /** Permission permanently denied; tap opens the system app settings. */
    DeniedPermanently,

    /** Feature on but the permission was revoked; tap re-requests it. */
    Revoked
}

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
    val useGps by viewModel.useGps.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val activity = LocalActivity.current

    // The app's only runtime permission. Re-check on every resume so a grant or a
    // revocation made in the system settings is reflected as soon as we're back.
    var permissionEpoch by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionEpoch++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasPermission = remember(permissionEpoch) {
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    var deniedPermanently by remember { mutableStateOf(false) }
    var gpsDeniedFlash by remember { mutableStateOf(false) }
    LaunchedEffect(gpsDeniedFlash) {
        if (gpsDeniedFlash) {
            delay(4_000)
            gpsDeniedFlash = false
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionEpoch++
        if (granted) {
            deniedPermanently = false
            viewModel.setUseGps(true)
        } else if (
            activity?.shouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == false
        ) {
            deniedPermanently = true
        } else {
            gpsDeniedFlash = true
        }
    }

    val gpsState = when {
        useGps && hasPermission -> GpsLineState.On
        useGps -> GpsLineState.Revoked
        deniedPermanently && !hasPermission -> GpsLineState.DeniedPermanently
        else -> GpsLineState.Off
    }

    SettingsScreen(
        settings = settings,
        gpsState = gpsState,
        gpsDeniedFlash = gpsDeniedFlash,
        onGpsLine = {
            when (gpsState) {
                GpsLineState.On -> viewModel.setUseGps(false)
                GpsLineState.Off ->
                    if (hasPermission) {
                        viewModel.setUseGps(true)
                    } else {
                        permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                GpsLineState.Revoked ->
                    if (deniedPermanently) {
                        context.openAppSystemSettings()
                    } else {
                        permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                GpsLineState.DeniedPermanently -> context.openAppSystemSettings()
            }
        },
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
            onOpenUrl = uriHandler::openUri,
            onReset = viewModel::resetToDefaults
        )
    )
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    actions: SettingsActions,
    gpsState: GpsLineState = GpsLineState.Off,
    gpsDeniedFlash: Boolean = false,
    onGpsLine: () -> Unit = {}
) {
    val syntax = TweatherTheme.syntax
    val resources = LocalContext.current.resources
    // Two-tap confirm for the reset command; disarms by itself after a few seconds.
    var resetArmed by remember { mutableStateOf(false) }
    LaunchedEffect(resetArmed) {
        if (resetArmed) {
            delay(4_000)
            resetArmed = false
        }
    }
    val lines = buildSettingsLines(
        settings, syntax, actions,
        changeLabel = { key -> resources.getString(R.string.cd_change_setting, key) },
        openLabel = { name -> resources.getString(R.string.cd_open_link, name) },
        gpsState = gpsState,
        gpsDeniedFlash = gpsDeniedFlash,
        gpsLabel = resources.getString(
            when (gpsState) {
                GpsLineState.On -> R.string.cd_disable_gps
                GpsLineState.DeniedPermanently -> R.string.cd_grant_location
                else -> R.string.cd_enable_gps
            }
        ),
        onGpsLine = onGpsLine,
        resetArmed = resetArmed,
        resetLabel = resources.getString(
            if (resetArmed) R.string.cd_confirm_reset else R.string.cd_reset_settings
        ),
        onResetLine = {
            if (resetArmed) {
                resetArmed = false
                actions.onReset()
            } else {
                resetArmed = true
            }
        }
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
    openLabel: (String) -> String,
    gpsState: GpsLineState,
    gpsDeniedFlash: Boolean,
    gpsLabel: String,
    onGpsLine: () -> Unit,
    resetArmed: Boolean,
    resetLabel: String,
    onResetLine: () -> Unit
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

    add(keyOpenLine("location", 1, syntax))
    add(gpsLine(gpsState, syntax, gpsLabel, onGpsLine))
    if (gpsDeniedFlash) {
        // transient (~4 s), like the search screen's terminal errors
        add(
            CodeLine(
                AnnotatedString(
                    "// ERROR: permission denied — gps stays off",
                    SpanStyle(color = syntax.diffDel)
                ),
                indent = 2
            )
        )
    }
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

    // Terminal prompt below the buffer: factory reset as a git command. First tap
    // arms it (confirm hint in diff-deletion red), second tap runs it.
    add(punctLine("", 0, syntax))
    add(commentLine("// restore defaults (discards local changes):", syntax))
    add(
        CodeLine(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = syntax.comment)) { append("$ ") }
                append("git restore settings.config")
                if (resetArmed) {
                    withStyle(SpanStyle(color = syntax.diffDel)) {
                        append("  // tap again to confirm")
                    }
                }
            },
            indent = 0,
            onClick = onResetLine,
            onClickLabel = resetLabel
        )
    )
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
 * `"use_gps": false  // tap to enable` — the boolean reflects the persisted toggle
 * (a revoked permission keeps it true, with the problem in the hint); the hint goes
 * diff-deletion red for the two permission-error states.
 */
private fun gpsLine(
    state: GpsLineState,
    syntax: SyntaxColors,
    onClickLabel: String,
    onClick: () -> Unit
): CodeLine {
    val value = state == GpsLineState.On || state == GpsLineState.Revoked
    val (hint, hintColor) = when (state) {
        GpsLineState.Off -> "// tap to enable" to syntax.comment.copy(alpha = 0.6f)
        GpsLineState.On ->
            "// current_location.json in explorer" to syntax.comment.copy(alpha = 0.6f)
        GpsLineState.DeniedPermanently ->
            "// ERROR: denied — open system settings" to syntax.diffDel
        GpsLineState.Revoked ->
            "// ERROR: permission revoked — tap to re-grant" to syntax.diffDel
    }
    return CodeLine(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = syntax.key)) { append("\"use_gps\"") }
            withStyle(SpanStyle(color = syntax.comment)) { append(": ") }
            withStyle(SpanStyle(color = syntax.number)) { append(value.toString()) }
            withStyle(SpanStyle(color = hintColor)) { append("  $hint") }
        },
        indent = 2,
        onClick = onClick,
        onClickLabel = onClickLabel
    )
}

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

/** Permanently denied permissions can only be granted back from the app's page. */
private fun android.content.Context.openAppSystemSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
    )
}

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
            actions = SettingsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        )
    }
}
