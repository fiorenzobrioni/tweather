package com.callbackdev.tweather.ui.logs

import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.data.UnitSettings
import com.callbackdev.tweather.data.WindSpeedUnit
import com.callbackdev.tweather.data.local.SnapshotDiff
import com.callbackdev.tweather.data.local.SnapshotDiff.Line
import com.callbackdev.tweather.data.local.SnapshotDiff.Type
import com.callbackdev.tweather.data.local.WeatherSnapshots
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The render-time half of both diff files (Fase 28). Room stores one canonical
 * snapshot — English, Celsius, km/h — so what is COMPARED never moves; everything
 * the reader's settings touch is applied here.
 */
class DiffValuesTest {

    private val metric = UnitSettings()
    private val imperial = UnitSettings(TemperatureUnit.FAHRENHEIT, WindSpeedUnit.MPH)

    @Test
    fun `metric leaves the stored strings exactly as they are`() {
        val lines = listOf(
            Line(Type.CONTEXT, "current.temp_c", "18.5"),
            Line(Type.REMOVED, "current.wind_kph", "24.0"),
            Line(Type.ADDED, "current.wind_kph", "31.0"),
            Line(Type.CONTEXT, "2026-08-18.high_c", "27.4")
        )

        assertEquals(lines, lines.rendered(metric) { it })
    }

    @Test
    fun `a temperature changes value AND key, like weather_data json does`() {
        val rendered = listOf(
            Line(Type.REMOVED, "current.temp_c", "18.5"),
            Line(Type.ADDED, "current.temp_c", "21.0"),
            Line(Type.CONTEXT, "current.feels_like_c", "17.2")
        ).rendered(imperial) { it }

        assertEquals(
            listOf(
                Line(Type.REMOVED, "current.temp_f", "65.3"),
                Line(Type.ADDED, "current.temp_f", "69.8"),
                Line(Type.CONTEXT, "current.feels_like_f", "63.0")
            ),
            rendered
        )
    }

    @Test
    fun `the forecast file renames its bare keys the same way`() {
        val rendered = listOf(
            Line(Type.REMOVED, "high_c", "31.0"),
            Line(Type.ADDED, "high_c", "27.4"),
            Line(Type.CONTEXT, "low_c", "12.0"),
            // no unit, no rename: probability is a number of points either way
            Line(Type.CONTEXT, "precip_pct", "70")
        ).rendered(imperial) { it }

        assertEquals(
            listOf(
                Line(Type.REMOVED, "high_f", "87.8"),
                Line(Type.ADDED, "high_f", "81.3"),
                Line(Type.CONTEXT, "low_f", "53.6"),
                Line(Type.CONTEXT, "precip_pct", "70")
            ),
            rendered
        )
    }

    @Test
    fun `wind converts, and the keys that only look like units do not`() {
        val rendered = listOf(
            Line(Type.CONTEXT, "current.wind_kph", "24.0"),
            Line(Type.CONTEXT, "current.wind_dir", "NW"),
            Line(Type.CONTEXT, "current.pressure_mb", "1015.2"),
            Line(Type.CONTEXT, "current.humidity_pct", "54"),
            Line(Type.CONTEXT, "current.uv_index", "4"),
            Line(Type.CONTEXT, "air_quality.aqi", "42")
        ).rendered(imperial) { it }

        assertEquals(
            listOf(
                Line(Type.CONTEXT, "current.wind_mph", "14.9"),
                Line(Type.CONTEXT, "current.wind_dir", "NW"),
                Line(Type.CONTEXT, "current.pressure_mb", "1015.2"),
                Line(Type.CONTEXT, "current.humidity_pct", "54"),
                Line(Type.CONTEXT, "current.uv_index", "4"),
                Line(Type.CONTEXT, "air_quality.aqi", "42")
            ),
            rendered
        )
    }

    @Test
    fun `null keeps its key renamed and stays null`() {
        val rendered = listOf(
            Line(Type.REMOVED, "current.temp_c", "18.5"),
            Line(Type.ADDED, "current.temp_c", WeatherSnapshots.NullValue)
        ).rendered(imperial) { it }

        assertEquals(
            listOf(
                Line(Type.REMOVED, "current.temp_f", "65.3"),
                Line(Type.ADDED, "current.temp_f", WeatherSnapshots.NullValue)
            ),
            rendered
        )
    }

    /**
     * 10.1 and 10.2 km/h are both 6.3 mph. The change is real in the store and
     * invisible in the file, and two identical `±` lines read as a bug.
     */
    @Test
    fun `a change that rounds away becomes the context line it has become`() {
        val rendered = listOf(
            Line(Type.CONTEXT, "current.status", "Clear ☀️"),
            Line(Type.REMOVED, "current.wind_kph", "10.1"),
            Line(Type.ADDED, "current.wind_kph", "10.2")
        ).rendered(imperial) { it }

        assertEquals(
            listOf(
                Line(Type.CONTEXT, "current.status", "Clear ☀️"),
                Line(Type.CONTEXT, "current.wind_mph", "6.3")
            ),
            rendered
        )
        // and in the unit it was stored in, the same change is still a change
        assertEquals(
            listOf(
                Line(Type.CONTEXT, "current.status", "Clear ☀️"),
                Line(Type.REMOVED, "current.wind_kph", "10.1"),
                Line(Type.ADDED, "current.wind_kph", "10.2")
            ),
            listOf(
                Line(Type.CONTEXT, "current.status", "Clear ☀️"),
                Line(Type.REMOVED, "current.wind_kph", "10.1"),
                Line(Type.ADDED, "current.wind_kph", "10.2")
            ).rendered(metric) { it }
        )
    }

    @Test
    fun `a key that left the file is still a removal, not half a pair`() {
        val rendered = listOf(
            Line(Type.CONTEXT, "current.temp_c", "18.5"),
            Line(Type.REMOVED, "air_quality.aqi", "42")
        ).rendered(metric) { it }

        assertEquals(Type.REMOVED, rendered.last().type)
        assertEquals(2, rendered.size)
    }

    @Test
    fun `values localize and keys do not, whatever the units`() {
        val shout = { value: String -> value.uppercase() }
        val rendered = listOf(
            Line(Type.REMOVED, "current.status", "Overcast ☁️"),
            Line(Type.ADDED, "current.status", "Rainy 🌧️"),
            Line(Type.CONTEXT, "astronomical.moon_phase", "Waxing Gibbous 🌔"),
            // a value that is not weather stays put, gate by key
            Line(Type.CONTEXT, "location", "Milan, Lombardy"),
            Line(Type.CONTEXT, "current.temp_c", "18.5")
        ).rendered(imperial, shout)

        assertEquals(
            listOf(
                Line(Type.REMOVED, "current.status", "OVERCAST ☁️"),
                Line(Type.ADDED, "current.status", "RAINY 🌧️"),
                Line(Type.CONTEXT, "astronomical.moon_phase", "WAXING GIBBOUS 🌔"),
                Line(Type.CONTEXT, "location", "Milan, Lombardy"),
                Line(Type.CONTEXT, "current.temp_f", "65.3")
            ),
            rendered
        )
    }

    @Test
    fun `an all-context diff is returned untouched`() {
        val lines = listOf(
            Line(Type.CONTEXT, "current.temp_c", "18.5"),
            Line(Type.CONTEXT, "current.wind_kph", "24.0")
        )
        val rendered: List<SnapshotDiff.Line> = lines.rendered(metric) { it }

        assertEquals(lines, rendered)
    }
}
