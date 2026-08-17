# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

**tweather** is a planned Android weather app (Kotlin 1.9+ / Jetpack Compose Material 3) whose entire UI mimics a code editor: weather data is rendered as syntax-highlighted "files" (`weather_data.json`, `search_query.json`, `settings.config`, `weather_history.diff`) with line numbers, editor tabs, and a terminal aesthetic inspired by Obsidian/VS Code.

All four screens plus the home widget are implemented (see `PLANNING.md`, phases 0 through 9e); what remains is Fase 10, release. Design artifacts and specifications:

- `tweather_comprehensive_project_prd_final.md` — the PRD; the source of truth for features, screens, and colors.
- `obsidian_syntax/DESIGN.md` — the full design system ("Obsidian Syntax" theme): Material 3 color tokens (YAML frontmatter), typography scale, spacing, and component specs.
- `*/code.html` + `*/screen.png` — static Tailwind HTML mockups and screenshots for each screen (main editor, search, settings, logs/diff, brand logo). These are reference-only prototypes, not production code.
- `weather_data.json_sample.json` / `weather_data.json_full_sample.json` — sample data shapes the main screen renders. The full sample defines the expected weather data model (location, current conditions, air quality, pollen, astronomical, hourly/daily forecast, system info).

## Writing `README.md` (root file only)

**No em dashes (`—`) or en dashes (`–`) in the root `README.md`.** Rewrite the sentence
rather than swapping in a hyphen: a `-` standing in for a dash reads as a typo. Use a
colon when the clause explains, a full stop when the thoughts are separate, parentheses
when it is an aside.

This is a house style for the project's shop window, not a grammar rule, and it is
**deliberately scoped to that one file** — every other file in the repo, including
`PLANNING.md` and `CLAUDE.md` itself, keeps normal punctuation.

## Build and commands

Stack: Kotlin 2.2 + Jetpack Compose (Material 3), Gradle 9.1 / AGP 8.13, version catalog in `gradle/libs.versions.toml`. Package/applicationId: `com.callbackdev.tweather`. minSdk 33 (deciso a fine Fase 9: language picker di sistema per la l10n IT/EN, un solo code path per POST_NOTIFICATIONS, themed icons, java.time completo — niente fallback per Android vecchi), compile/targetSdk 36.

- Build debug APK: `./gradlew :app:assembleDebug` (output: `app/build/outputs/apk/debug/app-debug.apk`)
- Unit tests: `./gradlew :app:testDebugUnitTest` — single test class: `./gradlew :app:testDebugUnitTest --tests "com.callbackdev.tweather.SomeTest"`
- Lint: `./gradlew :app:lintDebug`
- Installable minified build: `./gradlew :app:assembleRelease -PsignReleaseWithDebugKey`
- On this machine there is no system JDK: prepend `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` (Android Studio's bundled JDK) to gradlew commands.

**Debug signing**: `keystore/debug.keystore` is intentionally committed (alias `tweather-debug`, store/key password `android`) so debug APKs from CI and any machine share one signature and can update an existing install. Do not regenerate it.

**CI**: `.github/workflows/android-ci.yml` runs on every push — unit tests, lint, then both APKs. Artifacts: `tweather-debug-apk`, `tweather-release-apk-testing-only`, `tweather-release-mapping` (the R8 map, needed to read a release stack trace), plus `app/build/reports/` on failure. Tests run *before* the builds so a red suite never produces an installable artifact.

**Release signing**: the release build is unsigned by default (`app-release-unsigned.apk`). Passing `-PsignReleaseWithDebugKey` signs it with the committed debug key so the minified build is installable for testing — R8 breakage shows up nowhere else. It is opt-in precisely so a store artifact can never be signed with a committed key by accident; Fase 10 replaces it with a real keystore.

**Alert engine** (Fase 9c): background notifications via a single WorkManager periodic job (`weather-sync`, interval = `update_frequency_min`, now 15/30/60/120 default 60, CONNECTED-only constraint, no foreground services/exact alarms/FCM/background location). Pure rules in `domain/AlertEngine.kt` (severe WMO buckets 12h, precip ≥70% 6h, daily summary 06–12), dedup fingerprints in DataStore `alerts`, notification body rendered as a JSON object (localized title, English keys and command line, localized data values via `WeatherTranslations` — same l10n rule as the main screen and the widget; folded onto one line when collapsed, pretty-printed when expanded). Background fetches write Logs commits like any fetch.

**GPS current location** (Fase 9b): the device position can be the active weather source — platform `LocationManager`/`Geocoder` only (no play-services), `ACCESS_COARSE_LOCATION` only, GPS pseudo-city with reserved id `-1L` kept out of the saved list, toggle in `settings.config` (`location.use_gps`), pinned `current_location.json` entry in the Explorer (tertiary color).

**Decided (overrides the PRD's OpenWeatherMap example): the weather provider is Open-Meteo** — free, no API key. Forecast API for current/hourly/daily + astronomy, Air Quality API for AQI/pollutants/pollen (pollen is Europe-only), Geocoding API for city search. Moon phase is not provided and must be computed locally. See `PLANNING.md` (Fase 3) for details; `PLANNING.md` is the phased implementation plan with checkable steps — keep it updated as work progresses.

## App structure (per the PRD)

Four screens, each presented as a fake "file" behind a bottom navigation bar (Explorer / Search / Settings / Logs):

1. **Main editor** (`weather_data.json`) — live weather rendered as formatted JSON with syntax highlighting and a line-number gutter; refresh via a glowing FAB.
2. **Search** (`search_query.json`) — the input field is the `"search_term"` JSON property; recent searches shown as a JSON array.
3. **Settings** (`settings.config`) — booleans as toggles, theme profiles (Obsidian, Dracula, Monokai).
4. **Logs** (`weather_history.diff`) — each data fetch is a git-style "commit" with hash and author `sys@tweather.app`; changes shown as `+`/`-` diff lines.

## Design constraints (non-negotiable per the design system)

- **Typography**: JetBrains Mono everywhere, 4px baseline grid, 20px indent per nesting level. The **home widget is the single exception** (Fase 9d): since CVE-2021-0567 the launcher inflates widget layouts in a restricted context that silently drops `@font/` resources, so its layouts use the system `monospace` family. Glance has the same limitation; the only workaround would be rasterizing text to bitmaps, which costs system font scaling and TalkBack. Decided with the committente.
- **Syntax highlight colors**: keys `#79c0ff`, string values `#a5d6ff`, numbers/booleans `#ffa657`, comments/braces `#8b949e`, diff additions `#2ea043`, deletions `#f85149`.
- **Core palette**: background `#10141a`, surface container `#181c22`, on-surface `#dfe2eb`, borders `#30363d`. Full Material 3 token set is in the `obsidian_syntax/DESIGN.md` frontmatter.
- **No drop shadows** — depth comes from 1px borders and tonal stacking. The only exception is the FAB's glow (`box-shadow: 0 0 15px #79c0ff88`); every element, FAB included, is rectangular with a 4px corner radius (the FAB was circular in early revisions — squared during development, nothing in an editor is round).
- **Controls rendered as text**: checkboxes as `[x]`/`[ ]`, inputs as terminal prompts (`> Search Location _` with blinking underscore cursor).
- Weather icons are inline Unicode emoji inside the JSON text (`☀️`, `🌧️`, `🌔`), not image assets.
