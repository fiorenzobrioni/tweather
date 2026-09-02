# DESIGN.md — Chiaro

The design system of Chiaro, written the way `obsidian_syntax/DESIGN.md` is written
for the terminal line: values first, reasons attached, nothing decorative left to
taste at implementation time.

It is Material 3, and the emphasis matters. Material is not a fallback for having no
design: it is a system with a color algorithm, a type scale, a shape scale and a
motion physics, and most apps that "use Material" use its defaults and stop. Chiaro
commits to it — generated color, the expressive scales, real motion — and adds the
three things Material has no opinion about because no other app needs them: a sky
computed from an ephemeris (§3), a ribbon of the day's light (§4), and the palettes
for weather quantities (§9).

**The one rule this document exists to enforce:** every visual decision here is either
a Material token, a value with a measured number beside it, or a rule with a test that
holds it. Nothing is "roughly amber".

---

## 1. Principles

### 1.1 The screen must not lie

The t-series' hard rule, in a UI with no text to say it. Its four visual forms:

| Situation | tweather said | Chiaro shows |
|---|---|---|
| data older than the interval | `# stale` | a freshness chip with the real age, in the warning role, tappable to retry |
| the provider has no data for here | the key is absent | the card is not drawn; the details sheet names what is unavailable and why |
| a computed value | a `//` disclaimer | the word "estimated" in the label, in `onSurfaceVariant` |
| a verdict | `~ unstable  cloud 61%` | the chip's word, plus the number that decided it, on the same line |

Two corollaries that cost layout work and are not negotiable. **A section with no data is
not drawn** — never a card with an em dash in it. And **no placeholder ever renders as a
value**: a skeleton must be visibly a skeleton, which in practice means it is a shimmer
of `surfaceContainerHigh`, never a grey "0°".

### 1.2 Every number says what to do with it

The mainstream inversion of the series' evidence rule. UV 7 is a number, "burns in about
25 minutes" is the answer. Every metric card is a pair: the value in `titleLarge` and its
consequence in `bodySmall` / `onSurfaceVariant`, from a lookup keyed on the value's band.
No card ships without its second line, and if a metric has no honest second line, that
metric belongs in the details sheet and not on the home screen.

### 1.3 Depth is optional, never mandatory

Three levels, and nothing important lives only at the third: **the sentence** (the canvas
headline), **the card** (the number and its meaning), **the sheet** (everything, including
the technical fields). A reader who never taps anything must still be correctly informed.

### 1.4 What Chiaro never does

The anti-pattern list, kept short so it is actually remembered:

- No photographic or 3D weather art. No glass droplets, no animated cartoon clouds.
- No full-screen spinner. Ever. Content first, freshness stated (§1.1).
- No number without its unit, and no unit that disagrees with the setting.
- No color as the only carrier of meaning (§10).
- No "beginner mode" toggle. One app, understandable by default.
- No rainbow ramp for a quantity (§9.1), and no dual-axis chart anywhere.
- No jargon. Not translated jargon — absent jargon.

---

## 2. Color

### 2.1 The scheme is generated, not painted

Chiaro's color comes from Material's tonal palettes, and there are two sources for them:

1. **Dynamic color** (default on): the palettes are derived from the reader's wallpaper by
   the platform. Chiaro consumes `dynamicLightColorScheme` / `dynamicDarkColorScheme` and
   never hardcodes a role over them.
2. **The Chiaro scheme** (the fallback, and an explicit choice in settings for readers who
   want the app to look like itself): generated from the source colors in §2.2 by the same
   tonal algorithm.

Consequence for implementation: **no composable ever names a hex.** It names a role
(`MaterialTheme.colorScheme.primaryContainer`) or a semantic token (§2.3). A hex in a
screen file is a bug, and the sweep test in §12 fails the build over it.

### 2.2 The Chiaro source colors

Three hues, and each one is a time of day the product is actually about:

| Source | Hex | What it is |
|---|---|---|
| Primary | `#E8A33D` | the golden hour |
| Secondary | `#3A7CA5` | daylight sky |
| Tertiary | `#6C5B8C` | the blue hour |
| Neutral | `#8C857A` | warm, so the surfaces read as paper and not as aluminium |
| Neutral variant | `#8A8578` | outlines and dividers |
| Error | `#BA1A1A` | Material's, unchanged — a convention worth borrowing |

Roles resolve as tones off those palettes, exactly per Material (primary = P40 light /
P80 dark, primaryContainer = P90 / P30, and so on). The two that get named here because
everything else is measured against them:

| Role | Light | Dark |
|---|---|---|
| `surface` | `#FCFAF6` | `#141311` |

The two surfaces sit 17.8:1 apart, which is the headroom every token in §2.3 is
measured inside.

Amber as the primary is a deliberate risk. It is the least-used hue in a category that is
overwhelmingly blue, it is the color of the thing this app knows about that others do not,
and Material's tone system keeps it legible where a hand-picked amber would not be: the
primary role at tone 40 is a deep bronze, not a highlighter.

### 2.3 Semantic tokens Material does not have

Material has no slot for "the sky at nautical twilight" or "70% chance of rain". These
live in a `ChiaroColors` object behind a `CompositionLocal`, they are theme-aware, and
their dark values are **selected for dark**, never flipped.

**Verdicts** (§8.7). Green / amber / red is the classic color-vision trap, so the numbers
are stated rather than assumed: measured with the palette validator, `unstable`↔`fail`
separate by ΔE 0.7 under deuteranopia at ink weight. That is not fixable by re-picking
hues, which is exactly why **a verdict is never a colored dot: it is a glyph and a word**,
and the color is the third carrier, not the first.

| Verdict | ink (light) | container (light) | ink (dark) | container (dark) |
|---|---|---|---|---|
| pass | `#0F5C30` 7.8:1 | `#D7EBDD` | `#7FD69A` 10.6:1 | `#173D28` |
| unstable | `#7A5200` 6.6:1 | `#F7E6BF` | `#F2C063` 11.1:1 | `#3D2F08` |
| fail | `#8E1B10` 8.7:1 | `#F9DEDA` | `#FFB4AB` 10.9:1 | `#4A1712` |
| unknown | `#4F5359` 7.4:1 | `#E7E7E4` | `#A8ADB6` 8.2:1 | `#2B2B2E` |

Ratios are against the surface of §2.2; ink-on-container is 5.6:1 or better in both
schemes. `unknown` is deliberately the one hueless entry: not knowing is not a state with
a color, and a gray chip reads as "no answer" without anyone having to be taught it.

**Rain** — a quantity, so a single hue, light to dark, monotonic in luminance (verified,
§12):

```
light  #E3EEF7  #BBD7EB  #8CBADB  #5896C6  #2E6F9E     Y .84 .65 .46 .28 .14
dark   #1A2E3D  #234A63  #2F6B8C  #4A90B5  #79B9DA     Y .03 .06 .13 .25 .44
```

**Temperature** — a diverging quantity, because it has a meaningful middle. Two hues and a
neutral midpoint, never a rainbow; the midpoint is anchored at **15 °C / 59 °F**, a fixed
comfortable reference, and **never at the min/max of what happens to be on screen** (a
scale that re-anchors itself makes a mild week look like a heatwave):

```
light  #2E6F9E  #6BA3C6  #A9C7DC  #DCD7CC  #EBC190  #D9843A  #B45415
dark   #7FB6D8  #4E90BC  #356F99  #4A4740  #93601F  #C1782A  #E39A4A
```

Luminance peaks at the midpoint in light and troughs at it in dark, so in both schemes the
middle recedes and the extremes come forward.

**Freshness / warning**: the `unstable` pair above, reused deliberately — "this data is old"
and "the sky is iffy" are the same class of statement and should not learn two colors.

### 2.4 Rules for using color

- Roles, never hexes (§2.1).
- Text wears text roles (`onSurface`, `onSurfaceVariant`) — **never a data color**. A
  colored mark may sit beside a label; the label itself stays ink.
- Any fill that carries meaning has a text or glyph companion (§10).
- On the canvas, text is white over the scrim contract of §3.6 and nowhere else.

---

## 3. The sky canvas

### 3.1 What it is

The hero of the Today screen is a gradient computed from the sky above the active city:
the sun's altitude from `AstronomyEngine`, the current hour's cloud cover and
precipitation probability, and at night the moon's illumination and altitude. It is not a
mood; it is the same data the screen below it is about, which is why it cannot contradict
that screen — one engine, one sunrise, and a test that says so.

### 3.2 The bands

Solar altitude picks the band; within a band the app interpolates on altitude so the
transition is continuous and a reader watching at sunset sees it move.

| Band | Altitude | top | mid | bottom |
|---|---|---|---|---|
| Day | > 12° | `#4E8FBF` | `#7FB4D6` | `#C7DDEB` |
| Low sun | 6°..12° | `#5583B0` | `#93B5CE` | `#E0CFB4` |
| Golden hour | −0.833°..6° | `#5C7FA8` | `#E0A45C` | `#F3D3A0` |
| Civil / blue hour | −6°..−0.833° | `#2A3E63` | `#4B5F8F` | `#8A7FA8` |
| Nautical | −12°..−6° | `#1B2540` | `#2C3A5E` | `#46527A` |
| Astronomical | −18°..−12° | `#121A2E` | `#18223C` | `#232E4B` |
| Night | < −18° | `#0E1320` | `#131A2A` | `#1A2233` |

The bands are the same in the light and dark schemes. The sky is not a surface: it does
not follow the reader's theme, because at 23:00 it is dark outside whatever the phone is
set to. This is the single deliberate exception to §2.1, and its scrim contract (§3.6) is
what makes it safe.

### 3.3 Cloud and rain

Cloud cover mixes each stop toward `#8E9298` by `0.7 × cloudPct`, so a fully overcast day
keeps 30% of its band and stays recognizably morning or evening. Precipitation probability
above 50% multiplies value by `1 − 0.25 × (p − 50)/50`. Both are applied after the band
interpolation and before the moon lift, in that order, and the order is part of the spec:
clouds hide the moon, not the other way round.

### 3.4 The moon

At night only (altitude < −6°), each stop is lifted toward `#2A3550` by
`illumination × clamp(moonAltitude/40°, 0, 1)`. A full moon high in a clear sky makes a
visibly lighter canvas, which is both true and the reason the sky section says the dark
window is spoiled.

### 3.5 Motion and cost

One shader, one animation, and a hard budget: **the canvas may not cost more than 2 ms per
frame on a mid-range device.** It animates only the interpolation between bands (a slow
crossfade on a 30-second tick, not a per-frame recomputation), and it becomes a static
gradient when the system is in battery saver, when the reader has reduced motion on, or
when the activity is not resumed. No particles, no parallax, no video.

### 3.6 The scrim contract

Text over the canvas is `#FFFFFF` (secondary text `#FFFFFF` at 70% alpha) over a bottom
scrim from `rgba(16,18,22,0.00)` to `rgba(16,18,22,0.55)` covering the text band. The rule
that makes this safe is testable and tested: **for the brightest possible canvas (Day band,
0% cloud, bottom stop), white on the scrimmed band is ≥ 4.5:1.** If a future band breaks
it, the test fails rather than the reader squinting.

---

## 4. The daylight ribbon

A 6dp band (4dp in compact rows) showing one day of light: night, astronomical, nautical
and civil twilight, the golden hours, daylight — drawn with the §3.2 stops at fixed
saturation, with the current moment marked by a 2dp `onSurface` line and a 4dp dot.

It is the app's signature element and the one component that makes a week of rows read as
a season rather than seven identical stripes. It is also, deliberately, **a depiction and
not an encoding**: nobody has to decode a color into a phase, because every phase it shows
is named in text on the Sky screen, and the ribbon's own content description reads the
phases with their times. That is why it is allowed to be a natural sky gradient where §9.1
forbids rainbows for data.

---

## 5. Typography

**Inter** (variable, OFL), with the platform sans as fallback. Never a monospace: the
terminal line owns that, and Chiaro must not read as its sibling. The one exception is
nothing — there is no exception.

| Role | Size / line | Weight | Where |
|---|---|---|---|
| `heroTemperature` (extended) | 64 / 68 | 300 | the canvas' current temperature |
| `displaySmall` | 36 / 44 | 400 | a day's high in the expanded day sheet |
| `titleLarge` | 22 / 28 | 500 | the headline sentence |
| `titleMedium` | 16 / 24 | 600 | section titles, metric values |
| `bodyLarge` | 16 / 24 | 400 | prose in the guide and the journal |
| `bodyMedium` | 14 / 20 | 400 | card body |
| `bodySmall` | 12 / 16 | 400 | the meaning line under a number, timestamps |
| `labelLarge` | 14 / 20 | 500 | buttons, chips, the hour strip's temperature |
| `labelSmall` | 11 / 16 | 500 | axis labels, ribbon legends |

**Every figure that sits in a column is tabular** (`FontFeatureSetting("tnum")`): the hour
strip, the week rows, the journal's deltas. Proportional digits in a column are the
typographic equivalent of a wobbling table, and this app has a lot of columns.

Rounding is a rule, not a call: temperatures to whole degrees everywhere except the
current one and the feels-like, which carry one decimal because the source does;
probabilities to whole percent; wind to whole units; distances to one decimal below 10.

---

## 6. Shape, elevation, spacing

**Shape** — Material's scale, assigned once:

| Component | Shape |
|---|---|
| canvas (bottom corners) | extraLarge, 28dp |
| cards, sheets | large, 16dp |
| metric tiles, hour cells | medium, 12dp |
| chips, buttons, FAB | full |
| ribbon, bars, sparkline ends | 4dp round caps |

**Elevation**: tonal, not shadow. Hierarchy comes from `surface` →
`surfaceContainerLow` → `surfaceContainer` → `surfaceContainerHigh`. Shadow is permitted
at level 1 on scrolled app bars and at level 3 on the FAB, and nowhere else. The series'
distaste for fake depth survives the reskin as restraint rather than prohibition.

**Spacing**: an 8dp grid with a 4dp sub-unit. Screen margin 16dp; card padding 16dp; gap
between cards 12dp; gap between sections 24dp; touch targets never below 48dp.

**Density**: on a 6.1" phone at default font size, the canvas, the headline sentence and
the first hours of the strip are above the fold. That is the layout's acceptance test.

---

## 7. Motion

Material 3 Expressive's spring physics, from `MaterialTheme.motionScheme`:

| Kind | Spec | Used for |
|---|---|---|
| spatial default | spring(damping 0.8, stiffness 380) | anything that moves or resizes |
| spatial fast | spring(damping 0.9, stiffness 800) | chips, toggles, small state |
| effects | spring(damping 1.0, stiffness 1600) | color, alpha, elevation |

Shared-element transition from a week row to its day sheet; predictive back everywhere;
the pager between places moves the canvas with it, so switching city looks like turning to
another sky. **Reduced motion collapses every one of these to a 100 ms fade**, and the
canvas freezes (§3.5). No animation ever gates information: a reader who disables motion
sees the same content at the same moment.

---

## 8. The component kit

Each entry is the contract; the Compose signatures land in Fase 1.

**8.1 SkyCanvas** — the gradient (§3), the place name, `heroTemperature`, condition,
feels-like, the daylight ribbon, the headline sentence, the scrim (§3.6). Collapses on
scroll into the app bar, keeping place and temperature.

**8.2 FreshnessChip** — appears only when the data is older than the update interval.
Warning role, the real age ("3 hours ago"), tappable to retry, with a progress state while
retrying. Never a toast: a toast is gone before it is read.

**8.3 HourStrip** — horizontal, 24 cells from the next full hour, each 56dp wide: hour,
icon, temperature (tabular), rain probability. Under it a **rain sparkline**: single series,
rain ramp (§2.3), 2px line, 4px rounded ends, no legend (one series is named by its title),
values direct-labeled only at the peaks.

**8.4 TimelineRow** — the merged day (VISION §5.2.4): time, icon or event glyph, one line
of prose, optional verdict chip. Sun events, weather turns and the reader's own alerts use
the same row; only the leading glyph differs.

**8.5 DayRow** — weekday, icon, rain probability, the **temperature range bar** and the
ribbon. The bar is one horizontal track per day, all seven **sharing one scale across the
week** so the week has a shape, filled with the diverging temperature ramp (§2.3) and
anchored at 15 °C; the low and high are printed at its ends in tabular figures, because a
colored bar is not a number.

**8.6 MetricTile** — icon, label, value (`titleMedium`, tabular), meaning line
(`bodySmall`, `onSurfaceVariant`). Never ships without the meaning line (§1.2). Tapping
opens the details sheet at that metric.

**8.7 VerdictChip** — glyph + word + evidence, in that order: `✓ Great · 12% cloud`. The
container is the verdict container color, the text is the ink color. **Never the color
alone** (§2.3), never a bare dot, never a number without the word.

**8.8 MomentCard** — a sky event: name in plain words ("Golden hour, evening"), time,
verdict chip, the number behind it, a bell for a reminder. The dotted job id never appears.

**8.9 RuleSentence** — the alert builder as a sentence of tappable chips: *Notify me when*
`[rain, next 6 h]` *is* `[above]` `[70%]`. Every chip opens a picker; no free-text field
for a value with a range, which is how tweather's "a syntax error is not writable" property
survives into a UI with no syntax.

**8.10 JournalEntry** and **DriftStrip** — an entry is a line of prose with its numbers.
The drift strip is one row per target day and one column per fetch, colored on **the
metric's own ramp** (rain on the rain ramp, temperature on the diverging one) rather than
on a good/bad scale: whether Saturday got "better" is a judgement, and the judgement
belongs in the sentence beside the strip, not in the color. Legend always present, cells
≥ 8dp, and a table view behind a long press for anyone who cannot read the colors at all.

**8.11 States** — empty ("no place yet", with the one action that fixes it), error (what
failed, in plain language, and a retry), stale (§8.2), loading (a shimmer that cannot be
mistaken for a value, §1.1).

**8.12 Navigation** — a Material 3 `NavigationBar` with four destinations, a place switcher
in the app bar with a dots indicator, and a horizontal pager between saved places.

---

## 9. Charts and quantities

### 9.1 The three rules

1. **A quantity gets one hue, light to dark** (rain). A quantity with a meaningful middle
   gets two hues and a neutral midpoint (temperature). Never a rainbow, in either case.
2. **One axis.** No chart in this app ever carries two scales.
3. **The scale is anchored to the world, not to the data on screen** — 15 °C for
   temperature, 0–100% for probabilities. A self-scaling axis turns a quiet week into a
   dramatic one.

### 9.2 Marks

2px lines, 4px rounded ends anchored to the baseline, markers ≥ 8dp, a 2px surface gap
between adjacent fills, recessive gridlines (`outlineVariant` at 1dp, horizontal only).
Direct labels on the extremes only — never a number on every point.

### 9.3 Every chart has a text equivalent

The sparkline's peaks are printed, the range bar's ends are printed, the drift strip has a
table view. This is both the accessibility floor and §1.2: a picture of a number is not a
number.

---

## 10. Accessibility

- **Contrast**: every ink token ≥ 4.5:1 against its surface, measured in §2.3 and asserted
  in §12. Non-text marks ≥ 3:1. The canvas is covered by the scrim contract (§3.6).
- **Never color alone**: verdicts carry a glyph and a word; the drift strip has a table;
  chart series are direct-labeled.
- **Type scale to 200%**: layouts wrap and reflow, they do not clip or ellipsize a value.
  The week rows and the metric grid are the two places this is tested.
- **TalkBack**: reading order is canvas → sentence → freshness → content. Every icon has a
  description that says the word ("mostly cloudy"), never the glyph. The ribbon reads its
  phases with times. Charts announce their extremes and their current value.
- **Reduced motion**: §7. **Touch targets**: ≥ 48dp, always.
- **Color vision**: the status set's measured CVD separation is in §2.3, and the mitigation
  is structural, not hopeful.

---

## 11. Localization in the UI

Everything on screen is prose or data, so **everything localizes** (VISION §8) — there is
no code register in this product to protect. Two mechanical consequences for design:

- Italian runs 15–25% longer than English. Every label is laid out for the longer string;
  no single-line assumption survives without a wrap test.
- Numbers and dates go through the locale's formatter, always: decimal separator, day
  names, 12/24-hour clock. A hand-built `"$h:$m"` is a bug.
- RTL is not a target language today, but no layout may hardcode left/right — start/end
  only, so that decision stays cheap.

---

## 12. Implementation and the guards

```
ui/theme/
  Color.kt      raw tokens, private to the package
  Scheme.kt     light/dark ColorScheme + the dynamic-color branch
  ChiaroColors.kt   the semantic extras of §2.3, behind a CompositionLocal
  SkyPalette.kt     the canvas bands of §3.2 and the mixing rules of §3.3–3.4
  Type.kt Shape.kt Motion.kt
  ChiaroTheme.kt    the entry point
```

Three tests keep this document from rotting, in the series' habit of turning a design rule
into something CI can fail:

- **`PaletteContrastTest`** asserts every ratio printed in §2.3 and the monotonicity of the
  two ramps. If a token is re-picked, the numbers in this file must be re-measured.
- **`ScrimContractTest`** asserts §3.6 against the brightest band.
- **`NoRawColorTest`** sweeps the UI sources and fails on a hex literal outside
  `ui/theme/`.

---

## 13. Open items

1. **The icon family** (VISION §4.5) — adopt a permissively licensed set, commission, or
   draw. Requirements: 24dp grid, 2dp stroke, two tones, optical sizes 24/32/48, animated
   variants for rain, snow, wind, sun and moon, full WMO coverage plus the sky events. The
   only line in this document that code cannot satisfy.
2. **Dynamic color default** — on, as written here. Worth revisiting after the first
   screenshots: a wallpaper-derived scheme makes every store screenshot a different app.
   Likely resolution: dynamic on device, the Chiaro scheme in the store assets.
3. **The brand mark** — the launcher icon in the skeleton is a placeholder (a sun over the
   ribbon). The real one is drawn with the icon family, from the same two elements.
4. **`heroTemperature`** is an extended type role, not a Material one. Confirm it survives
   contact with the expressive scale rather than becoming `displayLarge` with a tighter
   line height.
