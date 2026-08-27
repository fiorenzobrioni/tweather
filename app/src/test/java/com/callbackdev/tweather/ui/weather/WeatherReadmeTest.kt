package com.callbackdev.tweather.ui.weather

import android.app.Application
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.domain.model.HourlyForecast
import com.callbackdev.tweather.domain.model.WeatherCondition
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The README.md document (Fase 10): the human summary of weather_data.json,
 * FULLY localized (headings included — it's prose, not code), curated sections,
 * `## Status` as the repo's build badge via AlertEngine, read third since Fase 13d
 * (a badge below the fold is not a badge). Since Fase 11c both
 * tables are formatted (columns padded) and the next hours are here too; since
 * Fase 11d both tables end with the status column, emoji first and description
 * after it, so the numeric columns never pan off-screen; since Fase 11e the
 * hourly table runs fourteen rows from the hour AFTER the current one, whose
 * rain probability sits on Current's feels-like line instead.
 */
@RunWith(RobolectricTestRunner::class)
class WeatherReadmeTest {

    private val resources: Resources
        get() = ApplicationProvider.getApplicationContext<Application>().resources

    private fun readme(
        report: com.callbackdev.tweather.domain.model.WeatherReport = sampleWeatherReport(),
        locale: Locale = Locale.ENGLISH,
        options: DisplayOptions = DisplayOptions()
    ): List<String> = report.toReadmeMarkdown(
        resources = resources,
        translate = WeatherTranslations.translator(resources),
        locale = locale,
        options = options
    )

    /** The lines under one `##` heading, so a table can be counted in isolation. */
    private fun List<String>.section(heading: String): List<String> =
        dropWhile { it != heading }.drop(1).takeWhile { !it.startsWith("## ") }

    @Test
    fun `english document has every curated section in order`() {
        val lines = readme()
        val headings = lines.filter { it.startsWith("#") }
        assertEquals(
            listOf(
                "# New York", "## Current", "## Today", "## Status",
                "## Next hours", "## Forecast", "## Air quality", "## Conditions",
                "## Astronomy"
            ),
            headings
        )
        assertTrue("NY, USA" in lines)
        assertTrue("**18.5°C** · Partly Cloudy ⛅" in lines)
        assertTrue("Feels like: 17.2°C · Rain: 10%" in lines)
        assertTrue("High: 20°C · Low: 12°C" in lines)
        // Today's MAXIMUM (sample daily.first() = 5), NOT current.uvIndex (= 4):
        // under "## Today" the instant index reads as a daily figure and is 0 every
        // evening — the Aug 2026 report that started this fix
        assertTrue("UV index: 5 (Moderate ☀️)" in lines)
        assertTrue("🌬️ Wind: 12.5 km/h NW" in lines)
        assertTrue("💧 Humidity: 54%" in lines)
        assertTrue("AQI 42 · Good ⚪" in lines)
        assertTrue("Pollen: grass Low · tree High · weed Moderate" in lines)
        assertTrue("Sunrise: 07:12 · Sunset: 18:04" in lines)
        assertTrue("Daylight: 10h 52m" in lines)
        assertTrue("Moon: Waxing Gibbous 🌔" in lines)
    }

    @Test
    fun `forecast is a markdown table with one row per day`() {
        val forecast = readme().section("## Forecast")
        assertEquals(
            listOf(
                "| Day | High | Low | Rain | Status           |",
                "| --- | ---: | --: | ---: | ---------------- |",
                "| Mon |  20° | 12° |   0% | ☀️ Sunny         |",
                "| Tue |  18° | 11° |  85% | 🌧️ Rainy         |",
                "| Wed |  16° | 10° |  20% | ☁️ Cloudy        |",
                "| Thu |  19° | 13° |  10% | ⛅ Partly Cloudy |"
            ),
            forecast.filter { it.startsWith("|") }
        )
    }

    @Test
    fun `status is the badge of the page, read before the forecasts`() {
        // Fase 13d: it used to close the document — a warning landed on line 57 of 58,
        // below the moon phase. It is the only actionable line there is.
        val lines = readme()
        val status = lines.indexOf("## Status")
        assertTrue(status < lines.indexOf("## Next hours"))
        assertTrue(status < lines.indexOf("## Astronomy"))
        assertTrue(lines.indexOf("## Today") < status)
    }

    @Test
    fun `the next hours start at the hour after the current one, after today and status`() {
        val lines = readme()
        // Nothing but the status badge sits between Today and the hours.
        val headings = lines.filter { it.startsWith("## ") }
        assertEquals(
            listOf("## Today", "## Status", "## Next hours"),
            headings.subList(headings.indexOf("## Today"), headings.indexOf("## Next hours") + 1)
        )
        assertEquals(
            listOf(
                "| Hour  | Temp | Rain | Status           |",
                "| ----- | ---: | ---: | ---------------- |",
                "| 16:00 |  18° |   0% | ☀️ Sunny         |",
                "| 17:00 |  17° |   5% | ⛅ Partly Cloudy |",
                "| 18:00 |  15° |  10% | ⛅ Partly Cloudy |",
                "| 19:00 |  14° |   0% | 🌙 Clear         |"
            ),
            lines.section("## Next hours").filter { it.startsWith("|") }
        )
    }

    @Test
    fun `the hourly table stops at fourteen hours, the daily one takes over`() {
        val start = LocalDate.of(2023, 10, 27).atTime(15, 0)
        val report = sampleWeatherReport().copy(
            hourly = (0 until 24).map {
                HourlyForecast(
                    start.plusHours(it.toLong()),
                    19.0,
                    WeatherCondition(0, "Sunny", "☀️"),
                    0,
                    0
                )
            }
        )
        val rows = readme(report).section("## Next hours").filter { it.startsWith("|") }
        assertEquals(16, rows.size) // header + separator + 14 hours, from 16:00
        assertTrue(rows[2].startsWith("| 16:00 |"))
        assertTrue(rows.last().startsWith("| 05:00 |"))
    }

    @Test
    fun `a city without hourly data simply has no next hours section`() {
        val lines = readme(sampleWeatherReport().copy(hourly = emptyList()))
        assertTrue(lines.none { "Next hours" in it })
        assertTrue("## Forecast" in lines)
    }

    @Test
    fun `the current hour alone makes no table, it is Current's job`() {
        val base = sampleWeatherReport()
        val lines = readme(base.copy(hourly = base.hourly.take(1)))
        assertTrue(lines.none { "Next hours" in it })
    }

    @Test
    fun `calm forecast reports that everything looks good`() {
        val lines = readme()
        assertTrue("Everything looks good." in lines)
        assertTrue(lines.none { it.startsWith(">") })
    }

    @Test
    fun `a severe hour within the lookahead becomes a blockquote warning`() {
        val base = sampleWeatherReport()
        val report = base.copy(
            hourly = base.hourly + HourlyForecast(
                LocalDate.of(2023, 10, 27).atTime(18, 0),
                15.0,
                WeatherCondition(95, "Thunderstorm", "⛈️"),
                80,
                100
            )
        )
        val lines = readme(report)
        assertTrue("> ⚠️ Thunderstorm ⛈️ expected around 18:00" in lines)
        assertFalse("Everything looks good." in lines)
    }

    @Test
    fun `a likely-rain hour becomes its own blockquote warning`() {
        val base = sampleWeatherReport()
        val report = base.copy(
            hourly = base.hourly.map {
                if (it.time.hour == 16) it.copy(precipChancePct = 80) else it
            }
        )
        val lines = readme(report)
        assertTrue("> 🌧️ Rain likely around 16:00 (80%)" in lines)
    }

    @Test
    fun `sections the APIs could not fill are absent, not null`() {
        val report = sampleWeatherReport().copy(airQuality = null, pollen = null)
        val lines = readme(report)
        assertTrue(lines.none { "Air quality" in it })
        assertTrue(lines.none { it.startsWith("Pollen:") })
        // It leads the detail sections (Fase 13d) but is still the one that can vanish:
        // dropping it must not take Conditions or the heading order with it.
        assertEquals(
            listOf("## Forecast", "## Conditions", "## Astronomy"),
            lines.filter { it.startsWith("## ") }.takeLast(3)
        )
    }

    @Test
    fun `units follow the display options like the JSON`() {
        val lines = readme(options = DisplayOptions(temperature = TemperatureUnit.FAHRENHEIT))
        assertTrue("**65.3°F** · Partly Cloudy ⛅" in lines)
        assertTrue("| Mon |  68° | 54° |   0% | ☀️ Sunny         |" in lines)
        assertTrue("| 16:00 |  64° |   0% | ☀️ Sunny         |" in lines)
        assertTrue(lines.none { "°C" in it })
    }

    @Test
    fun `footer stamps the last sync in the city's timezone`() {
        // 1_698_413_400 = 2023-10-27 13:30 UTC = 09:30 in America/New_York (EDT)
        assertTrue("*Last updated 09:30 · data by Open-Meteo*" in readme())
    }

    @Test
    @Config(qualifiers = "it")
    fun `the italian document is fully localized, headings included`() {
        val lines = readme(locale = Locale.ITALIAN)
        assertTrue("## Attuale" in lines)
        assertTrue("**18.5°C** · Parzialmente nuvoloso ⛅" in lines)
        assertTrue("Percepita: 17.2°C · Pioggia: 10%" in lines)
        assertTrue("Max: 20°C · Min: 12°C" in lines)
        assertTrue("## Prossime ore" in lines)
        assertTrue("| Ora   | Temp | Pioggia | Stato                    |" in lines)
        assertTrue("| 16:00 |  18° |      0% | ☀️ Sunny                 |" in lines)
        assertTrue("| Gg  | Max | Min | Pioggia | Stato                    |" in lines)
        assertTrue("| Mar | 18° | 11° |     85% | 🌧️ Pioggia               |" in lines)
        assertTrue("Tutto regolare." in lines)
        assertTrue(lines.any { it.startsWith("*Aggiornato alle 09:30") })
    }
}
