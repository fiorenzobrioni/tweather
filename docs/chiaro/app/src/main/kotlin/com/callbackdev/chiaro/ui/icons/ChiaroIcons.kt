package com.callbackdev.chiaro.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The weather icon set, behind one lookup (DESIGN.md §4.5, §13.1).
 *
 * **The decision: Meteocons** (github.com/basmilius/meteocons), MIT, ~475 hand-drawn
 * weather icons with animated variants. It satisfies every requirement in §4.5 and the
 * only obligation is preserving the copyright notices, which costs a file in `licenses/`.
 *
 * **What ships today is not that.** Converting several hundred SVGs into Android vector
 * drawables is a mechanical job that wants Android Studio's importer and a look at the
 * result, so it belongs to Fase 2 where the icons first appear on a screen. Until then
 * this maps to Material's own outlined set: coherent, complete enough for every WMO
 * bucket, and — the point — behind the same function call, so Fase 2 changes the bodies
 * here and nothing else in the app.
 *
 * What must NOT happen is the thing tweather could get away with: emoji. They are
 * rendered by the system font, differ per device and per OEM, cannot be tinted, and
 * would undo the design system on the one screen everybody looks at.
 */
object ChiaroIcons {

    /**
     * The icon for a WMO weather code. [night] picks the nocturnal variant where one
     * exists — a clear night is not a sunny day, and that is the only place in the
     * mapping where the distinction changes anything.
     */
    fun forCondition(wmoCode: Int, night: Boolean = false): ImageVector = when (wmoCode) {
        0 -> if (night) Icons.Outlined.DarkMode else Icons.Outlined.WbSunny
        1, 2 -> if (night) Icons.Outlined.DarkMode else Icons.Outlined.WbCloudy
        3 -> Icons.Outlined.Cloud
        45, 48 -> Icons.Outlined.BlurOn
        51, 53, 55, 56, 57 -> Icons.Outlined.Grain
        61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Outlined.Opacity
        71, 73, 75, 77, 85, 86 -> Icons.Outlined.AcUnit
        95, 96, 99 -> Icons.Outlined.Bolt
        else -> Icons.Outlined.Cloud
    }

    val wind: ImageVector = Icons.Outlined.Air
    val humidity: ImageVector = Icons.Outlined.Opacity
    val visibility: ImageVector = Icons.Outlined.Visibility
    val temperature: ImageVector = Icons.Outlined.Thermostat
    val uv: ImageVector = Icons.Outlined.WbSunny
}
