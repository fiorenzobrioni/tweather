#!/usr/bin/env python3
"""Renders the palette to an HTML sheet, for the step no test can do: looking at it.

The dataviz rule the design system borrows — "render it and look at it" — is what found
that the golden hour was not golden at 3°, which every contrast and monotonicity test in
the suite had happily passed.

It reads the hexes **out of the Kotlin sources**, never from a copy of them: a preview
that has its own idea of the palette is a preview that lies the first time somebody
retunes a token.

    python3 tools/palette_sheet.py > /tmp/palette.html
    chromium --headless --screenshot=/tmp/palette.png --window-size=1160,1200 file:///tmp/palette.html
"""
from __future__ import annotations

import pathlib
import re
import sys

THEME = pathlib.Path("app/src/main/kotlin/com/callbackdev/chiaro/ui/theme")

SKY = re.compile(
    r"(-?[\d.]+) to SkyGradient\(Color\(0xFF([0-9A-Fa-f]{6})\), "
    r"Color\(0xFF([0-9A-Fa-f]{6})\), Color\(0xFF([0-9A-Fa-f]{6})\)\)"
)
VERDICT = re.compile(r"(\w+) = VerdictColors\(Color\(0xFF([0-9A-Fa-f]{6})\), Color\(0xFF([0-9A-Fa-f]{6})\)\)")
RAMP = re.compile(r"(rainRamp|temperatureRamp) = listOf\((.*?)\)\s*\n", re.S)
HEX = re.compile(r"0xFF([0-9A-Fa-f]{6})")


def read(name: str) -> str:
    path = THEME / name
    if not path.is_file():
        sys.exit(f"run me from the repo root: {path} not found")
    return path.read_text()


def anchors() -> list[tuple[float, tuple[str, str, str]]]:
    found = SKY.findall(read("SkyPalette.kt"))
    if not found:
        sys.exit("no sky anchors parsed — did SkyPalette.kt change shape?")
    return [(float(a), (f"#{t}", f"#{m}", f"#{b}")) for a, t, m, b in found]


def semantic() -> dict:
    text = read("ChiaroColors.kt")
    light, dark = text.split("internal val ChiaroDarkColors")
    out = {}
    for label, chunk in (("light", light), ("dark", "internal val ChiaroDarkColors" + dark)):
        verdicts = {n: (f"#{i}", f"#{c}") for n, i, c in VERDICT.findall(chunk)}
        ramps = {n: [f"#{h}" for h in HEX.findall(body)] for n, body in RAMP.findall(chunk)}
        if len(verdicts) != 4 or len(ramps) != 2:
            sys.exit(f"{label}: parsed {len(verdicts)} verdicts and {len(ramps)} ramps, expected 4 and 2")
        out[label] = {"verdicts": verdicts, **ramps}
    return out


def surfaces() -> dict:
    text = read("Scheme.kt")
    light, dark = text.split("internal val ChiaroDarkScheme")
    def roles(chunk: str) -> dict:
        return {
            m.group(1): "#" + m.group(2)
            for m in re.finditer(r"(\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)", chunk)
        }
    return {"light": roles(light), "dark": roles("internal val ChiaroDarkScheme" + dark)}


def mix(a: str, b: str, t: float) -> str:
    pa = [int(a.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4)]
    pb = [int(b.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4)]
    return "#{:02X}{:02X}{:02X}".format(*[round(x + (y - x) * t) for x, y in zip(pa, pb)])


def sky_at(altitude: float, table) -> tuple[str, str, str]:
    altitude = max(-90.0, min(90.0, altitude))
    upper = [a for a in table if a[0] >= altitude][-1]
    lower = [a for a in table if a[0] <= altitude][0]
    if upper[0] == lower[0]:
        return upper[1]
    t = (upper[0] - altitude) / (upper[0] - lower[0])
    return tuple(mix(u, l, t) for u, l in zip(upper[1], lower[1]))


def main() -> None:
    table = anchors()
    sem = semantic()
    sur = surfaces()
    p = print
    p("<html><head><meta charset=utf-8><style>"
      "body{font-family:system-ui;margin:0;background:%s;color:%s}"
      "h2{font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:#7C7768;margin:20px 16px 8px}"
      ".row{display:flex;gap:4px;padding:0 16px}.b{flex:1;height:120px;border-radius:8px;position:relative;overflow:hidden}"
      ".l{position:absolute;bottom:0;left:0;right:0;color:#fff;font-size:11px;padding:14px 5px 5px;"
      "background:linear-gradient(transparent,rgba(16,18,22,.55))}"
      ".ramp{display:flex;height:32px;border-radius:8px;overflow:hidden;margin:0 16px}.ramp div{flex:1}"
      ".chips{display:flex;gap:8px;padding:0 16px}"
      ".chip{padding:6px 12px;border-radius:99px;font-size:13px;font-weight:500}"
      ".dark{background:%s;color:%s;padding:1px 0 24px;margin-top:24px}.dark h2{color:#969081}"
      "</style></head><body>" % (sur["light"]["surface"], sur["light"]["onSurface"],
                                 sur["dark"]["surface"], sur["dark"]["onSurface"]))

    p("<h2>The sky, degree by degree</h2><div class=row>")
    for altitude in (30, 12, 8, 6, 4, 2, 0, -2, -4, -6, -9, -12, -15, -18, -40):
        s = sky_at(altitude, table)
        p(f"<div class=b style='background:linear-gradient(180deg,{s[0]},{s[1]},{s[2]})'>"
          f"<span class=l>{altitude}°</span></div>")
    p("</div>")

    for mode in ("light", "dark"):
        wrap = "<div class=dark>" if mode == "dark" else ""
        p(wrap)
        p(f"<h2>Rain ramp · {mode}</h2><div class=ramp>")
        for c in sem[mode]["rainRamp"]:
            p(f"<div style='background:{c}'></div>")
        p(f"</div><h2>Temperature ramp · {mode}</h2><div class=ramp>")
        for c in sem[mode]["temperatureRamp"]:
            p(f"<div style='background:{c}'></div>")
        p(f"</div><h2>Verdicts · {mode}</h2><div class=chips>")
        for name, glyph in (("pass", "✓"), ("unstable", "~"), ("fail", "✗"), ("unknown", "?")):
            ink, container = sem[mode]["verdicts"][name]
            p(f"<span class=chip style='background:{container};color:{ink}'>{glyph} {name}</span>")
        p("</div>")
        p(f"<h2>Surfaces · {mode}</h2><div class=ramp>")
        for role in ("surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer",
                     "surfaceContainerHigh", "surfaceContainerHighest", "surfaceDim"):
            p(f"<div style='background:{sur[mode][role]}'></div>")
        p("</div>")
        if mode == "dark":
            p("</div>")
    p("</body></html>")


if __name__ == "__main__":
    main()
