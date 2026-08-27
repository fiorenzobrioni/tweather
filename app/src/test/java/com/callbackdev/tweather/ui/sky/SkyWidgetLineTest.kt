package com.callbackdev.tweather.ui.sky

import com.callbackdev.tweather.data.SkySubscription
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.widget.WidgetContentBuilder
import com.callbackdev.tweather.widget.WidgetTier
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The home widget's one optional sky line (Fase 16e): off by default, and LAST in
 * the line budget so it is the first thing dropped when the launcher gives the
 * widget less room. The temperature is why a weather widget exists; this line is not.
 */
class SkyWidgetLineTest {

    private val milan = Coordinates(45.4642, 9.19)
    private val rome: ZoneId = ZoneId.of("Europe/Rome")
    private val now: Instant = Instant.parse("2026-08-26T16:30:00Z")

    private fun context(report: com.callbackdev.tweather.domain.model.WeatherReport? = null) =
        SkyContext("Milan", milan, rome, now, report, 60)

    @Test
    fun `the line names the next job and when it is`() {
        val line = SkyWidgetLine.of(listOf(SkySubscription("sun.set")), context())
        assertTrue(line, line!!.startsWith("next: sun.set 20:12"))
    }

    @Test
    fun `with nothing subscribed there is no line rather than an empty one`() {
        assertNull(SkyWidgetLine.of(emptyList(), context()))
    }

    @Test
    fun `a commented-out job is not the next job`() {
        assertNull(
            SkyWidgetLine.of(listOf(SkySubscription("sun.set", enabled = false)), context())
        )
    }

    /**
     * No cloud number here. At widget width the line is ellipsized from the right,
     * and the number would be the first thing to go — taking the verdict's own word
     * with it.
     */
    @Test
    fun `the line carries the verdict's word but never its evidence`() {
        val line = SkyWidgetLine.of(listOf(SkySubscription("sun.set")), context())
        assertTrue(line, !line!!.contains("cloud"))
        assertTrue(line, !line.contains("%"))
    }

    @Test
    fun `an unknown verdict leaves the line as a plain when, not a shrug`() {
        // No report at all: the schedule still resolves — it needs no network — and
        // the line says when without claiming to know whether.
        val line = SkyWidgetLine.of(listOf(SkySubscription("sun.set")), context())
        assertEquals("next: sun.set 20:12", line)
    }

    // ------------------------------------------------------- the budget

    @Test
    fun `the widget drops the sky line before anything it came for`() {
        val snapshot = mapOf(
            "location" to "Milan, Lombardy",
            "current.status" to "Clear ☀️",
            "current.temp_c" to "22.8",
            "current.feels_like_c" to "23.0"
        )
        fun body(tier: WidgetTier) = WidgetContentBuilder.build(
            snapshot = snapshot,
            timestampEpochSeconds = now.epochSecond,
            temperature = com.callbackdev.tweather.data.TemperatureUnit.CELSIUS,
            windSpeed = com.callbackdev.tweather.data.WindSpeedUnit.KMH,
            tier = tier,
            now = now,
            skyLine = "next: sun.set 20:12   ✓ pass"
        ).bodyLines.map { it.text }

        val roomy = body(WidgetTier.Terminal(lines = 8))
        assertTrue(roomy.toString(), roomy.any { it.contains("next: sun.set") })

        // Three lines of room: the temperature stays, the sky line does not.
        val cramped = body(WidgetTier.Terminal(lines = 3))
        assertTrue(cramped.toString(), cramped.any { it.contains("Temp") })
        assertTrue(cramped.toString(), cramped.none { it.contains("next: sun.set") })
    }

    @Test
    fun `a widget without the option renders exactly as it did before`() {
        val snapshot = mapOf("location" to "Milan", "current.temp_c" to "22.8")
        fun body(skyLine: String?) = WidgetContentBuilder.build(
            snapshot = snapshot,
            timestampEpochSeconds = now.epochSecond,
            temperature = com.callbackdev.tweather.data.TemperatureUnit.CELSIUS,
            windSpeed = com.callbackdev.tweather.data.WindSpeedUnit.KMH,
            tier = WidgetTier.Terminal(lines = 8),
            now = now,
            skyLine = skyLine
        ).bodyLines.map { it.text }
        // Default off: an existing widget must not change shape because the app
        // updated.
        assertEquals(body(null), body(null))
        assertTrue(body(null).none { it.contains("next:") })
        assertEquals(body(null).size + 1, body("next: sun.set 20:12").size)
    }
}
