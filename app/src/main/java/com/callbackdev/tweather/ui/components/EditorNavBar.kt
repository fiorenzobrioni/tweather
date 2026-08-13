package com.callbackdev.tweather.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
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
            .height(56.dp)
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
                    .clickable(role = Role.Tab) { onSelect(item) },
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

/** The app's four tabs, in mockup order and with the mockup's glyph choices. */
object EditorNavItems {
    val Explorer = EditorNavItem("explorer", R.string.nav_explorer, Icons.Filled.AccountTree)
    val Search = EditorNavItem("search", R.string.nav_search, Icons.Filled.Search)
    val Settings = EditorNavItem("settings", R.string.nav_settings, Icons.Filled.Code)
    val Logs = EditorNavItem("logs", R.string.nav_logs, Icons.Filled.Terminal)
    val All = listOf(Explorer, Search, Settings, Logs)
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun EditorNavBarPreview() {
    TweatherTheme {
        EditorNavBar(
            items = EditorNavItems.All,
            isSelected = { it == EditorNavItems.Explorer },
            onSelect = {}
        )
    }
}
