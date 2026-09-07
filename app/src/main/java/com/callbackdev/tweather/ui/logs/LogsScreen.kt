package com.callbackdev.tweather.ui.logs

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.tweather.R
import com.callbackdev.tweather.data.UnitSettings
import com.callbackdev.tweather.data.local.ForecastDiff
import com.callbackdev.tweather.data.local.SnapshotDiff
import com.callbackdev.tweather.data.local.WeatherSnapshots
import com.callbackdev.tweather.ui.components.CanvasLine
import com.callbackdev.tweather.ui.components.CodeCanvas
import com.callbackdev.tweather.ui.components.CodeLine
import com.callbackdev.tweather.ui.components.EditorTabs
import com.callbackdev.tweather.ui.components.StatusBarDivider
import com.callbackdev.tweather.ui.components.TerminalStatusBar
import com.callbackdev.tweather.ui.components.commentLine
import com.callbackdev.tweather.ui.theme.SyntaxColors
import com.callbackdev.tweather.ui.theme.TweatherTheme
import com.callbackdev.tweather.ui.theme.editorBorder
import com.callbackdev.tweather.ui.weather.WeatherTranslations
import com.callbackdev.tweather.ui.weather.shortName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * `history.diff` and `forecast.diff`, shortened from `weather_history.diff` and
 * `weather_forecast.diff` in Fase 16f.
 *
 * The `weather_` prefix was the only one in the app — `cities.json`,
 * `settings.config`, `alerts.rules`, `sky.crontab`, `HELP.md` all name their subject
 * and nothing else — and inside a weather app's Logs tab it was saying the one thing
 * the reader already knew. It also cost 16 characters the strip did not have: with
 * three files the third tab sat entirely off-screen at every phone width, which is
 * how a file goes undiscovered. Measured: with the short names all three are fully
 * visible down to 320dp.
 */
private const val HISTORY_FILE = "history.diff"
private const val FORECAST_FILE = "forecast.diff"

/**
 * Fase 16e. Not a `.diff`: this one records outcomes, not changes, and calling it a
 * diff would be the same kind of lie the crontab avoided.
 */
private const val SKY_RUNS_FILE = "sky_runs.log"

/** What `history.diff` is a diff OF — the document the main editor tab renders. */
private const val WEATHER_FILE = "weather_data.json"

/**
 * Logs screen: two fake files behind a real editor tab bar (Fase 9h).
 *
 * - `history.diff` (Fase 8): every fetch is a git-style commit (short
 *   hash, author `sys@tweather.app`, relative date) diffing observations — what
 *   actually changed since the previous fetch of the same city.
 * - `forecast.diff` (Fase 9h): same commits, different question — how did
 *   the *prediction* for the same target date change between fetches. Per-date
 *   `---`/`+++` headers and one `@@ Thu 20 Aug @@` hunk per target day;
 *   sub-threshold model wiggle is filtered out by [ForecastDiff], so the file only
 *   contains real revisions.
 *
 * L10n follows the app-wide rule (decided with the committente, post-9h): the git
 * format is code and stays English — hashes, `Author:`/`Date:`, `diff`/`---`/`@@`
 * headers, JSON keys — while the weather DATA values inside `±`/context lines
 * (conditions, moon phases) localize at render time via [WeatherTranslations],
 * exactly like the main screen, the widget and the notifications. The Room
 * snapshots stay English so diffs never churn on a language change.
 */
@Composable
fun LogsScreen(viewModel: LogsViewModel = viewModel(factory = LogsViewModel.Factory)) {
    val commits by viewModel.commits.collectAsStateWithLifecycle()
    val revisions by viewModel.revisions.collectAsStateWithLifecycle()
    val skyRuns by viewModel.skyRuns.collectAsStateWithLifecycle()
    val skyEnabled by viewModel.skyEnabled.collectAsStateWithLifecycle()
    val units by viewModel.units.collectAsStateWithLifecycle()
    LogsScreen(
        commits = commits,
        revisions = revisions,
        skyRuns = skyRuns,
        skyEnabled = skyEnabled,
        units = units
    )
}

@Composable
fun LogsScreen(
    commits: List<CommitUi>,
    revisions: List<ForecastRevisionUi>,
    skyRuns: List<SkyRunsLog.Row> = emptyList(),
    skyEnabled: Boolean = false,
    units: UnitSettings = UnitSettings()
) {
    val syntax = TweatherTheme.syntax
    val resources = LocalContext.current.resources
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val translate = remember(resources) { WeatherTranslations.valueTranslator(resources) }
    // Language AND units are render-time (see `rendered`): the snapshots stay
    // canonical so a diff never churns when either setting changes.
    val render: (List<SnapshotDiff.Line>) -> List<SnapshotDiff.Line> =
        remember(units, translate) { { it.rendered(units, translate) } }
    var activeFile by rememberSaveable { mutableIntStateOf(0) }
    // Relative dates rot while the screen sits open (commits can be hours apart),
    // so the clock re-ticks every minute — only while this composable is on screen
    // AND the app is foregrounded: repeatOnLifecycle parks the loop past ON_STOP
    // (leaving the app on this tab would otherwise keep ticking in the cached
    // process) and its restart re-clocks immediately on the way back.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val nowEpochSeconds by produceState(System.currentTimeMillis() / 1000, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis() / 1000
                delay(60_000)
            }
        }
    }
    val files = if (skyEnabled) {
        listOf(HISTORY_FILE, FORECAST_FILE, SKY_RUNS_FILE)
    } else {
        listOf(HISTORY_FILE, FORECAST_FILE)
    }
    // A tab that no longer exists cannot stay selected: switching the module off
    // while sitting on its file must not leave the strip pointing at nothing.
    val active = activeFile.coerceAtMost(files.lastIndex)
    // `resources` joins the remember keys: a per-app language change recreates the
    // activity with new resources, and the file has to be rebuilt in the language
    // it is now being read in.
    val lines = remember(
        commits, revisions, skyRuns, syntax, nowEpochSeconds, active, render, resources, locale
    ) {
        when (active) {
            0 -> buildLogLines(
                commits, syntax, nowEpochSeconds, ZoneId.systemDefault(), resources, locale,
                render
            )
            1 -> buildForecastLines(
                revisions, syntax, nowEpochSeconds, ZoneId.systemDefault(), resources, locale,
                render
            )
            else -> SkyRunsLog.build(skyRuns, ZoneId.systemDefault(), syntax, resources)
        }
    }
    // One scroll position per file: switching tab must not land mid-file because
    // the OTHER diff was scrolled there.
    val historyScroll = rememberLazyListState()
    val forecastScroll = rememberLazyListState()
    val skyScroll = rememberLazyListState()
    val canvasScroll = when (active) {
        0 -> historyScroll
        1 -> forecastScroll
        else -> skyScroll
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            EditorTabs(
                fileNames = files,
                activeIndex = active,
                onSelect = { activeFile = it }
            )
            Box(Modifier.weight(1f)) {
                CodeCanvas(
                    lines = lines,
                    state = canvasScroll,
                    modifier = Modifier.fillMaxSize()
                )
                BackToTop(
                    state = canvasScroll,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                )
            }
            TerminalStatusBar {
                when (active) {
                    0 -> {
                        Text("⎇ history")
                        StatusBarDivider()
                        Text(count(R.plurals.status_commits, commits.size))
                    }
                    1 -> {
                        Text("⎇ forecast")
                        StatusBarDivider()
                        Text(count(R.plurals.status_revisions, revisions.size))
                    }
                    else -> {
                        Text("⎇ sky")
                        StatusBarDivider()
                        Text(count(R.plurals.status_sky_runs, skyRuns.size))
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("read-only")
            }
        }
    }
}

/**
 * `1 revision` / `2 revisions` — a plural, not a format string (Fase 28). The status
 * bar counted with `%1$d revisions` and said `1 revisions` in English and
 * `1 revisioni` in Italian. `commit` and `run` are loanwords Italian does not
 * inflect, so their two forms are the same word; that is a fact about the language,
 * not a reason to keep the whole strip on the wrong kind of resource.
 */
@Composable
private fun count(@PluralsRes id: Int, quantity: Int): String =
    LocalContext.current.resources.getQuantityString(id, quantity, quantity)

/**
 * Floating "back to top" for long diffs: appears once the file is scrolled past
 * roughly a screenful. Rendered as text like every other control (`↑ top`) in a
 * bordered chip — no glow, that stays exclusive to the refresh FAB.
 */
@Composable
private fun BackToTop(state: LazyListState, modifier: Modifier = Modifier) {
    val visible by remember(state) { derivedStateOf { state.firstVisibleItemIndex > 4 } }
    val scope = rememberCoroutineScope()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Text(
            text = "↑ top",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .editorBorder()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.shapes.small
                )
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.cd_back_to_top)
                ) { scope.launch { state.animateScrollToItem(0) } }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

private fun buildLogLines(
    commits: List<CommitUi>,
    syntax: SyntaxColors,
    now: Long,
    zone: ZoneId,
    resources: Resources,
    locale: Locale,
    render: (List<SnapshotDiff.Line>) -> List<SnapshotDiff.Line> = { it }
): List<CanvasLine> {
    if (commits.isEmpty()) {
        // The marker is the file's syntax and stays; the sentence after it is
        // the one the reader has to understand (VISION §1.3, Fase 18).
        return listOf(
            commentLine("// " + resources.getString(R.string.note_no_commits), syntax),
            commentLine("// " + resources.getString(R.string.note_first_commit), syntax)
        )
    }
    return buildList {
        commits.forEachIndexed { index, commit ->
            if (index > 0) add(CodeLine(AnnotatedString("")))
            add(commitHeaderLine(commit.hash, commit.cityLabel, syntax))
            add(commentLine("Author: System <${commit.author}>", syntax))
            add(commentLine("Date:   ${relativeTime(commit.timestampEpochSeconds, now)}", syntax))
            // Weather CI (Fase 11): user rules that fired on this data — only the
            // fired ones, a ✓ per silent rule would be pure noise
            commit.firedRules.forEach { name ->
                add(
                    CodeLine(
                        AnnotatedString("✓ rule \"$name\" fired", SpanStyle(color = syntax.diffAdd))
                    )
                )
            }
            // The sky module's check lines (Fase 16e), from the same commit row and
            // the same store `sky_runs.log` reads: two files, one truth. Only the
            // jobs this fetch was the first to see as past — a ✓ for every job that
            // did not run would be the noise the fired-rules line already avoids.
            commit.skyRuns.forEach { run ->
                add(
                    CodeLine(
                        AnnotatedString(
                            "${skyGlyph(run)} ${run.jobId} ${skyWord(run)}${skyEvidence(run)}",
                            SpanStyle(color = skyColor(run, syntax))
                        )
                    )
                )
            }
            addAll(
                fileHeaderLines(
                    file = WEATHER_FILE,
                    baselineEpochSeconds = commit.baselineEpochSeconds,
                    fetchEpochSeconds = commit.timestampEpochSeconds,
                    zone = zone,
                    locale = locale,
                    syntax = syntax
                )
            )
            render(commit.lines).forEach { line -> add(diffLine(line, syntax)) }
        }
    }
}

private fun buildForecastLines(
    revisions: List<ForecastRevisionUi>,
    syntax: SyntaxColors,
    now: Long,
    zone: ZoneId,
    resources: Resources,
    locale: Locale,
    render: (List<SnapshotDiff.Line>) -> List<SnapshotDiff.Line> = { it }
): List<CanvasLine> {
    if (revisions.isEmpty()) {
        return listOf(
            commentLine("// " + resources.getString(R.string.note_no_revisions), syntax),
            commentLine("// " + resources.getString(R.string.note_revisions_land), syntax)
        )
    }
    return buildList {
        revisions.forEachIndexed { index, revision ->
            if (index > 0) add(CodeLine(AnnotatedString("")))
            add(commitHeaderLine(revision.hash, revision.cityLabel, syntax))
            add(commentLine("Author: System <${revision.author}>", syntax))
            add(commentLine("Date:   ${relativeTime(revision.timestampEpochSeconds, now)}", syntax))
            revision.hunks.forEach { hunk ->
                addAll(
                    fileHeaderLines(
                        file = "forecast_${hunk.date}.json",
                        baselineEpochSeconds = hunk.baselineEpochSeconds,
                        fetchEpochSeconds = revision.timestampEpochSeconds,
                        zone = zone,
                        locale = locale,
                        syntax = syntax
                    )
                )
                add(hunkHeaderLine(hunk.date, locale, syntax))
                render(hunk.lines).forEach { line -> add(diffLine(line, syntax)) }
            }
        }
    }
}

/**
 * The `---`/`+++` pair both diff files open a file with: the same name twice, and
 * beside each side the fetch that produced it.
 *
 *     --- a/weather_data.json (11:00)
 *     +++ b/weather_data.json (14:30)
 *
 * `forecast.diff` has been written this way since Fase 9h; `history.diff` joined it
 * in Fase 28c, on the committente's reading, replacing a bare
 * `diff --git a/weather_data.json b/weather_data.json`. That line named the file and
 * said nothing else, while the two TIMES are the half of a comparison the reader
 * could not otherwise reach: `history.diff` diffs against the previous commit of the
 * SAME city, which with two cities interleaved is not the row above — it can be three
 * hours back while the row above is fifteen minutes old. The `Date:` line has only
 * ever spoken about the near side.
 *
 * A first appearance is `--- /dev/null`, git's own way of saying new file, which is
 * why `new file mode 100644` left with `diff --git`: it belongs to that line's
 * extended header block, and alone it would be a fragment of a grammar the file no
 * longer speaks.
 */
private fun fileHeaderLines(
    file: String,
    baselineEpochSeconds: Long?,
    fetchEpochSeconds: Long,
    zone: ZoneId,
    locale: Locale,
    syntax: SyntaxColors
): List<CanvasLine> = buildList {
    if (baselineEpochSeconds == null) {
        add(commentLine("--- /dev/null", syntax))
    } else {
        val baseTime = fetchTimeLabel(baselineEpochSeconds, fetchEpochSeconds, zone, locale)
        add(commentLine("--- a/$file ($baseTime)", syntax))
    }
    val fetchTime = fetchTimeLabel(fetchEpochSeconds, fetchEpochSeconds, zone, locale)
    add(commentLine("+++ b/$file ($fetchTime)", syntax))
}

/**
 * `@@ Thu 20 Aug @@` — git colors hunk headers apart from the body, and key-blue is
 * our cyan.
 *
 * **The day is NAMED, not placed** (Fase 28). The header used to read
 * `@@ tomorrow @@` / `@@ in 2 days @@`, which was true of the fetch that wrote it and
 * of nothing after: scrolling the file you met the same three words on every commit,
 * each meaning a different day, and two revisions OF THE SAME DAY — the one thing
 * this file exists to show — were indistinguishable by their headers. A day the
 * reader can name is the whole point of a section header.
 *
 * The weekday localizes, like every other day name in the app (Fase 18: data
 * localizes, `@@` does not). The ISO date is still one line above, in the `---`/`+++`
 * pair, for anyone who wants it exact.
 */
private fun hunkHeaderLine(date: String, locale: Locale, syntax: SyntaxColors): CodeLine {
    val day = runCatching { LocalDate.parse(date) }.getOrNull()
    val label = day?.let { "${it.dayOfWeek.shortName(locale)} ${it.format(dayAndMonth(locale))}" }
        ?: date
    return CodeLine(AnnotatedString("@@ $label @@", SpanStyle(color = syntax.key)))
}

/** `20 Aug` / `20 ago` — the month localizes with the weekday beside it. */
private fun dayAndMonth(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", locale)

private val SameDayTime = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

/**
 * `(12:04)` when the compared prediction is from the same local day as the fetch,
 * `(16 ago 23:40)` when it is older — two forecasts hours apart read differently
 * from two a day apart, and a bare clock time would hide that.
 *
 * The clock is digits and takes [Locale.ENGLISH] to stay `HH:mm` everywhere; the
 * MONTH is a name and follows the reader, like the weekday in the hunk header four
 * lines below it (Fase 28). Until then it was pinned English, so an Italian reader
 * got `Aug 5` above a `@@ Gio 20 ago @@` — the half-finished translation the register
 * rule exists to prevent.
 */
internal fun fetchTimeLabel(
    epochSeconds: Long,
    fetchEpochSeconds: Long,
    zone: ZoneId,
    locale: Locale = Locale.ENGLISH
): String {
    val time = Instant.ofEpochSecond(epochSeconds).atZone(zone)
    val fetchDay = Instant.ofEpochSecond(fetchEpochSeconds).atZone(zone).toLocalDate()
    return if (time.toLocalDate() == fetchDay) time.format(SameDayTime)
    else time.format(DateTimeFormatter.ofPattern("d MMM HH:mm", locale))
}

private fun commitHeaderLine(hash: String, cityLabel: String, syntax: SyntaxColors) = CodeLine(
    buildAnnotatedString {
        withStyle(SpanStyle(color = syntax.key)) { append("commit $hash") }
        withStyle(SpanStyle(color = syntax.comment)) { append(" [$cityLabel]") }
    }
)

private fun diffLine(line: SnapshotDiff.Line, syntax: SyntaxColors): CodeLine = when (line.type) {
    SnapshotDiff.Type.CONTEXT -> CodeLine(
        buildAnnotatedString {
            withStyle(SpanStyle(color = syntax.comment)) { append("  ") }
            withStyle(SpanStyle(color = syntax.key)) { append("\"${line.key}\"") }
            withStyle(SpanStyle(color = syntax.comment)) { append(": ") }
            appendValue(line.value, syntax)
        },
        indent = 1
    )
    SnapshotDiff.Type.ADDED -> signedLine("+", line, syntax.diffAdd)
    SnapshotDiff.Type.REMOVED -> signedLine("-", line, syntax.diffDel)
}

/** Whole `+`/`-` line in the diff color over a faint tint of the same color; the
 * gutter number picks up the same tint like in the mockup. */
private fun signedLine(
    sign: String,
    line: SnapshotDiff.Line,
    color: androidx.compose.ui.graphics.Color
): CodeLine = CodeLine(
    AnnotatedString(
        "$sign \"${line.key}\": ${formatValue(line.value)}",
        SpanStyle(color = color, background = color.copy(alpha = 0.12f))
    ),
    indent = 1,
    gutterColor = color
)

/**
 * Numbers and [WeatherSnapshots.NullValue] render bare like in JSON, anything else
 * quoted. The null matters: a fetch above the Arctic circle in June has no sunrise,
 * and quoting it would print `"astronomical.sunrise": "null"` — a time whose value
 * is the word null, rather than the absence `weather_data.json` writes as `null` in
 * the very same place.
 */
private fun formatValue(value: String): String = when {
    value == WeatherSnapshots.NullValue -> value
    value.toDoubleOrNull() != null -> value
    else -> "\"$value\""
}

private fun AnnotatedString.Builder.appendValue(value: String, syntax: SyntaxColors) {
    when {
        // JSON's `null` is neither a number nor a string; the file paints it in the
        // gray it gives braces and comments, which is where the eye already reads
        // "structure, not data".
        value == WeatherSnapshots.NullValue ->
            withStyle(SpanStyle(color = syntax.comment)) { append(value) }
        value.toDoubleOrNull() != null ->
            withStyle(SpanStyle(color = syntax.number)) { append(value) }
        else ->
            withStyle(SpanStyle(color = syntax.string)) { append("\"$value\"") }
    }
}

/** Git-style relative date; git speaks English, so does this. */
internal fun relativeTime(epochSeconds: Long, nowEpochSeconds: Long): String {
    val delta = (nowEpochSeconds - epochSeconds).coerceAtLeast(0)
    return when {
        delta < 60 -> "just now"
        delta < 3_600 -> "${delta / 60} ${if (delta / 60 == 1L) "min" else "mins"} ago"
        delta < 86_400 -> "${delta / 3_600} ${if (delta / 3_600 == 1L) "hour" else "hours"} ago"
        else -> "${delta / 86_400} ${if (delta / 86_400 == 1L) "day" else "days"} ago"
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 640)
@Composable
private fun LogsScreenPreview() {
    TweatherTheme {
        LogsScreen(
            commits = listOf(
                CommitUi(
                    hash = "a1b2c3d",
                    cityLabel = "Milan, Lombardy",
                    author = "sys@tweather.app",
                    timestampEpochSeconds = System.currentTimeMillis() / 1000 - 600,
                    baselineEpochSeconds = System.currentTimeMillis() / 1000 - 7_800,
                    lines = listOf(
                        SnapshotDiff.Line(SnapshotDiff.Type.CONTEXT, "location", "Milan, Lombardy"),
                        SnapshotDiff.Line(SnapshotDiff.Type.REMOVED, "current.temp_c", "18.2"),
                        SnapshotDiff.Line(SnapshotDiff.Type.ADDED, "current.temp_c", "19.5"),
                        SnapshotDiff.Line(SnapshotDiff.Type.CONTEXT, "current.humidity_pct", "54"),
                        SnapshotDiff.Line(SnapshotDiff.Type.REMOVED, "current.status", "Partly Cloudy ⛅"),
                        SnapshotDiff.Line(SnapshotDiff.Type.ADDED, "current.status", "Clear ☀️")
                    )
                ),
                CommitUi(
                    hash = "9f8e7d6",
                    cityLabel = "Milan, Lombardy",
                    author = "sys@tweather.app",
                    timestampEpochSeconds = System.currentTimeMillis() / 1000 - 7_800,
                    baselineEpochSeconds = null,
                    lines = listOf(
                        SnapshotDiff.Line(SnapshotDiff.Type.ADDED, "location", "Milan, Lombardy"),
                        SnapshotDiff.Line(SnapshotDiff.Type.ADDED, "current.temp_c", "18.2")
                    )
                )
            ),
            revisions = listOf(
                ForecastRevisionUi(
                    hash = "a1b2c3d",
                    cityLabel = "Milan, Lombardy",
                    author = "sys@tweather.app",
                    timestampEpochSeconds = System.currentTimeMillis() / 1000 - 600,
                    hunks = listOf(
                        ForecastDiff.Hunk(
                            date = "2026-08-18",
                            baselineEpochSeconds = System.currentTimeMillis() / 1000 - 15_000,
                            lines = listOf(
                                SnapshotDiff.Line(SnapshotDiff.Type.REMOVED, "precip_pct", "20"),
                                SnapshotDiff.Line(SnapshotDiff.Type.ADDED, "precip_pct", "70"),
                                SnapshotDiff.Line(SnapshotDiff.Type.REMOVED, "high_c", "31.0"),
                                SnapshotDiff.Line(SnapshotDiff.Type.ADDED, "high_c", "27.4"),
                                SnapshotDiff.Line(SnapshotDiff.Type.CONTEXT, "status", "Rain 🌧️")
                            )
                        )
                    )
                )
            )
        )
    }
}
