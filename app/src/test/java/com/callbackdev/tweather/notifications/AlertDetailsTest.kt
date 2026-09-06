package com.callbackdev.tweather.notifications

import com.callbackdev.tweather.domain.model.HourlyForecast
import com.callbackdev.tweather.domain.model.WeatherCondition
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window under the expanded notification: everything it prints is read off the
 * same hours the engine judged, so the arithmetic is worth a table (Fase 25).
 */
class AlertDetailsTest {

    private val day = LocalDateTime.of(2026, 9, 4, 0, 0)

    /** [codes] is one WMO code per hour from 12:00, [rain] its chance, [temp] its degrees. */
    private fun hours(
        codes: List<Int>,
        rain: List<Int> = codes.map { 0 },
        temp: List<Double> = codes.map { 20.0 }
    ): List<HourlyForecast> = codes.indices.map { i ->
        HourlyForecast(
            time = day.withHour(12).plusHours(i.toLong()),
            tempC = temp[i],
            condition = WeatherCondition(codes[i], "x", "x"),
            precipChancePct = rain[i],
            cloudCoverPct = 0
        )
    }

    private fun at(hour: Int) = day.withHour(hour)

    @Test
    fun `a severe run is the consecutive stretch around the alert's hour`() {
        // 12 13 14 15 16 17 → clear, clear, thunder, thunder, thunder, clear
        val window = AlertDetails.severeWindow(
            hours(
                codes = listOf(0, 0, 95, 95, 96, 0),
                rain = listOf(0, 10, 80, 90, 70, 20),
                temp = listOf(26.0, 27.0, 25.0, 22.0, 19.0, 18.0)
            ),
            at = at(14)
        )!!
        assertEquals(at(14), window.start)
        assertEquals(at(16), window.end)
        assertEquals(90, window.peakPrecipPct)
        assertEquals(at(15), window.peakPrecipAt)
        assertEquals(19.0, window.lowC, 0.001)
        assertEquals(25.0, window.highC, 0.001)
        assertFalse(window.openEnded)
        assertFalse(window.singleHour)
    }

    @Test
    fun `the run extends backwards too - the alert names its first hour, not the window's`() {
        // The engine's alert is anchored at the first severe hour it FINDS from now,
        // which can sit mid-storm when the earlier hours are already in the past.
        val window = AlertDetails.severeWindow(
            hours(codes = listOf(95, 95, 95, 0)),
            at = at(14)
        )!!
        assertEquals(at(12), window.start)
        assertEquals(at(14), window.end)
    }

    @Test
    fun `a run that reaches the end of the forecast is open-ended`() {
        val window = AlertDetails.severeWindow(hours(codes = listOf(0, 95, 95)), at = at(13))!!
        assertEquals(at(13), window.start)
        assertTrue(window.openEnded)
    }

    @Test
    fun `one hour on its own is one hour, not a range`() {
        val window = AlertDetails.severeWindow(hours(codes = listOf(0, 95, 0)), at = at(13))!!
        assertTrue(window.singleHour)
        assertFalse(window.openEnded)
        assertEquals(window.start, window.end)
    }

    @Test
    fun `rain uses the threshold, not the weather code`() {
        val window = AlertDetails.rainWindow(
            hours(codes = listOf(61, 61, 61, 61), rain = listOf(40, 70, 85, 30)),
            at = at(13),
            thresholdPct = 70
        )!!
        assertEquals(at(13), window.start)
        assertEquals(at(14), window.end)
        assertEquals(85, window.peakPrecipPct)
    }

    @Test
    fun `an hour the forecast no longer carries has no window`() {
        // A cached report outlives the alert built from it: the honest answer is none,
        // and the object is then written without its `window` node at all.
        assertNull(AlertDetails.severeWindow(hours(codes = listOf(95, 95)), at = at(9)))
        assertNull(AlertDetails.severeWindow(emptyList(), at = at(12)))
    }

    @Test
    fun `an hour that does not hold has no window`() {
        assertNull(AlertDetails.severeWindow(hours(codes = listOf(0, 0)), at = at(12)))
        assertNull(
            AlertDetails.rainWindow(
                hours(codes = listOf(61, 61), rain = listOf(10, 20)),
                at = at(12)
            )
        )
    }
}
