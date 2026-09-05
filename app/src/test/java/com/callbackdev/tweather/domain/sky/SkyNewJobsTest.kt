package com.callbackdev.tweather.domain.sky

import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.HourlyForecast
import com.callbackdev.tweather.domain.model.WeatherCondition
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The jobs Fase 19 added, at the seam where the catalog meets the scheduler: the four
 * named moon quarters, the two dark-sky windows, the two eclipses and the annual
 * facts. The maths behind them is measured in `YearEventsTest` and
 * `EclipseEngineTest`; what is asserted here is that each job RESOLVES to the thing
 * its name promises, and that the ones a place cannot have say why.
 */
class SkyNewJobsTest {

    private val milan = Coordinates(45.4642, 9.1900)
    private val rome = ZoneId.of("Europe/Rome")
    private val date = LocalDate.of(2026, 6, 15)

    /** The four quarters, against the USNO phase table for January 2026. */
    @Test
    fun `each named quarter resolves to its own instant`() {
        val january = LocalDate.of(2026, 1, 1)
        val published = mapOf(
            SkyJobCatalog.MoonFull to "2026-01-03T10:03:00Z",
            SkyJobCatalog.MoonLastQuarter to "2026-01-10T15:48:00Z",
            SkyJobCatalog.MoonNew to "2026-01-18T19:52:00Z",
            SkyJobCatalog.MoonFirstQuarter to "2026-01-26T04:47:00Z"
        )
        published.forEach { (job, instant) ->
            val occurrence = SkyScheduler.resolve(job, january, ZoneId.of("UTC"), milan)
            assertTrue("${job.id} did not resolve", occurrence is SkyOccurrence.At)
            val at = (occurrence as SkyOccurrence.At).start
            assertTrue(
                "${job.id}: expected $instant, got $at",
                abs(Duration.between(Instant.parse(instant), at).toMinutes()) <= 5
            )
        }
    }

    /**
     * The point of splitting them out: `moon.phase` answers "whichever comes next",
     * and on 1 January 2026 that is the full moon two days later — while the new moon
     * a fortnight further on is what a dark-sky reader is waiting for.
     */
    @Test
    fun `the generic next quarter and a named one part company`() {
        val january = LocalDate.of(2026, 1, 1)
        val generic = SkyScheduler.resolve(SkyJobCatalog.MoonPhase, january, rome, milan)
        val newMoon = SkyScheduler.resolve(SkyJobCatalog.MoonNew, january, rome, milan)
        assertTrue(
            "the two should not be the same instant",
            (generic as SkyOccurrence.At).start != (newMoon as SkyOccurrence.At).start
        )
    }

    /**
     * The Milky Way's core is a window INSIDE the dark one: never wider, never
     * outside it, and absent in the months it never climbs high enough.
     */
    @Test
    fun `the galactic core window sits inside the darkness window`() {
        val darkness = SkyScheduler.resolve(SkyJobCatalog.DarknessWindow, date, rome, milan)
            as SkyOccurrence.At
        val core = SkyScheduler.resolve(SkyJobCatalog.MilkyWayCore, date, rome, milan)
        assertTrue("June should have a core window, got $core", core is SkyOccurrence.At)
        core as SkyOccurrence.At
        assertTrue(core.start >= darkness.start)
        assertTrue(core.end!! <= darkness.end!!)
        assertTrue(
            "the core is above the threshold in the middle of its own window",
            AstronomyEngine.galacticCoreAltitude(
                core.start.plus(Duration.between(core.start, core.end).dividedBy(2)), milan
            ) >= 9.0
        )

        val january = SkyScheduler.resolve(
            SkyJobCatalog.MilkyWayCore, LocalDate.of(2026, 1, 15), rome, milan
        )
        assertEquals(
            SkyNotScheduled.CORE_TOO_LOW,
            (january as SkyOccurrence.None).reason
        )
    }

    /**
     * The zodiacal light has a season and says so out of season: at Milan the evening
     * cone stands up in February and lies flat in June.
     */
    @Test
    fun `the zodiacal light is a season, and the off season carries its reason`() {
        val february = SkyScheduler.resolve(
            SkyJobCatalog.ZodiacalPm, LocalDate.of(2026, 2, 15), rome, milan
        )
        assertTrue("February evening should qualify, got $february", february is SkyOccurrence.At)
        val june = SkyScheduler.resolve(SkyJobCatalog.ZodiacalPm, date, rome, milan)
        assertEquals(
            SkyNotScheduled.ECLIPTIC_TOO_FLAT,
            (june as SkyOccurrence.None).reason
        )
        val october = SkyScheduler.resolve(
            SkyJobCatalog.ZodiacalAm, LocalDate.of(2026, 10, 15), rome, milan
        )
        assertTrue("October morning should qualify, got $october", october is SkyOccurrence.At)
    }

    @Test
    fun `white nights resolve to their reason at a latitude that never has them`() {
        val occurrence = SkyScheduler.resolve(SkyJobCatalog.WhiteNightsStart, date, rome, milan)
        assertEquals(
            SkyNotScheduled.DARKNESS_ALL_YEAR,
            (occurrence as SkyOccurrence.None).reason
        )
    }

    /**
     * An annual job's answer does not have to fall inside its own year: the latest
     * sunrise of the winter of 2026 is a morning in January 2027, and asking on 2
     * January must not skip the one two days away in favour of next winter's.
     */
    @Test
    fun `an annual job whose answer lands in January is not skipped in January`() {
        val latest = YearEvents.latestSunrise(2026, rome, milan)!!
        val asking = latest.minus(Duration.ofDays(2))
        val next = SkyScheduler.next(SkyJobCatalog.LatestSunrise, asking, rome, milan, limit = 1)
            .filterIsInstance<SkyOccurrence.At>()
            .first()
        assertEquals(latest, next.start)
    }

    @Test
    fun `both eclipses resolve to a window this place can watch`() {
        val lunar = SkyScheduler.resolve(SkyJobCatalog.LunarEclipse, date, rome, milan)
        assertTrue("no lunar eclipse ahead: $lunar", lunar is SkyOccurrence.At)
        lunar as SkyOccurrence.At
        assertTrue(AstronomyEngine.moonAltitude(lunar.start, milan) > -1)

        val solar = SkyScheduler.resolve(SkyJobCatalog.SolarEclipse, date, rome, milan)
        assertTrue("no solar eclipse ahead: $solar", solar is SkyOccurrence.At)
        solar as SkyOccurrence.At
        assertTrue(AstronomyEngine.sunAltitude(solar.start, milan) > -1)
    }

    /**
     * The rainbow window is the module's one weather-fed event, so it is tested the
     * way weather is: with hours. Midsummer at Milan puts the noon sun at 68°, well
     * over the bow's own 42° radius, so the geometric half of the rule is visible in
     * the same day as the weather half.
     */
    @Test
    fun `a rainbow window needs low sun, rain and a gap in the cloud`() {
        val day = LocalDate.of(2026, 6, 21)
        fun hour(at: Int, precip: Int, cloud: Int) = HourlyForecast(
            time = LocalDateTime.of(day, java.time.LocalTime.of(at, 0)),
            tempC = 22.0,
            condition = WeatherCondition(80, "Rovesci", "\uD83C\uDF26\uFE0F"),
            precipChancePct = precip,
            cloudCoverPct = cloud
        )
        // Noon: rain, broken cloud, and a sun far too high for a bow to clear the
        // horizon. Nothing to promise.
        assertTrue(RainbowWindow.windows(listOf(hour(12, 80, 60)), rome, milan).isEmpty())

        val windows = RainbowWindow.windows(
            listOf(hour(12, 80, 60), hour(19, 70, 60), hour(20, 60, 70), hour(23, 90, 50)),
            rome,
            milan
        )
        assertEquals("expected one merged window, got $windows", 1, windows.size)
        val window = windows.first()
        assertEquals(19, window.start.atZone(rome).hour)
        assertEquals(21, window.end.atZone(rome).hour)
        assertEquals(70, window.precipChancePct)
        // The bow stands opposite the sun, which on a June evening is in the north-west:
        // so the window points somewhere east of south.
        assertTrue(
            "look towards ${window.lookTowardsDeg}",
            window.lookTowardsDeg in 90.0..180.0
        )

        val dry = RainbowWindow.windows(listOf(hour(19, 10, 60)), rome, milan)
        assertTrue("no rain, no bow: $dry", dry.isEmpty())
        val overcast = RainbowWindow.windows(listOf(hour(19, 90, 100)), rome, milan)
        assertTrue("shut sky, no bow: $overcast", overcast.isEmpty())
    }

    /**
     * A shower's peak is rendered as the night it falls in, and a night has length.
     *
     * Four of the thirteen peak between dawn and noon — the delta Aquariids, the alpha
     * Capricornids and both Taurids — and those used to resolve to a window that
     * opened and closed on the same instant, because the start was clipped to a peak
     * already past and the end was that morning's dawn. The rule for a peak INSIDE
     * the night is unchanged and deliberate: the window opens at the peak, so the
     * Geminids of 2026, peaking sixteen minutes before dawn, are sixteen minutes of
     * dark and the file says so.
     */
    @Test
    fun `no shower resolves to a window with no length in it`() {
        listOf(2026, 2027, 2031).forEach { year ->
            SkyJobCatalog.meteorShowers.forEach { job ->
                val occurrence = SkyScheduler.resolve(job, LocalDate.of(year, 1, 1), rome, milan)
                if (occurrence is SkyOccurrence.At) {
                    val minutes = Duration.between(occurrence.start, occurrence.end).toMinutes()
                    assertTrue(
                        "${job.id} in $year is ${minutes}m: ${occurrence.start}..${occurrence.end}",
                        minutes > 0
                    )
                }
            }
        }
    }

    /** A peak the sun is up for gets the whole night, not the instant of dawn. */
    @Test
    fun `a shower peaking after dawn is watched over the night, whole`() {
        val taurids = SkyJobCatalog.meteorShowers
            .first { it.id == "meteor.southern_taurids.peak" }
        val occurrence = SkyScheduler
            .resolve(taurids, LocalDate.of(2026, 1, 1), rome, milan) as SkyOccurrence.At
        val night = occurrence.start.atZone(rome).toLocalDate()
        val dusk = SkyAlmanac.solarDay(night, rome, milan).astronomicalDusk
        assertEquals(dusk, occurrence.start)
        assertTrue(
            "a night, not a moment: ${occurrence.start}..${occurrence.end}",
            Duration.between(occurrence.start, occurrence.end).toHours() >= 4
        )
    }

    @Test
    fun `every new job renders a cron expression and a name the catalog knows`() {
        listOf(
            SkyJobCatalog.MoonNew, SkyJobCatalog.MoonFull, SkyJobCatalog.MoonClosestFull,
            SkyJobCatalog.LunarEclipse, SkyJobCatalog.SolarEclipse,
            SkyJobCatalog.MilkyWayCore, SkyJobCatalog.ZodiacalAm, SkyJobCatalog.ZodiacalPm,
            SkyJobCatalog.Perihelion, SkyJobCatalog.Aphelion,
            SkyJobCatalog.EarliestSunset, SkyJobCatalog.LatestSunrise,
            SkyJobCatalog.WhiteNightsStart, SkyJobCatalog.WhiteNightsEnd
        ).forEach { job ->
            assertNotNull(SkyJobCatalog.byId(job.id))
            assertTrue(job.expression.isNotBlank())
        }
    }
}
