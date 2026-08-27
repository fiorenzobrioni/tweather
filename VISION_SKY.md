# VISION_SKY.md — the sky module

> Spec for a new module inside **tweather**, revision 2. English, like the rest of the
> repo's design documents. Lives next to `PLANNING.md`, where the phased plan (Fase 16)
> tracks the work.
>
> Revision 2 is a review of the original draft against the codebase as it stands at
> v1.0.0 + Fase 15. The substantive changes are listed in §0.1; everything else is the
> original argument, kept because it holds.

---

## 0. The one-line idea

`weather_data.json` says what the atmosphere is doing right now.
`sky.crontab` says **what the sky has scheduled next**, and whether the clouds will let
it run.

The sky is the only scheduler in existence that has never missed a run. Everything above
your city happens on a timetable: the sun rises, the moon turns, a meteor shower peaks on
a date you could have known ten years in advance. So the file is a crontab, and every job
carries a build verdict from the forecast tweather already has.

```
Aug 25 20:24  sun.set         ✓ passed    cloud 8%
Aug 25 20:58  blue_hour.pm    ✗ failed    cloud 92%
```

No other weather app tells you whether tonight's sunset is worth walking outside for, and
none of them tells you afterwards whether it was.

### 0.1 What changed in revision 2

| # | Change | Why |
|---|---|---|
| 1 | **No fifth navigation tab.** The module lands as new *editor tabs* in existing top bars: `sky.crontab` third in the Editor strip, `sky_runs.log` third in the Logs strip. | Committente's call, and it is the better one — see §1. It also dissolves the draft's biggest stated cost. |
| 2 | **The README's sky content merges into the existing `## Astronomy`**, and the warning blockquote goes into `## Status`. No `## Tonight` section. | A second sky section next to `## Astronomy` would be the duplication §8 of the draft was written to forbid, applied to the engine but not to the document. |
| 3 | **`sky_runs.log` reuses the existing commit row** (one nullable column, exactly like `fired_rules`) instead of a new Room table + recorder + pruning. | The run record belongs to the fetch that observed it. That is what `obs +12m` was trying to say; attaching it to the commit says it structurally. |
| 4 | **The data layer needs two changes before any verdict is possible**: hourly cloud cover must reach the domain model, and the hourly window must widen past 25 slots. | The draft assumed a 3-day verdict horizon. Today the app throws away 143 of the 168 hourly values it already downloads, and never maps `cloud_cover` at all. Both fixes are free at the network layer. |
| 5 | **Verdict thresholds are printed in the file, not promoted to `settings.config`.** `[sky]` shrinks to three keys. | The honesty requirement was "not constants users cannot see". A comment line in the file satisfies it at a tenth of the settings surface. `horizon_days` and `time_format` are dropped outright — one is a fact, the other is already global. |
| 6 | **`iss.pass` is deferred indefinitely, not phased last.** | §12, rewritten. The recommendation is now explicit rather than left open. |
| 7 | **`darkness.window` added to the catalog.** | The one derived fact an astronomer actually wants — dark sky *minus* moon — and the module's clearest reason to exist. |
| 8 | Notifications: the real cost is named (`AlarmManager` is a new scheduling primitive in an app that has none, plus a boot receiver), the `5m` lead is dropped as contradicting the 15-minute floor. | The draft proposed inexact alarms and a 15-minute minimum in §10, then offered `5m` in §4's cycle. |
| 9 | The whole module is gated on **`sky.enabled`**, and the edge-case table gains the states the draft predates: no location configured (Fase 14b), the GPS pseudo-city, and a tab strip that now has to scroll. | Written before Fase 14b existed. |

---

## 1. Why this belongs inside tweather, and where exactly

The decisive argument is not convenience, it is that **the feature is impossible without
the forecast**. A standalone `tsky` that wants to say `✗ failed: cloud 92%` has to fetch
hourly cloud cover per city, manage saved cities, persist a settings file, run a
background job and paint a widget — at which point it has re-implemented tweather and
become a worse weather app with an astronomy tab.

Second argument: `weather_data.json` already ships an `astronomical` block (sunrise,
sunset, moon phase) and the moon phase is already computed locally. The module is not a
new subject arriving in the app, it is an existing block of the app finally getting the
room it deserves — and, in passing, the moon phase getting an implementation that is not
an eight-bucket average of a mean synodic month.

Third: it is the one thing that makes tweather structurally different from every other
weather app, rather than better-dressed than them.

### 1.1 It is a tab bar module, not a nav bar module

The original draft spent a fifth bottom-bar slot on this and called it "the last module
tweather can absorb in this shape". That was the wrong trade and the committente refused
it. The right shape was already in the app:

**tweather has two levels of navigation, and they mean different things.** The bottom bar
answers *what am I looking at* — the city's data, the city list, the history, the
options. The top strip answers *which file of that thing* — `weather_data.json` or
`README.md`, `settings.config` or `alerts.rules` or `HELP.md`, `weather_history.diff` or
`weather_forecast.diff`. A module that adds files does not add a destination.

So:

| File | Lives in | Next to | Why there |
|---|---|---|---|
| `sky.crontab` | **Editor** strip, third tab | `weather_data.json`, `README.md` | It is a document *about the active city* — its latitude produces every instant in it. The editor's tabs belong to the city; this one does too. It is also the app's front page, which is where a feature has to be to be found. |
| `sky_runs.log` | **Logs** strip, third tab | `weather_history.diff`, `weather_forecast.diff` | It is history. The Logs tab is where the app keeps what already happened, and this is the same shelf. |
| `[sky]` block | `settings.config` | the `notifications` block | Three keys. No new settings file. |

That mapping is worth more than the slot it saves. `sky.crontab` in the Editor strip
**switches city with the ⎇ branch switcher**, exactly as its neighbours do, which is the
correct behaviour for a file whose every line depends on where you are standing. A fifth
nav tab would have made the sky look like a peer of the weather instead of a view of it.

The catalog subscription list is global (§13), so the *editable* part of `sky.crontab` is
app state while the *rendered* part is city state. That sounds like a contradiction and
is not: `alerts.rules` already does exactly this — global rules, rendered in the active
units, evaluated against the active city.

### 1.2 What it still costs, stated honestly

- **"Single-purpose" gets restated, but less than the draft thought.** The purpose stops
  being "the weather" and becomes "the sky above your active city". Because the module
  arrives as a file inside the city's editor rather than a destination beside it, this is
  a widening of the *documents*, not a promotion of a second subject. The README still
  has to say it out loud rather than let the app quietly grow.
- **Three tabs in the Editor strip.** `weather_data.json · README.md · sky.crontab` is 39
  characters at bodyMedium bold; on a 5" screen the strip scrolls. Settings already
  carries three names and survives, but `EditorTabs` does **not** scroll the selected tab
  into view — a latent bug that this module makes visible and must therefore fix (§11).
- **Duplication risk.** Sun and moon would now appear in three places: the JSON's
  `astronomical`, the README's `## Astronomy`, and the crontab. §9 makes one engine the
  single source of truth for all three; without that rule this module makes the app
  worse, not better.
- **Data-layer work before any of it.** The verdict needs hourly cloud cover in the
  domain model and more than 25 hours of it (§13.1). Neither costs a byte of network —
  the values are already downloaded and already on disk in `ReportDiskCache` — but both
  touch the mapper, which is the most load-bearing pure function in the app.
- **What is lost by not shipping it standalone:** a separate repo, its own README, and
  discovery by the astronomy crowd who would never search for a weather app. That is a
  real loss and the only honest argument for the other choice.

**Verdict: build it inside tweather, as tabs.** Keep `tsky` retired; the fourth app
should be a different metaphor, not this one.

---

## 2. Non-goals

- No telescope control, no star charts, no sky rendering, no AR. The app draws text.
- No light-pollution modelling. There is no free key-less source worth trusting, and
  inventing a Bortle class from a city name would be the app lying. `sky.crontab` says so
  once, in the comment channel, and never pretends otherwise.
- No "best time to photograph" scoring, no recommendations, no encouragement. Facts and
  verdicts, in the tone the series already set: never guilt, never hype.
- No user-authored cron lines. Same reason `alerts.rules` has no parser: a syntax error
  you cannot physically write is a state you never have to handle.
- No prediction beyond what the forecast covers. Past the horizon the verdict column is
  empty and says why.
- **No second astronomy implementation.** The moment `AstronomyEngine` exists,
  `MoonPhase.at()` stops being a computation and becomes a lookup into it (§9.2).

---

## 3. The honesty problem in the metaphor, and its solution

A crontab line asserts a fixed schedule. Sunrise is not fixed: it drifts about a minute a
day and jumps an hour at the DST boundary. Writing `29 6 * * *  sunrise` would be a file
declaring a grammar it does not keep.

The resolution is that **real crontabs already have this shape**. A sysadmin who has to
run something at a computed instant does not write a fake minute field; they write a
recurrence and let the job work out the moment, and they document the resolved value in a
comment. So:

- The **schedule field states the recurrence, which is true.** `@daily` for a job that
  runs once a day is correct whatever time it lands on.
- The **exact instant lives in the comment channel**, where a real crontab puts computed
  facts. This is the same `//` channel tweather already uses for everything the app knows
  but the data structure cannot hold.
- Events with no recurrence rule (moon quarters, ISS passes) become **polling jobs** with
  an honest `*/N * * * *` expression, because that is how you really schedule an
  aperiodic event: you look often and act when the condition holds. It also happens to be
  literally what the app does — it evaluates on each fetch.

Three job kinds follow, and the taxonomy is the feature, not a workaround:

| Kind | Expression | Meaning | Examples |
|---|---|---|---|
| daily | `@daily` | once per day, instant computed per day | `sun.rise`, `blue_hour.pm`, `moon.rise` |
| annual | `@yearly` | once per year, instant computed per year | `perseids.peak`, `solstice.summer` |
| polling | `*/30 * * * *` | aperiodic, detected by evaluation | `moon.phase`, `iss.pass` |

**Acceptance criterion (§14): every line the app renders must parse as a valid cron
expression under a real cron parser.** That is a unit test, not a promise.

---

## 4. `sky.crontab`

Third tab of the Editor strip, after `weather_data.json` and `README.md`.

```
# sky.crontab — Milan, Lombardy (Europe/Rome)
# 6 jobs · 1 disabled · next: sun.set in 2h 14m ✓
# times are computed per occurrence, not fixed; see the comment on each line

@daily         sun.rise                        # 06:31   +1m04s vs today
@daily         sun.set          --notify=30m   # 20:22   ✓ clear (8%)
#@daily        golden_hour.am
@daily         golden_hour.pm                  # 19:39..20:22   ✓ clear (8%)
@daily         blue_hour.pm                    # 20:22..20:56   ~ partly (45%)
@daily         darkness.window                 # 21:47..05:03   moonless from 23:11
*/30 * * * *   moon.phase                      # 🌓 first quarter, Aug 27 04:12
@yearly        perseids.peak    --notify=1d    # 2026-08-12 03:00   in 352d   [rm]

+ add job

// pass ≤ 25% cloud · unstable ≤ 65% · fail above, or rain at the event
// light pollution is not modelled: the app does not know your sky
// verdicts stop where the forecast does (7 days)

$ tweather run sky
```

### Reading it

- One line per subscribed job, in the order the catalog defines (§5), not sorted by next
  occurrence — a crontab is a file, not a queue. The **next job to fire** is called out in
  the header instead, with its verdict, because that is the one thing a header is for.
- The comment channel carries: the resolved local time, the verdict when one exists, and
  the delta against today for drifting jobs.
- A disabled job is a **commented-out line**, which is exactly how everyone disables a
  cron job in real life. It renders greyed, in the comment colour, and is not evaluated.
- **The thresholds are printed in the file** (`// pass ≤ 25% cloud · …`). That is the
  whole of the draft's "thresholds must not be constants users cannot see", discharged by
  the comment channel rather than by three more lines of `settings.config`.
- **English, like every other code surface in the app.** Job names, cron expressions and
  the verdict words are code; the localized register lives in the README (§9) and in the
  accessibility announcements (§14). Same rule as `weather_data.json` and `alerts.rules`.

### Editing it

No text field, no parser. Tokens are tapped, as in `alerts.rules`:

| Tap target | Behaviour |
|---|---|
| the job name | toggles the leading `#` — enable / disable |
| `--notify=30m` | cycles `off · 15m · 30m · 1h · 3h · 1d`. Rendered from 16f (before it, a token promising a reminder the app could not send would have been the first thing this module lied about) and **only on the lines that have a lead** — a file nobody set a reminder on does not pay a column for the possibility, and that column would cost the resolved instant its place on a 360dp screen. The cycle starts from the lead the line SHOWS, which is `notify_default` when it has none of its own |
| `[rm]` | removes the line, job returns to the catalog. Two-tap, and the confirm is the token itself (`[rm]` → `[rm?]`): appended after the line as `// tap again`, it landed off the right edge of a phone. It also sits BEFORE the comment, for the same reason Fase 11d moved the README's status column last — a comment can afford to clip, a tap target cannot |
| `+ add job` | opens the catalog as an IDE-style autocomplete list of jobs not in the file |
| `$ tweather run sky` | dry run, two-tap confirm |

`#` and `[rm]` are different on purpose and the difference is real: a commented job stays
in your file and can be switched back on in one tap; a removed job is gone from the file
and has to be re-added from the catalog. Neither ever sends a notification.

The `--notify` cycle **starts at 15m, not 5m** — the draft offered `5m` here and forbade
it in §10, and §10 was right: with inexact alarms a 5-minute lead can be delivered after
the event it announces. A lead the app cannot honour is not a shorter lead, it is a lie.

### `$ tweather run sky`

Mirrors `$ tweather run rules` exactly: evaluates every enabled job against the current
forecast, prints the resolved instant and verdict inline under the command, sends nothing,
touches no state, writes no run record.

Worth being straight about what it is in 16d, before the log and the reminders exist:
there is nothing it *avoids* doing yet, so it is a second view of facts the rows already
carry. It earns its place anyway — a resolved crontab row is wide enough to pan sideways,
so this block is the one place the verdicts stand in a column under each other, with the
window each was computed over written out rather than abbreviated to fit. A job with no
verdict prints the fact it resolved to instead of a window and a dash.

```
$ tweather run sky
// sun.set          20:22        ✓ pass      cloud 8%
// blue_hour.pm     20:22..20:56 ~ unstable  cloud 45%
// perseids.peak    2027-08-12   ? unknown   // beyond forecast horizon
```

---

## 5. The job catalog

Fixed set, versioned in code, same discipline as the 22 variables of `alerts.rules`.
Names are dotted and namespaced so the file sorts and reads like a real one.

### Pure computation, no network at all

| Job | Kind | Notes |
|---|---|---|
| `sun.rise` | daily | upper limb, standard −0.833° refraction |
| `sun.set` | daily | |
| `solar.noon` | daily | |
| `twilight.civil.am` / `.pm` | daily | −6°, rendered as a range |
| `twilight.nautical.am` / `.pm` | daily | −12° |
| `twilight.astronomical.am` / `.pm` | daily | −18°, the one that matters for stargazing |
| `golden_hour.am` / `.pm` | daily | +6° → −0.833°, a range |
| `blue_hour.am` / `.pm` | daily | −4° → −6°, a range |
| `darkness.window` | daily | **new in rev 2** — astronomical dusk → dawn, with the moonless sub-window named in the comment |
| `moon.rise` / `moon.set` | daily | may be absent on a given day — see §11 |
| `moon.today` | daily | illumination % and phase name at local noon |
| `moon.phase` | polling | the instant of the next quarter |
| `solstice.summer` / `.winter` | annual | |
| `equinox.spring` / `.autumn` | annual | |
| `meteor.<shower>.peak` | annual | curated table, §6 |

Defaults enabled on first run: `sun.rise`, `sun.set`, `golden_hour.pm`, `moon.today`.
Four lines. A user who opens the tab and finds twenty-three jobs will close it.

**Why `darkness.window` earns its place.** Every other job in the table is a time the sun
or moon crosses an angle — useful, but recoverable from an ephemeris site. This one is the
*intersection*: when it is genuinely dark **and** the moon is down. It is the only line in
the file that answers the question an amateur astronomer actually asks, it is pure
computation from two engines the module already has, and no weather app anywhere prints
it. If the module ships one line that justifies the module, it is this one.

### Deferred indefinitely

| Job | Kind | Status |
|---|---|---|
| `iss.pass` | polling | **not planned** — see §12 |

Explicitly out of scope: planets, conjunctions, eclipses. Each needs real ephemeris work
and each deserves its own decision. Deferred, not rejected — and the catalog is a list of
`SkyJob` values, so any of them can arrive later without touching the file format, the
renderer, or the store.

---

## 6. Meteor showers

A curated table of the ~10 majors (Quadrantids, Lyrids, Eta Aquariids, Perseids,
Draconids, Orionids, Leonids, Geminids, Ursids), each with its solar-longitude peak.
Peaks are computed from solar longitude, not hard-coded per year, so the table does not
expire and does not need updating with the app.

Two honesty rules:

1. **The peak is a night, not an instant.** Render the local night window the peak falls
   in, not a bare timestamp that implies a precision nobody has.
2. **Moonlight is part of the verdict.** A Geminid peak under a full moon is a failed
   build even under a clear sky. The verdict engine takes moon illumination and moon
   altitude at the event, and says which of the two conditions failed. This is the one
   place the module is genuinely more useful than any weather app, so do not skip it.

ZHR is not shown. A predicted rate is a modelled number the app cannot verify, and the
series does not print numbers it cannot stand behind.

---

## 7. Verdicts

Verdict vocabulary is **shared with the series**, so the three apps speak one grammar:
`✓ pass`, `~ unstable`, `✗ fail`, plus two states this module also needs, `? unknown` and
`∅ not scheduled`.

| Verdict | Condition |
|---|---|
| `✓ pass` | cloud cover ≤ 25 % over the event window, rain below 40 % |
| `~ unstable` | 26–65 % cloud, or rain ≥ 40 %, or a bright moon on a dark-sky job |
| `✗ fail` | > 65 % cloud, or rain ≥ 70 % |
| `? unknown` | beyond the forecast horizon, no fetch yet, data gone stale, or a gap in the hours |
| `∅ not scheduled` | the event does not occur here today (polar day, moonless day) |

"Precipitation at the event" became a probability threshold in 16d, because a
probability is what the hourly data actually carries. 70 % is deliberately the same
number the builtin precipitation alert already uses: "it is going to rain" should mean
one thing across the app, and a sky job disagreeing with a notification about the same
hour would be the app arguing with itself.

Rules:

- **The window, not the instant** ✅ 16d. For a range job take the mean of the hourly
  buckets it spans; for an instant job take the bucket containing it. State the number
  used. Rain is the exception and takes the MAX over the window — an hour of it inside
  a two-hour event is not averaged away, it is the thing that ruins the event.
- **A verdict belongs to a SIGHT, not to a moment** ✅ 16d. `SkyJob.observable` is
  false for the solstice, the instant of a quarter moon and solar noon: they happen at
  a computed time nobody goes outside to watch, and a `✗ fail` on a first quarter
  would be the file inventing a stake nobody has. This is a different predicate from
  `visibilityDependent`, which governs whether a REMINDER is suppressed (§10) — an
  earlier cut of 16d conflated them and left `sun.set`, the module's headline case,
  without a verdict at all.
- **Thresholds are engine constants, printed in the file's comment channel** (§4). They
  are not `settings.config` keys — see §10 for the reasoning and the reversal.
- **Astronomical-darkness jobs additionally require the moon condition** (§6): above 60 %
  illumination with the moon up, a clear sky is still `~ unstable`, and the comment names
  the moon, not the clouds.
- **Beyond the horizon there is no verdict.** Not an optimistic guess, not a blank cell:
  `? unknown` with a comment saying why.
- **A verdict is a forecast, not an observation.** The app never sees the sky. A recorded
  `✓ pass` means the reported cloud cover for that hour was 8 %, and the log says so
  (§8). Never phrase it as "you saw it".
- **Verdicts are recomputed on read**, never frozen. A verdict shown for a future event is
  the current forecast's opinion and will change.
- **Stale data holds no opinion** ✅ 16d. Past the app's existing staleness threshold
  (twice the polling interval) the verdict is `? unknown`, never the last known
  answer: printing that would be answering a question about tonight with what the app
  thought yesterday, in the same words it uses when it knows. The rule moved out of
  the widget into `domain/WeatherFreshness` on the way — how old is too old is a fact
  about the data, not about one surface that draws it.
- **The input landed in Fase 16a** (§13.1): `HourlyForecast` carries `cloudCoverPct` and
  the mapper keeps every hour of the response instead of one day of it. The absence a
  verdict has to survive is therefore a missing *hour*, never a missing number — past
  the end of `report.hourly` there is no bucket to average, and that is exactly where
  `? unknown` comes from.

---

## 8. `sky_runs.log`

Third tab of the Logs strip, after `weather_history.diff` and `weather_forecast.diff`.
Not a `.diff`: this file records outcomes, not changes, and calling it a diff would be the
same kind of lie the crontab avoided.

Rendered as a cron/journal transcript, newest first, one line per fired job.

```
Aug 25 20:22  sun.set             ✓ pass       cloud  8%   obs +12m
Aug 25 20:56  blue_hour.pm        ✗ fail       cloud 92%   obs +6m
Aug 24 22:12  golden_hour.pm      ~ unstable   cloud 45%   obs +31m
Aug 23 06:27  sun.rise            – skipped    // no fetch within ±90 min
Jun 21 --:--  sun.set             ∅ not run    // polar day: the sun does not set
```

- `obs +12m` is how far the observing fetch was from the event. It is printed because a
  verdict resolved from a reading forty minutes away is a weaker claim than one from a
  reading five minutes away, and hiding that distance would be dishonest.
- **`– skipped` is the coverage state.** If the app never fetched near the event, no
  verdict is invented. Those days count in no statistic.
- Runs are recorded **for enabled jobs only**, at the moment a fetch first observes that
  the event's instant has passed. A job disabled before it fired leaves no record.
- Day dividers, and a `# summary` line per day: `4 passed · 1 unstable · 1 skipped`.

### 8.1 Storage: the commit row, not a new table

The draft specified a `SkyRunEntity`, a `SkyRunDao`, a `SkyRunRecorder` and its own
pruning to 200. **Revision 2 does not build any of that.** Sky runs are stored the way
fired rules already are: **one nullable `sky_runs` TEXT column on `weather_history`**
(Room v3 → v4), written by the same post-fetch update path as `setFiredRulesOnLatest`.

Three reasons, and the first is not about cost:

1. **It is more honest.** A sky run is not an independent event, it is *something a fetch
   noticed*. `obs +12m` is the draft admitting exactly that in a string. Attaching the run
   to the commit that observed it says it structurally: the row's own timestamp minus the
   event instant **is** `obs`, so the field stops being a number the recorder computes and
   becomes a number the schema implies.
2. **The Logs screen already knows how to render it.** `fired_rules` renders as `✓ rule
   "x" fired` check lines on a commit in `weather_history.diff`. Sky runs render as
   `✓ sun.set 20:22 cloud 8%` check lines on the same commit — for free — and
   `sky_runs.log` is then a second *view* of the same rows, grouped by day instead of by
   commit. Two files, one truth, no reconciliation test needed.
3. **Pruning solves itself.** Runs age out with the 200-commit history. The alternative
   was a second retention policy that could disagree with the first about how far back the
   app remembers.

Cost of the change, as built: one migration, one column, one DAO update, zero new files
in `data/`. The `– skipped` state fell out for free — when the forecast in hand no longer
carries the event's hour, the verdict engine already answers `? unknown`, so no ±90
minute coverage rule had to be invented.

Explicitly **not** in scope: a `stats.md` for the sky. tweather has no stats file and this
module is not the reason to add one.

---

## 9. The README: `## Astronomy` grows up, `## Status` gets the warning

The prose register already exists in the city README, and the series rule is that the
README is the human summary of the machine-readable content. So the sky's prose goes
there — but **not as a new section.**

The draft appended a `## Tonight` block after `## Next hours`. That would have put sunset,
moon phase and moonrise in `## Tonight` while `## Astronomy`, six lines down, printed
sunrise, sunset, daylight and moon phase again. One document, two sections, same subject:
precisely the duplication the draft forbade the *engine*, forgotten for the *document*.

### 9.1 What the README shows

`## Astronomy` stays where Fase 13d put it (after `## Conditions`, before the footer) and
becomes the sky's home in the document:

```
## Astronomy
Sunrise 06:31 · Sunset 20:22 · Daylight 13h 51m
Golden hour 19:39–20:22 · Blue hour 20:22–20:56
Astronomical darkness 21:47–05:03, moonless from 23:11
Waxing crescent 🌒 34% lit · rises 11:04, sets 23:11
```

Four lines instead of three, every number from the same engine that fed `sky.crontab`, and
the section is **always present** — it is today, not an advertisement for a module. Lines
two and three appear only when the module is enabled (`sky.enabled`); lines one and four
are today's README, improved.

The **warning** goes into `## Status`, the README's existing badge section, which is
already the only place in the document that speaks in `>` blockquotes:

```
## Status
> Blue hour looks compromised: 45% cloud forecast at 20:22.
```

It is raised only for an **enabled, visibility-dependent job in the next 12 hours whose
verdict is not `✓ pass`** — so the section reports your subscriptions, and someone who
never opened `sky.crontab` never sees a sky warning. `## Status` is the correct home for
it on the merits, too: a compromised blue hour is a status of tonight, and the README
should have exactly one place where it tells you something is off.

Fully localized, headings included, since it is prose. There is a test asserting the numbers in `## Astronomy` are the ones `sky.crontab`
resolved for the same city at the same instant — **with two deliberate divergences
pinned by tests of their own**. `## Astronomy` describes TODAY and `sky.crontab`
describes what is NEXT, so at six in the evening the README rightly says today's
sunrise, already past, while the crontab rightly says tomorrow's. The moon is the same
distinction magnified, since it rises about fifty minutes later each day. Two files
answering two questions are not in disagreement, and the tests exist so nobody later
"reconciles" one into the other.

### 9.2 One engine, three renders — including the JSON

`weather_data.json`'s `astronomical` block is fed by the engine too, and this is a real
behaviour change, not a refactor:

- `sunrise` / `sunset` stop being Open-Meteo's daily values and become computed ones. The
  contract test (§14) pins the two within 120 s, so the visible drift is under a minute —
  but it exists, it will produce one `astronomical.sunrise` line in `weather_history.diff`
  the first time it lands, and that is the honest way for it to show up.
- **Why the engine wins over the provider's own value**: the file must show the same time
  offline, past the 7-day horizon, and in a `sky.crontab` line that is computed anyway. A
  document that shows 06:31 in one tab and 06:32 in another because one waited for the
  network is exactly what "one engine is the single source of truth" was written against.
- `MoonPhase.at()` stops being a computation. Today it is an eight-bucket average of a
  mean synodic month with a hardcoded reference new moon — accurate to about a day, which
  is why the KDoc says "plenty for an emoji". `moon.phase` needs the actual quarter
  instant, so the engine computes it and `MoonPhase` becomes a *classifier over the
  engine's illumination value*. The enum, its labels and its emoji survive untouched;
  only the arithmetic behind them is replaced.
- `daylightDuration` follows from the engine's own sunrise/sunset rather than the
  provider's `daylight_duration`, for the same consistency reason: three numbers that
  must agree are better as two numbers and a subtraction.
- **`Astronomical` had to learn to say "it does not".** Its `sunrise` and `sunset` were
  non-null because the provider always sent something; above the Arctic circle in June
  there is no sunrise, and the old type could only put another time in its place. They
  are nullable now — the JSON prints `null` (in character: it already does that for a
  section the providers could not fill) and the README prints `∅`, the same glyph
  `sky.crontab` uses for the same fact.
- **One consequence worth naming**: the engine answers to the second and
  `WeatherSnapshots.flatten` writes `sunrise.toString()` into the history, so without
  truncating to the minute every single fetch would have added a fresh
  `astronomical.sunrise` line to `weather_history.diff`. The provider's values were
  minute-precise and nothing noticed until they stopped being the source.

---

## 10. Settings, notifications, widget

### `settings.config` — a three-key `[sky]` block

```
[sky]
enabled          = true    # false removes the sky.crontab tab and the README's sky lines
notify_default   = off     # the lead every sky.crontab line without one of its own (16f)
notify_on_fail   = false   # send the reminder anyway when it will not be visible (16f)
```

Only `enabled` shipped in 16c. The other two govern reminders 16f sends, and a setting
for a notification the app cannot deliver yet is the same lie as a `--notify` token
that does nothing.

✅ **`notify_default` is a fallback, not a seed, and it defaults to `off`.** The draft
read it as the lead copied into a job when you add it. Built that way it is a dead end:
every line stores its lead as null until somebody taps one, so a file of lines nobody
has touched renders no `--notify` column, and with no column there is nothing to tap —
a reminder could never be switched on at all. So null on a line means *follow
`notify_default`*, not *no reminder*: one setting and the whole file grows the token,
after which any line can be given its own. `off` out of the box, because an install
that switched `sky.enabled` on to *read* the file must not start notifying for it.

The draft had eight keys. Five are gone and each for a reason:

- `horizon_days` — **a fact, not a preference.** The horizon is where Open-Meteo's hourly
  data stops. A setting inviting the user to make it *shorter* offers a worse app as an
  option.
- `cloud_pass_pct`, `cloud_fail_pct`, `moon_wash_pct` — the requirement was that the
  thresholds be visible, not that they be adjustable. §4 prints them in the file's comment
  channel, which is where this app puts facts it knows. Three numeric cycles in
  `settings.config` for a module a user may never open is settings surface spent badly.
  If the committente wants them tunable later, promoting a constant to a key is a
  half-hour change; un-shipping three keys is not.
- `time_format` — the app formats every clock as `HH:mm`, everywhere, from one
  `DateTimeFormatter`. A per-module time format would be the first place tweather
  disagreed with itself about what a time looks like.

`$ git restore settings.config` resets these with the rest, and does not touch which jobs
are subscribed — that is file content, not a setting.

### Notifications, and their real cost

Reminders ride `--notify=<lead>` per job. **This is the only part of the module that adds
a scheduling primitive to the app, and it should be the last thing built.**

Today tweather schedules exactly one thing: a WorkManager periodic job at 15/30/60/120
minutes, default 60. Nothing else. A "30 minutes before sunset" reminder cannot ride that
tick — at the default interval the app wakes twice an hour at times that have nothing to
do with sunset — so it needs `AlarmManager`. That brings:

- **`AlarmManager.setAndAllowWhileIdle`**, never `setExact*`, never `SCHEDULE_EXACT_ALARM`.
  Battery is a feature, and a sunset is not an alarm clock. `setWindow` is not enough:
  under Doze a plain window alarm is deferred to the next maintenance window, which can be
  hours, and the reminder arrives after dark.
- **A `RECEIVE_BOOT_COMPLETED` receiver and a re-arm path**, because alarms do not survive
  a reboot and WorkManager's persistence does not extend to them. That is a new
  permission (normal, not runtime), a new manifest receiver, and one more thing that can
  silently stop working.
- **A drift the app must state.** Inexact means ±10 minutes in practice, which is why the
  minimum selectable lead is **15 minutes** and why `5m` is not in the cycle (§4). The
  settings hint says the reminder is approximate, in plain words.

Given that, the phasing (§15) puts notifications **after** the README and the widget, so
that everything which costs nothing ships first and the alarm question is answered on its
own, with the rest of the module already in the user's hands.

Behaviour, as built (16f) ✅:

- The notification carries the verdict: `golden hour in 30 min — ✓ clear (8%)`. With
  `notify_on_fail = false` (the default) a `✗ fail` verdict suppresses it, because a
  reminder for something you cannot see is noise.
- `? unknown` **is sent** for jobs that happen regardless of the sky (sun events) and
  **suppressed** for visibility-dependent ones. Both carry `// no recent data`.
- One notification per job per occurrence, deduplicated like the existing rule alerts,
  in the same DataStore shape as `alerts` and `rule_state`.
- Only the active city is scheduled. A pinned widget city does not schedule sky
  notifications; `HELP.md` says so rather than letting it be a surprise.
- The whole path is gated on the same `POST_NOTIFICATIONS` grant everything else uses.

✅ **Three things the build added to this list.**

- **One alarm at a time, and it is re-armed from four places**: after every fetch, after
  every edit to `sky.crontab`, when a reminder fires, and on boot. The re-arm on a fired
  reminder is in a `finally`, so a reminder that cannot be delivered at all still leaves
  the next alarm behind — without it, one failure ends the reminders silently and
  forever. A queue of alarms would buy nothing: the plan is recomputed on every fetch,
  and a queue would have to be cancelled and rebuilt on every `--notify` tap.
- **Switching `sky.enabled` off cancels the pending alarm there and then.** Left armed it
  would survive until it fired, wake the device, find the module off and cancel itself:
  correct, and one pointless wakeup after the user said no.
- **The dedup fingerprint identifies the occurrence to the MINUTE**, not the second. The
  engine's answer for one sunset moves by fractions of a second between two evaluations,
  and a fingerprint that moved with it would dedup nothing at all. Same lesson as the
  truncation 16e had to add to `WeatherSnapshots.flatten`, in a different place.

### Widget

No new widget. The existing terminal widget gains **one optional line**, in its resize
ladder, showing the next enabled sky job and its verdict:

```
$ tweather --now
Milan            22.8°C  🌤️
next: sun.set 20:22   ✓ clear
```

It repaints on the same fetch commit as everything else and costs no extra battery.
Whether the line appears is a `widget.config` option, default off, so existing widgets do
not silently change shape on update. It sits **last in the line budget**, so it is the
first line dropped when the launcher gives the widget less room — the temperature is why
the widget exists; the sky line is not.

---

## 11. Edge cases that must be handled explicitly

These are not defensive coding, they are the module's honesty surface. Each one gets a
test.

| Case | Required behaviour |
|---|---|
| **No location configured** (Fase 14b) | The tab renders `// no location configured` / `// hint: open cities.json and search a city`, like the editor's other two files. Nothing is computable without a latitude, and an empty schedule would be the first fabricated thing in the module. |
| **GPS pseudo-city** (id `-1`) | Renders like any other city, off the last persisted fix. Run history keys off `cacheKey`, so it partitions per ~1.1 km cell exactly as the weather history already does. |
| Polar day / polar night | `∅ not scheduled`, comment naming the cause. Never a fabricated time. |
| Moon does not rise on a given day (happens ~monthly) | `∅ not scheduled`, comment. Not an error, not 00:00. |
| DST transition ✅ 16c | Instants computed in UTC, rendered in the city's zone. On the switch day the header says so — but spelled out, not signed: this spec sketched `# DST +1h on Oct 25`, and on that date the offset goes DOWN an hour while the day gets an hour LONGER, so a `+` is right twice and wrong twice depending on which the reader meant. It reads `# DST: the clock falls back 1h on Oct 25`. Golden showcase for the `@daily` choice: the recurrence stays true while the time jumps. |
| Active city in another timezone | The whole file renders in **the city's** local time, and the header names the zone. A sunrise in Tokyo shown in Rome time is the file lying. |
| City switched while the tab is open | Recompute, clear nothing. Run history is per city; subscriptions are not. |
| Event exactly at the forecast boundary | `? unknown`, never extrapolated. |
| Stale data | Reuse the existing staleness signal; a verdict older than the staleness threshold prints `? unknown`, not the last known verdict. |
| Fetch failure | Same `//` channel as the rest of the app. The schedule still renders: it needs no network. |
| First run after install ✅ 16c | Schedule renders immediately from computation; verdicts fill in after the first fetch. The tab is never empty (unless there is no city, above). Four lines are seeded, and the seeding is marked with its own flag: without it, removing the last job looks exactly like a fresh install and the defaults grow back over an emptied file. |
| `moon.today` ✅ 16c | Not an event the sky has scheduled but a statement about the day you are in. Resolved as "the next occurrence" it read `Aug 27 12:00` from six in the evening — a line called `today` naming tomorrow. It answers for today, and without a clock time, because a phase does not happen at noon. |
| Equator | Golden and blue hour are genuinely short. Render the real numbers, do not floor them. |
| Two jobs at the same instant | Stable order from the catalog, no collapsing. |
| **Three tabs in the strip** ✅ 16c | `EditorTabs` scrolled horizontally but never brought the selected tab into view: with three names the third could be selected with its 2px indicator off-screen, so the bar showed no active file at all. Fixed in the component — measured tab bounds and an animated scroll, no experimental API — which also repays the Settings and Logs strips. |
| **Column widths** ✅ 16c | The file pads to its OWN longest name, not to the catalog's. Padded to `meteor.eta_aquariids.peak` a two-line file pushed its own `[rm]` past the edge of a 360dp screen. A crontab is aligned with `column -t`, which measures the file in front of it. |

---

## 12. ISS passes — recommended against

The draft phased `iss.pass` last with a go/no-go. Revision 2 makes the recommendation:
**do not build it**, and let the catalog stay open in case that changes.

What it would cost:

- **A second data source.** TLEs from CelesTrak (free, no key, no account). The README's
  data table and the "no API key" badge stay honest, but the "one source" story ends —
  and that story is currently one of the three things the README leads with.
- **Real orbital work.** SGP4 propagation, topocentric look angles, and the visibility
  condition (satellite sunlit, observer in astronomical darkness, elevation above ~10°).
  This is the largest single piece of maths the app would contain, larger than everything
  else in this spec combined, and it needs its own test vectors to be trustworthy.
- **TLE freshness as a first-class fact**, with its own staleness rule and its own comment
  channel line, because a TLE older than ~7 days degrades badly.
- **A notification that cannot be delivered.** A pass lasts six minutes; an inexact alarm
  drifts ten. There is no honest `--notify` for this job, and "the one job whose reminder
  does not work" is a worse artefact than a missing job.

The last point is the decisive one. Every other job in the catalog degrades gracefully
when the app is imprecise — a sunset reminder eight minutes late is still a sunset
reminder. An ISS pass eight minutes late is a notification about something that is over.
A module whose thesis is "the file may not know something, it may not invent it" should
not ship a line it structurally cannot honour.

If it is ever built, nothing else in the module changes: it is one more `SkyJob` in the
catalog with a polling expression.

---

## 13. Architecture

Same stack, same conventions. One test-only dependency (§14); no runtime dependencies.

```
domain/sky/
  AstronomyMath.kt        Meeus's series; internal; degrees in, degrees out    ✅ 16b
  AstronomyEngine.kt      pure; no clock; lat/lon/zone in, instants out        ✅ 16b
  SkyJob.kt               id, kind, cron expression, catalog metadata          ✅ 16b
  SkyJobCatalog.kt        the fixed set; versioned; no runtime registration    ✅ 16b
  SkyScheduler.kt         resolves the next N occurrences of a subscription    ✅ 16b
  MeteorShowerTable.kt    solar-longitude peaks                                ✅ 16b
  SkyVerdictEngine.kt     resolved event + hourly forecast + moon → verdict
  SkyAlmanac.kt           the engine with a memo; bounded, per (city, local date)  ✅ 16c
ui/sky/
  SkyDocument.kt          the file as pure data, so it is asserted without Compose ✅ 16c
  SkyCrontabScreen.kt     the rows; reuses the gutter, canvas and token renderer   ✅ 16c
  SkyScreen.kt            third tab of the Editor strip                            ✅ 16c
  SkyReadme.kt            the README's sky summary, as data, not words           ✅ 16e
  SkyWidgetLine.kt        the widget's one optional line                          ✅ 16e
ui/logs/
  SkyRunsLog.kt           third tab of the Logs strip; a grouped view over commits ✅ 16e
data/
  SkySubscriptionStore.kt DataStore `sky`, the subscribed jobs and their leads     ✅ 16c
```

No `SkyRunDao`, no `SkyRunEntity`, no `SkyRunRecorder`: §8.1.

Decisions to record in `PLANNING.md`:

- **Subscriptions are global, not per city.** A user who cares about golden hour cares
  about it in every city. Run history is per city; the subscription list is not.
- **No new WorkManager job.** The existing fetch tick resolves upcoming events, evaluates
  verdicts and records elapsed runs. Notification alarms are the only new scheduling
  primitive, and they are inexact (§10).
- **`AstronomyEngine` takes no Android and no clock**, like the alert and rule engines, so
  all of it is JVM-testable.
- **One primitive, every event** (16b): rather than a formula per event, the engine finds
  the instant a body's altitude crosses a threshold. Sunrise is that crossing at −0.833°,
  civil dawn at −6°, golden hour between +6° and −0.833°. The event list can then grow
  without the maths growing, and polar day, a moonless day and a ten-minute equatorial
  twilight are one code path instead of three special cases.
- **Solstices and equinoxes are the exception, and the reason is measured** (16b). The plan
  was to root-find them on the same solar longitude the showers use. The low-accuracy solar
  series is good to ~0.01°, the sun covers 0.01° in a quarter of an hour, and the 2026
  March equinox came out **eight minutes** from the published instant — inside the model's
  own error bar and outside what an `HH:mm` may claim. Seasons therefore come from the
  series fitted to season instants (Meeus 27, within a minute across 2024–2030); the
  root-find stays for shower peaks, where the answer is a nine-hour night. A test measures
  the gap between the two and pins it, so a change in either is a red test rather than a
  surprise.
- **Positions run on TT, hour angles on UT** (16b). ΔT is ~75 s: passing UT to a position
  series is a small error, passing TT to sidereal time is a large one.
- **Memoize per (city, local date)**; measured at 1.7 ms per `solarDay` and 0.9 ms per
  `lunarDay` on a development JVM, so on a phone this is a dropped frame if a long file
  recomputes it per recomposition. The memo is required, not precautionary.

### 13.1 The data-layer prerequisite — **done, Fase 16a**

Nothing in §7 was computable against the domain model as it stood. Two changes, both in
`WeatherReportMapper`, both free at the network layer:

1. **`HourlyForecast` gains `cloudCoverPct`.** Open-Meteo's `cloud_cover` is already in
   `HOURLY_VARIABLES` — it has been fetched since Fase 13c, where it repairs `weather_code`'s
   unreliable fog — and it is already parsed into `ForecastHourlyDto`. The mapper simply
   stops dropping it. Zero new requests, zero new bytes.
2. **`HOURLY_WINDOW` widens from 25 to 168.** `forecast_days = 7` already returns 168
   hourly values, they are already parsed, and `ReportDiskCache` already persists all of
   them as raw DTOs. The app maps 25 and discards 143 — so a 3-day verdict horizon is not
   a new capability, it is data the app currently throws away.

Widening the window was the risky half, because three renderers read `report.hourly`:

- **`weather_data.json`** did `hourly.drop(1)` with no cap and would have printed 167
  rows. It gained an explicit `.take(HourlyJsonRows)` — which is what its KDoc already
  claimed it showed.
- **`README.md`** already does `take(HourlyRows)`. Untouched.
- **`RuleVariables`** windows filter by `time` (`next_6h`, `next_12h`) and **`AlertEngine`**
  bounds by `now.plusHours(…)`. Both are already time-bounded. Untouched.

A test asserting `hourly_forecast` renders exactly 24 rows locks the first one down, and
was verified to fail without the cap.

**One decision reversed by its own test, worth carrying into 16d.** `cloudCoverPct` was
drafted nullable, so that the verdict's `? unknown` would have an input able to produce
it: a cloud cover the app does not have is not a clear sky. The test written for that
case never reached the mapping — the fog repair reads the same column one step earlier,
across every index, and throws first. The null was a state the type allowed and the data
cannot hold, and a field like that gets `?: 0` written against it at every read site,
where 0 % cloud means "clear". The nullable version was the shortest road to the exact
lie it was meant to prevent. So the field is **non-null**, and 16d's `? unknown` comes
from where it always really came from: an event with no hour behind it.

---

## 14. Tests

The existing suite is 360 JVM tests and CI gates the build on it. This module should add
roughly 80–120.

**Correctness**

- ✅ **Contract test against Open-Meteo** (16b): nine sites from −67.6° to +69.6°, three
  days each, 54 sunrise/sunset comparisons against values captured from the live API and
  frozen in the test. Tolerance **120 s**, and the limit is on the provider's side: its
  values are truncated to the minute (every mid-latitude row lands 0–60 s after the
  reference, never before) and past |lat| 60 a minute of clock is only ~0.07° of altitude,
  so its own approximation shows through. This is the test that makes §9.2's "the engine
  wins" a defensible choice rather than a preference.
- ✅ **Solver exactness** (16b): for every solar event, the sun really is at the altitude
  that defines it, within 0.005° — one second of its motion, which is the resolution the
  engine answers at. Paired with the contract test this covers the whole solar catalog
  without a reference table per event: the first proves the sun's POSITION, the second
  proves the SOLVER at any threshold, and twilight, golden hour and blue hour are then
  correct by construction.
- ✅ Solstices and equinoxes against seven published instants across 2024–2030, within
  56 s (16b).
- ✅ Moon quarter instants against reference quarters, within 5 min. `MoonPhase.at()` is
  reconciled with the new engine, not left as a second implementation (§9.2) — its six
  original assertions pass unchanged (16b).

**The metaphor**

- ✅ **Every cron expression the renderer emits parses under a real cron parser** (16b).
  `cron-utils` as a `testImplementation`-only dependency: the app emits cron and never
  reads any. The emitted set is finite and enumerable, so this could have been a
  hand-written check — the point of an outside parser is that it is not our opinion of
  what a crontab is. Worth noting what it cost to satisfy: `CronType.UNIX` alone answers
  "Nicknames not supported!" to `@daily`, so the test builds the definition that a real
  vixie crontab has, nicknames included. §3's whole argument rests on `@daily` being a
  thing a crontab accepts, and that is now asserted rather than assumed.

**Behaviour**

- Verdict table: every threshold boundary, precipitation override, moon-wash override.
- Coverage: no fetch near an event → `– skipped`, and it enters no summary count.
- Polar day, polar night, moonless day, equatorial short twilight.
- DST forward and backward, Europe/Rome and a Southern Hemisphere zone.
- Remote-city rendering: all times in the city's zone.
- No location configured: the crontab renders the two comment lines and nothing else.
- Notification dedup across fetches; suppression on `fail` and on `unknown` for
  visibility-dependent jobs; re-arm after a simulated boot.
- **Agreement test:** the numbers in `README.md` `## Astronomy` equal the numbers in
  `sky.crontab` for the same city and instant. The series' first rule, as an assertion.
- **Regression guard for §13.1:** `hourly_forecast` renders exactly 24 rows, written
  before `HOURLY_WINDOW` changes.

**Accessibility**

- Every crontab line and every log row announces its state in words, English and Italian,
  including the verdict and the disabled state.

---

## 15. Phasing

Each phase ends green and shippable. `PLANNING.md` carries these as **Fase 16a–16f** with
the decisions and every deviation, as it already does.

| Phase | Content | Network |
|---|---|---|
| **16a** ✅ | `HourlyForecast.cloudCoverPct`, `HOURLY_WINDOW` 25 → `FORECAST_DAYS × 24`, an explicit cap in the JSON render, regression test first. No UI, no sky code. | none |
| **16b** ✅ | `AstronomyEngine` + `SkyJobCatalog` + `SkyScheduler` + `MeteorShowerTable` + the full correctness suite. `MoonPhase` reconciled. No UI. | none |
| **16c** ✅ | `sky.crontab` as the Editor strip's third tab: resolved schedule, tap to enable/disable, `[rm]`, `+ add job`, `sky.enabled` in `settings.config`. `EditorTabs` brings the active tab into view. No verdicts, no log, no notifications. | none |
| **16d** ✅ | `SkyVerdictEngine` on the widened hourly forecast. Verdicts in the comment channel, `$ tweather run sky`. | existing fetch |
| **16e** ✅ | `sky_runs` column (Room v4) + check lines on the commit + `sky_runs.log` as the Logs strip's third tab. `## Astronomy` and `## Status` in the README. Optional widget line. README/CHANGELOG. | existing fetch |
| **16f** ✅ | `--notify` leads resolved through `notify_default`, `AlarmManager.setAndAllowWhileIdle`, boot receiver and re-arm, `sky_alerts` dedup, `notify_on_fail`. The separate go/no-go was taken and passed: everything above still ships without it. | existing fetch |

16c alone is already a shippable, genuinely useful feature: a correct, localized,
timezone-honest astronomical schedule for any city, computed entirely offline. If the
project stops there, nothing is half-built — and 16a is worth having on its own merits
regardless, since it is the app keeping data it already pays for.

---

## 16. Documentation to update on the way out

- **`README.md`**: a `### sky.crontab` section between `alerts.rules` and the log files,
  written in the same register as the rest — the metaphor first, then the honesty rule
  that follows from it. The one-liner at the top widens from "weather app" to something
  that admits the sky. The data table gains a row: *sun, moon, twilight, showers →
  computed locally, since the API does not provide them* — the row the moon phase already
  occupies today. **No em dashes or en dashes in that file** (house style, `CLAUDE.md`).
- **`HELP.md`**: the borrowed-words list gains *crontab*, in one line, for the same reason
  it explains *commit* and *diff*. And the sentence about which city gets notifications.
- **`CHANGELOG.md`**: a minor version bump per phase, not one entry at the end.
- **`PLANNING.md`**: Fase 16a–16f, decisions and deviations, as the file already does.
- **`CLAUDE.md`**: the app structure section lists four screens and their files; the two
  new tabs belong in it.
- **`obsidian_syntax/DESIGN.md`**: three token roles to define — cron expression, job
  name, verdict glyph. Reuse existing colours (keys blue, numbers orange, comments grey,
  additions green, deletions red); do not introduce a new hue for this.

---

## 17. The line to hold

If a decision in implementation is ambiguous, the tiebreaker is the rule that already
governs the app:

> The file may not know something. It may not invent it.

A cloud verdict the forecast cannot support is `? unknown`. A moonrise that does not
happen is `∅`, not `00:00`. A cron expression that would be false is not written. A run
the app did not observe is `– skipped` and counts nowhere. A reminder the app cannot
deliver on time is not offered as a shorter lead.
