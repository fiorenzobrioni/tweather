package com.callbackdev.tweather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.callbackdev.tweather.ui.theme.TweatherTheme

/**
 * Editor-style top bar: terminal prompt glyph + one tappable tab per open file,
 * 48dp tall, flat with a 1px bottom border (mockup TopAppBar). Border in
 * `syntax.border` like the nav and status bars — DESIGN.md wants one color for all
 * structural separation, and this bar is the same chrome. Active tab in primary with
 * a 2px bottom indicator (the nav bar's active treatment); inactive tabs in comment
 * gray. Tabs scroll horizontally when the file names outgrow a narrow screen.
 * [actions] land pinned on the right, outside the scrolling tab strip. (The main
 * screen's `$ ls cities/` lived there in Fase 10; gone since 10b — a pinned action
 * steals fixed width from the strip and truncates the file names.)
 *
 * File name at bodyMedium + bold on every screen (post-9h, decided with the
 * committente): the tab bar is chrome, not content, and once the Logs grew a real
 * two-tab bar at 14sp the 24sp single-file titles clashed on every tab switch.
 *
 * Single-file screens (`cities.json`, `widget.config`) pass a one-element list
 * rather than a plainer bar of their own (pre-v1): they were the only two places
 * where the open file had no indicator under it, which read as a different kind of
 * chrome on every switch into them. A one-element strip also means those screens
 * grow a second file without touching their layout.
 *
 * **The active tab is scrolled into view** (Fase 16c). The strip has scrolled since
 * it was written, but nothing ever brought the selection back: with three names —
 * which the editor strip now has — a tab could be selected with its 2px indicator
 * sitting off-screen, so the bar showed no active file at all. It is fixed in the
 * component rather than at the one call site that exposed it, because the Settings
 * and Logs strips have the same three-name shape and the same latent bug.
 */
@Composable
fun EditorTabs(
    fileNames: List<String>,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val syntax = TweatherTheme.syntax
    val borderColor = syntax.border
    val indicatorColor = MaterialTheme.colorScheme.primary
    val scrollState = rememberScrollState()
    // Where each tab starts and ends inside the scrolling row, filled in as the tabs
    // are laid out. Positions rather than an index arithmetic: the names have
    // different widths, so there is nothing to compute them from.
    val bounds = remember { mutableStateMapOf<Int, IntRange>() }
    val viewportWidth = remember { mutableIntStateOf(0) }
    LaunchedEffect(activeIndex, bounds[activeIndex], scrollState.maxValue) {
        val tab = bounds[activeIndex] ?: return@LaunchedEffect
        val viewportEnd = scrollState.value + viewportWidth.intValue
        val target = when {
            viewportWidth.intValue == 0 -> null
            // Off the left edge: bring its start to the edge.
            tab.first < scrollState.value -> tab.first
            // Off the right edge: bring its end to the edge, so the indicator lands
            // inside the viewport rather than one pixel past it.
            tab.last > viewportEnd -> tab.last - viewportWidth.intValue
            else -> null
        }
        target?.let { scrollState.animateScrollTo(it.coerceIn(0, scrollState.maxValue)) }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
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
        Row(
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { viewportWidth.intValue = it.size.width }
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            fileNames.forEachIndexed { index, fileName ->
                val active = index == activeIndex
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { coordinates ->
                            val start = coordinates.positionInParent().x.toInt()
                            bounds[index] = start..(start + coordinates.size.width)
                        }
                        // Tab height = bar height, so the 2px indicator lands on
                        // the bar's bottom edge (and keeps growing with font scale)
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = active,
                            role = Role.Tab,
                            onClick = { onSelect(index) }
                        )
                        .drawBehind {
                            if (active) {
                                drawLine(
                                    color = indicatorColor,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (active) indicatorColor else syntax.comment
                    )
                }
            }
        }
        actions()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun EditorTabSingleFilePreview() {
    TweatherTheme {
        EditorTabs(fileNames = listOf("cities.json"), activeIndex = 0, onSelect = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun EditorTabsPreview() {
    TweatherTheme {
        EditorTabs(
            fileNames = listOf("history.diff", "forecast.diff"),
            activeIndex = 0,
            onSelect = {}
        )
    }
}
