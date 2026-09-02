#!/usr/bin/env python3
"""Generates ui/theme/Scheme.kt — the Chiaro fallback color scheme (DESIGN.md §2.2).

Material builds a role out of a palette and a TONE, where the tone is CIELAB L*.
So: take each source color, keep its hue and chroma in CIELAB LCh, set L* to the
tone Material names for that role, and clamp the chroma down until the result is
inside sRGB. Neutrals get their chroma pinned low, which is what makes a surface
read as warm paper instead of as beige.

This is an approximation of Material's HCT, not HCT itself: HCT's chroma is CAM16's
and its tone is L*, so the tone is exact and the chroma is close. That is a fine
trade for a fallback scheme nobody sees unless they turn dynamic color off, and it
is the reason PaletteContrastTest asserts the OUTCOME rather than trusting the
method. Regenerate with:

    python3 tools/gen_scheme.py > app/src/main/kotlin/com/callbackdev/chiaro/ui/theme/Scheme.kt
"""
from __future__ import annotations

import math

SOURCES = {
    "primary": ("#E8A33D", None),
    "secondary": ("#3A7CA5", None),
    "tertiary": ("#6C5B8C", None),
    "error": ("#BA1A1A", None),
    # Chroma pinned: a surface is a warm white, not a beige.
    "neutral": ("#8C857A", 3.0),
    "neutralVariant": ("#8A8578", 7.0),
}

# role -> (palette, light tone, dark tone), straight off the Material 3 spec.
ROLES = [
    ("primary", "primary", 40, 80),
    ("onPrimary", "primary", 100, 20),
    ("primaryContainer", "primary", 90, 30),
    ("onPrimaryContainer", "primary", 10, 90),
    ("inversePrimary", "primary", 80, 40),
    ("secondary", "secondary", 40, 80),
    ("onSecondary", "secondary", 100, 20),
    ("secondaryContainer", "secondary", 90, 30),
    ("onSecondaryContainer", "secondary", 10, 90),
    ("tertiary", "tertiary", 40, 80),
    ("onTertiary", "tertiary", 100, 20),
    ("tertiaryContainer", "tertiary", 90, 30),
    ("onTertiaryContainer", "tertiary", 10, 90),
    ("error", "error", 40, 80),
    ("onError", "error", 100, 20),
    ("errorContainer", "error", 90, 30),
    ("onErrorContainer", "error", 10, 90),
    ("background", "neutral", 98, 6),
    ("onBackground", "neutral", 10, 90),
    ("surface", "neutral", 98, 6),
    ("onSurface", "neutral", 10, 90),
    ("surfaceVariant", "neutralVariant", 90, 30),
    ("onSurfaceVariant", "neutralVariant", 30, 80),
    ("surfaceTint", "primary", 40, 80),
    ("inverseSurface", "neutral", 20, 90),
    ("inverseOnSurface", "neutral", 95, 20),
    ("outline", "neutralVariant", 50, 60),
    ("outlineVariant", "neutralVariant", 80, 30),
    ("scrim", "neutral", 0, 0),
    ("surfaceBright", "neutral", 98, 24),
    ("surfaceDim", "neutral", 87, 6),
    ("surfaceContainer", "neutral", 94, 12),
    ("surfaceContainerHigh", "neutral", 92, 17),
    ("surfaceContainerHighest", "neutral", 90, 22),
    ("surfaceContainerLow", "neutral", 96, 10),
    ("surfaceContainerLowest", "neutral", 100, 4),
]

WHITE = (95.047, 100.0, 108.883)


def srgb_to_linear(c: float) -> float:
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def linear_to_srgb(c: float) -> float:
    return 12.92 * c if c <= 0.0031308 else 1.055 * (c ** (1 / 2.4)) - 0.055


def hex_to_lch(h: str) -> tuple[float, float, float]:
    h = h.lstrip("#")
    r, g, b = (srgb_to_linear(int(h[i:i + 2], 16) / 255) for i in (0, 2, 4))
    x = (0.4124 * r + 0.3576 * g + 0.1805 * b) * 100
    y = (0.2126 * r + 0.7152 * g + 0.0722 * b) * 100
    z = (0.0193 * r + 0.1192 * g + 0.9505 * b) * 100

    def f(t: float) -> float:
        return t ** (1 / 3) if t > 0.008856 else (7.787 * t) + 16 / 116

    fx, fy, fz = f(x / WHITE[0]), f(y / WHITE[1]), f(z / WHITE[2])
    L = 116 * fy - 16
    a = 500 * (fx - fy)
    bb = 200 * (fy - fz)
    return L, math.hypot(a, bb), math.degrees(math.atan2(bb, a)) % 360


def lch_to_rgb(L: float, C: float, hue: float) -> tuple[float, float, float]:
    a = C * math.cos(math.radians(hue))
    bb = C * math.sin(math.radians(hue))
    fy = (L + 16) / 116
    fx, fz = fy + a / 500, fy - bb / 200

    def finv(t: float) -> float:
        return t ** 3 if t ** 3 > 0.008856 else (t - 16 / 116) / 7.787

    x, y, z = finv(fx) * WHITE[0] / 100, finv(fy) * WHITE[1] / 100, finv(fz) * WHITE[2] / 100
    r = 3.2406 * x - 1.5372 * y - 0.4986 * z
    g = -0.9689 * x + 1.8758 * y + 0.0415 * z
    b = 0.0557 * x - 0.2040 * y + 1.0570 * z
    return tuple(linear_to_srgb(max(0.0, min(1.0, v))) for v in (r, g, b))


def tone(source_hex: str, pinned_chroma: float | None, t: int) -> str:
    """The role's color: the source's hue, its chroma (or the pinned one) reduced
    until it fits in sRGB, at lightness `t`. The two ends are exact: a scrim is
    black and the lightest tone is white, not a rounding of them."""
    if t <= 0:
        return "#000000"
    _, c0, hue = hex_to_lch(source_hex)
    c = pinned_chroma if pinned_chroma is not None else c0
    while c > 0:
        a = c * math.cos(math.radians(hue))
        bb = c * math.sin(math.radians(hue))
        fy = (t + 16) / 116
        fx, fz = fy + a / 500, fy - bb / 200

        def finv(v: float) -> float:
            return v ** 3 if v ** 3 > 0.008856 else (v - 16 / 116) / 7.787

        x, y, z = finv(fx) * WHITE[0] / 100, finv(fy) * WHITE[1] / 100, finv(fz) * WHITE[2] / 100
        lin = (
            3.2406 * x - 1.5372 * y - 0.4986 * z,
            -0.9689 * x + 1.8758 * y + 0.0415 * z,
            0.0557 * x - 0.2040 * y + 1.0570 * z,
        )
        if all(-0.001 <= v <= 1.001 for v in lin):
            break
        c -= 0.5
    r, g, b = lch_to_rgb(t, max(c, 0.0), hue)
    return "#{:02X}{:02X}{:02X}".format(*(round(v * 255) for v in (r, g, b)))


HEADER = """package com.callbackdev.chiaro.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// GENERATED by tools/gen_scheme.py from the source colors of DESIGN.md §2.2.
// Do not hand-edit: change a source there, regenerate, and let PaletteContrastTest
// say whether the result still holds. This scheme is only ever used when dynamic
// color is off or unavailable (ChiaroTheme decides); it is the app looking like
// itself rather than like the wallpaper.
"""


def scheme(index: int, name: str, builder: str) -> str:
    lines = [f"internal val {name}: ColorScheme = {builder}("]
    for role, palette, lt, dt in ROLES:
        src, pinned = SOURCES[palette]
        value = tone(src, pinned, (lt, dt)[index])
        lines.append(f"    {role} = Color(0xFF{value.lstrip('#')}),")
    lines[-1] = lines[-1].rstrip(",")
    lines.append(")")
    return "\n".join(lines)


def main() -> None:
    print(HEADER)
    print(scheme(0, "ChiaroLightScheme", "lightColorScheme"))
    print()
    print(scheme(1, "ChiaroDarkScheme", "darkColorScheme"))


if __name__ == "__main__":
    main()
