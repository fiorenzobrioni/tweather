package com.callbackdev.tweather.domain.sky

import com.callbackdev.tweather.domain.model.Coordinates
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * [AstronomyEngine] with a memory (Fase 16c).
 *
 * The engine is a stateless calculator and stays one; this is the thin layer that
 * stops the same day being recomputed thirty-two times. `sky.crontab` resolves every
 * subscribed job against the same local date, and a measured `solarDay` costs ~1.7 ms
 * on a development JVM (call it 5–8 ms on a phone): thirty-two of them is a fifth of
 * a second, once per recomposition of a scrolling file. With the memo it is one.
 *
 * **Referentially transparent**: same key, same answer, for the same reason the
 * engine gives the same answer twice. Nothing here decides anything — it only
 * remembers, so a test can bypass it and talk to the engine directly.
 *
 * Bounded and least-recently-used. The bound matters because the key includes a date
 * and the file can be asked about a year of them: a map that only grew would be a
 * slow leak that never announced itself.
 */
object SkyAlmanac {

    fun solarDay(date: LocalDate, zone: ZoneId, coords: Coordinates): SolarDay =
        solar.get(Key(date, zone, coords)) { AstronomyEngine.solarDay(date, zone, coords) }

    fun lunarDay(date: LocalDate, zone: ZoneId, coords: Coordinates): LunarDay =
        lunar.get(Key(date, zone, coords)) { AstronomyEngine.lunarDay(date, zone, coords) }

    /**
     * The next lunar eclipse visible from here, as asked from the local day [date] —
     * memoized because the search walks full moons over three years and the screen,
     * the widget and the reminder planner all ask the same question on the same day.
     */
    fun nextLunarEclipse(
        date: LocalDate,
        zone: ZoneId,
        coords: Coordinates
    ): EclipseEngine.LocalLunarEclipse? = lunarEclipse.get(Key(date, zone, coords)) {
        Optional(EclipseEngine.nextLunarFrom(date.atStartOfDay(zone).toInstant(), coords))
    }.value

    /** The next solar eclipse with a bite visible from here. Same reason, longer walk. */
    fun nextSolarEclipse(
        date: LocalDate,
        zone: ZoneId,
        coords: Coordinates
    ): SolarEclipse? = solarEclipse.get(Key(date, zone, coords)) {
        Optional(EclipseEngine.nextSolar(date.atStartOfDay(zone).toInstant(), coords))
    }.value

    /** Drops everything; the app calls it on nothing, tests call it between cases. */
    fun clear() {
        solar.clear()
        lunar.clear()
        lunarEclipse.clear()
        solarEclipse.clear()
    }

    /**
     * A box for a nullable answer. "No eclipse in the next three years" is an answer
     * worth remembering, and a memo keyed on presence would recompute it every time.
     */
    private class Optional<V>(val value: V?)

    /**
     * Coordinates are rounded to ~10 m before they become part of the key. Two GPS
     * fixes a metre apart produce sunrises that differ by microseconds and would
     * otherwise be two entries; five decimal places is far finer than any difference
     * this app renders and coarse enough that standing still is one key.
     */
    private data class Key(val date: LocalDate, val zone: ZoneId, val lat: Int, val lon: Int) {
        constructor(date: LocalDate, zone: ZoneId, coords: Coordinates) : this(
            date, zone, (coords.lat * 100_000).roundToInt(), (coords.lon * 100_000).roundToInt()
        )
    }

    private class Memo<V>(private val maxEntries: Int) {
        private val entries = object : LinkedHashMap<Key, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, V>?): Boolean =
                size > maxEntries
        }

        fun get(key: Key, compute: () -> V): V = synchronized(entries) { entries[key] }
            // Computed OUTSIDE the lock: the engine takes milliseconds, and holding a
            // monitor across it would serialize every caller behind the slowest one.
            // The cost of a race is that two threads compute the same day twice and
            // one write wins, which is the same answer either way.
            ?: compute().also { synchronized(entries) { entries[key] = it } }

        fun clear() = synchronized(entries) { entries.clear() }
    }

    /**
     * Enough for a year of one city, or a couple of months of several — the file
     * shows one date at a time, so this is generous by design.
     */
    private const val MAX_ENTRIES = 400

    private const val ECLIPSE_ENTRIES = 8

    private val solar = Memo<SolarDay>(MAX_ENTRIES)
    private val lunar = Memo<LunarDay>(MAX_ENTRIES)

    /**
     * Smaller by design: an eclipse search is dear but there is only ever one date in
     * flight, and unlike a solar day nobody scrolls a year of them.
     */
    private val lunarEclipse = Memo<Optional<EclipseEngine.LocalLunarEclipse>>(ECLIPSE_ENTRIES)
    private val solarEclipse = Memo<Optional<SolarEclipse>>(ECLIPSE_ENTRIES)
}
