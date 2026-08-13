package com.callbackdev.tweather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.callbackdev.tweather.ui.theme.TweatherTheme

/** One rendered line of the fake source file shown by [CodeCanvas]. */
@Immutable
sealed interface CanvasLine {
    val indent: Int
}

/** A plain (optionally tappable) syntax-highlighted text line. */
@Immutable
data class CodeLine(
    val text: AnnotatedString,
    override val indent: Int = 0,
    val onClick: (() -> Unit)? = null
) : CanvasLine

/** A line whose content is an arbitrary composable (e.g. the search input). */
@Immutable
class WidgetLine(
    override val indent: Int = 0,
    val content: @Composable () -> Unit
) : CanvasLine

/**
 * Editor behavior from `settings.config`, provided app-wide by the shell. Defaults
 * match the mobile mockups: no line numbers, no wrap.
 */
@Immutable
data class EditorOptions(
    val showLineNumbers: Boolean = false,
    val wordWrap: Boolean = false
)

val LocalEditorOptions = compositionLocalOf { EditorOptions() }

private val IndentWidth = 20.dp    // design system: 20px per nesting level
private val GutterGap = 12.dp     // gap between the gutter divider and column 0
private val EdgeMargin = 16.dp    // design system: 16px side margins (no gutter)
private val GuideInset = 6.dp     // tree guide offset inside its indent slot (mockup: 6px)
private val WrapHangingIndent = 20.sp // continuation lines of a wrapped row

/**
 * Scrollable editor canvas: monospaced content with 20px indentation per nesting
 * level, 1px vertical tree guides and an optional line-number gutter (right-aligned
 * numbers, 1px divider — [EditorOptions.showLineNumbers], off by default on mobile).
 *
 * Long lines follow [EditorOptions.wordWrap]: off = the whole document pans
 * horizontally in one gesture (every row shares one ScrollState AND one measured
 * content width, so short rows can't clamp the scroll range back to zero); on = rows
 * soft-wrap with a hanging indent, editor style.
 */
@Composable
fun CodeCanvas(
    lines: List<CanvasLine>,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    showIndentGuides: Boolean = true,
    options: EditorOptions = LocalEditorOptions.current
) {
    val codeStyle = MaterialTheme.typography.bodySmall // code-block 13/22
    val gutterColor = MaterialTheme.colorScheme.outlineVariant
    val guideColor = TweatherTheme.syntax.border
    val gutterDigits = lines.size.coerceAtLeast(1).toString().length
    val horizontalScroll = rememberScrollState()
    val startGap = if (options.showLineNumbers) GutterGap else EdgeMargin

    // Monospace makes the widest row measurable exactly; every row gets that same
    // width so the shared horizontal ScrollState has one consistent range.
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val contentWidth: Dp = remember(lines, codeStyle, options.wordWrap, density) {
        if (options.wordWrap) {
            Dp.Unspecified
        } else {
            with(density) {
                lines.maxOfOrNull { line ->
                    val textPx = when (line) {
                        is CodeLine ->
                            textMeasurer.measure(line.text, style = codeStyle).size.width
                        is WidgetLine -> 0
                    }
                    textPx + (IndentWidth * line.indent).toPx()
                }?.toDp()?.plus(startGap + EdgeMargin) ?: Dp.Unspecified
            }
        }
    }

    LazyColumn(modifier = modifier, state = state, contentPadding = contentPadding) {
        itemsIndexed(lines) { index, line ->
            Row(Modifier.height(IntrinsicSize.Min)) {
                if (options.showLineNumbers) {
                    Text(
                        text = (index + 1).toString().padStart(gutterDigits),
                        style = codeStyle,
                        color = gutterColor,
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp)
                    )
                    Spacer(
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(guideColor)
                    )
                }
                val rowModifier = Modifier.weight(1f)
                Box(
                    if (options.wordWrap) {
                        rowModifier
                    } else {
                        rowModifier.horizontalScroll(horizontalScroll)
                    }
                ) {
                    val lineModifier = Modifier
                        .then(
                            if (contentWidth.isSpecified) {
                                Modifier.width(contentWidth)
                            } else {
                                Modifier.fillMaxWidth()
                            }
                        )
                        .lineDecoration(line.indent, startGap, guideColor, showIndentGuides)
                    when (line) {
                        is CodeLine -> Text(
                            text = line.text,
                            style = if (options.wordWrap) {
                                codeStyle.copy(
                                    textIndent = TextIndent(restLine = WrapHangingIndent)
                                )
                            } else {
                                codeStyle
                            },
                            softWrap = options.wordWrap,
                            modifier = if (line.onClick != null) {
                                lineModifier.clickable(onClick = line.onClick)
                            } else {
                                lineModifier
                            }
                        )
                        is WidgetLine -> Box(lineModifier) { line.content() }
                    }
                }
            }
        }
    }
}

/** Indent guides behind the line plus the 20px-per-level content offset. */
private fun Modifier.lineDecoration(
    indent: Int,
    startGap: Dp,
    guideColor: Color,
    showIndentGuides: Boolean
): Modifier = this
    .drawBehind {
        if (!showIndentGuides) return@drawBehind
        val stroke = 1.dp.toPx()
        for (level in 1..indent) {
            val x = startGap.toPx() +
                (level - 1) * IndentWidth.toPx() +
                GuideInset.toPx()
            drawLine(
                color = guideColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = stroke
            )
        }
    }
    .padding(
        start = startGap + IndentWidth * indent,
        end = EdgeMargin
    )

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun CodeCanvasPreview() {
    com.callbackdev.tweather.ui.theme.TweatherTheme {
        val syntax = TweatherTheme.syntax
        val lines = listOf(
            commentLine("// tweather editor canvas", syntax),
            CodeLine(AnnotatedString("{"), 0),
            CodeLine(AnnotatedString("\"nested\": true"), 1),
            CodeLine(AnnotatedString("\"deeper\": [1, 2, 3]"), 2),
            CodeLine(AnnotatedString("}"), 0)
        )
        CodeCanvas(lines = lines, options = EditorOptions(showLineNumbers = true))
    }
}
