package com.callbackdev.tweather.ui.sky

import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.tweather.data.SkySubscription
import com.callbackdev.tweather.domain.model.Astronomical
import com.callbackdev.tweather.domain.model.CacheStatus
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.CurrentConditions
import com.callbackdev.tweather.domain.model.HourlyForecast
import com.callbackdev.tweather.domain.model.Location
import com.callbackdev.tweather.domain.model.MoonPhase
import com.callbackdev.tweather.domain.model.Precipitation
import com.callbackdev.tweather.domain.model.SystemInfo
import com.callbackdev.tweather.domain.model.WeatherCondition
import com.callbackdev.tweather.domain.model.WeatherReport
import com.callbackdev.tweather.domain.model.Wind
import com.callbackdev.tweather.domain.sky.AstronomyEngine
import com.callbackdev.tweather.ui.weather.toReadmeMarkdown
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **The agreement test** (`VISION_SKY.md` §9.1 and §14): the numbers in the README's
 * `## Astronomy` are the same numbers `sky.crontab` prints, for the same city at the
 * same instant.
 *
 * This is the series' first rule turned into an assertion. The whole reason the sky
 * lives inside tweather rather than in a fourth app is that one engine answers for
 * every surface; the moment two files could disagree about tonight's sunset, that
 * argument is gone.
 */
@RunWith(RobolectricTestRunner::class)
class SkyReadmeAgreementTest {

    private val resources: Resources =
        ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private val milan = Coordinates(45.4642, 9.19)
    private val rome: ZoneId = ZoneId.of("Europe/Rome")
    private val now: Instant = Instant.parse("2026-08-26T16:30:00Z")

    private fun report(cloud: Int = 8, rain: Int = 0): WeatherReport {
        val start = now.atZone(rome).toLocalDateTime().truncatedTo(ChronoUnit.HOURS)
        val day = AstronomyEngine.solarDay(start.toLocalDate(), rome, milan)
        return WeatherReport(
            location = Location("Milan", "Lombardy", "Italy", milan, rome.id, start),
            current = CurrentConditions(
                WeatherCondition(0, "Clear", "☀️"), 20.0, 20.0, 50, 10.0, 10.0, 1013.0,
                3, "Moderate", Wind(5.0, "N", 0, 8.0), Precipitation(0.0, 0)
            ),
            airQuality = null,
            pollen = null,
            astronomical = Astronomical(
                sunrise = day.sunrise?.atZone(rome)?.toLocalTime()?.truncatedTo(ChronoUnit.MINUTES),
                sunset = day.sunset?.atZone(rome)?.toLocalTime()?.truncatedTo(ChronoUnit.MINUTES),
                moonPhase = MoonPhase.at(now),
                daylightDuration = day.daylight
            ),
            hourly = (0 until 168).map { i ->
                HourlyForecast(
                    start.plusHours(i.toLong()), 20.0,
                    WeatherCondition(0, "Clear", "☀️"), rain, cloud
                )
            },
            daily = emptyList(),
            systemInfo = SystemInfo("Open-Meteo API", now, CacheStatus.HIT, 100)
        )
    }

    private fun context(report: WeatherReport?) = SkyContext(
        cityLabel = "Milan, Lombardy",
        coordinates = milan,
        zone = rome,
        now = now,
        report = report,
        updateFrequencyMin = 60
    )

    private val allJobs = listOf(
        "sun.rise", "sun.set", "golden_hour.pm", "blue_hour.pm",
        "darkness.window", "moon.rise", "moon.set", "moon.today"
    ).map { SkySubscription(it) }

    private fun readme(report: WeatherReport, summary: SkySummary?) =
        report.toReadmeMarkdown(resources = resources, sky = summary)

    /**
     * The body of one `##` section. Stops at the next heading OR at the blank line
     * before the footer — `## Astronomy` is the document's last heading, so without
     * that the "section" swallowed `*Last updated 18:30*` and the test read the
     * footer's clock as one of the sky's times.
     */
    private fun section(lines: List<String>, heading: String) = lines
        .dropWhile { !it.startsWith("## ") || !it.contains(heading) }
        .drop(1)
        .takeWhile { !it.startsWith("## ") && it.isNotBlank() }

    /** Every `HH:mm` in a block of text, so two renderings can be compared as sets. */
    private fun times(lines: List<String>) =
        Regex("\\d{2}:\\d{2}").findAll(lines.joinToString(" ")).map { it.value }.toSet()

    /**
     * Compared in the MORNING on purpose. The two files answer different questions —
     * `## Astronomy` describes today, `sky.crontab` describes what is next — so at
     * six in the evening the README rightly says today's sunrise (06:37) while the
     * crontab rightly says tomorrow's (06:38). Before either has happened, "today's"
     * and "next" are the same events, and that is where the two must agree to the
     * minute. The deliberate divergence has a test of its own below.
     */
    @Test
    fun `the README and the crontab print the same times`() {
        val morning = Instant.parse("2026-08-26T03:00:00Z")
        val report = report().let { it.copy(location = it.location) }
        val context = context(report).copy(now = morning)
        val summary = SkyReadme.summarize(context, allJobs)
        val document = SkyDocumentBuilder.build(allJobs, context)

        // The sun's lines only. The moon's rise and set drift ~50 minutes a day, so
        // "today's" and "the next one" part company within hours of each other even
        // in the morning — the same today-vs-next distinction as the sunrise, just
        // magnified. It has its own test below.
        val astronomy = section(readme(report, summary), "Astronom")
            .filterNot { it.startsWith("Moon") }
        val fromReadme = times(astronomy)
        val fromCrontab = times(document.rows.map { it.comment })

        assertTrue("the README should print some times", fromReadme.isNotEmpty())
        // Every time the README shows is one the crontab also resolved. The crontab
        // holds more (a moonrise the README omits when it has none for tonight), so
        // containment is the honest relation, not equality.
        val disagreements = fromReadme - fromCrontab
        assertTrue(
            "the README shows times sky.crontab does not: $disagreements",
            disagreements.isEmpty()
        )
    }

    @Test
    fun `the sunset in the README is the sunset in the crontab, to the minute`() {
        val report = report()
        val context = context(report)
        val summary = SkyReadme.summarize(context, allJobs)
        val fromCrontab = SkyDocumentBuilder.build(allJobs, context)
            .rows.first { it.job.id == "sun.set" }
            .at!!.atZone(rome).toLocalTime().truncatedTo(ChronoUnit.MINUTES)
        assertEquals(fromCrontab, report.astronomical.sunset)
        assertTrue(
            section(readme(report, summary), "Astronom").first().contains(fromCrontab.toString())
        )
    }

    @Test
    fun `the darkness window agrees with the crontab's`() {
        val context = context(report())
        val summary = SkyReadme.summarize(context, allJobs)
        val row = SkyDocumentBuilder.build(allJobs, context)
            .rows.first { it.job.id == "darkness.window" }
        assertEquals(
            row.at!!.atZone(rome).toLocalTime().truncatedTo(ChronoUnit.MINUTES),
            summary.darkness!!.start.truncatedTo(ChronoUnit.MINUTES)
        )
    }

    // -------------------------------------------------------- what the README shows

    /**
     * `## Astronomy` is always there — it is today, not an advertisement — and only
     * the lines the module ADDS to it come and go with `sky.enabled`.
     */
    @Test
    fun `the section survives the module being switched off, minus the sky lines`() {
        val report = report()
        val withSky = section(readme(report, SkyReadme.summarize(context(report), allJobs)), "Astronom")
        val without = section(readme(report, null), "Astronom")
        assertTrue("the section must not vanish", without.isNotEmpty())
        assertTrue(without.first().contains("20:"))   // sunrise/sunset survive
        assertTrue("the sky lines should be gone", without.size < withSky.size)
        assertFalse(without.joinToString(" ").contains("Golden"))
    }

    /**
     * `## Status` reports YOUR subscriptions. Somebody who never opened `sky.crontab`
     * never gets a sky warning out of the README.
     */
    @Test
    fun `an unsubscribed sky raises nothing in Status`() {
        val report = report(cloud = 95)
        val summary = SkyReadme.summarize(context(report), subscriptions = emptyList())
        val status = section(readme(report, summary), "Status")
        assertFalse(status.joinToString(" "), status.any { it.contains("sun.set") })
    }

    @Test
    fun `a subscribed job the clouds will spoil earns one line in Status`() {
        val report = report(cloud = 95)
        val summary = SkyReadme.summarize(context(report), allJobs)
        val status = section(readme(report, summary), "Status")
        val warnings = status.filter { it.startsWith("> ") }
        assertEquals("exactly one sky line, or Status stops being a badge", 1, warnings.size)
        assertTrue(warnings.single(), warnings.single().contains("overcast"))
    }

    /**
     * **The line is prose** (Fase 16g). It used to read
     * `> golden_hour.pm at 19:32: ✗ fail  cloud 95%`: the crontab's dotted id and the
     * crontab's verdict, dropped into the one document this app writes in a language.
     * The job arrives by name and the verdict as a sentence — and the NUMBER survives,
     * because a verdict without the figure it was built from is an opinion
     * (`VISION_SKY.md` §7).
     */
    @Test
    fun `the sky's line in Status is a sentence, not a crontab row`() {
        val report = report(cloud = 95)
        val summary = SkyReadme.summarize(context(report), allJobs)
        val line = section(readme(report, summary), "Status").single { it.startsWith("> ") }

        assertEquals("> 🌇 The evening golden hour at 19:32: the sky will be overcast (95% cloud)", line)
        assertFalse(line, line.contains("golden_hour"))
        assertFalse(line, line.contains("fail"))
        assertFalse(line, line.contains("✗"))
    }

    /** The same line in Italian: the whole point of naming the job in a resource. */
    @Test
    @Config(qualifiers = "it")
    fun `the sky's line is localized like the rest of the document`() {
        val report = report(cloud = 95)
        val summary = SkyReadme.summarize(context(report), allJobs)
        val line = section(readme(report, summary), "Stato").single { it.startsWith("> ") }

        assertEquals(
            "> 🌇 L'ora d'oro della sera alle 19:32: il cielo sarà coperto (nuvole al 95%)",
            line
        )
    }

    /**
     * Rain and moonlight are named for what they are: the cloud number is not the
     * reason in either case, and printing it would be the file blaming the sky for a
     * night the moon ruined.
     */
    @Test
    fun `the reason the sky said no is the reason the line gives`() {
        val rainy = report(cloud = 10, rain = 80)
        val summary = SkyReadme.summarize(context(rainy), allJobs)
        // Rain at 80% also trips the builtin precipitation alert, which is the older
        // line of the two and reads first: the sky's is the one that names a job.
        val line = section(readme(rainy, summary), "Status").last { it.startsWith("> ") }
        assertEquals("> 🌇 The evening golden hour at 19:32: rain is likely (80%)", line)

        // 26 Aug 2026: the moon is nearly full and up all night, so the dark window is
        // `~ unstable` under a perfectly clear sky — the one verdict the clouds did
        // not decide.
        val clear = report(cloud = 5)
        val darkness = listOf(SkySubscription("darkness.window"))
        val moonlit = SkyReadme.summarize(context(clear), darkness)
        val moon = section(readme(clear, moonlit), "Status").single { it.startsWith("> ") }
        assertTrue(moon, moon.startsWith("> 🌌 The dark sky window at "))
        assertTrue(moon, moon.contains("the moon will be up and "))
        assertFalse(moon, moon.contains("cloud"))
    }

    @Test
    fun `a clear night says everything looks good rather than nothing at all`() {
        // Without `darkness.window`: on 26 Aug 2026 the moon is nearly full and up
        // all night, so that job is honestly `~ unstable` under any sky — which is
        // the point of the moon condition and would make this a test of the wrong
        // thing.
        val report = report(cloud = 5)
        val subscriptions = allJobs.filterNot { it.jobId == "darkness.window" }
        val summary = SkyReadme.summarize(context(report), subscriptions)
        val status = section(readme(report, summary), "Status")
        assertTrue(status.joinToString(" "), status.none { it.startsWith("> ") })
        assertTrue(status.isNotEmpty())
    }

    /**
     * The one place the two files say different numbers, and both are right: at six
     * in the evening `## Astronomy` reports today's sunrise, already past, while
     * `sky.crontab` reports the next one, tomorrow's. A file that answers "what is
     * today" and a file that answers "what is next" are not in disagreement — pinned
     * here so nobody later "fixes" one into the other.
     */
    @Test
    fun `today's sunrise and the next one are allowed to differ, and do`() {
        val report = report()
        val fromReadme = report.astronomical.sunrise!!
        val fromCrontab = SkyDocumentBuilder.build(allJobs, context(report))
            .rows.first { it.job.id == "sun.rise" }.at!!.atZone(rome)
        assertEquals("the crontab should be looking at tomorrow", 27, fromCrontab.dayOfMonth)
        assertEquals("the README should be looking at today", 26, now.atZone(rome).dayOfMonth)
        assertTrue(fromCrontab.toLocalTime() != fromReadme)
    }

    /**
     * The moon is the same distinction, magnified: it rises about fifty minutes later
     * every day, so `## Astronomy`'s "tonight's moon" and the crontab's "the next
     * moonrise" are routinely different clock times — and describing tonight is the
     * README's job. Pinned so the two are never "reconciled" into one wrong answer.
     */
    @Test
    fun `tonight's moon and the next moonrise are different questions`() {
        val report = report()
        val summary = SkyReadme.summarize(context(report), allJobs)
        val row = SkyDocumentBuilder.build(allJobs, context(report))
            .rows.first { it.job.id == "moon.set" }
        assertTrue("the summary describes tonight", summary.moonset != null)
        assertTrue(
            "the crontab looks forward",
            row.at!!.isAfter(now)
        )
    }

    /**
     * Above the Arctic circle in June the sun does not set, and the README says so
     * with the glyph `sky.crontab` uses for the same fact rather than with `00:00`.
     */
    @Test
    fun `a polar day renders as absent, not as midnight`() {
        val polar = report().let {
            it.copy(
                astronomical = it.astronomical.copy(
                    sunrise = null, sunset = null, daylightDuration = null
                )
            )
        }
        val astronomy = section(readme(polar, null), "Astronom")
        assertTrue(astronomy.first(), astronomy.first().contains("∅"))
        assertFalse(astronomy.joinToString(" "), astronomy.any { it.contains("00:00") })
    }
}
