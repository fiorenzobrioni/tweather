package com.callbackdev.tweather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.callbackdev.tweather.ui.theme.TweatherTheme

/**
 * Editor-style top bar: terminal prompt glyph + active file name in primary, 48dp
 * tall, flat with a 1px bottom border (mockup TopAppBar). [actions] land on the right.
 */
@Composable
fun EditorTab(
    fileName: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            // min instead of fixed: 48dp normally, grows with the system font scale
            .heightIn(min = 48.dp)
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = ">_",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primaryContainer
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = fileName,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun EditorTabPreview() {
    TweatherTheme {
        EditorTab(fileName = "weather_data.json")
    }
}
