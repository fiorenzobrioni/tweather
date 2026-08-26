package com.callbackdev.tweather.domain.sky

import com.callbackdev.tweather.domain.model.Coordinates
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scheduler, and with it the module's honesty surface (`VISION_SKY.md` §11):
 * polar day, polar night, a moonless day, an equatorial twilight that really is that
 * short, a DST switch, a city in somebody else's timezone.
 *
 * Each of these is a case where the easy implementation would print something
 * plausible and false — `00:00` for a moonrise that does not happen, a Tokyo sunrise
 * on Rome's clock — so each one gets a test rather than a comment.
 */
class SkySchedulerTest {

    private val milan = Coordinates(45.4642, 9.19)
    private val rome = ZoneId.of("Europe/Rome")
    private val tromso = Coordinates(69.6492, 18.9553)
    private val oslo = ZoneId.of("Europe/Oslo")

    private fun instant(zone: ZoneId, date: String, hour: Int = 0): Instant =
        LocalDate.parse(date).atStartOfDay(zone).plusHours(hour.toLong()).toInstant()

    @Test
    fun `next returns the following occurrences of a daily job in order`() {
        val occurrences = SkyScheduler.next(
            SkyJobCatalog.SunSet, instant(rome, "2026-08-26"), rome, milan, limit = 5
        ).filterIsInstance<SkyOccurrence.At>()
        assertEquals(5, occurrences.size)
        assertEquals(occurrences.map { it.start }.sorted(), occurrences.map { it.start })
        // Consecutive local days, and August sunsets in Milan creep earlier.
        val local = occurrences.map { it.start.atZone(rome) }
        assertEquals((26..30).toList(), local.map { it.dayOfMonth })
        assertTrue("sunsets should move earlier", local.last().toLocalTime() < local.first().toLocalTime())
    }

    @Test
    fun `next skips an occurrence that has already passed`() {
        // Milan sunrise on the 26th is 06:37 local; asking from 12:00 must not offer it.
        val from = instant(rome, "2026-08-26", hour = 12)
        val first = SkyScheduler.next(SkyJobCatalog.SunRise, from, rome, milan)
            .filterIsInstance<SkyOccurrence.At>()
            .first()
        assertTrue("returned a sunrise in the past", first.start.isAfter(from))
        assertEquals(27, first.start.atZone(rome).dayOfMonth)
    }

    @Test
    fun `nextToFire picks the earliest of the subscribed jobs`() {
        val from = instant(rome, "2026-08-26", hour = 12)
        val next = SkyScheduler.nextToFire(
            listOf(SkyJobCatalog.SunRise, SkyJobCatalog.SunSet, SkyJobCatalog.GoldenPm),
            from, rome, milan
        )
        // Golden hour starts before sunset, and both come before tomorrow's sunrise.
        assertEquals(SkyJobCatalog.GoldenPm.id, next?.job?.id)
    }

    /**
     * Polar day: the sun does not set at Tromsø in late June. The scheduler says so
     * and names the cause; it does not return midnight, and it does not return the
     * moment the sun was lowest and call that a sunset.
     */
    @Test
    fun `polar day reports that the sun does not set, and why`() {
        val occurrence = SkyScheduler.resolve(
            SkyJobCatalog.SunSet, LocalDate.of(2026, 6, 21), oslo, tromso
        )
        assertEquals(SkyOccurrence.None(SkyJobCatalog.SunSet, SkyNotScheduled.POLAR_DAY), occurrence)
    }

    @Test
    fun `polar night reports that the sun does not rise, and why`() {
        val occurrence = SkyScheduler.resolve(
            SkyJobCatalog.SunRise, LocalDate.of(2026, 12, 21), oslo, tromso
        )
        assertEquals(SkyOccurrence.None(SkyJobCatalog.SunRise, SkyNotScheduled.POLAR_NIGHT), occurrence)
    }

    /**
     * A white night is not polar anything: at Tromsø in early August the sun rises and
     * sets, but never sinks 18° below the horizon, so there is no astronomical dusk.
     * The `∅` reason has to tell those two situations apart.
     */
    @Test
    fun `a night that never gets dark is not called a polar night`() {
        // Copenhagen at the June solstice: the sun sets at 21:57 local and then never
        // sinks 18° under, so there is no astronomical dusk although the day is in no
        // way polar. Tromso would NOT do here — in mid-July it is still under the
        // midnight sun, which is the other reason entirely.
        val copenhagen = Coordinates(55.6761, 12.5683)
        val zone = ZoneId.of("Europe/Copenhagen")
        val date = LocalDate.of(2026, 6, 21)
        assertTrue(
            "the fixture must be a day the sun does set",
            SkyScheduler.resolve(SkyJobCatalog.SunSet, date, zone, copenhagen) is SkyOccurrence.At
        )
        assertEquals(
            SkyOccurrence.None(SkyJobCatalog.AstronomicalPm, SkyNotScheduled.NO_DARKNESS),
            SkyScheduler.resolve(SkyJobCatalog.AstronomicalPm, date, zone, copenhagen)
        )
    }

    @Test
    fun `a day with no moonrise reports MOON_ABSENT rather than a time`() {
        val moonless = (0..60L)
            .map { LocalDate.of(2026, 1, 1).plusDays(it) }
            .first { SkyScheduler.resolve(SkyJobCatalog.MoonRise, it, rome, milan) is SkyOccurrence.None }
        assertEquals(
            SkyOccurrence.None(SkyJobCatalog.MoonRise, SkyNotScheduled.MOON_ABSENT),
            SkyScheduler.resolve(SkyJobCatalog.MoonRise, moonless, rome, milan)
        )
    }

    /**
     * `∅` is an occurrence, not a gap. A scheduler that quietly rolled on to the next
     * day that worked would turn "the moon does not rise today" into a missing line.
     */
    @Test
    fun `a day the job does not happen is reported, not skipped`() {
        val results = SkyScheduler.next(
            SkyJobCatalog.SunSet, instant(oslo, "2026-06-18"), oslo, tromso, limit = 4
        )
        assertEquals(4, results.size)
        assertTrue("expected polar days", results.all { it is SkyOccurrence.None })
    }

    /**
     * The darkness window runs from tonight's dusk to TOMORROW's dawn. Taking both
     * ends from one calendar day would pair tonight's dusk with this morning's dawn:
     * a night of negative length, rendered as a fact.
     */
    @Test
    fun `the darkness window is one night, not one calendar day`() {
        val occurrence = SkyScheduler.resolve(
            SkyJobCatalog.DarknessWindow, LocalDate.of(2026, 8, 26), rome, milan
        ) as SkyOccurrence.At
        val end = requireNotNull(occurrence.end)
        assertTrue("window runs backwards", end.isAfter(occurrence.start))
        assertEquals(26, occurrence.start.atZone(rome).dayOfMonth)
        assertEquals(27, end.atZone(rome).dayOfMonth)
        assertTrue("a night is not this long", Duration.between(occurrence.start, end).toHours() < 12)
    }

    /**
     * A city in another timezone renders on ITS clock. The engine returns instants, so
     * the test is that the instant lands at a plausible local hour THERE — a Tokyo
     * sunrise that reads 22:00 in Tokyo would be the file lying even though the
     * instant is right.
     */
    @Test
    fun `a remote city resolves on its own clock`() {
        val tokyo = Coordinates(35.6762, 139.6503)
        val zone = ZoneId.of("Asia/Tokyo")
        val sunrise = (SkyScheduler.resolve(
            SkyJobCatalog.SunRise, LocalDate.of(2026, 8, 26), zone, tokyo
        ) as SkyOccurrence.At).start
        assertEquals(5, sunrise.atZone(zone).hour)
        // The same instant on Rome's clock is 22:08 the PREVIOUS evening. That pair —
        // one instant, two readings, only one of which may appear under "Tokyo" — is
        // the whole reason the zone travels down from the active city to the resolver.
        val inRome = sunrise.atZone(rome)
        assertEquals(22, inRome.hour)
        assertEquals(25, inRome.dayOfMonth)
    }

    /**
     * The DST switch, and the reason the scheduler walks `plusDays(1)` instead of
     * adding 24 hours: on 25 Oct 2026 the Italian day is 25 hours long. A 24-hour step
     * would leave an hour of it unsearched and would drift the whole series afterwards.
     */
    @Test
    fun `a DST day is searched whole, and the series stays on local days`() {
        val switch = LocalDate.of(2026, 10, 25)
        val dayLength = Duration.between(
            switch.atStartOfDay(rome), switch.plusDays(1).atStartOfDay(rome)
        )
        assertEquals("the fixture day is not the DST day", 25, dayLength.toHours())

        // From midnight on the 23rd, the 23rd's own sunrise is still ahead.
        val occurrences = SkyScheduler.next(
            SkyJobCatalog.SunRise, instant(rome, "2026-10-23"), rome, milan, limit = 5
        ).filterIsInstance<SkyOccurrence.At>()
        assertEquals((23..27).toList(), occurrences.map { it.start.atZone(rome).dayOfMonth })
        // The clock jumps back an hour, so the LOCAL sunrise time drops by about one.
        val before = occurrences.first { it.start.atZone(rome).dayOfMonth == 24 }
        val after = occurrences.first { it.start.atZone(rome).dayOfMonth == 26 }
        val jump = before.start.atZone(rome).hour - after.start.atZone(rome).hour
        assertEquals("the local hour should fall back by one", 1, jump)
    }

    /**
     * At the equator golden and blue hour are genuinely short. The engine renders the
     * real numbers rather than flooring them to something that looks more like a
     * European evening.
     */
    @Test
    fun `equatorial twilight is short and reported as it is`() {
        val quito = Coordinates(-0.1807, -78.4678)
        val zone = ZoneId.of("America/Guayaquil")
        val blue = SkyScheduler.resolve(
            SkyJobCatalog.BluePm, LocalDate.of(2026, 3, 20), zone, quito
        ) as SkyOccurrence.At
        val minutes = Duration.between(blue.start, requireNotNull(blue.end)).toMinutes()
        assertTrue("blue hour of $minutes minutes at the equator", minutes in 5..12)
    }

    @Test
    fun `moon phase polls forward through consecutive quarters`() {
        val quarters = SkyScheduler.next(
            SkyJobCatalog.MoonPhase, instant(rome, "2026-08-26"), rome, milan, limit = 4
        ).filterIsInstance<SkyOccurrence.At>()
        assertEquals(4, quarters.size)
        assertEquals(quarters.map { it.start }.sorted(), quarters.map { it.start })
        // A quarter every ~7.4 days: consecutive, never the same instant four times.
        quarters.zipWithNext().forEach { (a, b) ->
            val days = Duration.between(a.start, b.start).toHours() / 24.0
            assertTrue("quarters $days days apart", days in 6.0..9.0)
        }
    }

    @Test
    fun `moon today lands at local noon, where its illumination is measured`() {
        val occurrence = SkyScheduler.resolve(
            SkyJobCatalog.MoonToday, LocalDate.of(2026, 8, 26), rome, milan
        ) as SkyOccurrence.At
        val local: ZonedDateTime = occurrence.start.atZone(rome)
        assertEquals(12, local.hour)
        assertEquals(0, local.minute)
    }

    @Test
    fun `every job in the catalog resolves rather than throwing`() {
        // The `else ->` of the resolver is an `error()`: this is the test that keeps a
        // job added to the catalog without a branch from reaching a user as a crash.
        SkyJobCatalog.all.forEach { job ->
            SkyScheduler.resolve(job, LocalDate.of(2026, 8, 26), rome, milan)
        }
    }
}
