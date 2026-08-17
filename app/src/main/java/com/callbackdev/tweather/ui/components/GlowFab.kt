package com.callbackdev.tweather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.callbackdev.tweather.ui.theme.TweatherTheme
import com.callbackdev.tweather.ui.theme.fabGlow

/**
 * The app's refresh FAB: rectangular with the same 4px radius as everything else
 * (a circle would be the one Material-ism left in the editor) and flat — no
 * Material elevation/shadow. What sets it apart is its glow, the app's only
 * shadow-like effect (`box-shadow: 0 0 15px #79c0ff88`).
 */
@Composable
fun GlowFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Refresh",
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    glowColor: Color = TweatherTheme.syntax.glow,
    icon: @Composable () -> Unit = {
        Icon(Icons.Filled.Refresh, contentDescription = null)
    }
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .fabGlow(glowColor)
            .clip(MaterialTheme.shapes.small)
            .background(containerColor)
            .clickable(role = Role.Button) { onClick() }
            .semantics {
                // The icon inside is decorative; this names the button for TalkBack.
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            icon()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun GlowFabPreview() {
    TweatherTheme {
        GlowFab(onClick = {}, modifier = Modifier.padding(32.dp))
    }
}
