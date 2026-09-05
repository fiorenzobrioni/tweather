package com.callbackdev.tweather.ui.sky

import android.content.res.Resources
import androidx.annotation.StringRes
import com.callbackdev.tweather.R
import com.callbackdev.tweather.domain.sky.MeteorShowerTable
import com.callbackdev.tweather.domain.sky.SkyJob
import com.callbackdev.tweather.domain.sky.SkyJobCatalog
import com.callbackdev.tweather.domain.sky.SkyJobKind
import com.callbackdev.tweather.domain.sky.SkyJobShape

/**
 * `man 7 <job>` — what each line of `sky.crontab` actually IS (Fase 23).
 *
 * The catalog names are dotted, English and never localized, because they are what
 * the file prints and a crontab line is code (`VISION_SKY.md` §4). That rule is
 * right and it has a cost: `zodiacal.pm` tells somebody who has not met the zodiacal
 * light precisely nothing, and the app was asking a reader to pick from fifty-one of
 * those. [SkyJobNames] fixed half of it for the README by giving every job a name in
 * words; this is the other half, and it is the half that can explain rather than
 * translate.
 *
 * A **man page** and not a tooltip or an info sheet, because this is an app that
 * looks like a terminal and `man` is what a terminal answers "what is this thing"
 * with. It also brings a shape worth having for free: NAME says what it is called,
 * DESCRIPTION says what it is, WHEN says how it behaves, SEE ALSO says what to read
 * next. The reader gets the same four answers in the same four places on all
 * fifty-one pages.
 *
 * **The section headers localize** and the job ids do not, which is the register
 * rule (Fase 18) applied rather than assumed: translating `DESCRIPTION` breaks no
 * lookup, no filename and no alignment, and the tool itself translates them — an
 * Italian man page says `NOME` and `VEDERE ANCHE`. Translating `zodiacal.pm` would
 * break the tie with the file, so it does not move.
 *
 * **WHEN is generated from [SkyJob] and never written by hand.** Cadence, shape, and
 * whether the clouds get an opinion are all fields the engine already reads; a
 * hand-written sentence about them would be a second copy of the truth, free to
 * drift the first time a job changes kind. Only DESCRIPTION is prose somebody wrote.
 */
object SkyManPages {

    /** The manual section, in the traditional numbering: 7 is "miscellany". */
    const val SECTION = 7

    private val byId: Map<String, Int> = mapOf(
        SkyJobCatalog.SunRise.id to R.string.sky_man_sun_rise,
        SkyJobCatalog.SunSet.id to R.string.sky_man_sun_set,
        SkyJobCatalog.SolarNoon.id to R.string.sky_man_solar_noon,
        SkyJobCatalog.CivilAm.id to R.string.sky_man_twilight_civil_am,
        SkyJobCatalog.CivilPm.id to R.string.sky_man_twilight_civil_pm,
        SkyJobCatalog.NauticalAm.id to R.string.sky_man_twilight_nautical_am,
        SkyJobCatalog.NauticalPm.id to R.string.sky_man_twilight_nautical_pm,
        SkyJobCatalog.AstronomicalAm.id to R.string.sky_man_twilight_astronomical_am,
        SkyJobCatalog.AstronomicalPm.id to R.string.sky_man_twilight_astronomical_pm,
        SkyJobCatalog.GoldenAm.id to R.string.sky_man_golden_hour_am,
        SkyJobCatalog.GoldenPm.id to R.string.sky_man_golden_hour_pm,
        SkyJobCatalog.BlueAm.id to R.string.sky_man_blue_hour_am,
        SkyJobCatalog.BluePm.id to R.string.sky_man_blue_hour_pm,
        SkyJobCatalog.DarknessWindow.id to R.string.sky_man_darkness_window,
        SkyJobCatalog.MilkyWayCore.id to R.string.sky_man_milky_way_core,
        SkyJobCatalog.ZodiacalPm.id to R.string.sky_man_zodiacal_pm,
        SkyJobCatalog.ZodiacalAm.id to R.string.sky_man_zodiacal_am,
        SkyJobCatalog.MoonRise.id to R.string.sky_man_moon_rise,
        SkyJobCatalog.MoonSet.id to R.string.sky_man_moon_set,
        SkyJobCatalog.MoonToday.id to R.string.sky_man_moon_today,
        SkyJobCatalog.MoonPhase.id to R.string.sky_man_moon_phase,
        SkyJobCatalog.MoonNew.id to R.string.sky_man_moon_new,
        SkyJobCatalog.MoonFirstQuarter.id to R.string.sky_man_moon_first_quarter,
        SkyJobCatalog.MoonFull.id to R.string.sky_man_moon_full,
        SkyJobCatalog.MoonLastQuarter.id to R.string.sky_man_moon_last_quarter,
        SkyJobCatalog.MoonClosestFull.id to R.string.sky_man_moon_closest_full,
        SkyJobCatalog.LunarEclipse.id to R.string.sky_man_eclipse_lunar,
        SkyJobCatalog.SolarEclipse.id to R.string.sky_man_eclipse_solar,
        SkyJobCatalog.EquinoxSpring.id to R.string.sky_man_equinox_spring,
        SkyJobCatalog.SolsticeSummer.id to R.string.sky_man_solstice_summer,
        SkyJobCatalog.EquinoxAutumn.id to R.string.sky_man_equinox_autumn,
        SkyJobCatalog.SolsticeWinter.id to R.string.sky_man_solstice_winter,
        SkyJobCatalog.Perihelion.id to R.string.sky_man_earth_perihelion,
        SkyJobCatalog.Aphelion.id to R.string.sky_man_earth_aphelion,
        SkyJobCatalog.EarliestSunset.id to R.string.sky_man_sun_earliest_set,
        SkyJobCatalog.LatestSunrise.id to R.string.sky_man_sun_latest_rise,
        SkyJobCatalog.WhiteNightsStart.id to R.string.sky_man_night_white_start,
        SkyJobCatalog.WhiteNightsEnd.id to R.string.sky_man_night_white_end
    ) + MeteorShowerTable.all.associate { shower ->
        MeteorShowerTable.jobId(shower) to meteorPage(shower.id)
    }

    /**
     * What to read next. Hand-written because the interesting neighbour is rarely the
     * next line in the file: `blue_hour.pm` belongs beside `golden_hour.pm` and
     * `twilight.civil.pm`, which sit in three different blocks of the catalog.
     *
     * Symmetric by construction, and a test walks it both ways: a page that points at
     * a page that does not point back leaves a reader in a dead end. The one
     * deliberate exception is added in [seeAlso] rather than here — every meteor
     * shower points at `darkness.window`, because whether you see any of them is
     * decided there, and nothing points back, because a page whose SEE ALSO listed
     * thirteen showers would be a page nobody reads to the end.
     */
    private val related: Map<String, List<String>> = symmetric(
        SkyJobCatalog.SunRise.id to listOf(
            SkyJobCatalog.SunSet.id, SkyJobCatalog.GoldenAm.id, SkyJobCatalog.CivilAm.id
        ),
        SkyJobCatalog.SunSet.id to listOf(
            SkyJobCatalog.GoldenPm.id, SkyJobCatalog.CivilPm.id, SkyJobCatalog.BluePm.id
        ),
        SkyJobCatalog.SolarNoon.id to listOf(
            SkyJobCatalog.EarliestSunset.id, SkyJobCatalog.LatestSunrise.id
        ),
        SkyJobCatalog.CivilAm.id to listOf(SkyJobCatalog.NauticalAm.id, SkyJobCatalog.BlueAm.id),
        SkyJobCatalog.CivilPm.id to listOf(SkyJobCatalog.NauticalPm.id),
        SkyJobCatalog.NauticalAm.id to listOf(SkyJobCatalog.AstronomicalAm.id),
        SkyJobCatalog.NauticalPm.id to listOf(SkyJobCatalog.AstronomicalPm.id),
        SkyJobCatalog.AstronomicalAm.id to listOf(SkyJobCatalog.DarknessWindow.id),
        SkyJobCatalog.AstronomicalPm.id to listOf(
            SkyJobCatalog.DarknessWindow.id, SkyJobCatalog.WhiteNightsStart.id
        ),
        SkyJobCatalog.GoldenAm.id to listOf(SkyJobCatalog.GoldenPm.id, SkyJobCatalog.BlueAm.id),
        SkyJobCatalog.GoldenPm.id to listOf(SkyJobCatalog.BluePm.id),
        SkyJobCatalog.BlueAm.id to listOf(SkyJobCatalog.BluePm.id),
        SkyJobCatalog.DarknessWindow.id to listOf(
            SkyJobCatalog.MoonSet.id, SkyJobCatalog.MilkyWayCore.id, SkyJobCatalog.MoonNew.id
        ),
        SkyJobCatalog.MilkyWayCore.id to listOf(SkyJobCatalog.ZodiacalPm.id),
        SkyJobCatalog.ZodiacalPm.id to listOf(SkyJobCatalog.ZodiacalAm.id),
        SkyJobCatalog.ZodiacalAm.id to listOf(SkyJobCatalog.EquinoxAutumn.id),
        SkyJobCatalog.MoonRise.id to listOf(SkyJobCatalog.MoonSet.id, SkyJobCatalog.MoonToday.id),
        SkyJobCatalog.MoonToday.id to listOf(SkyJobCatalog.MoonPhase.id),
        SkyJobCatalog.MoonPhase.id to listOf(
            SkyJobCatalog.MoonNew.id, SkyJobCatalog.MoonFirstQuarter.id,
            SkyJobCatalog.MoonFull.id, SkyJobCatalog.MoonLastQuarter.id
        ),
        SkyJobCatalog.MoonNew.id to listOf(SkyJobCatalog.SolarEclipse.id),
        SkyJobCatalog.MoonFull.id to listOf(
            SkyJobCatalog.LunarEclipse.id, SkyJobCatalog.MoonClosestFull.id
        ),
        SkyJobCatalog.MoonFirstQuarter.id to listOf(SkyJobCatalog.MoonLastQuarter.id),
        SkyJobCatalog.LunarEclipse.id to listOf(SkyJobCatalog.SolarEclipse.id),
        SkyJobCatalog.EquinoxSpring.id to listOf(
            SkyJobCatalog.SolsticeSummer.id, SkyJobCatalog.EquinoxAutumn.id
        ),
        SkyJobCatalog.SolsticeSummer.id to listOf(
            SkyJobCatalog.SolsticeWinter.id, SkyJobCatalog.Aphelion.id,
            SkyJobCatalog.WhiteNightsStart.id
        ),
        SkyJobCatalog.EquinoxAutumn.id to listOf(SkyJobCatalog.SolsticeWinter.id),
        SkyJobCatalog.SolsticeWinter.id to listOf(
            SkyJobCatalog.EarliestSunset.id, SkyJobCatalog.LatestSunrise.id,
            SkyJobCatalog.Perihelion.id
        ),
        SkyJobCatalog.Perihelion.id to listOf(SkyJobCatalog.Aphelion.id),
        SkyJobCatalog.EarliestSunset.id to listOf(SkyJobCatalog.LatestSunrise.id),
        SkyJobCatalog.WhiteNightsStart.id to listOf(SkyJobCatalog.WhiteNightsEnd.id),
        SkyJobCatalog.WhiteNightsEnd.id to listOf(SkyJobCatalog.DarknessWindow.id),
        // The showers point at what decides whether you see any of them, and the two
        // that share a parent body point at each other.
        MeteorShowerTable.jobId(MeteorShowerTable.all.first { it.id == "eta_aquariids" }) to
            listOf(MeteorShowerTable.jobId(MeteorShowerTable.all.first { it.id == "orionids" })),
        MeteorShowerTable.jobId(MeteorShowerTable.all.first { it.id == "southern_taurids" }) to
            listOf(
                MeteorShowerTable.jobId(
                    MeteorShowerTable.all.first { it.id == "northern_taurids" }
                )
            )
    )

    /** True for every job in the catalog — [pageOf] never has to guess. */
    fun hasPage(jobId: String): Boolean = jobId in byId

    @StringRes
    fun pageOf(jobId: String): Int = byId.getValue(jobId)

    /**
     * The SEE ALSO list, with the shared darkness window folded in for the showers and
     * the same job never listed twice or pointing at itself.
     */
    fun seeAlso(jobId: String): List<String> {
        val extra = if (MeteorShowerTable.showerOf(jobId) != null) {
            listOf(SkyJobCatalog.DarknessWindow.id)
        } else {
            emptyList()
        }
        return (related[jobId].orEmpty() + extra)
            .distinct()
            .filter { it != jobId && it in byId }
    }

    /**
     * The WHEN section: cadence, shape, and what the clouds have to say — read off
     * [SkyJob] rather than written down a second time.
     */
    fun whenLines(resources: Resources, job: SkyJob): List<String> = buildList {
        add(
            resources.getString(
                when (job.kind) {
                    SkyJobKind.DAILY -> R.string.man_when_daily
                    SkyJobKind.ANNUAL -> R.string.man_when_annual
                    SkyJobKind.POLLING -> R.string.man_when_polling
                }
            )
        )
        add(
            resources.getString(
                when (job.shape) {
                    SkyJobShape.INSTANT -> R.string.man_when_instant
                    SkyJobShape.RANGE -> R.string.man_when_range
                }
            )
        )
        if (!job.observable) {
            add(resources.getString(R.string.man_when_geometry))
        } else if (job.visibilityDependent) {
            add(resources.getString(R.string.man_when_visibility))
        }
        if (job.needsDarkness) {
            add(resources.getString(R.string.man_when_darkness))
        }
    }

    /** `GOLDEN_HOUR.PM(7)` — the header a man page opens with. */
    fun header(jobId: String): String = "${jobId.uppercase()}($SECTION)"

    @StringRes
    private fun meteorPage(showerId: String): Int = when (showerId) {
        "quadrantids" -> R.string.sky_man_meteor_quadrantids_peak
        "lyrids" -> R.string.sky_man_meteor_lyrids_peak
        "eta_aquariids" -> R.string.sky_man_meteor_eta_aquariids_peak
        "delta_aquariids" -> R.string.sky_man_meteor_delta_aquariids_peak
        "alpha_capricornids" -> R.string.sky_man_meteor_alpha_capricornids_peak
        "perseids" -> R.string.sky_man_meteor_perseids_peak
        "draconids" -> R.string.sky_man_meteor_draconids_peak
        "southern_taurids" -> R.string.sky_man_meteor_southern_taurids_peak
        "orionids" -> R.string.sky_man_meteor_orionids_peak
        "northern_taurids" -> R.string.sky_man_meteor_northern_taurids_peak
        "leonids" -> R.string.sky_man_meteor_leonids_peak
        "geminids" -> R.string.sky_man_meteor_geminids_peak
        "ursids" -> R.string.sky_man_meteor_ursids_peak
        // A shower added to the table without a page would otherwise reach the screen
        // as a blank section; the totality test is what actually keeps this unreached.
        else -> error("no manual page for meteor shower '$showerId'")
    }

    /** Both directions of every pair, so no page is a dead end. */
    private fun symmetric(
        vararg pairs: Pair<String, List<String>>
    ): Map<String, List<String>> {
        val out = mutableMapOf<String, MutableList<String>>()
        pairs.forEach { (from, targets) ->
            targets.forEach { to ->
                out.getOrPut(from) { mutableListOf() }.add(to)
                out.getOrPut(to) { mutableListOf() }.add(from)
            }
        }
        return out.mapValues { (_, v) -> v.distinct() }
    }
}
