package com.callbackdev.chiaro.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * The entry point of the design system (DESIGN.md §12).
 *
 * Dynamic color is ON by default (§4.1): a weather app that takes the reader's own
 * wallpaper is a weather app that belongs on their phone. [dynamicColor] false gives the
 * generated Chiaro scheme instead — the setting exists for readers who want the app to
 * look like itself, and it is what the store screenshots use, since a wallpaper-derived
 * scheme would make every screenshot a different app.
 *
 * The semantic palette of §2.3 does NOT follow the wallpaper (see [ChiaroColors]); only
 * the Material roles do.
 */
@Composable
fun ChiaroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        // minSdk is 33, so the S check is always true; it stays because lint reads it
        // as the contract it is, and because the day this app supports an older API
        // the compiler should not be the last to know.
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> ChiaroDarkScheme
        else -> ChiaroLightScheme
    }

    CompositionLocalProvider(
        LocalChiaroColors provides if (darkTheme) ChiaroDarkColors else ChiaroLightColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ChiaroTypography,
            shapes = ChiaroShapes,
            content = content
        )
    }
}
