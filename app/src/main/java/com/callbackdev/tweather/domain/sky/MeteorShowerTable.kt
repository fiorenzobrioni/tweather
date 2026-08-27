package com.callbackdev.tweather.domain.sky

/**
 * The major annual meteor showers, each pinned to the SOLAR LONGITUDE of its peak
 * rather than to a calendar date (Fase 16b, `VISION_SKY.md` §6).
 *
 * Solar longitude is the earth's position in its own orbit, so it names the same
 * point of the debris stream every year: the table cannot go stale, cannot need an
 * update shipped with the app, and gives the right instant for a year nobody thought
 * about when this was written. A hard-coded "August 12" would be wrong by a day in
 * some years and wrong forever once the app stops being updated.
 *
 * Two honesty rules follow the table into the renderer:
 *
 * 1. **A peak is a night, not an instant.** The resolved instant is the centre of the
 *    maximum, and `sky.crontab` renders the local night it falls in — printing
 *    `03:00` alone would promise a precision nobody has.
 * 2. **Moonlight is part of the verdict** (16d). A Geminid peak under a full moon is
 *    a failed build under a perfectly clear sky, and the verdict has to say which of
 *    the two conditions failed.
 *
 * ZHR is deliberately absent. A predicted rate is a modelled number the app cannot
 * verify, and this series does not print numbers it cannot stand behind.
 */
object MeteorShowerTable {

    /**
     * [solarLongitudeDeg] is the apparent longitude of the sun at maximum (J2000
     * values from the IMO working list, rounded to the tenth of a degree — the
     * stream's own width is a degree or more, so the tenth is already generous).
     */
    data class MeteorShower(val id: String, val solarLongitudeDeg: Double)

    val all: List<MeteorShower> = listOf(
        MeteorShower("quadrantids", 283.2),
        MeteorShower("lyrids", 32.3),
        MeteorShower("eta_aquariids", 45.5),
        MeteorShower("delta_aquariids", 125.0),
        MeteorShower("perseids", 140.0),
        MeteorShower("draconids", 195.4),
        MeteorShower("orionids", 208.0),
        MeteorShower("leonids", 235.3),
        MeteorShower("geminids", 262.2),
        MeteorShower("ursids", 270.7)
    )

    fun byId(id: String): MeteorShower? = all.firstOrNull { it.id == id }

    /** The catalog job id of a shower, e.g. `meteor.perseids.peak`. */
    fun jobId(shower: MeteorShower): String = "meteor.${shower.id}.peak"

    /** The shower behind a catalog job id, or null when the id is not a shower's. */
    fun showerOf(jobId: String): MeteorShower? =
        jobId.removeSurrounding("meteor.", ".peak")
            .takeIf { it != jobId }
            ?.let(::byId)
}
