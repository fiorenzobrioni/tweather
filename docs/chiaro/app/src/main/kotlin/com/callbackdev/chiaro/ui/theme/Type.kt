package com.callbackdev.chiaro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.callbackdev.chiaro.R

/**
 * DESIGN.md §5. Inter, bundled as a variable font (OFL, `licenses/Inter-OFL.txt`)
 * rather than fetched from a font provider: a downloadable font is a runtime dependency
 * on Play Services, and an app that renders wrong on a de-Googled phone is an app that
 * renders wrong.
 *
 * Never a monospace. The terminal line owns that, and Chiaro must not read as its
 * sibling.
 */
// The variationSettings overload is still marked experimental. The opt-in is the
// whole point of bundling a VARIABLE font: without it Android synthesises the weights
// by smearing the outlines, which is exactly the look Inter was chosen to avoid.
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    resId = R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val InterFamily = FontFamily(
    inter(300), inter(400), inter(500), inter(600), inter(700)
)

/** Figures that sit in a column are tabular, always: proportional digits in a column are
 * the typographic equivalent of a wobbling table, and this app is mostly columns. */
private const val Tabular = "tnum"

val ChiaroTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = InterFamily),
        displayMedium = displayMedium.copy(fontFamily = InterFamily),
        displaySmall = displaySmall.copy(fontFamily = InterFamily, fontSize = 36.sp, lineHeight = 44.sp),
        headlineLarge = headlineLarge.copy(fontFamily = InterFamily),
        headlineMedium = headlineMedium.copy(fontFamily = InterFamily),
        headlineSmall = headlineSmall.copy(fontFamily = InterFamily),
        titleLarge = titleLarge.copy(
            fontFamily = InterFamily, fontSize = 22.sp, lineHeight = 28.sp,
            fontWeight = FontWeight.Medium
        ),
        titleMedium = titleMedium.copy(
            fontFamily = InterFamily, fontSize = 16.sp, lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold
        ),
        titleSmall = titleSmall.copy(fontFamily = InterFamily),
        bodyLarge = bodyLarge.copy(fontFamily = InterFamily),
        bodyMedium = bodyMedium.copy(fontFamily = InterFamily),
        bodySmall = bodySmall.copy(fontFamily = InterFamily),
        labelLarge = labelLarge.copy(fontFamily = InterFamily),
        labelMedium = labelMedium.copy(fontFamily = InterFamily),
        labelSmall = labelSmall.copy(fontFamily = InterFamily)
    )
}

/**
 * The one type role Material does not have. The current temperature is the largest thing
 * on the screen and it is a number, so it is light, tight and tabular — `displayLarge`
 * with a body weight would read as a headline instead of as a reading.
 */
val HeroTemperature = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Light,
    fontSize = 64.sp,
    lineHeight = 68.sp,
    fontFeatureSettings = Tabular
)

/** Any style, with the figures made tabular. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = Tabular)
