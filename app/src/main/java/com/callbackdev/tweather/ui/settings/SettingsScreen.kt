package com.callbackdev.tweather.ui.settings

import android.content.res.Resources
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.callbackdev.tweather.domain.sky.SkyLead
import com.callbackdev.tweather.ui.components.CanvasLine
import com.callbackdev.tweather.ui.components.CodeCanvas
import com.callbackdev.tweather.ui.components.CodeLine
import com.callbackdev.tweather.ui.components.EditorTabs
import com.callbackdev.tweather.ui.components.StatusBarDivider
import com.callbackdev.tweather.ui.components.TerminalStatusBar
import com.callbackdev.tweather.ui.components.commentLine
import com.callbackdev.tweather.ui.components.keyOpenLine
import com.callbackdev.tweather.ui.components.punctLine
import com.callbackdev.tweather.ui.components.stringValueLine
import com.callbackdev.tweather.ui.theme.SyntaxColors
import com.callbackdev.tweather.ui.theme.ThemeProfile
import com.callbackdev.tweather.notifications.AlertScheduler
import com.callbackdev.tweather.ui.theme.TweatherTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * The files open in the Settings tab bar: two since Fase 11, three since 14d —
 * `HELP.md` lands here because this is where someone goes when the app has confused
 * them, and because the editor's two tabs belong to the city, not to the app.
 */
internal val SettingsFiles = listOf("settings.config", "alerts.rules", "HELP.md")

/** Index of `HELP.md` in [SettingsFiles] — the target of the editor's first-run hint. */
internal const val HelpFileIndex = 2

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
    val onUserRules: (Boolean) -> Unit,
    val onSkyEnabled: (Boolean) -> Unit,
    val onCycleSkyNotifyDefault: () -> Unit,
    val onSkyNotifyOnFail: (Boolean) -> Unit,
    val onCycleFrequency: () -> Unit,
    val onCycleWidgetOpacity: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onReset: () -> Unit
)

/** Status of the `notifications` block's dynamic `//` line. */
enum class NotifLineState {
    /** All three toggles off — nothing scheduled. */
    Disabled,

    /** Engine armed: at least one toggle on and permission granted. */
    Armed,

    /** Toggles on but no permission; tap requests it. */
    MissingPermission,

    /** Permission permanently denied; tap opens the system app settings. */
    DeniedPermanently
}

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
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    rulesViewModel: RulesViewModel = viewModel(factory = RulesViewModel.Factory),
    /** Set by the editor's `HELP.md` hint: open that file rather than the config. */
    openHelp: Boolean = false,
    onHelpOpened: () -> Unit = {}
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val useGps by viewModel.useGps.collectAsStateWithLifecycle()
    val rules by rulesViewModel.rules.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val activity = LocalActivity.current

    // Two files behind one tab bar (Fase 11), scroll kept per file like the Logs.
    var activeFile by rememberSaveable { mutableIntStateOf(0) }
    val settingsScroll = rememberLazyListState()
    val rulesScroll = rememberLazyListState()
    val helpScroll = rememberLazyListState()

    // The hint in the editor asks for a file, not just for this tab (the nav graph
    // restores whichever one was open last).
    LaunchedEffect(openHelp) {
        if (openHelp) {
            activeFile = HelpFileIndex
            onHelpOpened()
        }
    }

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

    // POST_NOTIFICATIONS — same state machine as the GPS permission above.
    val hasNotifPermission = remember(permissionEpoch) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
    var notifDeniedPermanently by remember { mutableStateOf(false) }
    // A toggle the user flipped on before granting: applied right after the grant
    var pendingNotifToggle by remember { mutableStateOf<(() -> Unit)?>(null) }
    val scope = rememberCoroutineScope()
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionEpoch++
        if (granted) {
            notifDeniedPermanently = false
            pendingNotifToggle?.invoke()
            pendingNotifToggle = null
            // A grant doesn't touch DataStore, so MainActivity's settings collector
            // won't fire — reconcile the background work explicitly.
            scope.launch { AlertScheduler.reconcile(context.applicationContext) }
        } else {
            pendingNotifToggle = null
            if (activity?.shouldShowRequestPermissionRationale(
                    Manifest.permission.POST_NOTIFICATIONS
                ) == false
            ) {
                notifDeniedPermanently = true
            }
        }
    }
    // The system-settings detour has no result callback: every resume (the epoch,
    // not the boolean — a return WITHOUT the grant must run this too) is the return
    // path. A grant applies the toggle the user had flipped and re-arms the
    // background work (a bare grant doesn't touch DataStore, so nothing else
    // reconciles — and reconcile is idempotent, UPDATE preserves the cycle); any
    // other return clears the pending, so a stale toggle can never fire on a much
    // later grant.
    LaunchedEffect(permissionEpoch) {
        if (hasNotifPermission) {
            pendingNotifToggle?.invoke()
            AlertScheduler.reconcile(context.applicationContext)
        }
        pendingNotifToggle = null
    }
    val anyNotifOn = with(settings.notifications) {
        severeWeatherAlerts || dailySummary || precipitationWarning ||
            (userRules && rules.any { it.enabled })
    }
    val notifState = when {
        !anyNotifOn -> NotifLineState.Disabled
        hasNotifPermission -> NotifLineState.Armed
        notifDeniedPermanently -> NotifLineState.DeniedPermanently
        else -> NotifLineState.MissingPermission
    }

    /** Turning a notification toggle ON without the permission asks for it first. */
    fun gated(setter: (Boolean) -> Unit): (Boolean) -> Unit = { enabled ->
        if (enabled && !hasNotifPermission) {
            pendingNotifToggle = { setter(true) }
            if (notifDeniedPermanently) {
                context.openAppSystemSettings()
            } else {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            setter(enabled)
        }
    }

    if (activeFile == HelpFileIndex) {
        // Seen is seen: the hint stops pointing at a file the user has now opened.
        LaunchedEffect(Unit) { viewModel.markHelpSeen() }
        HelpScreen(onSelectFile = { activeFile = it }, canvasState = helpScroll)
        return
    }
    if (activeFile == 1) {
        RulesScreen(
            onSelectFile = { activeFile = it },
            canvasState = rulesScroll,
            viewModel = rulesViewModel
        )
        return
    }
    SettingsScreen(
        settings = settings,
        notifState = notifState,
        onSelectFile = { activeFile = it },
        canvasState = settingsScroll,
        onNotifLine = {
            when (notifState) {
                NotifLineState.MissingPermission ->
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                NotifLineState.DeniedPermanently -> context.openAppSystemSettings()
                else -> Unit
            }
        },
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
            onSevereAlerts = gated(viewModel::setSevereWeatherAlerts),
            onDailySummary = gated(viewModel::setDailySummary),
            onPrecipWarning = gated(viewModel::setPrecipitationWarning),
            onUserRules = gated(viewModel::setUserRules),
            onSkyEnabled = viewModel::setSkyEnabled,
            onCycleSkyNotifyDefault = viewModel::cycleSkyNotifyDefault,
            onSkyNotifyOnFail = gated(viewModel::setSkyNotifyOnFail),
            onCycleFrequency = viewModel::cycleUpdateFrequency,
            onCycleWidgetOpacity = viewModel::cycleWidgetOpacity,
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
    onGpsLine: () -> Unit = {},
    notifState: NotifLineState = NotifLineState.Armed,
    onNotifLine: () -> Unit = {},
    onSelectFile: (Int) -> Unit = {},
    canvasState: LazyListState = rememberLazyListState()
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
        notifState = notifState,
        notifLabel = resources.getString(R.string.cd_grant_notifications),
        onNotifLine = onNotifLine,
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
        },
        resources = resources
    )
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            EditorTabs(
                fileNames = SettingsFiles,
                activeIndex = 0,
                onSelect = onSelectFile
            )
            CodeCanvas(
                lines = lines,
                state = canvasState,
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
    notifState: NotifLineState,
    notifLabel: String,
    onNotifLine: () -> Unit,
    gpsState: GpsLineState,
    gpsDeniedFlash: Boolean,
    gpsLabel: String,
    onGpsLine: () -> Unit,
    resetArmed: Boolean,
    resetLabel: String,
    onResetLine: () -> Unit,
    resources: Resources
): List<CanvasLine> = buildList {
    // `// ` is the file's syntax and never translates; what follows it does when
    // it is a sentence (Fase 18). The banner is not one — it is the artifact's own
    // signature, like a shebang — and neither are the licences below.
    fun note(id: Int, vararg args: Any) = "// " + resources.getString(id, *args)
    add(commentLine("// Tweather Configuration File", syntax))
    settings.lastModifiedEpochSeconds?.let { epoch ->
        // mockup line 2; appears once the user edits something for the first time
        val stamp = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(epoch))
        add(commentLine("// Last modified: $stamp", syntax))
    }
    add(punctLine("{", 0, syntax))

    add(keyOpenLine("editor", 1, syntax))
    add(boolLine("line_numbers", settings.editor.lineNumbers, comma = true,
        hint = note(R.string.note_click_toggle), syntax = syntax,
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
        hint = note(R.string.note_full_json), syntax = syntax,
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
    add(
        notifStatusLine(
            notifState, settings.updateFrequencyMin, syntax, notifLabel, onNotifLine, resources
        )
    )
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
        comma = true, syntax = syntax,
        onClickLabel = changeLabel("precipitation_warning")) {
        actions.onPrecipWarning(!settings.notifications.precipitationWarning)
    })
    add(boolLine("user_rules", settings.notifications.userRules,
        comma = false, hint = "// alerts.rules", syntax = syntax,
        onClickLabel = changeLabel("user_rules")) {
        actions.onUserRules(!settings.notifications.userRules)
    })
    add(punctLine("},", 1, syntax))

    // Three keys since Fase 16f, and not one more: `VISION_SKY.md` §10 sketched
    // eight, and the five that went are the ones the file already answers — the
    // verdict thresholds are printed in `sky.crontab` itself, and the horizon is a
    // fact about the forecast rather than a preference.
    add(keyOpenLine("sky", 1, syntax))
    add(boolLine("enabled", settings.skyEnabled, comma = true,
        hint = note(R.string.note_sky_in_editor), syntax = syntax,
        onClickLabel = changeLabel("enabled")) {
        actions.onSkyEnabled(!settings.skyEnabled)
    })
    add(
        CodeLine(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = syntax.key)) { append("\"notify_default\"") }
                withStyle(SpanStyle(color = syntax.comment)) { append(": ") }
                withStyle(SpanStyle(color = syntax.string)) {
                    append("\"${SkyLead.ofMinutes(settings.skyNotifyDefaultMin).label}\"")
                }
                withStyle(SpanStyle(color = syntax.comment)) {
                    append(",  // " + resources.getString(R.string.note_sky_notify_default))
                }
            },
            indent = 2,
            onClick = actions.onCycleSkyNotifyDefault,
            onClickLabel = changeLabel("notify_default")
        )
    )
    add(boolLine("notify_on_fail", settings.skyNotifyOnFail, comma = false,
        hint = note(R.string.note_notify_invisible), syntax = syntax,
        onClickLabel = changeLabel("notify_on_fail")) {
        actions.onSkyNotifyOnFail(!settings.skyNotifyOnFail)
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
                withStyle(SpanStyle(color = syntax.comment)) { append("  // 15 | 30 | 60 | 120") }
            },
            indent = 2,
            onClick = actions.onCycleFrequency,
            onClickLabel = changeLabel("update_frequency_min")
        )
    )
    add(punctLine("},", 1, syntax))

    add(keyOpenLine("widget", 1, syntax))
    add(
        CodeLine(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = syntax.key)) { append("\"bg_opacity_pct\"") }
                withStyle(SpanStyle(color = syntax.comment)) { append(": ") }
                withStyle(SpanStyle(color = syntax.number)) {
                    append(settings.widgetOpacityPct.toString())
                }
                withStyle(SpanStyle(color = syntax.comment)) { append("  // 100 | 85 | 70 | 50") }
            },
            indent = 2,
            onClick = actions.onCycleWidgetOpacity,
            onClickLabel = changeLabel("bg_opacity_pct")
        )
    )
    add(punctLine("},", 1, syntax))

    add(keyOpenLine("location", 1, syntax))
    add(gpsLine(gpsState, syntax, gpsLabel, onGpsLine, resources))
    if (gpsDeniedFlash) {
        // transient (~4 s), like the search screen's terminal errors
        add(
            CodeLine(
                AnnotatedString(
                    "// ERROR: " + resources.getString(R.string.note_err_gps_denied),
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
    add(commentLine(note(R.string.note_restore_defaults), syntax))
    add(
        CodeLine(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = syntax.comment)) { append("$ ") }
                append("git restore settings.config")
                if (resetArmed) {
                    withStyle(SpanStyle(color = syntax.diffDel)) {
                        append("  // " + resources.getString(R.string.note_tap_again))
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
 * The notifications block's status line — what replaced the placeholder comment
 * `// alert engine ships later`. Error states are tappable (grant / app settings).
 */
private fun notifStatusLine(
    state: NotifLineState,
    pollMinutes: Int,
    syntax: SyntaxColors,
    onClickLabel: String,
    onClick: () -> Unit,
    resources: Resources
): CodeLine {
    // `ERROR:` is a level, not a word: it stays outside the resource exactly as a
    // `net::ERR_*` code would, and the sentence after it is the reader's.
    val (text, color) = when (state) {
        NotifLineState.Disabled ->
            "// " + resources.getString(R.string.note_alerts_disabled) to
                syntax.comment.copy(alpha = 0.6f)
        NotifLineState.Armed ->
            "// " + resources.getString(R.string.note_polling_every, pollMinutes) to
                syntax.comment.copy(alpha = 0.6f)
        NotifLineState.MissingPermission ->
            "// ERROR: " + resources.getString(R.string.note_err_notif_missing) to syntax.diffDel
        NotifLineState.DeniedPermanently ->
            "// ERROR: " + resources.getString(R.string.note_err_denied) to syntax.diffDel
    }
    val clickable = state == NotifLineState.MissingPermission ||
        state == NotifLineState.DeniedPermanently
    return CodeLine(
        text = AnnotatedString(text, SpanStyle(color = color)),
        indent = 2,
        onClick = onClick.takeIf { clickable },
        onClickLabel = onClickLabel.takeIf { clickable }
    )
}

/**
 * `"use_gps": false  // tap to enable` — the boolean reflects the persisted toggle
 * (a revoked permission keeps it true, with the problem in the hint); the hint goes
 * diff-deletion red for the two permission-error states.
 */
private fun gpsLine(
    state: GpsLineState,
    syntax: SyntaxColors,
    onClickLabel: String,
    onClick: () -> Unit,
    resources: Resources
): CodeLine {
    val value = state == GpsLineState.On || state == GpsLineState.Revoked
    val (hint, hintColor) = when (state) {
        GpsLineState.Off ->
            "// " + resources.getString(R.string.note_gps_tap_enable) to
                syntax.comment.copy(alpha = 0.6f)
        // It used to say `in explorer`, which is a tab nobody can see: the first
        // tab lost that name in Fase 11b and only its nav route kept it. The
        // translation pass is what walked past it — the entry lives in
        // `cities.json`, and that is the file the reader is looking for.
        GpsLineState.On ->
            "// " + resources.getString(R.string.note_gps_pinned) to
                syntax.comment.copy(alpha = 0.6f)
        GpsLineState.DeniedPermanently ->
            "// ERROR: " + resources.getString(R.string.note_err_denied) to syntax.diffDel
        GpsLineState.Revoked ->
            "// ERROR: " + resources.getString(R.string.note_err_gps_revoked) to syntax.diffDel
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

/** Permanently denied permissions can only be granted back from the app's page. */
private fun android.content.Context.openAppSystemSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 700)
@Composable
private fun SettingsScreenPreview() {
    TweatherTheme {
        SettingsScreen(
            settings = AppSettings(),
            actions = SettingsActions(
                {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}
            )
        )
    }
}
