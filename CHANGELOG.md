# Changelog

All notable changes to tweather are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- `sky.crontab`, a third file in the editor's tab bar: what the sky above the active
  city has scheduled next — sunrise and sunset, the golden and blue hours, the three
  twilights, moonrise and moonset, solstices, the major meteor showers — written as a
  real crontab, with the recurrence in the cron field and the computed instant in the
  comment beside it. Every line that is a sight to go outside for also carries a
  verdict from the forecast the app already has: `✓ pass  cloud 8%`, `~ unstable`,
  `✗ fail  rain 80%`, and `? unknown` when there is nothing recent enough to say.
  Nothing here needs the network: the schedule is computed on the device, so it is
  right in airplane mode. Edited by tapping, like `alerts.rules`: the job name
  comments the line out, `[rm]` takes it out of the file, `+ add job` adds one back.
  `$ tweather run sky` lines every enabled job up under itself. Switched on and off
  by `sky.enabled` in `settings.config`.
- `sky_runs.log`, a third file in the log: what the sky was actually seen to do, one
  line per job, grouped by day with a count at the foot of each. It records outcomes
  rather than changes, which is why it is a `.log` and not a `.diff`. Each line carries
  how far the observing update was from the event, and a job no update came near enough
  to judge is recorded as skipped rather than guessed at. The same runs appear as check
  lines on the commit that observed them in `weather_history.diff`.
- The city's `README.md` grew its `## Astronomy` section: the golden and blue hours,
  the astronomical dark window and the part of it the moon leaves alone, and the moon's
  own rise, set and illumination. If a job you subscribed to in `sky.crontab` is coming
  in the next twelve hours under a sky that will not cooperate, `## Status` says so in
  one line.
- The home widget can show one optional line with the next sky job and its verdict.
  Off by default, and the first line dropped when the widget has less room.
- Sky reminders. A line of `sky.crontab` can tell you before its time: tap its
  `--notify` token to cycle `off · 15m · 30m · 1h · 3h · 1d`. The reminder carries the
  verdict and the number behind it, so it says whether it is worth going out rather
  than only that something is about to happen; one for a sky that will not cooperate
  is held back unless you ask for it with `notify_on_fail`. The reminder is
  approximate by a few minutes on purpose, which is why fifteen is the shortest lead:
  an exact one would cost battery all day for a sunset that is not an alarm clock.
  Set `notify_default` in `settings.config` to give every line the same lead at once;
  it starts off, so nothing is sent until you ask for it.
- `$ tweather init`: the first run now asks where you are — your position, a city
  search, or skip — instead of assuming. Skipping lands on an editor that says
  `// no location configured` and offers the search, which is also what you get if
  you remove every city.
- `HELP.md`, a third file behind the Settings tab bar: what the four tabs are, what
  the borrowed words mean (commit, diff, branch, CI), where the data comes from and
  why the app looks like this. Written for someone who does not read `git` for a
  living, and fully localized. A one-off `// new here? open HELP.md` line at the top
  of the editor points at it once and goes away as soon as the file has been opened.

### Changed

- Sunrise, sunset and daylight are now computed on the phone rather than read off the
  provider's response. They agree with it to within a minute, and they are now the same
  numbers everywhere in the app, correct without a connection and correct past the
  seven day forecast. Where the sun does not rise or set at all, the app says so
  instead of printing another time in its place.
- The two log files lost their `weather_` prefix: they are `history.diff` and
  `forecast.diff` now. It was the only place in the app where a file said it was about
  the weather, which every file here is, and the sixteen characters it cost were what
  kept the third log file off the edge of the screen.
- The bottom bar now ends on Settings, like every other Android app and like tsteps
  and thabit: Editor, Search, Logs, Settings. The Logs tab also swaps its terminal
  glyph for the commit one (a dot on a branch line), which is what
  `weather_history.diff` actually is.
- A fresh install no longer comes with Milan already saved. `cities.json` listing a
  city you never chose was the one thing left in the app that the file could not
  honestly claim, and on an Italian phone it arrived in English on top of that. An
  install that predates this keeps the city it has been watching.

### Fixed

- Re-adding a city you already have now refreshes its record instead of ignoring it.
  Searching "Milano" in Italian kept the file named `milan.json`, because it is the
  same GeoNames id as the English "Milan" already in the list. Same for anyone who
  switches the phone's language and re-adds a city.

- City search now asks Open-Meteo in the device's language. `language` is not a display
  setting there: it also picks the index the query is matched against, so with the
  hardcoded `en` an Italian phone had to spell its own cities in English — "Firenze"
  returned only the hamlet Firenze Nova, "Napoli" five places that are not Naples.
  English spellings keep working ("Florence" still finds Firenze) and results now come
  back with local names. Cities already saved keep the name they were saved with.
- Home widget: the ↻ now answers the tap. It used to redraw the very same frame — the
  fetch is a queued job that lands seconds later, and `# last_sync`, the only line that
  moves, is cut by the sizes most people place — so a tap looked like nothing at all.
  The glyph turns to `…` while the fetch is in flight and comes back on the first
  repaint after it, or after five seconds for the tap nothing serves (offline, the job
  waits for a connection). Same acknowledgment tsteps got in its Fase 16.
- `README.md`, `## Today`: the UV index line showed the *instant* reading next to the
  day's max and min, so it was 0 every evening. It now shows the day's peak
  (Open-Meteo's `uv_index_max`, fetched and parsed all along but never mapped into the
  domain). The instant value keeps its place in `weather_data.json`'s
  `current_conditions`, where the key says what it is.
- `README.md`, `## Status`: the sky's warning line spoke the crontab's language. It read
  `golden_hour.pm at 19:21: ✗ fail  cloud 100%`, which is a row of `sky.crontab` dropped
  into the one page this app writes in prose. It now names the job and says what will go
  wrong in a sentence, in your language: `🌇 The evening golden hour at 19:21: the sky
  will be overcast (100% cloud)`. The number stays, because a verdict without the figure
  it was built from is an opinion. The dotted job names keep their place in
  `sky.crontab`, where they are code.

### Added

- `weather_data.json`: `uv_index_max` on each `daily_forecast` row (with
  `show_details` on).
- `alerts.rules`: new variable `today.uv_max`, the day's peak UV — a sunscreen rule
  has to fire in the morning, when the current index is still low.

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
