package com.callbackdev.chiaro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.callbackdev.chiaro.ui.theme.ChiaroTheme

/**
 * DESIGN.md §8.2 and §1.1. The visual form of "the screen must not lie": data older than
 * the update interval says its real age, in the warning role, with the retry attached.
 *
 * Deliberately not a toast and deliberately not a spinner. A toast is gone before it is
 * read, and a spinner would hide the data that is still perfectly usable — a forecast
 * three hours old is not wrong, it is old, and those are different words.
 *
 * [age] is already formatted by the caller: "3 hours ago" is prose, and prose is
 * localized where the reader is, not here.
 */
@Composable
fun FreshnessChip(
    age: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ChiaroTheme.colors.freshness
    Row(
        modifier = modifier
            .background(colors.container, CircleShape)
            .clickable(onClick = onRetry)
            .padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = age, style = MaterialTheme.typography.labelLarge, color = colors.ink)
        Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = null, // the row is one target; the text says what it does
            tint = colors.ink
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FreshnessChipPreview() {
    com.callbackdev.chiaro.ui.theme.ChiaroTheme(dynamicColor = false) {
        FreshnessChip(age = "Aggiornato 3 ore fa", onRetry = {}, modifier = Modifier.padding(16.dp))
    }
}
