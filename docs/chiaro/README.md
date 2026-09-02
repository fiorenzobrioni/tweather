# Chiaro

![Status](https://img.shields.io/badge/status-fase%200-8C857A?labelColor=FCFAF6)
![License](https://img.shields.io/badge/license-GPL--3.0-3A7CA5?labelColor=FCFAF6)
![minSdk](https://img.shields.io/badge/minSdk-33-6C5B8C?labelColor=FCFAF6)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-E8A33D?labelColor=FCFAF6)
![API key](https://img.shields.io/badge/API%20key-none%20needed-2E6B3E?labelColor=FCFAF6)

**The weather app that tells you what to do about it.**

Most weather apps answer "how many degrees". Chiaro answers the question people actually
have: is it worth going outside, and when. It opens with one computed sentence
("Umbrella around 17:00, clear after that"), and the numbers are there for whoever wants
them.

Three things it does that a weather app normally does not:

- **The sky has an agenda.** Sunrise, the golden hour, the blue hour, the genuinely dark
  window, the moon, the meteor peaks: each one with the time it happens and whether the
  sky will actually let you see it, computed from the same cloud forecast the app already
  downloaded. The number that decided the answer is printed next to the answer.
- **It remembers the forecast.** Saturday used to be 70% rain and now it is 30%. No other
  everyday app tells you that the forecast moved, and the movement is often more useful
  than either number on its own.
- **You write the alerts.** Start from a template ("tell me when I can ride") or build one
  from real variables. Rain above 70% in the next six hours, below zero tomorrow morning,
  whatever you actually care about.

No account, no ads, no tracking, no API key. It works offline with the last data it
managed to fetch, and it says how old that data is instead of pretending.

## Status

Early. Fase 0 is done: the project builds, and the engines it inherited arrive with their
test suite green (248 tests). The screens start with Fase 2. `PLANNING.md` is the honest
account of where things are.

## Where it comes from

Chiaro is the daylight edition of [tweather](https://github.com/fiorenzobrioni/tweather),
a weather app whose entire interface is a code editor: the forecast as syntax highlighted
JSON, the settings as a config file you edit by tapping values, the update history as a
git diff. tweather is furniture for developers and was built that way on purpose.

Underneath that interface sits about eight thousand lines that have no opinion about
looking like an editor: an Open-Meteo client, a full solar and lunar ephemeris, an alert
engine, a rules engine, a Room history. Chiaro takes that layer as it stands, with its
tests, and puts a Material 3 product on top of it. The two apps ship side by side and
neither replaces the other.

`UPSTREAM.md` records exactly which commit the shared core came from and how to reproduce
the copy.

## Design

Material 3, committed to rather than defaulted to: generated color (dynamic color from the
wallpaper, with the app's own scheme as the fallback), light and dark, the expressive type
and shape scales, spring motion.

Two elements are Chiaro's own, and both are computed rather than decorative:

- **The sky canvas**, the gradient at the top of the home screen, comes from the real
  position of the sun, the cloud cover and the moon. It cannot disagree with the forecast
  below it, because it is drawn from the same engine.
- **The daylight ribbon**, a thin band showing night, twilight, the golden hours and
  daylight, used on the canvas and on every day of the week.

Everything is specified in `DESIGN.md`, with the measured contrast numbers attached.

## Build

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew test :app:testDebugUnitTest # every module's tests
./gradlew :app:lintDebug              # lint
```

The debug keystore is committed on purpose so builds from CI and from any machine share
one signature. Debug builds install side by side with the release build.

## License

GPL-3.0, like the rest of the family.
