package com.callbackdev.tweather.ui.sky

import android.content.res.Resources
import com.callbackdev.tweather.R
import com.callbackdev.tweather.domain.sky.MeteorShowerTable
import com.callbackdev.tweather.domain.sky.SkyJobCatalog

/**
 * A catalog job in WORDS (Fase 16g), for the one surface that speaks instead of
 * coding: the city's `README.md`.
 *
 * `SkyJob.id` is a dotted name, English and never localized, because it is what
 * `sky.crontab` prints and a crontab line is code (`VISION_SKY.md` §4). The README is
 * prose — headings included, since Fase 10 — so a `## Status` line that read
 * `golden_hour.pm alle 19:21` was the file's vocabulary leaking into the document
 * whose whole job is to say the same thing in a language. This is the dictionary
 * between the two, and nothing here ever reaches `sky.crontab`.
 *
 * The map is TOTAL over [SkyJobCatalog.all] and a test says so: a job with no name
 * would fall back to its id, which is exactly the bug this exists to fix, and a
 * fallback nobody notices is a bug that ships.
 */
object SkyJobNames {

    private val byId: Map<String, Int> = mapOf(
        SkyJobCatalog.SunRise.id to R.string.sky_job_sun_rise,
        SkyJobCatalog.SunSet.id to R.string.sky_job_sun_set,
        SkyJobCatalog.SolarNoon.id to R.string.sky_job_solar_noon,
        SkyJobCatalog.CivilAm.id to R.string.sky_job_twilight_civil_am,
        SkyJobCatalog.CivilPm.id to R.string.sky_job_twilight_civil_pm,
        SkyJobCatalog.NauticalAm.id to R.string.sky_job_twilight_nautical_am,
        SkyJobCatalog.NauticalPm.id to R.string.sky_job_twilight_nautical_pm,
        SkyJobCatalog.AstronomicalAm.id to R.string.sky_job_twilight_astronomical_am,
        SkyJobCatalog.AstronomicalPm.id to R.string.sky_job_twilight_astronomical_pm,
        SkyJobCatalog.GoldenAm.id to R.string.sky_job_golden_hour_am,
        SkyJobCatalog.GoldenPm.id to R.string.sky_job_golden_hour_pm,
        SkyJobCatalog.BlueAm.id to R.string.sky_job_blue_hour_am,
        SkyJobCatalog.BluePm.id to R.string.sky_job_blue_hour_pm,
        SkyJobCatalog.DarknessWindow.id to R.string.sky_job_darkness_window,
        SkyJobCatalog.MoonRise.id to R.string.sky_job_moon_rise,
        SkyJobCatalog.MoonSet.id to R.string.sky_job_moon_set,
        SkyJobCatalog.MoonToday.id to R.string.sky_job_moon_today,
        SkyJobCatalog.MoonPhase.id to R.string.sky_job_moon_phase,
        SkyJobCatalog.EquinoxSpring.id to R.string.sky_job_equinox_spring,
        SkyJobCatalog.SolsticeSummer.id to R.string.sky_job_solstice_summer,
        SkyJobCatalog.EquinoxAutumn.id to R.string.sky_job_equinox_autumn,
        SkyJobCatalog.SolsticeWinter.id to R.string.sky_job_solstice_winter,
        // Fase 19
        SkyJobCatalog.MilkyWayCore.id to R.string.sky_job_milky_way_core,
        SkyJobCatalog.ZodiacalPm.id to R.string.sky_job_zodiacal_pm,
        SkyJobCatalog.ZodiacalAm.id to R.string.sky_job_zodiacal_am,
        SkyJobCatalog.MoonNew.id to R.string.sky_job_moon_new,
        SkyJobCatalog.MoonFirstQuarter.id to R.string.sky_job_moon_first_quarter,
        SkyJobCatalog.MoonFull.id to R.string.sky_job_moon_full,
        SkyJobCatalog.MoonLastQuarter.id to R.string.sky_job_moon_last_quarter,
        SkyJobCatalog.MoonClosestFull.id to R.string.sky_job_moon_closest_full,
        SkyJobCatalog.LunarEclipse.id to R.string.sky_job_eclipse_lunar,
        SkyJobCatalog.SolarEclipse.id to R.string.sky_job_eclipse_solar,
        SkyJobCatalog.Perihelion.id to R.string.sky_job_earth_perihelion,
        SkyJobCatalog.Aphelion.id to R.string.sky_job_earth_aphelion,
        SkyJobCatalog.EarliestSunset.id to R.string.sky_job_sun_earliest_set,
        SkyJobCatalog.LatestSunrise.id to R.string.sky_job_sun_latest_rise,
        SkyJobCatalog.WhiteNightsStart.id to R.string.sky_job_night_white_start,
        SkyJobCatalog.WhiteNightsEnd.id to R.string.sky_job_night_white_end
    )

    /**
     * The showers are named apart from their job: a peak is `the peak of the X` in
     * one language and `il picco delle X` in another, and the shower keeps its own
     * name in both. Ten strings instead of ten sentences.
     */
    private val showers: Map<String, Int> = mapOf(
        "quadrantids" to R.string.shower_quadrantids,
        "lyrids" to R.string.shower_lyrids,
        "eta_aquariids" to R.string.shower_eta_aquariids,
        "delta_aquariids" to R.string.shower_delta_aquariids,
        "perseids" to R.string.shower_perseids,
        "draconids" to R.string.shower_draconids,
        "orionids" to R.string.shower_orionids,
        "leonids" to R.string.shower_leonids,
        "geminids" to R.string.shower_geminids,
        "ursids" to R.string.shower_ursids,
        "alpha_capricornids" to R.string.shower_alpha_capricornids,
        "southern_taurids" to R.string.shower_southern_taurids,
        "northern_taurids" to R.string.shower_northern_taurids
    )

    /**
     * The emoji the name travels with. Not localized (it is a picture) and not in the
     * catalog either: `sky.crontab` renders no emoji at all — its lines are code —
     * while the README's `## Status` opens every one of its lines with one, so this
     * belongs to the reading surface, like the names above.
     */
    private fun emoji(jobId: String): String = when {
        jobId == SkyJobCatalog.SunRise.id -> "🌅"
        jobId == SkyJobCatalog.SunSet.id -> "🌇"
        jobId == SkyJobCatalog.SolarNoon.id -> "☀️"
        jobId.startsWith("golden_hour.") -> "🌇"
        jobId.startsWith("blue_hour.") || jobId.startsWith("twilight.") -> "🌆"
        jobId == SkyJobCatalog.DarknessWindow.id -> "🌌"
        jobId == SkyJobCatalog.MilkyWayCore.id -> "🌌"
        jobId.startsWith("zodiacal.") -> "🌌"
        jobId == SkyJobCatalog.SolarEclipse.id -> "🌑"
        jobId == SkyJobCatalog.LunarEclipse.id -> "🌘"
        jobId.startsWith("night.white.") -> "🌉"
        jobId.startsWith("earth.") -> "🌍"
        jobId.startsWith("moon.") -> "🌙"
        jobId.startsWith("meteor.") -> "🌠"
        else -> "🗓️" // the equinoxes and the solstices: a date, not a sight
    }

    /** `The evening golden hour`, `Il picco delle Perseidi`. */
    fun name(resources: Resources, jobId: String): String {
        byId[jobId]?.let { return resources.getString(it) }
        MeteorShowerTable.showerOf(jobId)?.let { shower ->
            showers[shower.id]?.let {
                return resources.getString(R.string.sky_job_meteor_peak, resources.getString(it))
            }
        }
        // Unreachable while SkyJobNamesTest holds; the id is still the least wrong
        // thing to print, since it is at least what the user's own file says.
        return jobId
    }

    /**
     * A bearing in words, to the eighth of the compass — prose, so it localizes. The
     * README says "look east", never "look 92°": a number nobody can act on without
     * turning the phone into a compass first.
     */
    fun bearingRes(degrees: Double): Int {
        val point = (((degrees % 360.0) + 360.0) % 360.0 + 22.5).toInt() / 45 % 8
        return when (point) {
            0 -> R.string.compass_n
            1 -> R.string.compass_ne
            2 -> R.string.compass_e
            3 -> R.string.compass_se
            4 -> R.string.compass_s
            5 -> R.string.compass_sw
            6 -> R.string.compass_w
            else -> R.string.compass_nw
        }
    }

    /** [name] with the job's emoji in front, the way `## Status` writes a line. */
    fun label(resources: Resources, jobId: String): String =
        "${emoji(jobId)} ${name(resources, jobId)}"
}
