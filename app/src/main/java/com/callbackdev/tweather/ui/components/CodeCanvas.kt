package com.callbackdev.tweather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.callbackdev.tweather.ui.theme.TweatherTheme

/** One rendered line of the fake source file shown by [CodeCanvas]. */
@Immutable
data class CodeLine(
    val text: AnnotatedString,
    val indent: Int = 0
)

private val IndentWidth = 20.dp // design system: 20px per nesting level
private val ContentGap = 12.dp  // gap between the gutter divider and column 0
private val GuideInset = 6.dp   // tree guide offset inside its indent slot (mockup: 6px)

/**
 * Scrollable editor canvas: line-number gutter on the left (right-aligned numbers,
 * 1px divider) and monospaced content on the right, with 20px indentation per nesting
 * level and 1px vertical tree guides. Lines never soft-wrap; overly long lines scroll
 * horizontally, all rows in sync.
 */
@Composable
fun CodeCanvas(
    lines: List<CodeLine>,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    showIndentGuides: Boolean = true
) {
    val codeStyle = MaterialTheme.typography.bodySmall // code-block 13/22
    val gutterColor = MaterialTheme.colorScheme.outlineVariant
    val guideColor = TweatherTheme.syntax.border
    val gutterDigits = lines.size.coerceAtLeast(1).toString().length
    val horizontalScroll = rememberScrollState()

    LazyColumn(modifier = modifier, state = state, contentPadding = contentPadding) {
        itemsIndexed(lines) { index, line ->
            Row(Modifier.height(IntrinsicSize.Min)) {
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
                Box(
                    Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScroll)
                ) {
                    Text(
                        text = line.text,
                        style = codeStyle,
                        softWrap = false,
                        modifier = Modifier
                            .drawBehind {
                                if (!showIndentGuides) return@drawBehind
                                val stroke = 1.dp.toPx()
                                for (level in 1..line.indent) {
                                    val x = ContentGap.toPx() +
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
                                start = ContentGap + IndentWidth * line.indent,
                                end = 16.dp
                            )
                    )
                }
            }
        }
    }
}

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
        CodeCanvas(lines = lines)
    }
}
