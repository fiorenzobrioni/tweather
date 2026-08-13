package com.callbackdev.tweather.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Dark-only "Obsidian Syntax" scheme; full token set + theme profiles in Fase 1/7
private val ObsidianSyntaxColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant
)

@Composable
fun TweatherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ObsidianSyntaxColorScheme,
        typography = TweatherTypography,
        content = content
    )
}
