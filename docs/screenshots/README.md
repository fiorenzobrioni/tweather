# Screenshots used by the README

Real captures from a device, not the design mockups in `*/screen.png` — those are
reference art the finished app deliberately departs from in several places (the
deviations are listed in `PLANNING.md`), so showing them as the product would
misrepresent it.

Captured with the phone in Italian, which is why the chrome and the weather *values*
read `Impostazioni` and `Prevalentemente sereno` while the JSON keys, the filenames and
the git headers stay English. That split is the app's l10n rule, not an artifact of the
screenshots.

| File | What it shows |
| --- | --- |
| `main-json.jpg` | Editor tab, `weather_data.json` — the forecast as highlighted JSON |
| `main-md.jpg` | Editor tab, `README.md` — the city summary, with the hourly table |
| `logs-history.jpg` | Logs tab, `weather_history.diff` — one fetch as a commit |
| `logs-forecast.jpg` | Logs tab, `weather_forecast.diff` — the same future day, re-predicted |
| `settings.jpg` | Settings tab, `settings.config`, `alerts.rules` beside it in the tab bar |
| `widget.jpg` | the home-screen widget, cropped |

The pairs are deliberate: `main-*.jpg` and `logs-*.jpg` are the same screen with the
other file open. One shot each is what shows the tab bar doing something, which is why
those two screens cost two files apiece.

Not captured yet: `cities.json` (Search) and the `alerts.rules` tab itself. Both are
described in the root README without an image; if either is ever shot, add it here and
link it there in the same pass.

JPEG on purpose, not an oversight. These are already-lossy captures, so re-encoding
them as PNG would produce a lossless copy of a lossy image: PNG's size with JPEG's
quality. Measured on this exact set, that trade is 1.5 MB → 4.9 MB (3.4×) for a
difference GitHub scales away in the README column anyway. The rule is never to
*re-encode*: if a future capture comes off the device as PNG, keep it PNG.
