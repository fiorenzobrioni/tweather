package com.callbackdev.tweather.domain.rules

import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.data.UnitSettings
import com.callbackdev.tweather.data.WindSpeedUnit
import com.callbackdev.tweather.domain.model.HourlyForecast
import com.callbackdev.tweather.domain.model.WeatherCondition
import com.callbackdev.tweather.ui.weather.sampleWeatherReport
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleVariablesTest {

    // The sample's hourly rows run 15:00–19:00 on 2023-10-27
    private val now: LocalDateTime = LocalDateTime.of(2023, 10, 27, 14, 30)
    private val report = sampleWeatherReport()
    private val metric = UnitSettings()
    private val imperial = UnitSettings(
        temperature = TemperatureUnit.FAHRENHEIT,
        windSpeed = WindSpeedUnit.MPH
    )

    private fun resolve(id: String) = RuleVariables.byId(id)!!.resolve(report, now)

    @Test
    fun `current variables mirror the report`() {
        assertEquals(18.5, resolve("current.temp_c")!!.value, 0.0)
        assertEquals(4.0, resolve("current.uv_index")!!.value, 0.0)
        assertEquals(18.0, resolve("current.wind.gust_kph")!!.value, 0.0)
        assertEquals(10.0, resolve("current.precipitation.chance_pct")!!.value, 0.0)
        assertEquals(42.0, resolve("current.aqi_index")!!.value, 0.0)
    }

    @Test
    fun `aqi resolves null when air quality is unavailable`() {
        val noAq = report.copy(airQuality = null)
        assertNull(RuleVariables.byId("current.aqi_index")!!.resolve(noAq, now))
    }

    @Test
    fun `window aggregates pick value and hour`() {
        // max precip chance in the next 6h is 10% at 18:00
        val precip = resolve("next_6h.precip_chance_max")!!
        assertEquals(10.0, precip.value, 0.0)
        assertEquals(LocalDateTime.of(2023, 10, 27, 18, 0), precip.at)
        // min temperature is 14.0 at 19:00
        val tempMin = resolve("next_6h.temp_c_min")!!
        assertEquals(14.0, tempMin.value, 0.0)
        assertEquals(LocalDateTime.of(2023, 10, 27, 19, 0), tempMin.at)
        assertEquals(19.0, resolve("next_6h.temp_c_max")!!.value, 0.0)
    }

    @Test
    fun `window boundary is inclusive and an empty window resolves null`() {
        // 19:00 row sits exactly at now+6h when now is 13:00
        val at13 = LocalDateTime.of(2023, 10, 27, 13, 0)
        val variable = RuleVariables.byId("next_6h.temp_c_min")!!
        assertEquals(14.0, variable.resolve(report, at13)!!.value, 0.0)
        // all rows are in the past relative to a much later now
        assertNull(variable.resolve(report, LocalDateTime.of(2023, 10, 28, 9, 0)))
    }

    @Test
    fun `wmo_severe is a boolean with the triggering hour`() {
        assertEquals(0.0, resolve("next_12h.wmo_severe")!!.value, 0.0)
        val thunder = report.copy(
            hourly = report.hourly + HourlyForecast(
                time = LocalDateTime.of(2023, 10, 27, 20, 0),
                tempC = 13.0,
                condition = WeatherCondition(95, "Thunderstorm", "⛈️"),
                precipChancePct = 90
            )
        )
        val severe = RuleVariables.byId("next_12h.wmo_severe")!!.resolve(thunder, now)!!
        assertEquals(1.0, severe.value, 0.0)
        assertEquals(LocalDateTime.of(2023, 10, 27, 20, 0), severe.at)
    }

    @Test
    fun `today variables read the first daily entry`() {
        assertEquals(20.0, resolve("today.high_c")!!.value, 0.0)
        assertEquals(12.0, resolve("today.low_c")!!.value, 0.0)
        assertEquals(0.0, resolve("today.precip_pct")!!.value, 0.0)
        assertNull(RuleVariables.byId("today.high_c")!!.resolve(report.copy(daily = emptyList()), now))
    }

    @Test
    fun `display names follow the units setting`() {
        val temp = RuleVariables.byId("current.temp_c")!!
        assertEquals("current.temp_c", RuleVariables.displayId(temp, metric))
        assertEquals("current.temp_f", RuleVariables.displayId(temp, imperial))
        val tempMin = RuleVariables.byId("next_6h.temp_c_min")!!
        assertEquals("next_6h.temp_f_min", RuleVariables.displayId(tempMin, imperial))
        val gust = RuleVariables.byId("current.wind.gust_kph")!!
        assertEquals("current.wind.gust_mph", RuleVariables.displayId(gust, imperial))
        // unit-less numbers never change name
        val uv = RuleVariables.byId("current.uv_index")!!
        assertEquals("current.uv_index", RuleVariables.displayId(uv, imperial))
    }

    @Test
    fun `displayed names map back to canonical ids`() {
        assertEquals("current.temp_c", RuleVariables.canonicalId("current.temp_f"))
        assertEquals("next_6h.temp_c_min", RuleVariables.canonicalId("next_6h.temp_f_min"))
        assertEquals("current.wind.speed_kph", RuleVariables.canonicalId("current.wind.speed_mph"))
        assertEquals("current.uv_index", RuleVariables.canonicalId("current.uv_index"))
        assertNull(RuleVariables.canonicalId("current.nonsense"))
    }

    @Test
    fun `values convert and round-trip between units`() {
        val kind = RuleVariableKind.TEMPERATURE
        assertEquals(41.0, RuleVariables.displayValue(kind, 5.0, imperial), 1e-9)
        assertEquals(5.0, RuleVariables.canonicalValue(kind, 41.0, imperial), 1e-9)
        assertEquals(5.0, RuleVariables.displayValue(kind, 5.0, metric), 0.0)
        val speed = RuleVariableKind.SPEED
        assertEquals(10.0, RuleVariables.canonicalValue(
            speed, RuleVariables.displayValue(speed, 10.0, imperial), imperial
        ), 1e-9)
    }

    @Test
    fun `formatValue renders integers bare and booleans as words`() {
        assertEquals("19", RuleVariables.formatValue(RuleVariableKind.NUMBER, 19.0, metric))
        assertEquals("19.5", RuleVariables.formatValue(RuleVariableKind.NUMBER, 19.5, metric))
        assertEquals("65.3", RuleVariables.formatValue(RuleVariableKind.TEMPERATURE, 18.5, imperial))
        assertEquals("true", RuleVariables.formatValue(RuleVariableKind.BOOLEAN, 1.0, metric))
        assertEquals("false", RuleVariables.formatValue(RuleVariableKind.BOOLEAN, 0.0, metric))
    }

    @Test
    fun `every registry id resolves against the sample or declares unavailability`() {
        RuleVariables.all.forEach { variable ->
            // must not throw; today/current/aggregates all resolve on the sample
            assertTrue(variable.id, variable.resolve(report, now) != null)
        }
    }
}
