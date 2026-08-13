package com.callbackdev.tweather.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Selectable theme profiles (Settings, `"available_profiles"`). Dracula and Monokai
 * palettes are implemented in Fase 7; until then they resolve to Obsidian.
 */
enum class ThemeProfile {
    Obsidian,
    Dracula,
    Monokai
}

@Immutable
private data class ThemeSpec(
    val colorScheme: ColorScheme,
    val syntax: SyntaxColors
)

private val ObsidianSpec = ThemeSpec(
    colorScheme = darkColorScheme(
        primary = ObsidianColors.primary,
        onPrimary = ObsidianColors.onPrimary,
        primaryContainer = ObsidianColors.primaryContainer,
        onPrimaryContainer = ObsidianColors.onPrimaryContainer,
        inversePrimary = ObsidianColors.inversePrimary,
        secondary = ObsidianColors.secondary,
        onSecondary = ObsidianColors.onSecondary,
        secondaryContainer = ObsidianColors.secondaryContainer,
        onSecondaryContainer = ObsidianColors.onSecondaryContainer,
        tertiary = ObsidianColors.tertiary,
        onTertiary = ObsidianColors.onTertiary,
        tertiaryContainer = ObsidianColors.tertiaryContainer,
        onTertiaryContainer = ObsidianColors.onTertiaryContainer,
        error = ObsidianColors.error,
        onError = ObsidianColors.onError,
        errorContainer = ObsidianColors.errorContainer,
        onErrorContainer = ObsidianColors.onErrorContainer,
        background = ObsidianColors.background,
        onBackground = ObsidianColors.onBackground,
        surface = ObsidianColors.surface,
        onSurface = ObsidianColors.onSurface,
        surfaceVariant = ObsidianColors.surfaceVariant,
        onSurfaceVariant = ObsidianColors.onSurfaceVariant,
        surfaceTint = ObsidianColors.surfaceTint,
        inverseSurface = ObsidianColors.inverseSurface,
        inverseOnSurface = ObsidianColors.inverseOnSurface,
        outline = ObsidianColors.outline,
        outlineVariant = ObsidianColors.outlineVariant,
        surfaceBright = ObsidianColors.surfaceBright,
        surfaceDim = ObsidianColors.surfaceDim,
        surfaceContainer = ObsidianColors.surfaceContainer,
        surfaceContainerHigh = ObsidianColors.surfaceContainerHigh,
        surfaceContainerHighest = ObsidianColors.surfaceContainerHighest,
        surfaceContainerLow = ObsidianColors.surfaceContainerLow,
        surfaceContainerLowest = ObsidianColors.surfaceContainerLowest
    ),
    syntax = ObsidianSyntax
)

private val ThemeProfile.spec: ThemeSpec
    get() = when (this) {
        ThemeProfile.Obsidian -> ObsidianSpec
        ThemeProfile.Dracula -> ObsidianSpec
        ThemeProfile.Monokai -> ObsidianSpec
    }

/**
 * App theme. Always dark: the design system has no light variant, so the profile —
 * not the system dark-mode setting — is the only axis of variation.
 */
@Composable
fun TweatherTheme(
    profile: ThemeProfile = ThemeProfile.Obsidian,
    content: @Composable () -> Unit
) {
    val spec = profile.spec
    CompositionLocalProvider(LocalSyntaxColors provides spec.syntax) {
        MaterialTheme(
            colorScheme = spec.colorScheme,
            typography = TweatherTypography,
            shapes = TweatherShapes,
            content = content
        )
    }
}

/** Accessor for theme values outside the Material color scheme, à la [MaterialTheme]. */
object TweatherTheme {
    val syntax: SyntaxColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSyntaxColors.current
}
