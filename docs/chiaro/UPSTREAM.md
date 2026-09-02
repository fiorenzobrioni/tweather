# UPSTREAM.md — where `:core` came from

`:core:domain` and `:core:data` are a **copy** of tweather's domain and data layers,
not a link to them (VISION.md §7.3). This file is the ledger that decision depends on:
without it, the first time the same bug has to be fixed in both apps, telling what
drifted from what is archaeology.

## The seed

| | |
|---|---|
| Source | [fiorenzobrioni/tweather](https://github.com/fiorenzobrioni/tweather) |
| Commit | `d7914ec838c38b1dc0757279cdf6e3772526b750` |
| Short | `d7914ec`, 2026-09-02 |
| Seeded | 2026-09-02 (Fase 0) |
| Files | 78 Kotlin files: 24 domain main, 16 domain test, 23 data main, 15 data test |

Reproduce it with a tweather checkout beside this repo:

```bash
python3 tools/seed_core.py ../tweather
python3 tools/seed_edits.py
```

`seed_core.py` is the mechanical half (package rename, four identifier renames, the
sample report repackaged). `seed_edits.py` is the half that is not mechanical, and it
is short on purpose — three edits, each with its reason in the file:

1. The four settings types the engines read (`TemperatureUnit`, `WindSpeedUnit`,
   `UnitSettings`, `NotificationSettings`) move from the store into
   `domain/settings/`, so `:core:domain` depends on nothing underneath it.
2. `ServiceLocator` stops importing the app: the User-Agent and the "new data landed"
   callback are handed in by `ServiceLocator.install` from `ChiaroApplication`, rather
   than reached for through `BuildConfig` and a widget class.
3. `sampleWeatherReport` becomes public: it crossed a module boundary, so `internal`
   no longer reaches its readers.

## What is NOT the same as upstream

- `sys@tweather.app` → `sys@chiaro.app` in the history rows. A value, not a comment.
- `TweatherDatabase` → `ChiaroDatabase`, `tweather.db` → `chiaro.db`.
- No `:core:sync` yet: the worker and the notifiers arrive in Fase 6 (PLANNING.md).

## The known debt

**The inherited comments still speak tweather's vocabulary.** `CityStore` mentions
`$ tweather init`, `RuleEngine` mentions `$ tweather run rules`, `WorkspaceStore`
mentions a hint in a file that does not exist here. Ten lines, all of them comments.

They were deliberately left alone in Fase 0, and the reason is worth writing down: each
one names a tweather SURFACE, and the honest replacement is the name of the Chiaro
surface that does the same job — which for most of them has not been designed yet.
Rewriting them now would mean inventing vocabulary in a comment instead of in a phase.
**Every phase rewrites the comments in the code it touches**, and the count above is
what "done" is measured against.

## When to extract

The rule from VISION.md §7.3: copy now, extract `weather-core` into its own repo when
the same bug has to be fixed in both apps for the second time. The `:core:*` split
exists from day one precisely so that extraction never requires moving code between
packages — only moving directories between repositories.
