package com.callbackdev.tweather.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.callbackdev.tweather.ui.theme.TweatherTheme

private val ChildIndent = 20.dp // design system: 20px per nesting level
private val GuideInset = 6.dp   // vertical guide offset (mockup: margin-left 6px)

/**
 * Tree-view node: `▾`/`▸` expander (or `·` for leaves) plus label; [children] are
 * indented 20px and connected by a 1px vertical guide wire in `#30363d`.
 */
@Composable
fun TreeViewItem(
    label: AnnotatedString,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true,
    children: (@Composable ColumnScope.() -> Unit)? = null
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val guideColor = TweatherTheme.syntax.border
    Column(modifier) {
        Row(
            modifier = if (children != null) {
                Modifier.clickable(role = Role.Button) { expanded = !expanded }
            } else {
                Modifier
            }
        ) {
            Text(
                text = when {
                    children == null -> "·"
                    expanded -> "▾"
                    else -> "▸"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(ChildIndent)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (children != null && expanded) {
            Column(
                Modifier
                    .drawBehind {
                        drawLine(
                            color = guideColor,
                            start = Offset(GuideInset.toPx(), 0f),
                            end = Offset(GuideInset.toPx(), size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    .padding(start = ChildIndent)
            ) {
                children()
            }
        }
    }
}

@Composable
fun TreeViewItem(
    label: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true,
    children: (@Composable ColumnScope.() -> Unit)? = null
) {
    TreeViewItem(AnnotatedString(label), modifier, initiallyExpanded, children)
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun TreeViewItemPreview() {
    TweatherTheme {
        TreeViewItem(label = "saved_locations/", modifier = Modifier.padding(16.dp)) {
            TreeViewItem(label = "new_york.json")
            TreeViewItem(label = "europe/") {
                TreeViewItem(label = "london.json")
                TreeViewItem(label = "milan.json")
            }
        }
    }
}
