package com.callbackdev.chiaro.domain.sky

import com.callbackdev.chiaro.domain.model.Coordinates
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
 * The contract test of `VISION_SKY.md` §14: the engine's sunrise and sunset against
 * Open-Meteo's own `daily` values, for nine sites spanning latitude −67.6° to +69.6°
 * and three consecutive days each — 54 comparisons.
 *
 * **Why this is the load-bearing accuracy test.** The app already ships the
 * provider's sunrise and sunset, so this is not a comparison against a number
 * somebody typed from a table: it is the same figure the JSON tab prints today,
 * captured from the live API on 26 Aug 2026 and frozen here. If the solar model is
 * wrong, these 54 rows say so before a user does.
 *
 * **Tolerance is 120 s, and the reason is on Open-Meteo's side of the wire.** Its
 * values are truncated to the minute (every mid-latitude row here lands 0–60 s after
 * the reference, never before, which is exactly what truncation looks like), and past
 * |lat| 60 a minute of clock is only ~0.07° of altitude, so its own approximation
 * shows through. What this test can prove is that the two agree to well inside the
 * width of a rendered `HH:mm`; the second test below is what pins the engine to the
 * angle it was asked for.
 */
class SolarEventsTest {

    private data class Site(
        val name: String,
        val lat: Double,
        val lon: Double,
        val zone: String,
        val date: String,
        val sunrise: String,
        val sunset: String
    ) {
        val coords get() = Coordinates(lat, lon)
        val zoneId: ZoneId get() = ZoneId.of(zone)
        val localDate: LocalDate get() = LocalDate.parse(date)
        fun instant(local: String): Instant =
            LocalDateTime.parse(local).atZone(zoneId).toInstant()
    }

    // Captured from api.open-meteo.com/v1/forecast on 2026-08-26 (daily=sunrise,sunset,
    // timezone=auto). Frozen on purpose: a test that phones a provider is a test that
    // fails on a train.
    private val sites = listOf(
        Site("Tromso", 69.6492, 18.9553, "Europe/Oslo", "2026-08-26", "2026-08-26T04:37", "2026-08-26T20:54"),
        Site("Tromso", 69.6492, 18.9553, "Europe/Oslo", "2026-08-27", "2026-08-27T04:41", "2026-08-27T20:49"),
        Site("Tromso", 69.6492, 18.9553, "Europe/Oslo", "2026-08-28", "2026-08-28T04:45", "2026-08-28T20:45"),
        Site("Reykjavik", 64.1355, -21.8954, "Atlantic/Reykjavik", "2026-08-26", "2026-08-26T05:53", "2026-08-26T21:05"),
        Site("Reykjavik", 64.1355, -21.8954, "Atlantic/Reykjavik", "2026-08-27", "2026-08-27T05:56", "2026-08-27T21:01"),
        Site("Reykjavik", 64.1355, -21.8954, "Atlantic/Reykjavik", "2026-08-28", "2026-08-28T05:59", "2026-08-28T20:58"),
        Site("Milan", 45.4642, 9.19, "Europe/Rome", "2026-08-26", "2026-08-26T06:37", "2026-08-26T20:12"),
        Site("Milan", 45.4642, 9.19, "Europe/Rome", "2026-08-27", "2026-08-27T06:38", "2026-08-27T20:10"),
        Site("Milan", 45.4642, 9.19, "Europe/Rome", "2026-08-28", "2026-08-28T06:39", "2026-08-28T20:08"),
        Site("NewYork", 40.7128, -74.006, "America/New_York", "2026-08-26", "2026-08-26T06:17", "2026-08-26T19:37"),
        Site("NewYork", 40.7128, -74.006, "America/New_York", "2026-08-27", "2026-08-27T06:18", "2026-08-27T19:36"),
        Site("NewYork", 40.7128, -74.006, "America/New_York", "2026-08-28", "2026-08-28T06:19", "2026-08-28T19:34"),
        Site("Singapore", 1.3521, 103.8198, "Asia/Singapore", "2026-08-27", "2026-08-27T07:02", "2026-08-27T19:10"),
        Site("Singapore", 1.3521, 103.8198, "Asia/Singapore", "2026-08-28", "2026-08-28T07:01", "2026-08-28T19:10"),
        Site("Singapore", 1.3521, 103.8198, "Asia/Singapore", "2026-08-29", "2026-08-29T07:01", "2026-08-29T19:10"),
        Site("Nairobi", -1.2921, 36.8219, "Africa/Nairobi", "2026-08-26", "2026-08-26T06:32", "2026-08-26T18:36"),
        Site("Nairobi", -1.2921, 36.8219, "Africa/Nairobi", "2026-08-27", "2026-08-27T06:31", "2026-08-27T18:36"),
        Site("Nairobi", -1.2921, 36.8219, "Africa/Nairobi", "2026-08-28", "2026-08-28T06:31", "2026-08-28T18:36"),
        Site("Sydney", -33.8688, 151.2093, "Australia/Sydney", "2026-08-27", "2026-08-27T06:20", "2026-08-27T17:33"),
        Site("Sydney", -33.8688, 151.2093, "Australia/Sydney", "2026-08-28", "2026-08-28T06:18", "2026-08-28T17:34"),
        Site("Sydney", -33.8688, 151.2093, "Australia/Sydney", "2026-08-29", "2026-08-29T06:17", "2026-08-29T17:34"),
        Site("Ushuaia", -54.8019, -68.303, "America/Argentina/Ushuaia", "2026-08-26", "2026-08-26T08:27", "2026-08-26T18:41"),
        Site("Ushuaia", -54.8019, -68.303, "America/Argentina/Ushuaia", "2026-08-27", "2026-08-27T08:25", "2026-08-27T18:43"),
        Site("Ushuaia", -54.8019, -68.303, "America/Argentina/Ushuaia", "2026-08-28", "2026-08-28T08:23", "2026-08-28T18:45"),
        Site("Rothera", -67.5678, -68.1272, "Antarctica/Rothera", "2026-08-26", "2026-08-26T09:08", "2026-08-26T18:01"),
        Site("Rothera", -67.5678, -68.1272, "Antarctica/Rothera", "2026-08-27", "2026-08-27T09:04", "2026-08-27T18:04"),
        Site("Rothera", -67.5678, -68.1272, "Antarctica/Rothera", "2026-08-28", "2026-08-28T09:00", "2026-08-28T18:08")
    )

    @Test
    fun `sunrise and sunset agree with Open-Meteo from minus 68 to plus 70 degrees`() {
        val failures = mutableListOf<String>()
        sites.forEach { site ->
            val day = AstronomyEngine.solarDay(site.localDate, site.zoneId, site.coords)
            listOf(
                "sunrise" to (day.sunrise to site.instant(site.sunrise)),
                "sunset" to (day.sunset to site.instant(site.sunset))
            ).forEach { (label, pair) ->
                val (computed, reference) = pair
                if (computed == null) {
                    failures += "${site.name} ${site.date} $label: engine says it does not happen"
                    return@forEach
                }
                val delta = Duration.between(reference, computed).seconds
                if (abs(delta) > TOLERANCE_SECONDS) {
                    failures += "${site.name} ${site.date} $label: ${delta}s off"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * The other half of the accuracy argument: whatever angle an event is defined at,
     * the sun is really at that angle when the engine says so.
     *
     * Together with the contract test above this covers the whole solar catalog
     * without a reference table for each event. The contract test proves the SUN'S
     * POSITION is right (a wrong one could not agree with a real provider at nine
     * latitudes); this proves the SOLVER is right at any threshold. Twilight, golden
     * hour and blue hour are then correct by construction, which is the point of
     * having one crossing primitive instead of six formulas.
     */
    @Test
    fun `every solar event lands on the altitude that defines it`() {
        val milan = Coordinates(45.4642, 9.19)
        val zone = ZoneId.of("Europe/Rome")
        val date = LocalDate.of(2026, 8, 26)
        val day = AstronomyEngine.solarDay(date, zone, milan)
        mapOf(
            AstronomyEngine.SUNRISE_ALTITUDE to listOf(day.sunrise, day.sunset),
            AstronomyEngine.CIVIL_TWILIGHT to listOf(day.civilDawn, day.civilDusk),
            AstronomyEngine.NAUTICAL_TWILIGHT to listOf(day.nauticalDawn, day.nauticalDusk),
            AstronomyEngine.ASTRONOMICAL_TWILIGHT to listOf(day.astronomicalDawn, day.astronomicalDusk),
            AstronomyEngine.GOLDEN_HOUR_ALTITUDE to
                listOf(day.goldenHourMorningEnd, day.goldenHourEveningStart),
            AstronomyEngine.BLUE_HOUR_START to
                listOf(day.blueHourMorningEnd, day.blueHourEveningStart),
            AstronomyEngine.BLUE_HOUR_END to
                listOf(day.blueHourMorningStart, day.blueHourEveningEnd)
        ).forEach { (altitude, instants) ->
            instants.forEach { instant ->
                assertNotNull("no event at $altitude°", instant)
                assertEquals(
                    "altitude at $instant",
                    altitude,
                    AstronomyEngine.sunAltitude(instant!!, milan),
                    ONE_SECOND_OF_SUN
                )
            }
        }
    }

    @Test
    fun `the day's events run in the order the sky puts them in`() {
        val day = AstronomyEngine.solarDay(
            LocalDate.of(2026, 8, 26), ZoneId.of("Europe/Rome"), Coordinates(45.4642, 9.19)
        )
        val ordered = listOf(
            day.astronomicalDawn, day.nauticalDawn, day.civilDawn, day.blueHourMorningStart,
            day.sunrise, day.goldenHourMorningEnd, day.solarNoon, day.goldenHourEveningStart,
            day.sunset, day.blueHourEveningEnd, day.civilDusk, day.nauticalDusk,
            day.astronomicalDusk
        ).map { requireNotNull(it) }
        assertEquals(ordered.sorted(), ordered)
    }

    @Test
    fun `solar noon sits between sunrise and sunset and is the day's highest sun`() {
        val milan = Coordinates(45.4642, 9.19)
        val day = AstronomyEngine.solarDay(
            LocalDate.of(2026, 8, 26), ZoneId.of("Europe/Rome"), milan
        )
        val noonAltitude = AstronomyEngine.sunAltitude(day.solarNoon, milan)
        listOf(-3600L, -600L, 600L, 3600L).forEach { offset ->
            val other = AstronomyEngine.sunAltitude(day.solarNoon.plusSeconds(offset), milan)
            assertTrue("higher sun ${offset}s away from noon", other <= noonAltitude)
        }
    }

    @Test
    fun `daylight duration matches the provider's own within two minutes`() {
        // Open-Meteo reported 13h 34m 41s of daylight for Milan on 2026-08-26; the
        // engine derives it from its own sunrise and sunset, so this is really a check
        // that the two ends are consistent with each other and not just individually.
        val day = AstronomyEngine.solarDay(
            LocalDate.of(2026, 8, 26), ZoneId.of("Europe/Rome"), Coordinates(45.4642, 9.19)
        )
        val daylight = requireNotNull(day.daylight)
        val reference = Duration.ofHours(13).plusMinutes(35)
        assertTrue(
            "daylight $daylight",
            abs(daylight.seconds - reference.seconds) <= 120
        )
    }

    private companion object {
        const val TOLERANCE_SECONDS = 120L

        /**
         * The engine answers to the second, which is the resolution an `HH:mm` line
         * needs and no more. At Milan the sun climbs 0.169°/min, so one second of
         * clock is 0.0028° of altitude: this tolerance is that second, not a fudge
         * factor. Tightening it would only assert that the bisection was allowed to
         * run longer than the product wants.
         */
        const val ONE_SECOND_OF_SUN = 0.005
    }
}
