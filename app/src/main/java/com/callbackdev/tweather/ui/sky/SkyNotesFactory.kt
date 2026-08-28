package com.callbackdev.tweather.ui.sky

import android.content.res.Resources
import com.callbackdev.tweather.R
import com.callbackdev.tweather.domain.sky.SkyVerdictEngine
import com.callbackdev.tweather.ui.weather.WeatherTranslations

/**
 * [SkyNotes] in the reader's language — the bridge between the module's pure
 * document and the only layer that is allowed to know a locale.
 *
 * It is the same shape `WeatherTranslations.translator(resources)` has: the
 * document stays a value that a plain JVM test can build, and the words arrive
 * from outside. What it does NOT touch is the evidence column — the verdict word,
 * the quantity, the drift — which is code and reads the same in every language
 * (`PLANNING.md` Fase 18).
 */
fun skyNotes(resources: Resources): SkyNotes {
    val translateValue = WeatherTranslations.valueTranslator(resources)
    return SkyNotes(
        times = resources.getString(R.string.note_sky_times),
        dstForward = { resources.getString(R.string.note_sky_dst_forward, it) },
        dstBack = { resources.getString(R.string.note_sky_dst_back, it) },
        footer = listOf(
            resources.getString(
                R.string.note_sky_thresholds,
                SkyVerdictEngine.CLOUD_PASS_PCT,
                SkyVerdictEngine.CLOUD_FAIL_PCT,
                SkyVerdictEngine.PRECIP_FAIL_PCT
            ),
            resources.getString(R.string.note_sky_moon, SkyVerdictEngine.MOON_WASH_PCT),
            resources.getString(R.string.note_sky_light),
            resources.getString(R.string.note_sky_opinion)
        ),
        polarDay = resources.getString(R.string.note_sky_polar_day),
        polarNight = resources.getString(R.string.note_sky_polar_night),
        moonAbsent = resources.getString(R.string.note_sky_moon_absent),
        noDarkness = resources.getString(R.string.note_sky_no_darkness),
        beyondHorizon = resources.getString(R.string.note_sky_beyond_horizon),
        noFetchYet = resources.getString(R.string.note_sky_no_data),
        staleData = resources.getString(R.string.note_sky_stale_data),
        noCoverage = resources.getString(R.string.note_sky_no_coverage),
        moonlessFrom = { resources.getString(R.string.note_sky_moonless_from, it) },
        moonless = resources.getString(R.string.note_sky_moonless),
        moonAllNight = resources.getString(R.string.note_sky_moon_all_night),
        // The same translator the main screen and the widget use, so a phase reads
        // the same word wherever the app prints it.
        moonPhase = translateValue
    )
}
