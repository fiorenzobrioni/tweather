package com.callbackdev.tweather.ui.theme

import androidx.compose.ui.graphics.Color

// "Obsidian Syntax" — Material 3 palette from the obsidian_syntax/DESIGN.md frontmatter.
// Dracula and Monokai get their own objects in Fase 7.
object ObsidianColors {
    val surface = Color(0xFF10141A)
    val surfaceDim = Color(0xFF10141A)
    val surfaceBright = Color(0xFF353940)
    val surfaceContainerLowest = Color(0xFF0A0E14)
    val surfaceContainerLow = Color(0xFF181C22)
    val surfaceContainer = Color(0xFF1C2026)
    val surfaceContainerHigh = Color(0xFF262A31)
    val surfaceContainerHighest = Color(0xFF31353C)
    val onSurface = Color(0xFFDFE2EB)
    val onSurfaceVariant = Color(0xFFC0C7D1)
    val surfaceVariant = Color(0xFF31353C)
    val inverseSurface = Color(0xFFDFE2EB)
    val inverseOnSurface = Color(0xFF2D3137)
    val outline = Color(0xFF8A919B)
    val outlineVariant = Color(0xFF404750)
    val surfaceTint = Color(0xFF96CCFF)

    val primary = Color(0xFFB5D9FF)
    val onPrimary = Color(0xFF003353)
    val primaryContainer = Color(0xFF79C0FF)
    val onPrimaryContainer = Color(0xFF004E7B)
    val inversePrimary = Color(0xFF00639A)

    val secondary = Color(0xFF74DD7E)
    val onSecondary = Color(0xFF003910)
    val secondaryContainer = Color(0xFF007F2D)
    val onSecondaryContainer = Color(0xFFC4FFC2)

    val tertiary = Color(0xFFE6CBFF)
    val onTertiary = Color(0xFF421B6A)
    val tertiaryContainer = Color(0xFFD2A8FF)
    val onTertiaryContainer = Color(0xFF5D3885)

    val error = Color(0xFFFFB4AB)
    val onError = Color(0xFF690005)
    val errorContainer = Color(0xFF93000A)
    val onErrorContainer = Color(0xFFFFDAD6)

    val background = Color(0xFF10141A)
    val onBackground = Color(0xFFDFE2EB)

    // Fixed roles: not part of ColorScheme in material3 1.3.x, kept as reference tokens
    val primaryFixed = Color(0xFFCEE5FF)
    val primaryFixedDim = Color(0xFF96CCFF)
    val onPrimaryFixed = Color(0xFF001D32)
    val onPrimaryFixedVariant = Color(0xFF004A76)
    val secondaryFixed = Color(0xFF90FA97)
    val secondaryFixedDim = Color(0xFF74DD7E)
    val onSecondaryFixed = Color(0xFF002106)
    val onSecondaryFixedVariant = Color(0xFF00531B)
    val tertiaryFixed = Color(0xFFEFDBFF)
    val tertiaryFixedDim = Color(0xFFDBB8FF)
    val onTertiaryFixed = Color(0xFF2B0052)
    val onTertiaryFixedVariant = Color(0xFF593482)
}

// Syntax highlighting tokens for the Obsidian profile (see SyntaxColors)
val ObsidianSyntax = SyntaxColors(
    key = Color(0xFF79C0FF),
    string = Color(0xFFA5D6FF),
    number = Color(0xFFFFA657),
    comment = Color(0xFF8B949E),
    diffAdd = Color(0xFF2EA043),
    diffDel = Color(0xFFF85149),
    border = Color(0xFF30363D),
    glow = Color(0x8879C0FF)
)
