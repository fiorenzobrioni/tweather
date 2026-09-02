#!/usr/bin/env python3
"""The two edits of Fase 0 that are NOT a package rename, applied after seed_core.py.

1. The four settings types the domain reads (units + notification toggles) move out
   of the store and into :core:domain. Without this the domain depends on the data
   layer, which is the one dependency the module split exists to forbid.
2. ServiceLocator stops importing the app: the User-Agent string and the
   "new data landed" callback are handed in by whoever installs it, instead of being
   reached for through BuildConfig and a widget class.

Both are recorded in PLANNING.md Fase 0. Idempotent: rerunning after seed_core.py
reproduces exactly this state.
"""
from __future__ import annotations

import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DATA = ROOT / "core/data/src/main/kotlin/com/callbackdev/chiaro/data"
DOMAIN = ROOT / "core/domain/src/main/kotlin/com/callbackdev/chiaro/domain"

SETTINGS_BLOCK = '''enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

enum class WindSpeedUnit { KMH, MPH }

data class UnitSettings(
    val temperature: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windSpeed: WindSpeedUnit = WindSpeedUnit.KMH
)

/** Read by the alert engine (Fase 9c): each toggle gates one rule in AlertEngine.
 * [userRules] (Fase 11) is the master switch of `alerts.rules`; default true so
 * writing a rule is enough — it only matters once rules exist. */
data class NotificationSettings(
    val severeWeatherAlerts: Boolean = true,
    val dailySummary: Boolean = false,
    val precipitationWarning: Boolean = true,
    val userRules: Boolean = true
)

'''

SETTINGS_FILE = '''package com.callbackdev.chiaro.domain.settings

/**
 * The preferences the ENGINES read, and only those.
 *
 * They lived in tweather's `SettingsStore` next to the DataStore keys, which made
 * the domain import the data layer to evaluate a rule ([com.callbackdev.chiaro.domain
 * .rules.RuleVariables] renders a threshold in the reader's units, so it needs to know
 * them). Chiaro splits the two: the values that decide an engine's answer live here,
 * the plumbing that persists them stays in the store, and `:core:domain` compiles
 * with nothing underneath it.
 *
 * Everything that only the UI cares about — the theme, the update interval, the
 * widget's opacity — deliberately did NOT move: it is not an input to any engine.
 */
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

enum class WindSpeedUnit { KMH, MPH }

/**
 * The units the reader chose. The engines keep every value metric internally and
 * convert at the edge, so switching this never rewrites stored data — a threshold
 * saved as 20 °C is still 20 °C after a switch to Fahrenheit, and only its rendering
 * changes.
 */
data class UnitSettings(
    val temperature: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windSpeed: WindSpeedUnit = WindSpeedUnit.KMH
)

/**
 * Which built-in alerts are on. Each flag gates one rule in
 * [com.callbackdev.chiaro.domain.AlertEngine]; [userRules] is the master switch of
 * the reader's own alerts, default true because it only matters once one exists.
 */
data class NotificationSettings(
    val severeWeatherAlerts: Boolean = true,
    val dailySummary: Boolean = false,
    val precipitationWarning: Boolean = true,
    val userRules: Boolean = true
)
'''

SL_IMPORTS_OLD = '''import com.callbackdev.chiaro.BuildConfig
import com.callbackdev.chiaro.data.local.ReportDiskCache'''
# The library generates its own BuildConfig (buildConfig = true in the module) — the
# only thing read off it is DEBUG, for the HTTP log interceptor.
SL_IMPORTS_NEW = '''import com.callbackdev.chiaro.core.data.BuildConfig
import com.callbackdev.chiaro.data.local.ReportDiskCache'''

SL_WIDGET_IMPORT = "import com.callbackdev.chiaro.widget.TweatherWidgetUpdater\n"

SL_UA_OLD = '''    /**
     * Sent on every API call. Open-Meteo doesn't require it, but rate-limits per IP
     * and reserves the right to block anonymous misbehaving traffic without notice:
     * a named agent with a contact URL turns "block" into "reach out".
     */
    private val userAgent =
        "tweather/${BuildConfig.VERSION_NAME} (+https://github.com/fiorenzobrioni/tweather)"
'''
SL_UA_NEW = '''    /**
     * Sent on every API call. Open-Meteo doesn't require it, but rate-limits per IP
     * and reserves the right to block anonymous misbehaving traffic without notice:
     * a named agent with a contact URL turns "block" into "reach out".
     *
     * Handed in by [install] rather than read off BuildConfig: this module is a
     * library and does not have the app's version, which is exactly the point of it
     * being a library.
     */
    @Volatile
    private var userAgent = "chiaro (+https://github.com/fiorenzobrioni/chiaro)"

    @Volatile
    private var historyListener: suspend () -> Unit = {}

    /**
     * Called once from `Application.onCreate`, before anything resolves a
     * dependency. Two things the data layer cannot know about itself: who it says it
     * is upstream, and who wants to hear that new data landed (the widget, in
     * practice). Calling it later still works and only affects the graph built after
     * it, which is why it is called first.
     */
    fun install(userAgent: String, onHistoryCommitted: suspend () -> Unit = {}) {
        this.userAgent = userAgent
        this.historyListener = onHistoryCommitted
    }
'''

SL_CALLBACK_OLD = '''            // Every fetch that commits new data repaints the home widget, so it
            // needs no polling of its own (no-op when no widget is placed)
            onHistoryCommitted = { TweatherWidgetUpdater.updateAll(appContext) }'''
SL_CALLBACK_NEW = '''            // Every fetch that commits new data notifies whoever installed us — the
            // widget repaint, in the app. The data layer does not know that.
            onHistoryCommitted = { historyListener() }'''


def edit(path: pathlib.Path, old: str, new: str) -> None:
    text = path.read_text()
    if old not in text:
        raise SystemExit(f"anchor not found in {path.name}:\n{old[:120]}...")
    path.write_text(text.replace(old, new, 1))


def main() -> int:
    # 1. the settings types
    (DOMAIN / "settings").mkdir(parents=True, exist_ok=True)
    (DOMAIN / "settings/Settings.kt").write_text(SETTINGS_FILE)
    store = DATA / "SettingsStore.kt"
    edit(store, SETTINGS_BLOCK, "")
    text = store.read_text()
    marker = "import kotlinx.coroutines.flow.map\n"
    if "domain.settings.UnitSettings" not in text:
        edit(
            store,
            marker,
            marker
            + "import com.callbackdev.chiaro.domain.settings.NotificationSettings\n"
            + "import com.callbackdev.chiaro.domain.settings.TemperatureUnit\n"
            + "import com.callbackdev.chiaro.domain.settings.UnitSettings\n"
            + "import com.callbackdev.chiaro.domain.settings.WindSpeedUnit\n",
        )

    # 3. the sample report crossed a module boundary, so its visibility has to:
    # `internal` was enough while it sat in the same module as its readers, and now
    # :core:data's tests and (from Fase 2) the app's previews are outside it.
    sample = DOMAIN / "sample/SampleWeatherReport.kt"
    edit(sample, "internal fun sampleWeatherReport()", "fun sampleWeatherReport()")

    # 2. ServiceLocator
    sl = DATA / "ServiceLocator.kt"
    edit(sl, SL_IMPORTS_OLD, SL_IMPORTS_NEW)
    edit(sl, SL_WIDGET_IMPORT, "")
    edit(sl, SL_UA_OLD, SL_UA_NEW)
    edit(sl, SL_CALLBACK_OLD, SL_CALLBACK_NEW)
    print("edits applied")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
