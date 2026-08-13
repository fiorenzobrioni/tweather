package com.callbackdev.tweather.ui.logs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.tweather.R
import com.callbackdev.tweather.data.local.SnapshotDiff
import com.callbackdev.tweather.ui.components.CanvasLine
import com.callbackdev.tweather.ui.components.CodeCanvas
import com.callbackdev.tweather.ui.components.CodeLine
import com.callbackdev.tweather.ui.components.EditorTab
import com.callbackdev.tweather.ui.components.StatusBarDivider
import com.callbackdev.tweather.ui.components.TerminalStatusBar
import com.callbackdev.tweather.ui.components.commentLine
import com.callbackdev.tweather.ui.theme.SyntaxColors
import com.callbackdev.tweather.ui.theme.TweatherTheme

/**
 * Logs screen: the fake file `weather_history.diff`. Every fetch is a git-style
 * commit (short hash, author `sys@tweather.app`, relative date) followed by the
 * diff against the previous fetch of the same city — old values as `-` lines in
 * red, new values as `+` in green, untouched keys as context. Git output is code:
 * it stays English by design.
 */
@Composable
fun LogsScreen(viewModel: LogsViewModel = viewModel(factory = LogsViewModel.Factory)) {
    val commits by viewModel.commits.collectAsStateWithLifecycle()
    LogsScreen(commits = commits)
}

@Composable
fun LogsScreen(commits: List<CommitUi>) {
    val syntax = TweatherTheme.syntax
    val lines = remember(commits, syntax) { buildLogLines(commits, syntax) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            EditorTab(fileName = "weather_history.diff")
            CodeCanvas(
                lines = lines,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            )
            TerminalStatusBar {
                Text("⎇ history")
                StatusBarDivider()
                Text(stringResource(R.string.status_commits, commits.size))
                Spacer(Modifier.weight(1f))
                Text("read-only")
            }
        }
    }
}

private fun buildLogLines(commits: List<CommitUi>, syntax: SyntaxColors): List<CanvasLine> {
    if (commits.isEmpty()) {
        return listOf(
            commentLine("// no commits yet", syntax),
            commentLine("// refresh weather_data.json to record the first one", syntax)
        )
    }
    val now = System.currentTimeMillis() / 1000
    return buildList {
        commits.forEachIndexed { index, commit ->
            if (index > 0) add(CodeLine(AnnotatedString("")))
            add(commitHeaderLine(commit, syntax))
            add(commentLine("Author: System <${commit.author}>", syntax))
            add(commentLine("Date:   ${relativeTime(commit.timestampEpochSeconds, now)}", syntax))
            add(commentLine("diff --git a/weather_data.json b/weather_data.json", syntax))
            if (commit.isInitial) {
                add(commentLine("new file mode 100644", syntax))
            }
            commit.lines.forEach { line -> add(diffLine(line, syntax)) }
        }
    }
}

private fun commitHeaderLine(commit: CommitUi, syntax: SyntaxColors) = CodeLine(
    buildAnnotatedString {
        withStyle(SpanStyle(color = syntax.key)) { append("commit ${commit.hash}") }
        withStyle(SpanStyle(color = syntax.comment)) { append(" [${commit.cityLabel}]") }
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

/** Whole `+`/`-` line in the diff color over a faint tint of the same color. */
private fun signedLine(
    sign: String,
    line: SnapshotDiff.Line,
    color: androidx.compose.ui.graphics.Color
): CodeLine = CodeLine(
    AnnotatedString(
        "$sign \"${line.key}\": ${formatValue(line.value)}",
        SpanStyle(color = color, background = color.copy(alpha = 0.12f))
    ),
    indent = 1
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
            )
        )
    }
}
