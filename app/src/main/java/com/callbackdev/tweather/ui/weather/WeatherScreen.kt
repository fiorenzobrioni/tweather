package com.callbackdev.tweather.ui.weather

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.res.Resources
import com.callbackdev.tweather.R
import com.callbackdev.tweather.data.MainEditorFile
import com.callbackdev.tweather.ui.components.CodeCanvas
import com.callbackdev.tweather.ui.components.CodeLine
import com.callbackdev.tweather.ui.components.EditorTabs
import com.callbackdev.tweather.ui.components.GlowFab
import com.callbackdev.tweather.ui.components.StatusBarDivider
import com.callbackdev.tweather.ui.components.StatusBarStart
import com.callbackdev.tweather.ui.components.StatusBarText
import com.callbackdev.tweather.ui.components.TerminalStatusBar
import com.callbackdev.tweather.ui.components.buildJsonLines
import com.callbackdev.tweather.ui.components.buildMarkdownLines
import com.callbackdev.tweather.ui.components.commentLine
import com.callbackdev.tweather.ui.sky.SkyScreen
import com.callbackdev.tweather.ui.sky.SkySummary
import com.callbackdev.tweather.ui.theme.SyntaxColors
import com.callbackdev.tweather.ui.theme.TweatherTheme
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Main screen: the live weather report as two open editor tabs (Fase 10) — the
 * fake source file `weather_data.json` (syntax-highlighted JSON, the full data)
 * and the city's `README.md` (markdown source, the human summary). Code canvas
 * with gutter, glowing refresh FAB (one fetch feeds both renders), terminal
 * status bar at the bottom. The active tab persists as workspace state.
 */
@Composable
fun WeatherScreen(
    onOpenCities: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    viewModel: WeatherViewModel = viewModel(factory = WeatherViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val displayOptions by viewModel.displayOptions.collectAsStateWithLifecycle()
    val activeFile by viewModel.activeFile.collectAsStateWithLifecycle()
    val skyEnabled by viewModel.skyEnabled.collectAsStateWithLifecycle()
    val showHelpHint by viewModel.showHelpHint.collectAsStateWithLifecycle()
    val skySummary by viewModel.skySummary.collectAsStateWithLifecycle()
    val files = editorFiles(skyEnabled)
    val visible = activeFile.visible(skyEnabled)
    val onSelect: (Int) -> Unit = { viewModel.selectFile(editorFileAt(it, skyEnabled)) }

    // `sky.crontab` is its own screen, so the Editor route dispatches here rather
    // than growing a third branch inside the weather document. The workspace state
    // stays owned by ONE view model either way: which file is open is a property of
    // the editor, not of whichever screen happens to be drawing it.
    if (visible == MainEditorFile.SKY) {
        SkyScreen(
            editorFiles = files,
            activeIndex = files.indexOf(SkyFileName),
            onSelectFile = onSelect
        )
        return
    }
    WeatherScreen(
        state = state,
        displayOptions = displayOptions,
        onRefresh = viewModel::refresh,
        onOpenCities = onOpenCities,
        activeFile = visible,
        skySummary = skySummary,
        editorFiles = files,
        onSelectTab = onSelect,
        showHelpHint = showHelpHint,
        onOpenHelp = {
            viewModel.dismissHelpHint()
            onOpenHelp()
        }
    )
}

@Composable
fun WeatherScreen(
    state: WeatherUiState,
    onRefresh: () -> Unit,
    onOpenCities: () -> Unit = {},
    displayOptions: DisplayOptions = DisplayOptions(),
    activeFile: MainEditorFile = MainEditorFile.JSON,
    /** What the sky adds to `README.md` (Fase 16e); null when the module is off. */
    skySummary: SkySummary? = null,
    /** The strip's names — two files, or three when `sky.enabled` (Fase 16c). */
    editorFiles: List<String> = editorFiles(skyEnabled = false),
    onSelectTab: (Int) -> Unit = {},
    /** Fase 14d: the one-shot pointer to `HELP.md`, first line of the document. */
    showHelpHint: Boolean = false,
    onOpenHelp: () -> Unit = {}
) {
    val syntax = TweatherTheme.syntax
    val resources = LocalContext.current.resources
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val hint = if (showHelpHint) stringResource(R.string.help_hint) else null
    val lines = remember(state, syntax, locale, displayOptions, activeFile, hint, skySummary) {
        val head = hint?.let {
            listOf(
                CodeLine(
                    AnnotatedString("// $it", SpanStyle(color = syntax.key)),
                    onClick = onOpenHelp,
                    onClickLabel = it
                )
            )
        } ?: emptyList()
        head + when (activeFile) {
            MainEditorFile.JSON -> buildScreenLines(
                state, syntax, WeatherTranslations.translator(resources), locale, displayOptions,
                resources
            )
            MainEditorFile.README -> buildReadmeLines(
                state, syntax, resources, locale, displayOptions, skySummary
            )
            // Unreachable through the app: the stateful wrapper hands `SKY` to
            // SkyScreen before this body runs. It is spelled out rather than left to
            // an `else` so that adding a fourth file breaks the compile here, which
            // is where a fourth file would need a document.
            MainEditorFile.SKY -> buildScreenLines(
                state, syntax, WeatherTranslations.translator(resources), locale, displayOptions,
                resources
            )
        }
    }
    // One scroll position per file, like the Logs: switching tab must not land
    // mid-document because the OTHER file was scrolled there.
    val jsonScroll = rememberLazyListState()
    val readmeScroll = rememberLazyListState()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // No more actions pinned right (Fase 10b): `$ ls cities/` used to live
            // there and squeezed the strip until README.md truncated — the city
            // list is reachable from the status bar's ⎇ and the Cerca tab now.
            EditorTabs(
                fileNames = editorFiles,
                activeIndex = editorFiles.indexOf(activeFile.fileName()).coerceAtLeast(0),
                onSelect = onSelectTab
            )
            Box(Modifier.weight(1f)) {
                CodeCanvas(
                    lines = lines,
                    state = if (activeFile == MainEditorFile.JSON) jsonScroll else readmeScroll,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = FabClearance)
                )
                // Hidden with no location (Fase 14b): a refresh button with nothing
                // to refresh is the same kind of lie as a metric with no input.
                if (!state.noLocation) {
                    GlowFab(
                        onClick = onRefresh,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 24.dp),
                        contentDescription = stringResource(R.string.cd_refresh),
                        icon = { RefreshIcon(spinning = state.isLoading) }
                    )
                }
            }
            WeatherStatusBar(state, onOpenCities)
        }
    }
}

/** The FAB's refresh glyph doubles as loading indicator: it spins during a fetch. */
@Composable
private fun RefreshIcon(spinning: Boolean) {
    val angle: Float
    if (spinning) {
        val transition = rememberInfiniteTransition(label = "refresh-spin")
        angle = transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
            label = "refresh-angle"
        ).value
    } else {
        angle = 0f
    }
    Icon(
        Icons.Filled.Refresh,
        contentDescription = null,
        modifier = Modifier.graphicsLayer { rotationZ = angle }
    )
}

@Composable
private fun WeatherStatusBar(state: WeatherUiState, onOpenCities: () -> Unit = {}) {
    val report = state.report
    TerminalStatusBar {
        // The left group yields to "Last Updated:" on the right: a long city name
        // truncates rather than wrapping the whole bar onto three lines.
        StatusBarStart {
            // ⎇ works like VS Code's branch switcher (Fase 10b): tapping the
            // city name opens cities.json. Small target, accepted with the
            // committente — the Cerca tab is the primary way in.
            StatusBarText(
                "⎇ ${report?.location?.city ?: "—"}",
                shrink = true,
                modifier = Modifier.clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.cd_open_cities)
                ) { onOpenCities() }
            )
            StatusBarDivider()
            StatusBarText(
                when {
                    state.error != null -> "api: ERR"
                    report != null -> "api: 200 OK"
                    else -> "api: …"
                },
                color = if (state.error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        StatusBarText(
            when {
                state.isLoading -> stringResource(R.string.status_syncing)
                report != null -> stringResource(
                    R.string.status_last_updated,
                    report.systemInfo.lastSync
                        .atZone(runCatching { ZoneId.of(report.location.timezone) }
                            .getOrDefault(ZoneId.systemDefault()))
                        .format(LastUpdated)
                )
                else -> stringResource(R.string.status_last_updated, "—")
            }
        )
    }
}

private val LastUpdated = DateTimeFormatter.ofPattern("HH:mm:ss")

/** The clock the README's own notes use — the same HH:mm as its footer. */
private val NoteTime = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Bottom room the canvas leaves for the floating FAB: 56dp of button + its 24dp
 * margin + a line of slack. Without it the tail of the document stops *underneath*
 * the FAB with no way to scroll it clear — the JSON's `system_info` and closing
 * brace, the README's last-sync footer. The FAB is the only screen element that
 * overlaps the canvas, so this padding lives here and not in [CodeCanvas].
 */
private val FabClearance = 96.dp

/**
 * The document shown in the canvas. Loading and errors are part of the fake file,
 * rendered as `//` comment lines above the last good JSON (which survives a failed
 * refresh). Comments and errors are code — English by design.
 */
private fun buildScreenLines(
    state: WeatherUiState,
    syntax: SyntaxColors,
    translate: (String) -> String,
    locale: Locale,
    displayOptions: DisplayOptions,
    resources: Resources
): List<CodeLine> = buildList {
    // The register rule (Fase 18): the `//` and the markers are the file's syntax
    // and never move; the sentence after each of them is the reader's. What stays
    // English on this screen is what stays English in the file — the `GET` line
    // and the `net::ERR_*` code, which are the useful form of those two facts.
    // `README.md` says both of them in prose, one tab away (Fase 17).
    fun note(id: Int, vararg args: Any) = "// " + resources.getString(id, *args)
    if (state.noLocation) {
        add(commentLine(note(R.string.note_no_location), syntax))
        add(commentLine(note(R.string.note_hint_search), syntax))
    } else if (state.acquiringFix) {
        add(commentLine("// gps: " + resources.getString(R.string.note_gps_acquiring), syntax))
    } else if (state.isLoading) {
        add(commentLine(note(R.string.note_fetching), syntax))
        add(commentLine("// GET https://api.open-meteo.com/v1/forecast", syntax))
    }
    state.error?.let { add(commentLine("// ERROR: ${it.terminalMessage}", syntax)) }
    // The document below is the last fetch that worked (Fase 17). `system_info`
    // carries the timestamp two screens down; up here it is the age, which is the
    // form of it a reader can act on.
    state.staleFor?.let {
        add(commentLine("// stale: " + resources.getString(R.string.note_stale, codeAge(it)), syntax))
    }
    state.error?.let { add(commentLine(note(R.string.note_hint_retry), syntax)) }
    state.report?.let {
        if (isNotEmpty()) add(CodeLine(AnnotatedString("")))
        addAll(buildJsonLines(it.toDisplayJson(translate, locale, displayOptions), syntax))
    }
}

/** `45m`, `3h`, `2d` — the comment channel's register, so English and compact. */
private fun codeAge(age: Duration): String {
    val minutes = age.toMinutes()
    return when {
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> "${age.toDays()}d"
    }
}

/**
 * The README.md tab: same states as the JSON, in the same place, in a different
 * language (Fase 17).
 *
 * A markdown file comments in HTML (`<!-- -->`) rather than `//`, and what goes
 * inside the comment is a SENTENCE. The JSON keeps
 * `// ERROR: net::ERR_INTERNET_DISCONNECTED — check your connection`, which is the
 * useful form for a file that is code; here the same fact is "no connection: the
 * weather could not be updated", because a reader of this document has not agreed to
 * learn Chrome's error names in order to be told the phone is offline. The `GET`
 * line has no prose equivalent at all and is simply not part of this file.
 */
private fun buildReadmeLines(
    state: WeatherUiState,
    syntax: SyntaxColors,
    resources: Resources,
    locale: Locale,
    displayOptions: DisplayOptions,
    skySummary: SkySummary? = null
): List<CodeLine> = buildList {
    fun note(text: String) = add(commentLine("<!-- $text -->", syntax))

    if (state.noLocation) {
        note(resources.getString(R.string.readme_note_no_location))
        note(resources.getString(R.string.readme_note_search_city))
    } else if (state.acquiringFix) {
        note(resources.getString(R.string.readme_note_gps))
    } else if (state.isLoading) {
        note(resources.getString(R.string.readme_note_loading))
    }
    state.error?.let { note(WeatherStateProse.error(resources, it)) }

    val report = state.report
    val zone = report?.let {
        runCatching { ZoneId.of(it.location.timezone) }.getOrDefault(ZoneId.systemDefault())
    }
    // Said before the document rather than after it: the reader is about to be given
    // a temperature, and how old it is changes what they do with it.
    if (report != null && zone != null && state.staleFor != null) {
        note(
            resources.getString(
                R.string.readme_note_stale,
                report.systemInfo.lastSync.atZone(zone).format(NoteTime),
                WeatherStateProse.age(resources, state.staleFor)
            )
        )
    }
    state.error?.let { note(resources.getString(R.string.readme_note_retry)) }

    if (report != null && zone != null) {
        if (isNotEmpty()) add(CodeLine(AnnotatedString("")))
        addAll(
            buildMarkdownLines(
                report.toReadmeMarkdown(
                    resources = resources,
                    translate = WeatherTranslations.translator(resources),
                    locale = locale,
                    options = displayOptions,
                    sky = skySummary,
                    // A recovered document is evaluated against the real present, not
                    // against the clock of the fetch that produced it: `## Status` asks
                    // "is anything coming", and asking it about a window that closed
                    // three hours ago answers "everything looks good" every time.
                    now = state.staleFor
                        ?.let { report.systemInfo.lastSync.plus(it).atZone(zone).toLocalDateTime() }
                        ?: report.location.localTime
                ),
                syntax
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 800)
@Composable
private fun WeatherScreenPreview() {
    TweatherTheme {
        WeatherScreen(
            state = WeatherUiState(report = sampleWeatherReport(), isLoading = false),
            onRefresh = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun WeatherScreenLoadingPreview() {
    TweatherTheme {
        WeatherScreen(state = WeatherUiState(isLoading = true), onRefresh = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 800)
@Composable
private fun WeatherScreenReadmePreview() {
    TweatherTheme {
        WeatherScreen(
            state = WeatherUiState(report = sampleWeatherReport(), isLoading = false),
            onRefresh = {},
            activeFile = MainEditorFile.README
        )
    }
}
