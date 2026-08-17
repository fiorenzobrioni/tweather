package com.callbackdev.tweather.domain.rules

import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.data.UnitSettings
import com.callbackdev.tweather.ui.weather.sampleWeatherReport
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleMessagesTest {

    private val now: LocalDateTime = LocalDateTime.of(2023, 10, 27, 14, 30)
    private val report = sampleWeatherReport() // temp 18.5, uv 4
    private val metric = UnitSettings()

    private fun rule(variable: String = "next_6h.precip_chance_max") = NotificationRule(
        id = 1,
        name = "umbrella",
        enabled = true,
        conditions = listOf(RuleCondition(variable, RuleOp.GTE, 60.0)),
        message = "unused here"
    )

    private fun interpolate(
        message: String,
        rule: NotificationRule = rule(),
        value: Double = 78.0,
        at: LocalDateTime? = LocalDateTime.of(2023, 10, 27, 18, 0),
        units: UnitSettings = metric
    ) = RuleMessages.interpolate(message, rule, value, at, report, now, units)

    @Test
    fun `trigger placeholders render the firing value and hour`() {
        assertEquals(
            "Umbrella: 78% at 18:00",
            interpolate("Umbrella: {trigger.value}% at {trigger.time}")
        )
    }

    @Test
    fun `trigger time falls back to now for instant rules`() {
        assertEquals(
            "14:30",
            interpolate("{trigger.time}", rule = rule("current.temp_c"), at = null)
        )
    }

    @Test
    fun `trigger value converts with the first condition's kind`() {
        val fahrenheit = UnitSettings(temperature = TemperatureUnit.FAHRENHEIT)
        assertEquals(
            "57.2",
            interpolate(
                "{trigger.value}",
                rule = rule("next_6h.temp_c_min"),
                value = 14.0,
                units = fahrenheit
            )
        )
    }

    @Test
    fun `variable placeholders resolve in canonical or displayed form`() {
        assertEquals("18.5", interpolate("{current.temp_c}"))
        val fahrenheit = UnitSettings(temperature = TemperatureUnit.FAHRENHEIT)
        assertEquals("65.3", interpolate("{current.temp_f}", units = fahrenheit))
        assertEquals("4", interpolate("{current.uv_index}"))
    }

    @Test
    fun `unknown or unavailable placeholders stay literal`() {
        assertEquals("hi {typo.temp} !", interpolate("hi {typo.temp} !"))
        val noAq = report.copy(airQuality = null)
        assertEquals(
            "{current.aqi_index}",
            RuleMessages.interpolate("{current.aqi_index}", rule(), 0.0, null, noAq, now, metric)
        )
    }

    @Test
    fun `plain text passes through untouched`() {
        assertEquals("Porta l'ombrello ☔", interpolate("Porta l'ombrello ☔"))
    }
}
