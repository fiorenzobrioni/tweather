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

    /**
     * The dark-sky pair the darkness window was the first of (Fase 19): what there is
     * to look AT once it is dark, rather than when the dark starts.
     *
     * The core of the Milky Way is above the horizon for part of the night from about
     * March to October at mid-northern latitudes and never at all far enough north;
     * the zodiacal light is dust along the ecliptic, so it stands up out of the
     * horizon only in the weeks when the ecliptic itself does. Both are intersections
     * — dark sky AND something in it — which is the same reason [DarknessWindow]
     * earns its place, one step further out.
     */
    val MilkyWayCore = SkyJob(
        "milky_way.core", SkyJobKind.DAILY, SkyJobShape.RANGE,
        visibilityDependent = true, needsDarkness = true
    )
    val ZodiacalAm = darkSky("zodiacal.am")
    val ZodiacalPm = darkSky("zodiacal.pm")

    // Moon --------------------------------------------------------------------
    val MoonRise = SkyJob("moon.rise", SkyJobKind.DAILY, SkyJobShape.INSTANT)
    val MoonSet = SkyJob("moon.set", SkyJobKind.DAILY, SkyJobShape.INSTANT)
    // The phase is a statement about the day and the quarter is an instant of
    // geometry: neither is a thing the clouds can spoil.
    val MoonToday = SkyJob("moon.today", SkyJobKind.DAILY, SkyJobShape.INSTANT, observable = false)
    val MoonPhase = SkyJob(
        "moon.phase", SkyJobKind.POLLING, SkyJobShape.INSTANT, observable = false
    )

    /**
     * The four quarters as four lines (Fase 19). [MoonPhase] answers "which is next",
     * which is the right answer to a question nobody asks: what a reader wants on the
     * calendar is the FULL moon, or the new moon they need for a dark sky. Each is the
     * same instant of geometry [MoonPhase] resolves, asked for by name.
     */
    val MoonNew = quarter("moon.new")
    val MoonFirstQuarter = quarter("moon.first_quarter")
    val MoonFull = quarter("moon.full")
    val MoonLastQuarter = quarter("moon.last_quarter")

    /**
     * The full moon of the year that comes nearest to the earth — about 14 % wider and
     * 30 % brighter than the farthest one, and the only honest reading of a word the
     * internet hands out three or four times a year.
     */
    val MoonClosestFull = SkyJob(
        "moon.closest_full", SkyJobKind.ANNUAL, SkyJobShape.INSTANT, observable = false
    )

    // Eclipses ----------------------------------------------------------------

    /**
     * The two eclipses, each resolved **for this place**: a lunar one only when the
     * moon is up here, a solar one only when the moon takes a bite out of the sun as
     * seen from these coordinates — and clipped to the part of it that happens in
     * daylight, because the geometry does not stop at the horizon and the reader does.
     *
     * Deliberately NOT [SkyJob.visibilityDependent]: clouds decide whether a meteor
     * shower is worth setting an alarm for, and do not decide that about an eclipse.
     * People travel for these.
     */
    val LunarEclipse = SkyJob("eclipse.lunar", SkyJobKind.POLLING, SkyJobShape.RANGE)
    val SolarEclipse = SkyJob("eclipse.solar", SkyJobKind.POLLING, SkyJobShape.RANGE)

    // Seasons -----------------------------------------------------------------
    val EquinoxSpring = season("equinox.spring")
    val SolsticeSummer = season("solstice.summer")
    val EquinoxAutumn = season("equinox.autumn")
    val SolsticeWinter = season("solstice.winter")

    /**
     * The two ends of the earth's orbit. Unobservable by definition — nothing looks
     * different — and worth a line for what they correct: the earth is at its closest
     * to the sun in the first week of JANUARY, which is the northern winter.
     */
    val Perihelion = season("earth.perihelion")
    val Aphelion = season("earth.aphelion")

    /**
     * The earliest sunset and the latest sunrise of the winter, which are **not** the
     * solstice: the equation of time pulls them a fortnight either side of it at
     * Milan's latitude and seven weeks at the equator. Both are sunsets and sunrises
     * like any other, so the clouds get their say on them.
     */
    val EarliestSunset = SkyJob("sun.earliest_set", SkyJobKind.ANNUAL, SkyJobShape.INSTANT)
    val LatestSunrise = SkyJob("sun.latest_rise", SkyJobKind.ANNUAL, SkyJobShape.INSTANT)

    /**
     * The two evenings that open and close the white nights: above roughly 48.5° the
     * summer sun stops going 18° under the horizon and the astronomical night pauses
     * for weeks. Below that latitude both resolve to `∅` with the reason, which is a
     * fact about where you are and not a gap in the list.
     */
    val WhiteNightsStart = season("night.white.start")
    val WhiteNightsEnd = season("night.white.end")

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
        add(DarknessWindow); add(MilkyWayCore); add(ZodiacalPm); add(ZodiacalAm)
        add(MoonRise); add(MoonSet); add(MoonToday); add(MoonPhase)
        add(MoonNew); add(MoonFirstQuarter); add(MoonFull); add(MoonLastQuarter)
        add(MoonClosestFull)
        add(LunarEclipse); add(SolarEclipse)
        add(EquinoxSpring); add(SolsticeSummer); add(EquinoxAutumn); add(SolsticeWinter)
        add(Perihelion); add(Aphelion)
        add(EarliestSunset); add(LatestSunrise)
        add(WhiteNightsStart); add(WhiteNightsEnd)
        addAll(meteorShowers)
    }

    /**
     * What a fresh install subscribes to: four lines. A user who opens the tab and
     * finds all fifty will close it — the catalog is what the file CAN hold, not what
     * it should greet anyone with.
     */
    val defaults: List<SkyJob> = listOf(SunRise, SunSet, GoldenPm, MoonToday)

    fun byId(id: String): SkyJob? = all.firstOrNull { it.id == id }

    /** Position in the file, used to keep the rendered order stable. */
    fun orderOf(job: SkyJob): Int = all.indexOfFirst { it.id == job.id }

    private fun twilight(id: String) =
        SkyJob(id, SkyJobKind.DAILY, SkyJobShape.INSTANT, visibilityDependent = false)

    private fun visibleRange(id: String) =
        SkyJob(id, SkyJobKind.DAILY, SkyJobShape.RANGE, visibilityDependent = true)

    /** A window that wants a clear sky AND a dark one. */
    private fun darkSky(id: String) = SkyJob(
        id, SkyJobKind.DAILY, SkyJobShape.RANGE,
        visibilityDependent = true, needsDarkness = true
    )

    /** An instant of geometry the clouds have no opinion about. */
    private fun quarter(id: String) =
        SkyJob(id, SkyJobKind.POLLING, SkyJobShape.INSTANT, observable = false)

    // A season is a date on the calendar, not an evening out.
    private fun season(id: String) =
        SkyJob(id, SkyJobKind.ANNUAL, SkyJobShape.INSTANT, observable = false)
}
