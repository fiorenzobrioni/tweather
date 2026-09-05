package com.callbackdev.tweather.ui.sky

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import com.callbackdev.tweather.R
import com.callbackdev.tweather.domain.sky.SkyJob
import com.callbackdev.tweather.domain.sky.SkyJobCatalog
import com.callbackdev.tweather.ui.components.CanvasLine
import com.callbackdev.tweather.ui.components.CodeCanvas
import com.callbackdev.tweather.ui.components.CodeLine
import com.callbackdev.tweather.ui.components.EditorOptions
import com.callbackdev.tweather.ui.components.LocalEditorOptions
import com.callbackdev.tweather.ui.components.StatusBarDivider
import com.callbackdev.tweather.ui.components.StatusBarStart
import com.callbackdev.tweather.ui.components.StatusBarText
import com.callbackdev.tweather.ui.components.TerminalStatusBar
import com.callbackdev.tweather.ui.theme.SyntaxColors
import com.callbackdev.tweather.ui.theme.TweatherTheme

/**
 * `man 7 <job>` over `sky.crontab` (Fase 23) — what a line of that file actually is.
 *
 * It takes the whole screen rather than expanding inside the document, and the tab
 * strip goes with it, because that is what `man` does to a terminal: it is not a
 * fourth file in the editor, it is a program you ran and quit. The way back is `[q]`
 * in the status bar and the system back gesture, which are the same action.
 *
 * **It always wraps** (Fase 22's line, applied where it obviously belongs): a man
 * page is only prose, has nothing aligned in it, and paragraphs that pan sideways
 * are not readable. `line_numbers` still follows `settings.config` — this is a
 * document, and how the reader likes to look at a document is theirs to set.
 */
@Composable
fun SkyManScreen(
    jobId: String?,
    onOpen: (String) -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
    canvasState: LazyListState = rememberLazyListState()
) {
    val syntax = TweatherTheme.syntax
    val resources = LocalContext.current.resources
    BackHandler(onBack = onQuit)
    // Every page opens at the top, the way `man` does. Without this, following a SEE
    // ALSO from halfway down one page lands the next one already scrolled: the canvas
    // keeps one scroll state and the new document is simply shorter content under an
    // old offset, so the header the reader needs is the one thing off screen.
    LaunchedEffect(jobId) { canvasState.scrollToItem(0) }
    val job = jobId?.let { SkyJobCatalog.byId(it) }
    val lines = if (job == null) {
        buildManIndexLines(resources, syntax, onOpen)
    } else {
        buildManPageLines(resources, syntax, job, onOpen)
    }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            CodeCanvas(
                lines = lines,
                state = canvasState,
                modifier = Modifier.weight(1f).fillMaxSize(),
                // Nothing on a man page is a column, so nothing here is protecting an
                // alignment: the whole file is sentences.
                options = LocalEditorOptions.current.copy(wordWrap = true),
                showIndentGuides = false
            )
            TerminalStatusBar {
                StatusBarStart {
                    StatusBarText("⎇ sky", shrink = true)
                    StatusBarDivider()
                    StatusBarText("man $SECTION_SUFFIX")
                }
                Spacer(Modifier.weight(1f))
                // `less` puts the way out at the bottom of the page, and so does this.
                // The `q` is the key and never moves; the word beside it is a word.
                StatusBarText(
                    text = "[q] " + stringResource(R.string.man_quit),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.cd_sky_man_quit)
                    ) { onQuit() }
                )
            }
        }
    }
}

private const val SECTION_SUFFIX = "${SkyManPages.SECTION}"

/**
 * One page. The four sections are always the same four, in the same order: a reader
 * who has read one page knows where to look on all fifty-one.
 */
internal fun buildManPageLines(
    resources: Resources,
    syntax: SyntaxColors,
    job: SkyJob,
    onOpen: (String) -> Unit
): List<CanvasLine> = buildList {
    add(headerLine(SkyManPages.header(job.id), syntax))
    add(blank())

    add(sectionLine(resources.getString(R.string.man_sec_name), syntax))
    add(
        CodeLine(
            buildAnnotatedString {
                withStyle(SpanStyle(color = syntax.key)) { append(job.id) }
                append(" — ")
                append(SkyJobNames.name(resources, job.id))
            },
            indent = 1
        )
    )
    add(blank())

    add(sectionLine(resources.getString(R.string.man_sec_description), syntax))
    addAll(paragraphs(resources.getString(SkyManPages.pageOf(job.id)), syntax))
    add(blank())

    add(sectionLine(resources.getString(R.string.man_sec_when), syntax))
    SkyManPages.whenLines(resources, job).forEach { sentence ->
        add(CodeLine(AnnotatedString(sentence), indent = 1))
    }

    val seeAlso = SkyManPages.seeAlso(job.id)
    if (seeAlso.isNotEmpty()) {
        add(blank())
        add(sectionLine(resources.getString(R.string.man_sec_see_also), syntax))
        seeAlso.forEach { add(referenceLine(resources, syntax, it, onOpen)) }
    }
}

/**
 * `man sky` — the index, and the one page a reader who does not yet know what any of
 * this is should land on. Grouped in the catalog's own order, which is by what the
 * event is about rather than by when it next fires.
 */
internal fun buildManIndexLines(
    resources: Resources,
    syntax: SyntaxColors,
    onOpen: (String) -> Unit
): List<CanvasLine> = buildList {
    add(headerLine("SKY(${SkyManPages.SECTION})", syntax))
    add(blank())

    add(sectionLine(resources.getString(R.string.man_sec_name), syntax))
    add(
        CodeLine(
            buildAnnotatedString {
                withStyle(SpanStyle(color = syntax.key)) { append("sky") }
                append(" — ")
                append(resources.getString(R.string.man_index_name))
            },
            indent = 1
        )
    )
    add(blank())

    add(sectionLine(resources.getString(R.string.man_sec_description), syntax))
    addAll(paragraphs(resources.getString(R.string.man_index_intro), syntax))
    add(blank())

    add(sectionLine(resources.getString(R.string.man_sec_jobs), syntax))
    SkyJobCatalog.all.forEach { job ->
        add(referenceLine(resources, syntax, job.id, onOpen))
    }
}

/** `GOLDEN_HOUR.PM(7)` — a real man page opens with its own name shouted. */
private fun headerLine(text: String, syntax: SyntaxColors): CodeLine =
    CodeLine(AnnotatedString(text, SpanStyle(color = syntax.comment)))

private fun sectionLine(text: String, syntax: SyntaxColors): CodeLine =
    CodeLine(
        AnnotatedString(text, SpanStyle(color = syntax.key, fontWeight = FontWeight.Bold))
    )

/** `blue_hour.pm    the evening blue hour` — the id, then what it is called. */
private fun referenceLine(
    resources: Resources,
    syntax: SyntaxColors,
    jobId: String,
    onOpen: (String) -> Unit
): CodeLine = CodeLine(
    text = buildAnnotatedString {
        withStyle(SpanStyle(color = syntax.key)) { append(jobId) }
        withStyle(SpanStyle(color = syntax.comment)) {
            append("  ")
            append(SkyJobNames.name(resources, jobId))
        }
    },
    indent = 1,
    onClick = { onOpen(jobId) },
    onClickLabel = resources.getString(R.string.cd_sky_man_open, jobId)
)

/**
 * A stored description is one string with its paragraphs separated by blank lines —
 * the page wraps, so a paragraph does not need to arrive pre-broken the way
 * `HELP.md` does.
 */
private fun paragraphs(text: String, syntax: SyntaxColors): List<CanvasLine> =
    text.split("\n\n").flatMapIndexed { index, paragraph ->
        val line = CodeLine(AnnotatedString(paragraph.trim()), indent = 1)
        if (index == 0) listOf(line) else listOf(blank(), line)
    }

private fun blank(): CodeLine = CodeLine(AnnotatedString(""))

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 700)
@Composable
private fun SkyManPagePreview() {
    TweatherTheme {
        SkyManScreen(jobId = SkyJobCatalog.DarknessWindow.id, onOpen = {}, onQuit = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 700)
@Composable
private fun SkyManIndexPreview() {
    TweatherTheme {
        // With the setting the screen deliberately overrides, so the preview shows
        // what a reader with `word_wrap: false` actually gets.
        CompositionLocalProvider(LocalEditorOptions provides EditorOptions(wordWrap = false)) {
            SkyManScreen(jobId = null, onOpen = {}, onQuit = {})
        }
    }
}
