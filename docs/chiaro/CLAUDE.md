# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in
this repository.

## What this repository is

**Chiaro** is an Android weather app (Kotlin 2.2 / Jetpack Compose Material 3) built for
a general audience: a computed sky as the hero, one sentence before any number, an agenda
of the day's sun and sky with a verdict, a journal of how the forecast changed, and alerts
the reader writes themselves. Free, no account, no ads, no API key.

It is the **daylight edition of [tweather](https://github.com/fiorenzobrioni/tweather)**:
the same data layer, alert engine, rules engine and astronomy engine, with the code-editor
UI replaced. tweather is furniture for developers and was built that way on purpose;
Chiaro is the same product for everybody else. The two ship side by side and neither
replaces the other.

Source of truth:

- `VISION.md` — the product: positioning against the store field (§2), identity (§3),
  design language (§4), every screen (§5), the parity map with tweather (§6), the module
  split and what is reused (§7), the roadmap (§10), the decisions still open (§12).
- `DESIGN.md` — the design system: color (generated, plus the semantic tokens Material
  has no slot for), the sky canvas, the daylight ribbon, type, shape, motion, the
  component kit, the chart rules, accessibility. Every value carries its measured number.
- `PLANNING.md` — the phased plan with checkable steps. **Keep it updated as work
  progresses**, recording every decision and deviation with its reason (the series' rule).
- `UPSTREAM.md` — where `:core` came from, how to reproduce the seed, and the debt the
  seed deliberately left behind.

## Build and commands

Stack: Kotlin 2.2 + Compose (Material 3), Gradle 9.1 / AGP 8.13, version catalog in
`gradle/libs.versions.toml`. Package/applicationId: `com.callbackdev.chiaro`. minSdk 33
(system per-app language picker, one runtime path for POST_NOTIFICATIONS, themed icons,
full java.time), compile/targetSdk 36.

- Build debug APK: `./gradlew :app:assembleDebug` (output: `app/build/outputs/apk/debug/app-debug.apk`)
- All unit tests: `./gradlew test :app:testDebugUnitTest`
- One module: `./gradlew :core:domain:test` — one class: `--tests "com.callbackdev.chiaro.domain.SomeTest"`
- Lint: `./gradlew :app:lintDebug`
- Installable minified build: `./gradlew :app:assembleRelease -PsignReleaseWithDebugKey`
- On a machine with no system JDK, prepend `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.

**Modules**: `:core:domain` is **pure Kotlin/JVM** and must stay that way. If a class in
it needs a `Context` or a `Resources`, it is in the wrong module. `:core:data` is the
Android library with the Open-Meteo client, the mapper, Room and DataStore. `:app` holds
everything visible. `:core:sync` (the shared WorkManager job) arrives in Fase 6.

**Debug signing**: `keystore/debug.keystore` is intentionally committed (alias
`chiaro-debug`, store/key password `android`) so debug APKs from CI and any machine share
one signature. Do not regenerate it. Debug builds carry `applicationIdSuffix ".debug"`, so
they install side by side with the release-signed app.

**Release signing**: the real keystore lives OUTSIDE the repo; the `release` signingConfig
is created only when the four `CHIARO_KEYSTORE*` properties are all set (from
`~/.gradle/gradle.properties` locally, from `ORG_GRADLE_PROJECT_*` env vars in CI). On an
unconfigured checkout the release build is unsigned by default;
`-PsignReleaseWithDebugKey` signs it with the committed debug key so the minified build is
installable for testing (R8 breakage shows up nowhere else), and stays opt-in so an
unconfigured checkout can never produce an installable release by accident.

**CI**: `.github/workflows/android-ci.yml` runs on every push. Tests and lint run *before*
the APKs: a red suite must never produce an installable artifact.

## Design constraints (non-negotiable)

The full system is `DESIGN.md`; these are the rules that get broken by accident.

- **Roles, never hexes.** No composable outside `ui/theme/` names a color literal.
  `NoRawColorTest` fails the build over it.
- **The screen must not lie.** A section with no data is not drawn, never a card with a
  dash in it. Stale data states its real age. Estimates say so. No placeholder ever
  renders as a value: a skeleton must look like a skeleton, never like a grey zero.
- **Every number says what to do with it.** A metric tile is a value plus its consequence.
  A metric with no honest second line belongs in the details sheet, not on the home screen.
- **A verdict ships with its arithmetic**, and is a glyph and a word before it is a color:
  green/amber/red do not separate under deuteranopia (measured, `DESIGN.md` §2.3).
- **No full-screen spinner, ever.** Cached content first, freshness stated, refresh silent.
- **One hue for a quantity, two plus a neutral for a diverging one, never a rainbow.** No
  dual-axis chart. Scales anchor to the world (15 °C, 0-100%), never to what is on screen.
- **No jargon.** Not translated jargon: absent jargon. The dot-notation identifiers
  (`golden_hour.pm`, `current.temp_c`) stay in the code and never reach a screen.
- **No emoji as iconography.** The weather icons are a vector family (`DESIGN.md` §13.1).
- **Localization**: everything on screen is prose or data, so **everything localizes**
  (IT/EN, system per-app language picker). There is no code register in this product to
  protect, which is the one rule of the terminal line that does not survive the reskin.
  The reader's own alert messages are user content and are never translated.

## Writing `README.md` (root file only)

**No em dashes (`—`) or en dashes (`–`) in the root `README.md`.** Rewrite the sentence
rather than swapping in a hyphen: use a colon when the clause explains, a full stop when
the thoughts are separate, parentheses for an aside. Same house style as tweather, tsteps
and thabit, deliberately scoped to that one file: every other file keeps normal
punctuation.

## Domain notes

- **Provider**: Open-Meteo (forecast, air quality, geocoding), no API key. Astronomy is
  computed locally by `:core:domain` and works offline.
- **Battery is a feature**: one shared periodic job for sync, alerts, rules and sky
  observation; inexact alarms for reminders (hence the 15 minute floor and no
  `SCHEDULE_EXACT_ALARM`); no foreground service, no background location, no FCM.
- **Offline**: the last successful report per place is kept with no TTL and carries a week
  of forecast, so the app is never blank. `WeatherRecency` drops the hours that have
  already happened; `WeatherFreshness` decides whether to trust what is left.
- **The engines are inherited and tested.** Before changing anything in `:core`, read
  `UPSTREAM.md`: the copy is deliberate, the ledger is what makes a later shared-core
  extraction cheap, and a fix that belongs upstream should probably be made upstream too.
