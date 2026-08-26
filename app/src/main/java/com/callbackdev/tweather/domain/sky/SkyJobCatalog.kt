package com.callbackdev.tweather.domain.sky

/**
 * The fixed set of jobs `sky.crontab` can carry (Fase 16b) — versioned in code, with
 * no runtime registration, exactly as the 22 variables of `alerts.rules` are. A user
 * picks lines from this list; a user cannot write one. That is the same discipline
 * that makes a syntax error unwritable in the rules file: the states the app has to
 * handle are the states the app defines.
 *
 * The order here is the order the FILE renders in ([all]) — a crontab is a file, not
 * a queue, so it does not re-sort itself by whatever fires next. The next job to fire
 * is called out in the header instead.
 */
object SkyJobCatalog {

    // Sun ---------------------------------------------------------------------
    val SunRise = SkyJob("sun.rise", SkyJobKind.DAILY, SkyJobShape.INSTANT)
    val SunSet = SkyJob("sun.set", SkyJobKind.DAILY, SkyJobShape.INSTANT)
    // A moment of geometry, not a sight: the sun is at its highest whether or not
    // anybody could tell by looking.
    val SolarNoon = SkyJob("solar.noon", SkyJobKind.DAILY, SkyJobShape.INSTANT, observable = false)

    // Twilight ----------------------------------------------------------------
    val CivilAm = twilight("twilight.civil.am")
    val CivilPm = twilight("twilight.civil.pm")
    val NauticalAm = twilight("twilight.nautical.am")
    val NauticalPm = twilight("twilight.nautical.pm")
    val AstronomicalAm = twilight("twilight.astronomical.am")
    val AstronomicalPm = twilight("twilight.astronomical.pm")

    // The photographer's hours ------------------------------------------------
    val GoldenAm = visibleRange("golden_hour.am")
    val GoldenPm = visibleRange("golden_hour.pm")
    val BlueAm = visibleRange("blue_hour.am")
    val BluePm = visibleRange("blue_hour.pm")

    /**
     * Astronomical dusk → dawn, with the moonless part of it named in the comment.
     *
     * The one derived line in the catalog, and the reason the module is worth
     * building: every other job is an hour at which the sun or the moon crosses an
     * angle, which any ephemeris site will tell you. This is the INTERSECTION — when
     * it is genuinely dark AND the moon is down — which is the thing an amateur
     * astronomer actually plans around and which no weather app prints.
     */
    val DarknessWindow = SkyJob(
        "darkness.window", SkyJobKind.DAILY, SkyJobShape.RANGE,
        visibilityDependent = true, needsDarkness = true
    )

    // Moon --------------------------------------------------------------------
    val MoonRise = SkyJob("moon.rise", SkyJobKind.DAILY, SkyJobShape.INSTANT)
    val MoonSet = SkyJob("moon.set", SkyJobKind.DAILY, SkyJobShape.INSTANT)
    // The phase is a statement about the day and the quarter is an instant of
    // geometry: neither is a thing the clouds can spoil.
    val MoonToday = SkyJob("moon.today", SkyJobKind.DAILY, SkyJobShape.INSTANT, observable = false)
    val MoonPhase = SkyJob(
        "moon.phase", SkyJobKind.POLLING, SkyJobShape.INSTANT, observable = false
    )

    // Seasons -----------------------------------------------------------------
    val EquinoxSpring = season("equinox.spring")
    val SolsticeSummer = season("solstice.summer")
    val EquinoxAutumn = season("equinox.autumn")
    val SolsticeWinter = season("solstice.winter")

    /**
     * One `meteor.<shower>.peak` per row of [MeteorShowerTable] — annual jobs whose
     * instant comes from a solar longitude, so the list never expires.
     */
    val meteorShowers: List<SkyJob> = MeteorShowerTable.all.map { shower ->
        SkyJob(
            MeteorShowerTable.jobId(shower), SkyJobKind.ANNUAL, SkyJobShape.RANGE,
            visibilityDependent = true, needsDarkness = true
        )
    }

    val all: List<SkyJob> = buildList {
        add(SunRise); add(SunSet); add(SolarNoon)
        add(CivilAm); add(CivilPm)
        add(NauticalAm); add(NauticalPm)
        add(AstronomicalAm); add(AstronomicalPm)
        add(GoldenAm); add(GoldenPm)
        add(BlueAm); add(BluePm)
        add(DarknessWindow)
        add(MoonRise); add(MoonSet); add(MoonToday); add(MoonPhase)
        add(EquinoxSpring); add(SolsticeSummer); add(EquinoxAutumn); add(SolsticeWinter)
        addAll(meteorShowers)
    }

    /**
     * What a fresh install subscribes to: four lines. A user who opens the tab and
     * finds all thirty-two will close it — the catalog is what the file CAN hold, not
     * what it should greet anyone with.
     */
    val defaults: List<SkyJob> = listOf(SunRise, SunSet, GoldenPm, MoonToday)

    fun byId(id: String): SkyJob? = all.firstOrNull { it.id == id }

    /** Position in the file, used to keep the rendered order stable. */
    fun orderOf(job: SkyJob): Int = all.indexOfFirst { it.id == job.id }

    private fun twilight(id: String) =
        SkyJob(id, SkyJobKind.DAILY, SkyJobShape.INSTANT, visibilityDependent = false)

    private fun visibleRange(id: String) =
        SkyJob(id, SkyJobKind.DAILY, SkyJobShape.RANGE, visibilityDependent = true)

    // A season is a date on the calendar, not an evening out.
    private fun season(id: String) =
        SkyJob(id, SkyJobKind.ANNUAL, SkyJobShape.INSTANT, observable = false)
}
