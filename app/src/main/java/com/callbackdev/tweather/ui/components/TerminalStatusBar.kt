package com.callbackdev.tweather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.callbackdev.tweather.ui.theme.TweatherTheme

/**
 * Fixed 28dp terminal bar for secondary metadata ("Last Updated: 12:01:04", branch,
 * encoding…). Flat, 1px top border, status-bar typography. Content is a single row
 * slot with 12dp spacing; use `Spacer(Modifier.weight(1f))` to split left/right and
 * [StatusBarDivider] as `|` separator.
 */
@Composable
fun TerminalStatusBar(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable RowScope.() -> Unit
) {
    val borderColor = TweatherTheme.syntax.border
    Row(
        modifier = modifier
            .fillMaxWidth()
            // min instead of fixed: 28dp on the default density, grows with the
            // system font scale instead of clipping the text
            .heightIn(min = 28.dp)
            .background(containerColor)
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.labelMedium,
            LocalContentColor provides contentColor
        ) {
            content()
        }
    }
}

/** `|` separator between status bar items. */
@Composable
fun StatusBarDivider() {
    Text(
        text = "|",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun TerminalStatusBarPreview() {
    TweatherTheme {
        TerminalStatusBar {
            Text("⎇ main")
            StatusBarDivider()
            Text("UTF-8")
            Spacer(Modifier.weight(1f))
            Text("Last Updated: 12:01:04")
        }
    }
}
