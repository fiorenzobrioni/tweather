package com.callbackdev.tweather.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.callbackdev.tweather.R
import com.callbackdev.tweather.ui.theme.TweatherTheme

/** One destination of [EditorNavBar]; [route] doubles as the selection key. */
data class EditorNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
)

/**
 * Bottom navigation styled like the mockups: 56dp, flat on surface-container-low
 * with a 1px top border, label-sm typography. The active item is primary-colored
 * with a 2px indicator line on its top edge (no Material ripple pill).
 */
@Composable
fun EditorNavBar(
    items: List<EditorNavItem>,
    isSelected: (EditorNavItem) -> Boolean,
    onSelect: (EditorNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = TweatherTheme.syntax.border
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .navigationBarsPadding()
            // 56dp on the default density; grows with the system font scale
            .heightIn(min = 56.dp)
            .height(IntrinsicSize.Min)
    ) {
        items.forEach { item ->
            val selected = isSelected(item)
            val tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .drawBehind {
                        if (selected) {
                            drawLine(
                                color = tint,
                                start = Offset(0f, 1.dp.toPx()),
                                end = Offset(size.width, 1.dp.toPx()),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                    .selectable(selected = selected, role = Role.Tab) { onSelect(item) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = stringResource(item.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = tint
                )
            }
        }
    }
}

/**
 * The app's four tabs, with two deliberate deviations from the mockup (pre-v1) and
 * one alignment on the series (post-v1):
 *
 * - the first tab was "Explorer" behind a file-tree glyph, which since Fase 10b
 *   promised a tree that no longer exists (`cities/` moved into the Cerca tab). It
 *   is an open editor, so it says so, behind the `{ }` of the file it opens. Its
 *   route stays "explorer" — that string is state, not a label.
 * - Logs opens `weather_history.diff`, a git log: it wears the **commit** glyph (a
 *   dot on a branch line), like tsteps and thabit. The terminal glyph it used to
 *   wear named the app's skin, not this file — and every actual terminal in the app
 *   (the `$` commands, the status bar, the widget) is somewhere else.
 * - **Settings is last**, as in tsteps, thabit and every Android bottom bar: the
 *   three tabs that hold the weather come first, the drawer of options after them.
 */
object EditorNavItems {
    val Editor = EditorNavItem("explorer", R.string.nav_editor, Icons.Filled.DataObject)
    val Search = EditorNavItem("search", R.string.nav_search, Icons.Filled.Search)
    val Logs = EditorNavItem("logs", R.string.nav_logs, Icons.Filled.Commit)
    val Settings = EditorNavItem("settings", R.string.nav_settings, Icons.Filled.Code)
    val All = listOf(Editor, Search, Logs, Settings)
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun EditorNavBarPreview() {
    TweatherTheme {
        EditorNavBar(
            items = EditorNavItems.All,
            isSelected = { it == EditorNavItems.Editor },
            onSelect = {}
        )
    }
}
