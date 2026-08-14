package com.callbackdev.tweather.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.tweather.R
import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.data.WindSpeedUnit
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The renderer is only observable through the view tree the launcher would get,
 * so every test inflates the RemoteViews for real (`apply`) and asserts on the
 * resulting Views — that also proves the layouts stay RemoteViews-compatible.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetRendererTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val palette = widgetPalette("Obsidian")

    /** Same key set WeatherSnapshots.flatten produces: canonical English + Celsius/kph. */
    private val fullSnapshot = mapOf(
        "location" to "Milan, IT",
        "current.status" to "Partly Cloudy ⛅",
        "current.temp_c" to "21.4",
        "current.humidity_pct" to "58",
        "current.feels_like_c" to "20.1",
        "current.wind_kph" to "12.0",
        "current.wind_dir" to "NW",
        "air_quality.aqi" to "34",
        "astronomical.sunrise" to "06:12",
        "astronomical.sunset" to "20:35"
    )

    private fun content(
        tier: WidgetTier,
        snapshot: Map<String, String>? = fullSnapshot,
        timestampEpochSeconds: Long? = null
    ) = WidgetContentBuilder.build(
        snapshot = snapshot,
        timestampEpochSeconds = timestampEpochSeconds,
        temperature = TemperatureUnit.CELSIUS,
        windSpeed = WindSpeedUnit.KMH,
        tier = tier,
        zone = ZoneId.of("UTC")
    )

    private fun inflate(
        content: WidgetContent,
        tier: WidgetTier,
        opacityPct: Int = 100
    ): View = WidgetRenderer.render(context, content, palette, opacityPct, tier)
        .apply(context, FrameLayout(context))

    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()

    private fun View.visibility(id: Int): Int = findViewById<View>(id).visibility

    /** Color of the token covering [index] — spans are the only carrier of per-token color. */
    private fun View.tokenColorAt(id: Int, index: Int): Int {
        val text = findViewById<TextView>(id).text as Spanned
        return text.getSpans(index, index + 1, ForegroundColorSpan::class.java)
            .single()
            .foregroundColor
    }

    @Test
    fun mediumBindsHeaderPromptAndFourBodyLines() {
        val view = inflate(content(WidgetTier.MEDIUM), WidgetTier.MEDIUM)

        assertEquals(WidgetContentBuilder.HEADER, view.text(R.id.widget_title))
        assertEquals("sys@tweather:~$ get weather -current", view.text(R.id.widget_prompt))

        // the region is dropped on the narrow tiers — it would eat the city name
        assertEquals("Location: \"Milan\"", view.text(R.id.widget_line1))
        assertEquals("Temp: 21°C", view.text(R.id.widget_line2))
        assertEquals("Status: \"Partly Cloudy\"", view.text(R.id.widget_line3))
        assertEquals("Humidity: 58%", view.text(R.id.widget_line4))
        listOf(R.id.widget_line1, R.id.widget_line2, R.id.widget_line3, R.id.widget_line4)
            .forEach { assertEquals(View.VISIBLE, view.visibility(it)) }

        // The medium layout stops at 4 slots on purpose (renderer takes 4 lines).
        assertNull(view.findViewById<TextView>(R.id.widget_line5))
        assertEquals("⛅", view.text(R.id.widget_emoji))
    }

    @Test
    fun largeHidesTheSlotsWithoutAContentLine() {
        val sparse = mapOf("location" to "Milan, IT", "current.temp_c" to "21.4")
        val view = inflate(content(WidgetTier.LARGE, sparse), WidgetTier.LARGE)

        assertEquals(View.VISIBLE, view.visibility(R.id.widget_line1))
        assertEquals(View.VISIBLE, view.visibility(R.id.widget_line2))
        listOf(
            R.id.widget_line3, R.id.widget_line4, R.id.widget_line5, R.id.widget_line6,
            R.id.widget_line7, R.id.widget_line8, R.id.widget_line9
        ).forEach { assertEquals(View.GONE, view.visibility(it)) }
    }

    @Test
    fun largeFillsEverySlotWhenTheSnapshotIsComplete() {
        val view = inflate(
            content(WidgetTier.LARGE, timestampEpochSeconds = 1_700_000_000L),
            WidgetTier.LARGE
        )

        // Feels sits next to Temp, so the tail shifts down by one
        assertEquals("Feels: 20°C", view.text(R.id.widget_line3))
        assertEquals("Humidity: 58%", view.text(R.id.widget_line5))
        assertEquals("Wind: 12 km/h NW", view.text(R.id.widget_line6))
        assertEquals("AQI: 34", view.text(R.id.widget_line7))
        assertEquals("Sun: 06:12 → 20:35", view.text(R.id.widget_line8))
        assertEquals("# last_sync: 22:13", view.text(R.id.widget_line9))
        assertEquals(View.VISIBLE, view.visibility(R.id.widget_line9))
    }

    @Test
    fun tokenRolesTravelAsForegroundColorSpans() {
        val view = inflate(content(WidgetTier.MEDIUM), WidgetTier.MEDIUM)

        val prompt = view.findViewById<TextView>(R.id.widget_prompt).text as Spanned
        val promptSpans = prompt.getSpans(0, prompt.length, ForegroundColorSpan::class.java)
        assertEquals(4, promptSpans.size)
        // "sys@tweather" is the PROMPT token; the rest of the line is plain/dim.
        assertEquals(palette.prompt, view.tokenColorAt(R.id.widget_prompt, 0))
        assertEquals(palette.plain, view.tokenColorAt(R.id.widget_prompt, "sys@tweather".length))

        // `Location: "Milan, IT"` — key, separator, string value.
        assertEquals(palette.key, view.tokenColorAt(R.id.widget_line1, 0))
        assertEquals(palette.string, view.tokenColorAt(R.id.widget_line1, "Location: ".length))
        assertEquals(palette.number, view.tokenColorAt(R.id.widget_line2, "Temp: ".length))
    }

    @Test
    fun opacityFadesOnlyTheFill() {
        val faded = inflate(content(WidgetTier.MEDIUM), WidgetTier.MEDIUM, opacityPct = 70)
        assertEquals(178, faded.findViewById<ImageView>(R.id.widget_bg_fill).imageAlpha)
        // The 1px frame must stay crisp whatever the user picked for the fill.
        assertEquals(255, faded.findViewById<ImageView>(R.id.widget_bg_border).imageAlpha)

        val opaque = inflate(content(WidgetTier.MEDIUM), WidgetTier.MEDIUM, opacityPct = 100)
        assertEquals(255, opaque.findViewById<ImageView>(R.id.widget_bg_fill).imageAlpha)
    }

    /**
     * `imageAlpha` alone proves nothing: what reaches the launcher is pixels. These
     * two draw the background layers for real, because a stroke-only shape and a
     * color filter interact in a way the setter-level assertions cannot see.
     */
    @Test
    fun theFadedFillActuallyDrawsSemiTransparentPixels() {
        val fill = layeredBackground(opacityPct = 50).first

        val pixel = fill.centerPixel()
        assertEquals("fill must honour the opacity setting", 128, Color.alpha(pixel).toNearest(128))
        // premultiplied storage costs a couple of levels per channel on the way back
        assertNear(0x10, Color.red(pixel))
        assertNear(0x14, Color.green(pixel))
        assertNear(0x1A, Color.blue(pixel))
    }

    @Test
    fun theBorderLayerPaintsNothingButItsFrame() {
        val border = layeredBackground(opacityPct = 50).second

        // A filled border layer would sit opaque on top of the fill and make the
        // opacity setting look broken, whatever alpha the fill carries.
        assertEquals(
            "the frame layer must stay hollow, or it hides the fill underneath",
            0,
            Color.alpha(border.centerPixel())
        )

        // ...and hollow must not mean absent: the frame itself still has to be drawn,
        // opaque and in the theme's border color, however faded the fill is.
        val edge = (0..2).map { border.getPixel(border.width / 2, it) }
            .maxBy { Color.alpha(it) }
        assertTrue("the frame edge is missing", Color.alpha(edge) > 100)
        assertNear(0x30, Color.red(edge))
        assertNear(0x36, Color.green(edge))
        assertNear(0x3D, Color.blue(edge))
    }

    /** Both background layers of a laid-out MEDIUM widget, each drawn on its own. */
    private fun layeredBackground(opacityPct: Int): Pair<Bitmap, Bitmap> {
        val root = inflate(content(WidgetTier.MEDIUM), WidgetTier.MEDIUM, opacityPct)
        val size = (200 * context.resources.displayMetrics.density).toInt()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, size, size)
        return root.drawAlone(R.id.widget_bg_fill) to root.drawAlone(R.id.widget_bg_border)
    }

    private fun View.drawAlone(id: Int): Bitmap {
        val target = findViewById<View>(id)
        return Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)
            .also { target.draw(Canvas(it)) }
    }

    private fun Bitmap.centerPixel(): Int = getPixel(width / 2, height / 2)

    /** Alpha maths rounds; anything within a step of the target is the right value. */
    private fun Int.toNearest(target: Int): Int = if (this in (target - 2)..(target + 2)) target else this

    private fun assertNear(expected: Int, actual: Int) =
        assertTrue("expected ~$expected but was $actual", actual in (expected - 3)..(expected + 3))

    @Test
    fun emptyStateRendersTheNoDataComment() {
        val view = inflate(content(WidgetTier.MEDIUM, snapshot = null), WidgetTier.MEDIUM)

        assertTrue(view.text(R.id.widget_line1).startsWith("# no data yet"))
        assertEquals(palette.comment, view.tokenColorAt(R.id.widget_line1, 0))
        assertEquals(View.GONE, view.visibility(R.id.widget_line2))
        // No weather, no glyph: the emoji slot collapses instead of showing "".
        assertEquals(View.GONE, view.visibility(R.id.widget_emoji))
    }

    @Test
    fun smallBindsTemperatureAndCity() {
        val view = inflate(content(WidgetTier.SMALL), WidgetTier.SMALL)

        assertEquals("21°C", view.text(R.id.widget_temp))
        assertEquals("Milan", view.text(R.id.widget_location))
        assertEquals(palette.number, view.tokenColorAt(R.id.widget_temp, 0))
        assertEquals(palette.plain, view.tokenColorAt(R.id.widget_location, 0))
        assertEquals("⛅", view.text(R.id.widget_emoji))
    }

    /**
     * A sizes-map key promises the layout FITS at that size — the host clips silently
     * otherwise, so the breakpoints get measured, not trusted.
     */
    @Test
    fun everyTierFitsInsideItsOwnBreakpoint() {
        val density = context.resources.displayMetrics.density

        WidgetRenderer.breakpoints().forEach { (tier, size) ->
            val root = inflate(content(tier, timestampEpochSeconds = 1_700_000_000L), tier)
            val widthPx = (size.width * density).toInt()
            val heightPx = (size.height * density).toInt()
            root.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, widthPx, heightPx)

            val visibleLines = (if (tier == WidgetTier.SMALL) {
                listOf(R.id.widget_temp, R.id.widget_location)
            } else {
                listOf(
                    R.id.widget_title, R.id.widget_prompt,
                    R.id.widget_line1, R.id.widget_line2, R.id.widget_line3, R.id.widget_line4
                )
            }).mapNotNull { root.findViewById<View>(it) }.filter { it.visibility == View.VISIBLE }

            visibleLines.forEach { line ->
                val bottom = IntArray(2).also { line.getLocationInWindow(it) }[1] + line.height
                assertTrue(
                    "$tier clips a line at its own ${size.width}x${size.height}dp breakpoint",
                    bottom <= heightPx
                )
            }
        }
    }

    @Test
    fun breakpointHeightsFollowTheSlotCount() {
        // chrome (34 header + 1 divider + 38 prompt) + lines * 23 + 12 body padding
        assertEquals(177f, WidgetRenderer.minHeightDp(4), 0.01f)
        assertEquals(200f, WidgetRenderer.minHeightDp(5), 0.01f)
        assertEquals(292f, WidgetRenderer.minHeightDp(9), 0.01f)

        // every rung must be reachable: equal heights would make one tier dead
        val heights = WidgetTier.entries.map { WidgetRenderer.breakpoints().getValue(it).height }
        assertEquals(heights.sorted(), heights)
        assertEquals(heights.distinct(), heights)
    }

    /**
     * The ↻ overlay is anchored bottom-right, so only the bottom line has to leave it
     * room; charging every line for it is what used to truncate the values.
     */
    @Test
    fun onlyTheBottomLineReservesTheRefreshGutter() {
        val view = inflate(content(WidgetTier.MEDIUM), WidgetTier.MEDIUM)

        fun endMarginOf(id: Int) =
            (view.findViewById<View>(id).layoutParams as ViewGroup.MarginLayoutParams).marginEnd

        assertEquals(0, endMarginOf(R.id.widget_line1))
        assertEquals(0, endMarginOf(R.id.widget_line3))
        assertTrue("the bottom line must clear the ↻", endMarginOf(R.id.widget_line4) > 0)
    }

    @Test
    fun layoutForMapsEveryTier() {
        assertEquals(R.layout.widget_tweather_small, WidgetRenderer.layoutFor(WidgetTier.SMALL))
        assertEquals(R.layout.widget_tweather_medium, WidgetRenderer.layoutFor(WidgetTier.MEDIUM))
        // EXTENDED borrows the large layout: same slots, fewer of them filled
        assertEquals(R.layout.widget_tweather_large, WidgetRenderer.layoutFor(WidgetTier.EXTENDED))
        assertEquals(R.layout.widget_tweather_large, WidgetRenderer.layoutFor(WidgetTier.LARGE))
    }
}
