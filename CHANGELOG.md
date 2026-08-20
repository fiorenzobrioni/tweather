# Changelog

All notable changes to tweather are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[Semantic Versioning](https://semver.org/).

## [1.0.0] — 2026-08-20

First release. Everything below is new.

### The editor

- Four screens behind a bottom navigation bar, each a fake file: `weather_data.json`
  (the forecast as syntax-highlighted JSON), `cities.json` (search and saved cities),
  `settings.config` (settings as an editable config file), `weather_history.diff`
  (every data fetch as a git-style commit with hash, author and `+`/`-` lines).
- Three more files on editor tab bars: the city's `README.md` (the forecast as prose —
  current conditions, a 14-row hourly table, the daily forecast, an alert-fed
  `## Status`), `alerts.rules` (user-defined notification rules) and
  `weather_forecast.diff` (how the forecast itself changed between fetches).
- Obsidian Syntax design system: JetBrains Mono everywhere, 1px borders instead of
  shadows, controls rendered as text — booleans flip when you tap `true`/`false`,
  destructive actions are `$` commands with a two-tap confirm, inputs are terminal
  prompts with a blinking cursor. Theme profiles: Obsidian, Dracula, Monokai.

### The weather

- Open-Meteo as the data provider: current conditions, 24h hourly and 7-day daily
  forecast, air quality, pollen (Europe), astronomy. No API key needed; moon phase
  computed locally.
- City search via geocoding, saved cities, and an optional GPS pseudo-city
  (`current_location.json`) using coarse location only.
- Background sync through a single WorkManager periodic job; built-in alerts (severe
  weather, precipitation, daily summary) plus Weather CI: notification rules you
  compose token by token — no syntax errors possible — evaluated on the same fetch,
  with fired rules landing in the Logs as `✓` check lines and a `$ tweather run rules`
  dry run.
- Home-screen widget with the same JSON aesthetic.
- Fully localized, Italian and English, via the system per-app language picker: JSON
  keys stay English like real code, values and prose translate.

[1.0.0]: https://github.com/fiorenzobrioni/tweather/releases/tag/v1.0.0
