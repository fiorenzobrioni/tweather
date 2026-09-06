package com.callbackdev.tweather.ui.init

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.callbackdev.tweather.R
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
 * Since Fase 27 the transcript **prints itself** rather than being already there:
 * see [TypedTranscript] for the two speeds, the tap that ends it and the two
 * accessibility switches that never start it. The four `#` lines above the choices
 * grew with it — a session that takes a second and a half to print can afford to
 * say what the app *is* before saying what it needs, and a still screen could not.
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
    val script = buildInitScript(
        syntax = syntax,
        intro = stringResource(R.string.init_intro),
        files = stringResource(R.string.init_files),
        privacy = stringResource(R.string.init_privacy),
        ask = stringResource(R.string.init_ask),
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
        // The insets the workspace has applied since it existed, and this screen
        // never did: it is not a Scaffold and has no nav bar, so the tab strip sat
        // under the clock and the terminal bar under the gesture pill (device,
        // Fase 27b). Same `statusBarsPadding()` as the workspace's own root Column.
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            EditorTabs(fileNames = listOf(SetupFile), activeIndex = 0, onSelect = {})
            TypedTranscript(script = script, modifier = Modifier.weight(1f))
            TerminalStatusBar(
                // Bottom-most element of this screen, unlike in the workspace where
                // EditorNavBar is: so it takes the gesture bar's inset the way that
                // bar does — the strip's colour reaches the edge, the text sits above
                // the pill. Painted here because the padding has to be INSIDE the
                // background, and the component applies its own after the modifier.
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .navigationBarsPadding()
            ) {
                Text("⎇ setup")
                StatusBarDivider()
                Text("1/1")
            }
        }
    }
}

/** The "file" this screen opens: a session, not a document — hence the shell name. */
internal const val SetupFile = "tweather.sh"

/**
 * The transcript as a pure value, with the time each line takes to arrive.
 *
 * The command is the only line typed at a hand's speed; everything else is the
 * program answering. The beats are where a real session breathes — after the
 * command, and between one offered answer and the next.
 */
internal fun buildInitScript(
    syntax: SyntaxColors,
    intro: String,
    files: String,
    privacy: String,
    ask: String,
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
): List<TypedLine> = buildList {
    add(
        TypedLine(
            CodeLine(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = syntax.comment)) { append("$ ") }
                    withStyle(SpanStyle(color = syntax.string)) { append("tweather init") }
                }
            ),
            msPerChar = PromptMsPerChar,
            pauseAfterMs = PromptPauseMs
        )
    )
    add(blank())
    add(printed(comment(intro, syntax)))
    add(printed(comment(files, syntax)))
    add(printed(comment(privacy, syntax)))
    add(printed(comment(ask, syntax), pauseAfterMs = StanzaPauseMs))
    denied?.let {
        add(blank())
        add(
            printed(
                CodeLine(AnnotatedString(it, SpanStyle(color = syntax.diffDel))),
                pauseAfterMs = StanzaPauseMs
            )
        )
    }
    option(gps, gpsNote, syntax, onUseGps)
    option(search, searchNote, syntax, onSearchCity)
    option(skip, skipNote, syntax, onSkip)
}

/** `> choice` plus its `#` note: one tap target, the note explains what it costs. */
private fun MutableList<TypedLine>.option(
    label: String,
    note: String,
    syntax: SyntaxColors,
    onClick: () -> Unit
) {
    add(blank())
    add(
        printed(
            CodeLine(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = syntax.comment)) { append("> ") }
                    withStyle(SpanStyle(color = syntax.key)) { append(label) }
                },
                onClick = onClick,
                onClickLabel = label
            )
        )
    )
    add(printed(comment(note, syntax, indent = 1), pauseAfterMs = StanzaPauseMs))
}

private fun printed(line: CodeLine, pauseAfterMs: Int = LinePauseMs): TypedLine =
    TypedLine(line, msPerChar = PrintMsPerChar, pauseAfterMs = pauseAfterMs)

private fun comment(text: String, syntax: SyntaxColors, indent: Int = 0): CodeLine =
    CodeLine(AnnotatedString("# $text", SpanStyle(color = syntax.comment)), indent)

private fun blank(): TypedLine = TypedLine(CodeLine(AnnotatedString("")), pauseAfterMs = 0)

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun InitScreenPreview() {
    TweatherTheme {
        InitScreen(onUseGps = {}, onSearchCity = {}, onSkip = {})
    }
}
