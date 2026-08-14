# Screenshots used by the README

Real captures from a device, not the design mockups in `*/screen.png` — those are
reference art the finished app deliberately departs from in several places (the
deviations are listed in `PLANNING.md`), so showing them as the product would
misrepresent it.

| File | What it shows |
| --- | --- |
| `main.jpg` | the Explorer tab on `weather_data.json` |
| `logs.jpg` | the Logs tab, one commit with its `+`/`-` lines |
| `settings.jpg` | the Settings tab |
| `widget.jpg` | the home-screen widget, cropped |

JPEG on purpose, not an oversight. These are already-lossy captures, so re-encoding
them as PNG would produce a lossless copy of a lossy image: PNG's size with JPEG's
quality. Measured on this set, that trade is 945 KB → 3.4 MB for a difference GitHub
scales away in the README column anyway. If a screenshot is ever retaken, capture it
straight to PNG and keep it that way.
