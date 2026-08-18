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
 * `## Status` as the repo's build badge via AlertEngine. Since Fase 11c both
 * tables are formatted (columns padded, emoji on the right edge) and the next
 * twelve hours are here too, compressed to hour, temperature, emoji and rain.
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
                "# New York", "## Current", "## Today", "## Next hours",
                "## Forecast", "## Conditions", "## Air quality", "## Astronomy",
                "## Status"
            ),
            headings
        )
        assertTrue("NY, USA" in lines)
        assertTrue("**18.5°C** · Partly Cloudy ⛅" in lines)
        assertTrue("Feels like: 17.2°C" in lines)
        assertTrue("High: 20°C · Low: 12°C" in lines)
        assertTrue("UV index: 4 (Moderate ☀️)" in lines)
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
                "| Day | High | Low | Status           | Rain |",
                "| --- | ---: | --: | ---------------- | ---: |",
                "| Mon |  20° | 12° | Sunny         ☀️ |   0% |",
                "| Tue |  18° | 11° | Rainy         🌧️ |  85% |",
                "| Wed |  16° | 10° | Cloudy        ☁️ |  20% |",
                "| Thu |  19° | 13° | Partly Cloudy ⛅ |  10% |"
            ),
            forecast.filter { it.startsWith("|") }
        )
    }

    @Test
    fun `the next hours are their own table, emoji only, right after today`() {
        val lines = readme()
        assertEquals("## Next hours", lines[lines.indexOf("## Today") + 5])
        assertEquals(
            listOf(
                "| Hour  | Temp | Status | Rain |",
                "| ----- | ---: | -----: | ---: |",
                "| 15:00 |  19° |     ☀️ |   0% |",
                "| 16:00 |  18° |     ☀️ |   0% |",
                "| 17:00 |  17° |     ⛅ |   5% |",
                "| 18:00 |  15° |     ⛅ |  10% |",
                "| 19:00 |  14° |     🌙 |   0% |"
            ),
            lines.section("## Next hours").filter { it.startsWith("|") }
        )
    }

    @Test
    fun `the hourly table stops at twelve hours, the daily one takes over`() {
        val start = LocalDate.of(2023, 10, 27).atTime(15, 0)
        val report = sampleWeatherReport().copy(
            hourly = (0 until 24).map {
                HourlyForecast(
                    start.plusHours(it.toLong()),
                    19.0,
                    WeatherCondition(0, "Sunny", "☀️"),
                    0
                )
            }
        )
        val rows = readme(report).section("## Next hours").filter { it.startsWith("|") }
        assertEquals(14, rows.size) // header + separator + 12 hours
        assertTrue(rows.last().startsWith("| 02:00 |"))
    }

    @Test
    fun `a city without hourly data simply has no next hours section`() {
        val lines = readme(sampleWeatherReport().copy(hourly = emptyList()))
        assertTrue(lines.none { "Next hours" in it })
        assertTrue("## Forecast" in lines)
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
                80
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
    }

    @Test
    fun `units follow the display options like the JSON`() {
        val lines = readme(options = DisplayOptions(temperature = TemperatureUnit.FAHRENHEIT))
        assertTrue("**65.3°F** · Partly Cloudy ⛅" in lines)
        assertTrue("| Mon |  68° | 54° | Sunny         ☀️ |   0% |" in lines)
        assertTrue("| 15:00 |  66° |     ☀️ |   0% |" in lines)
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
        assertTrue("Percepita: 17.2°C" in lines)
        assertTrue("Max: 20°C · Min: 12°C" in lines)
        assertTrue("## Prossime ore" in lines)
        assertTrue("| Ora   | Temp | Stato | Pioggia |" in lines)
        assertTrue("| 15:00 |  19° |    ☀️ |      0% |" in lines)
        assertTrue("| Gg  | Max | Min | Stato                    | Pioggia |" in lines)
        assertTrue("| Mar | 18° | 11° | Pioggia               🌧️ |     85% |" in lines)
        assertTrue("Tutto regolare." in lines)
        assertTrue(lines.any { it.startsWith("*Aggiornato alle 09:30") })
    }
}
