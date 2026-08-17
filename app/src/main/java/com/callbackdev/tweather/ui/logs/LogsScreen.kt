package com.callbackdev.tweather.ui.logs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.tweather.R
import com.callbackdev.tweather.data.local.ForecastDiff
import com.callbackdev.tweather.data.local.SnapshotDiff
import com.callbackdev.tweather.ui.components.CanvasLine
import com.callbackdev.tweather.ui.components.CodeCanvas
import com.callbackdev.tweather.ui.components.CodeLine
import com.callbackdev.tweather.ui.components.EditorTabs
import com.callbackdev.tweather.ui.components.StatusBarDivider
import com.callbackdev.tweather.ui.components.TerminalStatusBar
import com.callbackdev.tweather.ui.components.commentLine
import com.callbackdev.tweather.ui.theme.SyntaxColors
import com.callbackdev.tweather.ui.theme.TweatherTheme
import com.callbackdev.tweather.ui.weather.WeatherTranslations
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private const val HISTORY_FILE = "weather_history.diff"
private const val FORECAST_FILE = "weather_forecast.diff"

/**
 * Logs screen: two fake files behind a real editor tab bar (Fase 9h).
 *
 * - `weather_history.diff` (Fase 8): every fetch is a git-style commit (short
 *   hash, author `sys@tweather.app`, relative date) diffing observations — what
 *   actually changed since the previous fetch of the same city.
 * - `weather_forecast.diff` (Fase 9h): same commits, different question — how did
 *   the *prediction* for the same target date change between fetches. Per-date
 *   `---`/`+++` headers and `@@ tomorrow @@` hunks; sub-threshold model wiggle is
 *   filtered out by [ForecastDiff], so the file only contains real revisions.
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
    LogsScreen(commits = commits, revisions = revisions)
}

@Composable
fun LogsScreen(commits: List<CommitUi>, revisions: List<ForecastRevisionUi>) {
    val syntax = TweatherTheme.syntax
    val resources = LocalContext.current.resources
    val translate = remember(resources) { WeatherTranslations.valueTranslator(resources) }
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
    val lines = remember(commits, revisions, syntax, nowEpochSeconds, activeFile, translate) {
        if (activeFile == 0) buildLogLines(commits, syntax, nowEpochSeconds, translate)
        else buildForecastLines(
            revisions, syntax, nowEpochSeconds, ZoneId.systemDefault(), translate
        )
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            EditorTabs(
                fileNames = listOf(HISTORY_FILE, FORECAST_FILE),
                activeIndex = activeFile,
                onSelect = { activeFile = it }
            )
            CodeCanvas(
                lines = lines,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            )
            TerminalStatusBar {
                if (activeFile == 0) {
                    Text("⎇ history")
                    StatusBarDivider()
                    Text(stringResource(R.string.status_commits, commits.size))
                } else {
                    Text("⎇ forecast")
                    StatusBarDivider()
                    Text(stringResource(R.string.status_revisions, revisions.size))
                }
                Spacer(Modifier.weight(1f))
                Text("read-only")
            }
        }
    }
}

private fun buildLogLines(
    commits: List<CommitUi>,
    syntax: SyntaxColors,
    now: Long,
    translate: (String) -> String = { it }
): List<CanvasLine> {
    if (commits.isEmpty()) {
        return listOf(
            commentLine("// no commits yet", syntax),
            commentLine("// refresh weather_data.json to record the first one", syntax)
        )
    }
    return buildList {
        commits.forEachIndexed { index, commit ->
            if (index > 0) add(CodeLine(AnnotatedString("")))
            add(commitHeaderLine(commit.hash, commit.cityLabel, syntax))
            add(commentLine("Author: System <${commit.author}>", syntax))
            add(commentLine("Date:   ${relativeTime(commit.timestampEpochSeconds, now)}", syntax))
            add(commentLine("diff --git a/weather_data.json b/weather_data.json", syntax))
            if (commit.isInitial) {
                add(commentLine("new file mode 100644", syntax))
            }
            commit.lines.forEach { line -> add(diffLine(line.localized(translate), syntax)) }
        }
    }
}

private fun buildForecastLines(
    revisions: List<ForecastRevisionUi>,
    syntax: SyntaxColors,
    now: Long,
    zone: ZoneId,
    translate: (String) -> String = { it }
): List<CanvasLine> {
    if (revisions.isEmpty()) {
        return listOf(
            commentLine("// no forecast revisions yet", syntax),
            commentLine("// significant changes to upcoming forecasts land here", syntax)
        )
    }
    return buildList {
        revisions.forEachIndexed { index, revision ->
            if (index > 0) add(CodeLine(AnnotatedString("")))
            add(commitHeaderLine(revision.hash, revision.cityLabel, syntax))
            add(commentLine("Author: System <${revision.author}>", syntax))
            add(commentLine("Date:   ${relativeTime(revision.timestampEpochSeconds, now)}", syntax))
            revision.hunks.forEach { hunk ->
                val file = "forecast_${hunk.date}.json"
                val fetchTime = fetchTimeLabel(
                    revision.timestampEpochSeconds, revision.timestampEpochSeconds, zone
                )
                if (hunk.baselineEpochSeconds == null) {
                    add(commentLine("--- /dev/null", syntax))
                } else {
                    val baseTime = fetchTimeLabel(
                        hunk.baselineEpochSeconds, revision.timestampEpochSeconds, zone
                    )
                    add(commentLine("--- a/$file ($baseTime)", syntax))
                }
                add(commentLine("+++ b/$file ($fetchTime)", syntax))
                add(hunkHeaderLine(hunk.dayLabel, syntax))
                hunk.lines.forEach { line -> add(diffLine(line.localized(translate), syntax)) }
            }
        }
    }
}

/** Git colors hunk headers apart from the body; key-blue is our cyan. */
private fun hunkHeaderLine(dayLabel: String, syntax: SyntaxColors) = CodeLine(
    AnnotatedString("@@ $dayLabel @@", SpanStyle(color = syntax.key))
)

private val SameDayTime = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val OtherDayTime = DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.ENGLISH)

/**
 * `(12:04)` when the compared prediction is from the same local day as the fetch,
 * `(Aug 16 23:40)` when it is older — two forecasts hours apart read differently
 * from two a day apart, and a bare clock time would hide that.
 */
internal fun fetchTimeLabel(epochSeconds: Long, fetchEpochSeconds: Long, zone: ZoneId): String {
    val time = Instant.ofEpochSecond(epochSeconds).atZone(zone)
    val fetchDay = Instant.ofEpochSecond(fetchEpochSeconds).atZone(zone).toLocalDate()
    return if (time.toLocalDate() == fetchDay) time.format(SameDayTime)
    else time.format(OtherDayTime)
}

private fun commitHeaderLine(hash: String, cityLabel: String, syntax: SyntaxColors) = CodeLine(
    buildAnnotatedString {
        withStyle(SpanStyle(color = syntax.key)) { append("commit $hash") }
        withStyle(SpanStyle(color = syntax.comment)) { append(" [$cityLabel]") }
    }
)

/**
 * Weather DATA values localize at render time (app-wide l10n rule); everything
 * else in a diff line — keys, city names, compass points, clock times — is code
 * or proper nouns and passes through. Gated by key so a future snapshot value
 * that happens to collide with a translated word cannot be mistranslated.
 */
private fun SnapshotDiff.Line.localized(translate: (String) -> String): SnapshotDiff.Line =
    if (key == "status" || key.endsWith(".status") || key.endsWith(".moon_phase")) {
        copy(value = translate(value))
    } else {
        this
    }

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

/** Numbers render bare like in JSON, anything else quoted. */
private fun formatValue(value: String): String =
    if (value.toDoubleOrNull() != null) value else "\"$value\""

private fun AnnotatedString.Builder.appendValue(value: String, syntax: SyntaxColors) {
    if (value.toDoubleOrNull() != null) {
        withStyle(SpanStyle(color = syntax.number)) { append(value) }
    } else {
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
                    isInitial = false,
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
                    isInitial = true,
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
                            dayLabel = "tomorrow",
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
