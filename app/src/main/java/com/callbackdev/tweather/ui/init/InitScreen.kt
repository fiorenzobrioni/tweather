package com.callbackdev.tweather.ui.init

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.callbackdev.tweather.R
import com.callbackdev.tweather.ui.components.CanvasLine
import com.callbackdev.tweather.ui.components.CodeCanvas
import com.callbackdev.tweather.ui.components.CodeLine
import com.callbackdev.tweather.ui.components.EditorTabs
import com.callbackdev.tweather.ui.components.StatusBarDivider
import com.callbackdev.tweather.ui.components.TerminalStatusBar
import com.callbackdev.tweather.ui.theme.SyntaxColors
import com.callbackdev.tweather.ui.theme.TweatherTheme

/**
 * `$ tweather init` — the first run, since Fase 14c. It exists because Fase 14b
 * stopped inventing a saved city: the app has to ask for one, and the honest empty
 * editor behind this screen must be somewhere the user CHOSE to be, not the first
 * thing a fresh install shows.
 *
 * Deliberately not a carousel. Onboarding slides are the most skipped surface in
 * mobile, and a definition offered before you have seen the thing it defines does
 * not stick — so this screen only does the one job the app cannot start without
 * (a location), and the vocabulary lives in `HELP.md`, which is there whenever the
 * question actually comes up.
 *
 * Localized, unlike the terminal output elsewhere in the app: the same exception
 * `README.md` already makes. The fiction is carried by the shape — the prompt, the
 * `>` choices, the `#` notes — not by the language, and this is the one screen whose
 * whole purpose is being understood by someone who does not read `git` for a living.
 * `$ tweather init` itself is a command, so it stays as it is.
 */
@Composable
fun InitScreen(
    onUseGps: () -> Unit,
    onSearchCity: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    permissionDenied: Boolean = false
) {
    val syntax = TweatherTheme.syntax
    val lines = buildInitLines(
        syntax = syntax,
        intro = stringResource(R.string.init_intro),
        privacy = stringResource(R.string.init_privacy),
        gps = stringResource(R.string.init_option_gps),
        gpsNote = stringResource(R.string.init_option_gps_note),
        search = stringResource(R.string.init_option_search),
        searchNote = stringResource(R.string.init_option_search_note),
        skip = stringResource(R.string.init_option_skip),
        skipNote = stringResource(R.string.init_option_skip_note),
        denied = if (permissionDenied) stringResource(R.string.init_permission_denied) else null,
        onUseGps = onUseGps,
        onSearchCity = onSearchCity,
        onSkip = onSkip
    )
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            EditorTabs(fileNames = listOf(SetupFile), activeIndex = 0, onSelect = {})
            CodeCanvas(
                lines = lines,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
                showIndentGuides = false
            )
            TerminalStatusBar {
                Text("⎇ setup")
                StatusBarDivider()
                Text("1/1")
            }
        }
    }
}

/** The "file" this screen opens: a session, not a document — hence the shell name. */
internal const val SetupFile = "tweather.sh"

internal fun buildInitLines(
    syntax: SyntaxColors,
    intro: String,
    privacy: String,
    gps: String,
    gpsNote: String,
    search: String,
    searchNote: String,
    skip: String,
    skipNote: String,
    denied: String?,
    onUseGps: () -> Unit,
    onSearchCity: () -> Unit,
    onSkip: () -> Unit
): List<CanvasLine> = buildList {
    add(
        CodeLine(
            buildAnnotatedString {
                withStyle(SpanStyle(color = syntax.comment)) { append("$ ") }
                withStyle(SpanStyle(color = syntax.string)) { append("tweather init") }
            }
        )
    )
    add(blank())
    add(comment(intro, syntax))
    add(comment(privacy, syntax))
    denied?.let {
        add(blank())
        add(CodeLine(AnnotatedString(it, SpanStyle(color = syntax.diffDel))))
    }
    option(gps, gpsNote, syntax, onUseGps)
    option(search, searchNote, syntax, onSearchCity)
    option(skip, skipNote, syntax, onSkip)
}

/** `> choice` plus its `#` note: one tap target, the note explains what it costs. */
private fun MutableList<CanvasLine>.option(
    label: String,
    note: String,
    syntax: SyntaxColors,
    onClick: () -> Unit
) {
    add(blank())
    add(
        CodeLine(
            buildAnnotatedString {
                withStyle(SpanStyle(color = syntax.comment)) { append("> ") }
                withStyle(SpanStyle(color = syntax.key)) { append(label) }
            },
            onClick = onClick,
            onClickLabel = label
        )
    )
    add(comment(note, syntax, indent = 1))
}

private fun comment(text: String, syntax: SyntaxColors, indent: Int = 0): CodeLine =
    CodeLine(AnnotatedString("# $text", SpanStyle(color = syntax.comment)), indent)

private fun blank(): CodeLine = CodeLine(AnnotatedString(""))

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun InitScreenPreview() {
    TweatherTheme {
        InitScreen(onUseGps = {}, onSearchCity = {}, onSkip = {})
    }
}
