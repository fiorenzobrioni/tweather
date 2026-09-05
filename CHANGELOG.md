# Changelog

All notable changes to tweather are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Fixed

- **The GPS location names your town again, not your province.** In Cavenago di
  Brianza the header read "Provincia di Monza e della Brianza"; in Segrate it read
  "Milano". Three things had to be wrong at once. The reverse geocoder was being
  handed the coordinates rounded to about a kilometre rather than the ones the phone
  gave — a displacement of up to 679 m at these latitudes, which is enough to leave a
  small comune and land in the fields beside it, where there is no town to name.
  Only the first of the several addresses the lookup returns was read, and the chain
  that turned it into a name put the province *ahead* of the quarter, so one address
  with no town on it was all it took for the province to win. Rounding is now what
  leaves the app, not what the lookup is asked about; five addresses are read; and
  the most specific name any of them knows wins, with the province kept as the answer
  only when it is the only one there is.
- **Between two positions the phone already holds, the better one wins rather than
  the more recent.** They were ranked by their timestamp alone, so a position derived
  from a cell tower ten seconds ago beat a good fix from two minutes before — which is
  the other half of "Milano" while standing in Segrate. They are now ranked by how far
  you may be from each one by now: the accuracy it declares plus the ground you could
  have covered since.

### Added

- **The sky catalog explains itself** (Fase 23). Every event `sky.crontab` can carry
  now has a manual page: what it physically is, what you would actually see, and what
  has to be true for you to see it. The dotted names stay as they are, because they
  are what the file prints, but `zodiacal.pm` no longer has to mean nothing to
  somebody who has not met the zodiacal light. Tap `[man]` beside any line in the
  `+ add job` catalog, or open `$ man sky` at the foot of the file for the index of
  all fifty-one. The pages cross-reference each other, and `[q]` gives the file back.

### Changed

- `sky.crontab` no longer falls apart with `word_wrap` on. A row is five columns side
  by side, and with wrapping there is no sideways room to give them, so the comment
  was being crushed into a one-character column down the edge of the screen. It now
  takes a line of its own under the row, which is where a long crontab line has always
  put its comment.
- The manual's paragraphs wrap and its lists do not. Forcing the whole page to wrap
  turned the index of fifty-one jobs into ragged pairs of half-lines; the names are
  now a proper column that follows `word_wrap` like every other table in the app.
- `[man]` is no longer comment-grey, so it reads as something you can tap.
- `HELP.md` now always wraps its lines, whatever `word_wrap` says in
  `settings.config`, and its status bar says `wrap` so you can see that it does. Its
  paragraphs run past 400 characters and the setting is off by default: reading the
  file meant dragging sideways through every sentence. This is what a real editor
  does too, wrapping by language rather than globally. The `README.md` tab and
  `sky.crontab` still follow the setting: their columns are padded to their widths,
  and wrapping them would take the alignment apart.

### Added

- **The sky module learns eclipses, and eighteen other jobs** (Fase 19). `sky.crontab`
  can now carry a lunar eclipse and a solar one, both resolved **for this place** — the
  moon has to be up, the sun has to be up, and a window that runs past sunset ends at
  sunset with its maximum re-measured inside what is actually visible. The four moon
  quarters get lines of their own beside the generic `moon.phase`; so does the year's
  closest full moon. The dark-sky pair goes one step past `darkness.window`: the core
  of the Milky Way above 10° inside the astronomical night, and the zodiacal light on
  the nights the ecliptic stands steeply enough to show it. Four annual facts join the
  solstices — the earliest sunset and the latest sunrise (which are **not** the
  solstice), the earth at its closest to and farthest from the sun, and the start and
  end of the white nights above ~48.5°. Three meteor showers join the ten, from the
  same IMO list the table already cited.
- **A rainbow window in `README.md`.** The one sky event that is not astronomy: a bow
  is centred on the antisolar point and rises 42°, so it can only clear the horizon
  while the sun is under 42 — and whether rain is falling into that sunlight is two
  numbers the fetch already carries. `## Astronomy` says when, with what rain
  probability, and which way to face.
- **`alerts.rules` completes the `{placeholders}` a message can print.** Every name
  the registry knows can be written in braces inside a rule's message and comes out as
  that number when the rule fires — and nothing in the app said so: the only hint was
  the message a new rule is born with. While the message is being edited the file now
  lists all of them under the line, indented like the variable picker and spelled in
  the reader's own units, and a tap puts one at the caret. The one token still typed by
  hand no longer has a vocabulary to remember. `HELP.md` says the same in words.

### Changed

- The device position is asked for differently, and the difference is battery. A
  position the system already holds is now the answer whenever it is recent enough,
  instead of being a fallback consulted only after a fresh acquisition had already
  timed out: on the ordinary cold start behind a position tweather already knows, no
  radio is powered up at all. The one the reader asks for, from the FAB or the first
  time the source is switched on, is still a real acquisition.

### Fixed

- A last known position with no age limit could be yesterday's, or the city you flew
  home from, and it was shown as where you are now. It is bounded at 24 hours, read
  from a monotonic clock, and taken from the freshest provider rather than only the
  one an acquisition would have used.
- The reverse geocoder was handed the full-precision coordinates while everything
  else in the app rounds to about a kilometre first. It gets the rounded pair now:
  on most devices that lookup is a network service.

## [2.0.0] — 2026-08-30

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

- **The comment channel now speaks the reader's language** when what it is saying is
  a sentence. The rule that used to read "`//` comments stay English" mistook the
  punctuation for the register: under the same slashes sat
  `// GET https://api.open-meteo.com/v1/forecast`, which is a machine talking, and
  `// hint: open cities.json and search a city`, which exists only to be understood.
  Now the register decides. Code is still English everywhere — the file banners, the
  JSON keys, the `$` commands, the git chrome, the verdicts, `net::ERR_*`, the
  licences, `// active` and `// empty` — and so is `sky.crontab`'s aligned column of
  evidence, whose localized reading has been the README's `## Astronomy` since it was
  written. What moved is the prose: the editor's state lines, the search hints, every
  explanation in `settings.config` and `alerts.rules`, the empty states of all three
  log files, the crontab's header and its four closing notes, and the widget's two
  lines for when it has nothing to show. The marker never moves and neither does the
  level: an error reads `// ERROR: permesso negato — il gps resta spento`.
- The moon phase in `sky.crontab` is now the same word `weather_data.json` uses two
  tabs away. A phase is a weather value, and values have been translated since the
  app learned Italian; the sky module had simply never asked.

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

- The GPS hint in `settings.config` used to end `in explorer`, a tab that has not been
  called that since the pre-1.0 restyling: only its internal route kept the name, so
  the line was sending the reader to look for a word that is nowhere on screen. It now
  names `cities.json`, which is the file the entry actually lives in.

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
- A failed update no longer empties the editor. Open the app with no connection and
  `weather_data.json` and `README.md` showed two comment lines and nothing else, on a
  phone that had a full week of forecast on disk (the home widget had never done this:
  it keeps its last reading and marks it `# stale`). Both files now show the last fetch
  that worked and say so above it: `README.md` in a sentence with the time and the age,
  the JSON as `// stale: last good fetch 3h ago`. The hours and the days that have
  already passed are dropped first, so `## Next hours` never opens with hours that are
  over and `## Today` is today. Past the forecast's own horizon, when nothing in the
  file is still about the present, the app shows the error alone as before.
- `README.md` says what happened in words. The document that is otherwise entirely
  prose used to report a failed update as `<!-- ERROR: net::ERR_INTERNET_DISCONNECTED —
  check your connection -->`, which is Chrome's name for "the phone is offline". It now
  reads `<!-- No connection: the weather could not be updated. -->`, localized like the
  rest of the page, and the same for the loading, GPS and no-location lines.
  `weather_data.json` keeps the error codes: they are useful, and it is code.

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

[Unreleased]: https://github.com/fiorenzobrioni/tweather/compare/v2.0.0...HEAD
[2.0.0]: https://github.com/fiorenzobrioni/tweather/releases/tag/v2.0.0
[1.0.0]: https://github.com/fiorenzobrioni/tweather/releases/tag/v1.0.0
