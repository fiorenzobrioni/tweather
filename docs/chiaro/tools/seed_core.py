#!/usr/bin/env python3
"""Seed :core:domain and :core:data from a tweather checkout (PLANNING.md, Fase 0).

The copy is mechanical on purpose and this script IS the record of it: rerun it
against a newer tweather to see what drifted, and read UPSTREAM.md for the commit
the current tree came from.

Everything it does beyond a package rename is listed in EDITS below, because a
"mechanical copy" that quietly rewrites logic is neither.

    python3 tools/seed_core.py ../tweather
"""
from __future__ import annotations

import pathlib
import shutil
import sys

SRC_PKG = "com.callbackdev.tweather"
DST_PKG = "com.callbackdev.chiaro"

# Types that lived in tweather's data layer although the domain reads them. They
# move into :core:domain so the domain does not depend on the data layer; the
# store keeps only the DataStore plumbing.
MOVED_TO_DOMAIN = ["TemperatureUnit", "WindSpeedUnit", "UnitSettings", "NotificationSettings"]


def rewrite(text: str) -> str:
    text = text.replace(SRC_PKG, DST_PKG)
    # The sample report is a pure builder that tweather parked next to a Compose
    # preview; here it is domain data and the previews import it from there.
    text = text.replace(
        f"{DST_PKG}.ui.weather.sampleWeatherReport",
        f"{DST_PKG}.domain.sample.sampleWeatherReport",
    )
    for name in MOVED_TO_DOMAIN:
        text = text.replace(f"import {DST_PKG}.data.{name}", f"import {DST_PKG}.domain.settings.{name}")
    text = text.replace("TweatherDatabase", "ChiaroDatabase")
    text = text.replace('"tweather.db"', '"chiaro.db"')
    # Written into every history row and rendered by the Journal's entries: a value,
    # not a comment, so it is renamed here rather than left as inherited prose.
    text = text.replace("sys@tweather.app", "sys@chiaro.app")
    return text


def copy_tree(src: pathlib.Path, dst: pathlib.Path, package_from: str | None = None,
              package_to: str | None = None) -> int:
    n = 0
    for path in sorted(src.rglob("*.kt")):
        rel = path.relative_to(src)
        # A file keeps its class's name: ChiaroDatabaseMigrationTest does not live in
        # TweatherDatabaseMigrationTest.kt.
        rel = rel.with_name(rel.name.replace("Tweather", "Chiaro"))
        target = dst / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        text = rewrite(path.read_text())
        if package_from and package_to:
            text = text.replace(f"package {package_from}", f"package {package_to}", 1)
        target.write_text(text)
        n += 1
    return n


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    up = pathlib.Path(sys.argv[1]).resolve()
    main_src = up / "app/src/main/java/com/callbackdev/tweather"
    test_src = up / "app/src/test/java/com/callbackdev/tweather"
    if not main_src.is_dir():
        print(f"not a tweather checkout: {up}")
        return 1

    here = pathlib.Path(__file__).resolve().parent.parent
    dom_main = here / "core/domain/src/main/kotlin/com/callbackdev/chiaro/domain"
    dom_test = here / "core/domain/src/test/kotlin/com/callbackdev/chiaro/domain"
    dat_main = here / "core/data/src/main/kotlin/com/callbackdev/chiaro/data"
    dat_test = here / "core/data/src/test/kotlin/com/callbackdev/chiaro/data"
    for d in (dom_main, dom_test, dat_main, dat_test):
        if d.exists():
            shutil.rmtree(d)

    counts = {
        "domain/main": copy_tree(main_src / "domain", dom_main),
        "domain/test": copy_tree(test_src / "domain", dom_test),
        "data/main": copy_tree(main_src / "data", dat_main),
        "data/test": copy_tree(test_src / "data", dat_test),
    }

    # The sample report, repackaged (see rewrite()).
    sample = main_src / "ui/weather/SampleWeatherReport.kt"
    target = dom_main / "sample/SampleWeatherReport.kt"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        rewrite(sample.read_text()).replace(
            f"package {DST_PKG}.ui.weather", f"package {DST_PKG}.domain.sample", 1
        )
    )
    counts["domain/sample"] = 1

    for k, v in counts.items():
        print(f"  {k}: {v} files")
    print("seeded. Now apply tools/seed_edits.py for the two non-mechanical edits.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
