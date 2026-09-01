# VISION.md — Chiaro

> **The weather app that tells you what to do about it.**
> The same engine as tweather, with the code editor taken off.

Chiaro is the **daylight edition** of [tweather](https://github.com/fiorenzobrioni/tweather): the same
data layer, the same astronomy engine, the same rules engine, the same honesty — rendered for
somebody who has never opened a terminal. tweather is furniture for developers and was built that
way on purpose; Chiaro is the same product for everybody else.

It is not a replacement and not a rewrite. The two apps ship side by side, from the same developer
account, sharing a verified core (§7). tweather keeps its audience; Chiaro goes after the one
tweather deliberately excluded.

This document fixes the premise (§1), the positioning (§2), the product identity (§3), the design
language (§4), the screens (§5), the parity map (§6), the architecture and what is reused (§7),
localization (§8), data/battery/privacy (§9), the roadmap (§10), success criteria (§11) and the
decisions still open (§12).

---

## 1. The premise

### 1.1 Two editions, one engine

Everything below the UI in tweather is domain logic with no opinion about looking like a code
editor: an Open-Meteo client, a mapper, a Room history, a WorkManager sync job, a WMO code table,
an alert engine, a rules engine, and a full solar/lunar ephemeris. That is roughly two thirds of
the codebase and effectively all of the tests. Chiaro takes it as it stands and puts a Material 3
product on top.

The consequence to keep in mind through the whole document: **no feature in this VISION has to be
invented, only presented.** The engineering risk of Chiaro is not "can we compute the golden hour",
it is "can a person who does not know what a golden hour is understand why we are telling them".

### 1.2 What survives from the t-series

The skin is thrown away. The soul is not, because the soul was never the monospace font.

- **The file must not lie** becomes **the screen must not lie**. Missing data is absent, not zeroed.
  Stale data says how old it is instead of posing as current. Estimates are labeled. A number is
  never invented to fill a layout.
- **A verdict always ships with its arithmetic.** tweather prints `~ unstable  cloud 61%`; Chiaro
  prints a chip that says *So-so* with *61% cloud* next to it. The evidence travels with the
  judgement, always.
- **Battery is a feature.** One shared periodic job, no polling, no foreground services, no
  background location, inexact alarms only.
- **No account, no ads, no tracking, no API key.** Open-Meteo stays the provider precisely because
  it keeps the app free without a login wall.
- **PLANNING.md is a log**, not a backlog: every decision and every deviation recorded with its
  reason. Tests run before any APK is built.

### 1.3 What is deliberately left behind

- The editor metaphor in full: no files, no tabs, no gutter, no syntax colors, no `$` commands,
  no diff hunks, no crontab, no JSON on screen.
- The fixed dark palette. Chiaro follows the system, supports dynamic color, and its default look
  is light.
- Emoji as iconography (§4.5).
- Jargon of every kind. Not "translated jargon" — **absent** jargon. Where tweather taught the
  reader a borrowed word and glossed it in `HELP.md`, Chiaro simply never borrows it.
- The dot-notation vocabulary (`golden_hour.pm`, `next_6h.precip_chance_max`). It stays in the code
  as identifiers and never reaches a screen.

---

## 2. Positioning

### 2.1 The field

The Play Store's weather category is saturated at both ends and empty in the middle.

- **Everyday apps** (the pre-installed one, the big branded ones, the widget-first ones) answer
  "what is the temperature and will it rain". They are competent, ad-supported, account-hungry,
  and interchangeable. None of them knows anything about the sky beyond a sunrise time.
- **Sky apps for enthusiasts** (Astrospheric, Clear Outside, Ouranos, Solora and the astronomy
  almanacs) answer "will I see anything tonight" with cloud, seeing and transparency layers. They
  are excellent and they are unusable as an everyday weather app: the audience is astrophotographers
  and the UI says so.

Nothing in between asks the question most people actually have, which is neither of those two:
**"is it worth going outside, and when."**

### 2.2 The gap Chiaro takes

Three things Chiaro can do that the everyday apps structurally cannot, because they do not compute
them, and that the enthusiast apps will not do, because their audience is not this one:

1. **The sky's agenda, with a verdict.** Sunrise, golden hour, blue hour, the genuinely dark window,
   the moon, the meteor peaks — each with *will the sky allow it* answered from the same hourly
   cloud and precipitation forecast the app already downloads, and the number that decided it
   printed beside the answer. The engine exists and is tested; this is presentation work.
2. **Memory of the forecast.** tweather already stores every fetch as a snapshot and diffs the next
   days against the previous fetch. Nobody in the mainstream tells a user that Saturday's forecast
   *changed*: rain 70% → 30% is more actionable than either number alone, and it is the single most
   share-worthy screen in the product.
3. **Alerts the user writes.** Not five fixed switches — an alert built from real variables
   ("rain in the next 6 hours above 70%", "tomorrow morning below 0°"), with templates for people
   who do not want to build anything, and a preview that runs the rule against today's data.

Two more differentiators are look-and-feel rather than data, and matter just as much on a store
page: **the sky canvas is computed** (§4.2), and **the app opens already answered** (§3.3).

### 2.3 The promise

> **Il meteo che ti dice cosa farne.**
> Weather that tells you what to do about it, and never invents anything to say it.

---

## 3. Product identity

### 3.1 What Chiaro is

A fast, honest, beautiful everyday weather app with a planner for the sky attached, free of ads,
accounts and tracking, that works offline with the last data it managed to fetch and says so.

### 3.2 What Chiaro is not

- Not a radar/satellite app. Open-Meteo has no imagery and Chiaro does not pretend otherwise.
  (Radar is the single most requested weather feature that this product will not have; §12.)
- Not a social app. No sharing feed, no photo uploads, no community reports.
- Not an astronomy app. The sky section plans the naked-eye sky; seeing, transparency, Bortle
  ratings and telescope talk stay out.
- Not a dashboard for enthusiasts. Every advanced number is one tap deeper than the answer.
- Not a re-theme of tweather. A person who used both should recognize the honesty and nothing else.

### 3.3 Product principles

1. **Open and know.** The app renders the cached report before the network is even asked. There is
   no full-screen spinner in this product, ever: content first, freshness stated, refresh silent.
2. **One sentence before any number.** The top of the home screen is a computed line — "Umbrella
   around 17:00, clear after that" — built from the alert engine and the next hours. Numbers follow
   for the people who want them.
3. **Every number says what to do with it.** UV 7 is not a number, it is "burns in about 25 minutes,
   wear something". AQI 112 is "fine for a walk, not for a run". This is the mainstream inversion of
   the series' evidence rule: tweather ships the arithmetic with the verdict, Chiaro ships the
   verdict with the arithmetic.
4. **The screen must not lie** (§1.2), including about itself: a section with no data is not drawn.
5. **Depth is optional, never mandatory.** Three levels: the sentence, the card, the detail sheet.
   Nothing important lives only at level three.
6. **Speed is a feature with a number on it**: cold start to first painted content under 400 ms on a
   mid-range device, every navigation under one frame budget, no jank on the canvas.

---

## 4. Design language

The design system is called **Chiaro** as well, and its written form lives in `DESIGN.md`
(to be produced in Fase 1, the way `obsidian_syntax/DESIGN.md` did for the t-series).

### 4.1 Material 3 Expressive, done properly

Material 3 with the expressive type scale, shape scale and spring motion — used as a system, not as
defaults. Chiaro must read as *a designed Material app*, which means committing to the parts most
apps skip: real shape variety, real motion physics, button groups and FAB menus where they earn
their place, and a color scheme that is generated rather than hand-picked.

- **Dynamic color** on by default (Material You, wallpaper-derived), with a curated in-app palette
  as the fallback and as an explicit choice for people who want the app to look like itself.
- **Light is the default**, dark is complete and equal, both follow the system. The t-series' dark
  monopoly was a stylistic position; here it would just be an accessibility problem.
- **No skeuomorphic weather art.** No 3D glass droplets, no photographic backgrounds. The visual
  interest comes from color, gradient and motion driven by real data.

### 4.2 The sky canvas (the signature)

The home screen's hero is a gradient computed from the actual sky above the active city: the sun's
altitude from the ephemeris (`AstronomyEngine`), the cloud cover and precipitation probability of
the current hour, and the moon's illumination at night. At 05:40 under 90% cloud it is a flat grey
blue; at 19:20 under a clear sky it is the amber the reader can see out of the window; at 02:00
under a full moon it is not black.

This is a differentiator precisely because it is not decoration: it is the same ephemeris that
drives the sky section, so the canvas cannot disagree with the data below it (there is a test for
that in tweather already — one engine, one sunrise). Competitors animate a generic loop; Chiaro
paints the sky it just described.

The canvas is capped: it never costs more than one gradient shader and one low-frequency animation,
and it degrades to a static gradient when the system is in battery saver or the reader has reduced
motion enabled.

### 4.3 The daylight ribbon

A thin horizontal band, used on the canvas and on every day row of the week, showing night,
astronomical/nautical/civil twilight, daylight and the golden hours as color segments, with "now"
marked. It is the one component that says at a glance what a whole day of light looks like, it is
unique to this app, and it is recognizable in a store screenshot at thumbnail size. It comes free
from the ephemeris.

### 4.4 Type, shape, motion, density

- **One type family**, a humanist sans (Inter or the platform default; decided in Fase 1), never
  monospace — the t-series owns monospace and Chiaro must not read as its sibling.
- **The expressive type scale**, used with real contrast: the current temperature is display-sized,
  the headline sentence is a title, everything else is body. No mid-sized soup.
- **8dp grid**, generous corner radii from the M3 shape scale, cards with tonal surfaces and no
  drop shadows heavier than M3's elevation-1 (the series' distaste for fake depth survives, but as
  restraint rather than prohibition).
- **Spring motion** for every state change, shared-element transitions from a day row to its detail,
  predictive back everywhere.
- **Density**: the home screen fits Now, the sentence, and the beginning of the next hours above the
  fold on a 6.1" phone. Everything else is a scroll, never a tab.

### 4.5 Iconography (the emoji have to go)

tweather renders weather as Unicode emoji inside the JSON, which was right there and is wrong here:
emoji are rendered by the system font, differ per device and per OEM, cannot be tinted, and read as
cheap in a designed layout. Chiaro needs a **vector icon family**: one weight, two tones, animated
for four or five states (rain, snow, wind, sun, moon), covering the full WMO code table plus the
sky events.

This is the one substantial *new* asset the product needs and the one line item that costs real
design money. Options, in order of preference: adopt a permissively licensed open set (Meteocons and
similar sets are MIT — the license must be verified file by file before adoption), commission a
small custom set, or draw a minimal set in-house for v1 and commission later. Recorded here because
it is the only dependency in this document that code cannot satisfy.

### 4.6 Accessibility

Non-negotiable, and one of the reasons this edition exists.

- Every icon and every chip has a content description that says the *word*, not the glyph.
- Contrast checked in both schemes and against dynamic-color palettes, including the canvas: text
  over the gradient always sits on a scrim that guarantees the ratio.
- Full support for large font scales up to 200% — the layouts wrap, they do not clip.
- Reduced-motion honored (§4.2). No information carried by color alone: every verdict chip carries
  a word.
- TalkBack reading order verified per screen; the headline sentence is the first thing read.

---

## 5. The screens

### 5.1 Navigation model

Four bottom destinations, a place switcher in the app bar, and a horizontal pager between saved
places. That is the fastest arrangement for this content and it is the one the audience already
knows from every other weather app.

```
┌──────────────────────────────────────┐
│  Milano  ⌄                       ⚙   │   app bar: place switcher + settings
│  ┌────────────────────────────────┐  │
│  │      the sky canvas            │  │   computed gradient + daylight ribbon
│  │  22°  Mostly clear             │  │
│  │  Umbrella around 17:00,        │  │   ← the headline sentence
│  │  clear after that.             │  │
│  └────────────────────────────────┘  │
│  ...                                 │
│                                      │
│   Today    Sky    Alerts    Journal  │   bottom navigation
└──────────────────────────────────────┘
```

Swiping left/right moves between saved places (dots on the app bar). Settings and place management
live in the app bar, not in a tab: they are visited once a month.

### 5.2 Today

The spine of the product, one vertical scroll, in this order:

1. **The canvas**: place, current temperature, condition, feels-like, the daylight ribbon, and the
   headline sentence (§3.3).
2. **A freshness chip** when, and only when, it matters: "Updated 3 hours ago" in amber, tappable to
   retry. Pull to refresh; no FAB on this screen, because the app refreshes itself.
3. **Next hours**: a horizontal strip, 24 hours from the next full hour, each with icon, temperature
   and rain probability, plus a rain-probability sparkline underneath so a wet stretch is a shape
   rather than twelve numbers.
4. **The rest of the day**: the merged timeline, and the second differentiator on this screen. Sun
   and sky events, weather turns ("rain starts around 17:00"), and the reader's own alerts, in one
   chronological list. This is `sky.crontab` plus the hourly forecast plus `alerts.rules`, collapsed
   into the artifact a person actually keeps in their head: what is going to happen today, in order.
5. **What changed** (when something did): "Saturday improved: rain 70% → 30%". Two or three lines,
   tapping opens the Journal.
6. **The week**: seven rows, each with icon, a min/max bar aligned across the whole week (so the
   week has a *shape*), rain probability, and the daylight ribbon. Tapping a row expands it in
   place with a shared-element transition into that day's hours.
7. **Details**: air quality, pollen, UV, wind, humidity, pressure, visibility, dew point — as a grid
   of small cards, each with its number and its one-line meaning (§3.3). Cards for data the region
   does not have are not drawn (pollen outside Europe simply is not there).

### 5.3 Sky

The differentiator with the most engineering already behind it.

- **Tonight**: a hero verdict — *Great* / *So-so* / *No chance* / *Not sure yet* — for the dark
  window, with the numbers that decided it and the reason when it was not the clouds ("the moon is
  up, 94% lit").
- **Today's moments**: a list of the reader's subscribed events (sunrise, golden hour, sunset, dark
  window, moon by default — four to five, never the full catalog), each a card with time, verdict
  chip, evidence, and a bell for a reminder.
- **Next events**: the calendar ahead — meteor peaks with their dates, full moon, solstice,
  equinox — with the verdict where the forecast reaches that far and an honest "too far out to
  say" where it does not.
- **Add a moment**: the 32-job catalog, grouped (Sun · Moon · Night · Seasons · Meteor showers),
  each with a one-line explanation of what it is. This is where a person learns what a blue hour is,
  by adding one.
- **Reminders**: 15/30/60 minutes before, per moment, plus a default; inexact alarms, exactly as in
  tweather. A reminder for an event the sky will ruin is off by default.

Everything on this screen speaks words: `golden_hour.pm` is "Golden hour, evening", and the dot
notation never appears.

### 5.4 Alerts

Two groups, one screen.

- **Ready-made**: severe weather, rain in the next hours, morning summary — switches with a plain
  description of what each will actually send and when.
- **Yours**: the rules engine, approached from the answer rather than the syntax.
  - **Templates first**: "Tell me when I can ride", "Ice tomorrow morning", "A window to run",
    "High UV", "A clear night". Picking one creates a real rule with sensible thresholds, already on.
  - **The builder** is a sentence with three tappable chips: *Notify me when* **[rain, next 6 h]**
    *is* **[above]** **[70%]**, plus an optional second condition and the message. Pickers, never a
    text field for a value that has a range: the property tweather bought with token-by-token
    editing — a syntax error cannot be written — is kept, and it costs nothing in a chip UI.
  - **Preview** runs the rule against the current data and says what it would have done, with no
    notification posted.
  - Each rule card shows its sentence, its state, and when it last fired.

### 5.5 Journal

One Room row per fetch already carries the snapshot, the forecast for the coming days, the rules
that fired and the sky jobs observed. The Journal is that table read as prose, newest first, grouped
by day:

- "Saturday's forecast improved: rain 70% → 30%, high 24° → 27°"
- "Your alert *bike* fired at 07:12"
- "Sunrise, seen: clear sky, 8% cloud"

And one chart the rest of the store does not have: **forecast drift** — how the next seven days'
forecast changed over the last several days, as a compact heat strip (one row per target day, one
column per fetch, color = the metric). It answers "has this been getting better or worse", which is
the question anybody planning a weekend actually asks. It is built from data already on disk.

The Journal is also where offline honesty lives: a failed fetch is an entry, with the reason in
plain language.

### 5.6 Places

A bottom sheet from the app bar: saved places (with current temperature next to each, reorderable),
the GPS entry pinned at the top with its own treatment, search-as-you-type with recent searches, and
swipe-to-remove with undo. The active place is a tap; the pager is a swipe. Adding a place is the
one place a FAB earns its keep.

### 5.7 Settings and the guide

Standard M3 preferences, grouped: units, appearance (theme, dynamic color, canvas motion), update
frequency, notifications, widgets, language, about. Reset is a destructive-styled item with a
confirmation dialog rather than a two-tap `$` command.

`HELP.md` becomes **the guide**: short, illustrated, answering the four questions a new reader has
(where the data comes from, what the sky verdicts mean, how alerts work, why there is no radar).
Reachable from settings and from a one-time card on the home screen. It never explains an interface
element, because an interface element that needs explaining is a bug in this edition.

### 5.8 First run

One screen, two answers: use my location, or search for a place. Skipping is allowed and lands on a
real "no place yet" state, exactly as tweather's `ActiveSource.None`. No carousel, no account, no
permission asked before the sentence explaining why it is asked. Notifications are requested the
first time the reader turns on something that needs them, never at startup.

### 5.9 Widgets

Glance, three sizes, matching the app's dynamic color:

- **Now**: icon, temperature, place.
- **Today**: now plus the next hours strip plus the headline sentence.
- **Sky**: the next moment and its verdict — the widget nobody else ships.

A widget never invents: with stale data it says how old it is, and with no place configured it says
so and opens the app.

---

## 6. Parity map

Nothing in tweather is dropped. Everything moves to the surface that fits a Material product.

| tweather | Chiaro |
|---|---|
| `weather_data.json` (current, air quality, pollen, astronomical, hourly, daily, system info) | **Today**: canvas, next hours, week, details grid |
| `README.md` tab (human summary, `## Status`) | **The headline sentence** + the "rest of the day" timeline |
| `sky.crontab` (32 jobs, verdicts, `--notify`) | **Sky**: moments, verdicts with evidence, reminders |
| `cities.json` (search, saved cities, GPS entry) | **Places** sheet + the place pager |
| `settings.config` | **Settings** |
| `alerts.rules` (user rules, dry run, master switch) | **Alerts → Yours**: templates + chip builder + preview |
| built-in alert toggles | **Alerts → Ready-made** |
| `history.diff` + `forecast.diff` + `sky_runs.log` + fired-rule check lines | **Journal** (one entry per fetch) + the forecast-drift chart |
| `HELP.md` + `$ tweather init` | **Guide** + first run |
| widget (`tweather --now`, terminal tiers) | **Widgets** (Now / Today / Sky) |
| theme profiles Obsidian/Dracula/Monokai | dynamic color + curated palette, light and dark |

---

## 7. Architecture

### 7.1 Modules

```
:core:domain    pure Kotlin/JVM. models, WMO codes, freshness/recency,
                AlertEngine, rules/, sky/ (ephemeris, catalog, verdicts, reminders)
:core:data      Android library. Open-Meteo APIs + DTOs + mapper, WeatherRepository,
                Room history, disk cache, DataStore stores, LocationProvider
:core:sync      Android library. the single WorkManager job, the alarm scheduler,
                the notification *decisions* (never the notification copy)
:app            everything visible: Compose UI, Glance widgets, notification rendering, strings
```

The split is not ceremony: it is what makes the core extractable later (§7.3) and it is what keeps
a designer's change from being able to break the ephemeris.

### 7.2 What is reused, adapted, dropped

**Reused essentially verbatim** (with the package renamed and the JVM test suite coming along, which
is the real prize — the engine arrives already verified):

- `domain/model/`, `domain/WeatherCodes`, `WeatherFreshness`, `WeatherRecency`, `WeatherException`
- `domain/AlertEngine`, `domain/rules/` (variables, engine, messages, rule model)
- `domain/sky/` in full: `AstronomyMath`, `AstronomyEngine`, `SkyJobCatalog`, `MeteorShowerTable`,
  `SkyVerdictEngine`, `SkyAlmanac`, `SkyScheduler`, `SkyReminderPolicy`
- `data/` in full: `remote/`, `mapper/`, `WeatherRepository`, `local/` (Room v4 schema unchanged —
  the Journal reads exactly the columns that already exist), all DataStore stores
- `notifications/WeatherSyncWorker`, `AlertScheduler`, `SkyAlarmScheduler`, `SkyAlarmReceiver`

**Adapted**: the notifiers (same decisions, new copy and new channels), `WeatherLocalization`'s
translation tables (they move into `:core:domain` and stay, they are the WMO vocabulary), the widget
content builder (rewritten for Glance, the freshness logic kept).

**Dropped entirely**: everything under `ui/` (the editor kit, the document builders, the syntax
highlighters, the terminal components), the RemoteViews widget layouts, the three theme profiles.
That is around 6,000 lines of UI thrown away and roughly 8,000 lines of engine kept — the ratio is
the whole argument for this project.

**Small refactors the extraction requires**, all mechanical and worth doing in Fase 0:

- `TemperatureUnit`/`WindSpeedUnit`/`UnitSettings` move from `data` into `:core:domain`, because
  `RuleVariables` reads them and the domain must not depend on the data layer.
- The two places where a document builder takes an Android `Resources` do not travel: in Chiaro all
  prose is a Compose string resource read at the composable, so the core stays Android-free where it
  claims to be.
- `ServiceLocator` stays hand-rolled (no DI framework — the series' rule holds), with the graph
  split per module.

### 7.3 The shared-core decision

Three options were considered:

1. **Copy at fork.** The series' own precedent (tsteps and thabit copy the editor kit from tweather
   and never link it). Zero infrastructure, immediate, and it means a provider change has to be
   fixed twice.
2. **Extract `weather-core` into its own repo**, consumed by both apps via git subtree or a
   published artifact. Correct, and it puts a release process between the developer and a one-line
   fix.
3. **Monorepo**: both apps as modules of one repository. Cheapest technically, and it makes the two
   products look like one on GitHub, which they are not.

**Decision: option 1 now, option 2 when it bites twice.** Chiaro is seeded by copying the core, and
`UPSTREAM.md` records the exact tweather commit each core file came from. The first time the same
bug has to be fixed in both repos, the ledger is what makes the extraction a mechanical afternoon
rather than an archaeology project. The `:core:*` modules exist from day one for exactly that reason:
the extraction must never require moving code between packages.

### 7.4 Engineering rules kept

Same stack as the series (Kotlin 2.2, Compose M3, Gradle 9.1/AGP 8.13, version catalog, minSdk 33,
target 36), same CI shape (tests and lint before any APK, artifacts for debug and release plus the
R8 mapping), same signing arrangement (committed debug keystore, real key outside the repo behind
four properties, `-PsignReleaseWithDebugKey` for smoke tests), same `applicationIdSuffix ".debug"`
so the dev build installs side by side. `applicationId`: `com.callbackdev.chiaro`. License: GPL-3.0,
as the rest of the series.

---

## 8. Localization

Italian and English through the system per-app language picker, as the series.

The three-register rule (code / data / prose) that tweather spent a whole phase on **does not
survive, because its problem does not exist here**: there is no code register on screen. No keys, no
filenames, no `$` commands, no git chrome. Everything Chiaro renders is prose or data, and therefore
**everything Chiaro renders is localized**, with no exceptions to guard and no reflection test to
keep honest.

Two consequences worth stating. Identifiers (`golden_hour.pm`, `current.temp_c`) remain English in
the code and never surface — if one ever does, that is a bug with a test. And the user's own alert
messages are user content: never translated, ever.

---

## 9. Data, battery, privacy

- **Provider**: Open-Meteo (forecast, air quality, geocoding), no API key, attributed in the guide.
  Astronomy is computed locally and works offline.
- **Network**: one fetch per active place per interval (15/30/60/120 min, default 60), a 15-minute
  in-memory/disk TTL in front of it, CONNECTED-constrained. Nothing polls.
- **Battery**: one periodic WorkManager job for everything (sync, alerts, rules, sky observation),
  inexact alarms for reminders, no foreground service, no background location, no FCM.
- **Offline**: the last successful report per place is kept with no TTL and carries a week of
  forecast, so the app is never blank; recency drops the hours that have already happened, and the
  freshness chip says how old the answer is.
- **Privacy**: no account, no analytics, no crash reporting that leaves the device without consent,
  no advertising ID, coarse location only and only when the reader turns it on. The privacy policy
  fits in a paragraph, and that paragraph is a feature on the store page.

---

## 10. Roadmap

**Fase 0** — Repo, CI, signing, version catalog, `:core:*` seeded from tweather with the refactors
of §7.2, `UPSTREAM.md`, the whole inherited test suite green.
**Fase 1** — `DESIGN.md`: color (dynamic + fallback), type, shape, motion, the canvas spec, the
ribbon spec, the icon decision. Compose theme and the component kit.
**Fase 2** — Today: canvas, headline sentence, next hours, week, details grid.
**Fase 3** — Places: pager, search, GPS, no-place state, first run.
**Fase 4** — Settings and the guide.
**Fase 5** — Sky: tonight, moments, catalog, reminders.
**Fase 6** — Alerts: ready-made, templates, chip builder, preview.
**Fase 7** — Journal: entries, forecast-drift chart.
**Fase 8** — Widgets (Now, Today, Sky).
**Fase 9** — Accessibility and performance pass with numbers (§3.3.6), IT/EN sweep.
**Fase 10** — Store assets, screenshots, listing, v1.0.0 release.

**MVP is Fase 0–5 plus Fase 8**: an everyday weather app with the sky planner and a widget. Alerts
and Journal are v1.0 targets and not MVP targets only because their engines are already written and
their UI can land late without blocking anything else.

**Deliberately out of scope for v1**: radar and satellite imagery (no provider), severe-weather
government bulletins, tides, aurora, air-quality forecasting beyond the current index, Wear OS,
sharing, a second provider.

---

## 11. Success criteria

The first release succeeds if a person who has never heard of tweather can install Chiaro, add their
city in one screen, and:

- get an answer to "do I need a jacket" before they have finished reading the screen;
- learn what a golden hour is because the app made them curious, not because it made them study;
- be told once that Saturday got better, and remember that no other app has ever told them that;
- set up a rain alert without knowing that a rules engine exists;
- never wonder whether a number on screen is real;
- never see an ad, a login, or a permission they did not understand.

And the deeper test, inherited from the series: **does adding a feature make the app better, or just
bigger?**

> **North star:** the sky above your city, told in one sentence, with everything behind that sentence
> one tap away, and nothing on the screen that the data did not say.

---

## 12. Open decisions

1. **The name.** "Chiaro" is proposed (§ the title): it means both *clear sky* and *clear to
   understand*, which are the product's two promises. A Play listing under this name needs a
   trademark and store-collision check first — an unrelated ICT company already ships an app called
   "Chiaro App". Alternates kept warm: **Sereno** (nearby collision: "Meteo: Cielo Sereno"),
   **Vega**, **Aperto**.
2. **The family.** If the reskin extends to the siblings, the daylight line reads best as
   **Chiaro** (weather) · **Passo** (steps) · **Ritmo** (habits): one Italian word each, meaningful
   in its own domain, and the exact opposite pole of the terminal line.
3. **The icon set** (§4.5): adopt, commission, or draw. This is the only item with a real cost.
4. **The shared core** (§7.3): confirm copy-now, or pay for the extraction up front.
5. **Radar.** The most-requested feature Chiaro cannot have with this provider. Decide whether v2
   adds a second provider for imagery, or whether "no radar" stays a stated position.
6. **Monetization.** Free with no ads is assumed. If that ever changes, the honest form is a paid
   version, never an ad slot or a subscription wall in front of data that is free upstream.
7. **Whether the two editions cross-link** on the store, and how much of tweather's story to tell on
   Chiaro's listing.
