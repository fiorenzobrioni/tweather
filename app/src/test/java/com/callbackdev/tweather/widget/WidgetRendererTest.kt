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
        "current.precip_chance_pct" to "10",
        "current.uv_index" to "3.0",
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
        opacityPct: Int = 100,
        syncing: Boolean = false
    ): View = WidgetRenderer.render(context, content, palette, opacityPct, tier, syncing = syncing)
        .apply(context, FrameLayout(context))

    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()

    private fun View.visibility(id: Int): Int = findViewById<View>(id).visibility

    private fun View.textColor(id: Int): Int = findViewById<TextView>(id).currentTextColor

    /** Color of the token covering [index] — spans are the only carrier of per-token color. */
    private fun View.tokenColorAt(id: Int, index: Int): Int {
        val text = findViewById<TextView>(id).text as Spanned
        return text.getSpans(index, index + 1, ForegroundColorSpan::class.java)
            .single()
            .foregroundColor
    }

    @Test
    fun mediumBindsHeaderPromptAndFourBodyLines() {
        val view = inflate(content(WidgetTier.Terminal(4)), WidgetTier.Terminal(4))

        assertEquals(WidgetContentBuilder.HEADER, view.text(R.id.widget_title))
        assertEquals("sys@tweather:~$ get weather -current", view.text(R.id.widget_prompt))

        // the region is dropped on the narrow tiers — it would eat the city name
        assertEquals("Location: \"Milan\"", view.text(R.id.widget_line1))
        assertEquals("Temp: 21°C", view.text(R.id.widget_line2))
        assertEquals("Feels: 20°C", view.text(R.id.widget_line3))
        assertEquals("Status: \"Partly Cloudy\"", view.text(R.id.widget_line4))
        listOf(R.id.widget_line1, R.id.widget_line2, R.id.widget_line3, R.id.widget_line4)
            .forEach { assertEquals(View.VISIBLE, view.visibility(it)) }

        // The medium layout stops at 4 slots on purpose (renderer takes 4 lines).
        assertNull(view.findViewById<TextView>(R.id.widget_line5))
        assertEquals("⛅", view.text(R.id.widget_emoji))
    }

    @Test
    fun largeHidesTheSlotsWithoutAContentLine() {
        val sparse = mapOf("location" to "Milan, IT", "current.temp_c" to "21.4")
        val view = inflate(content(WidgetTier.Terminal(11), sparse), WidgetTier.Terminal(11))

        assertEquals(View.VISIBLE, view.visibility(R.id.widget_line1))
        assertEquals(View.VISIBLE, view.visibility(R.id.widget_line2))
        listOf(
            R.id.widget_line3, R.id.widget_line4, R.id.widget_line5, R.id.widget_line6,
            R.id.widget_line7, R.id.widget_line8, R.id.widget_line9,
            R.id.widget_line10, R.id.widget_line11
        ).forEach { assertEquals(View.GONE, view.visibility(it)) }
    }

    @Test
    fun largeFillsEverySlotWhenTheSnapshotIsComplete() {
        val view = inflate(
            content(WidgetTier.Terminal(11), timestampEpochSeconds = 1_700_000_000L),
            WidgetTier.Terminal(11)
        )

        // Feels sits next to Temp, so the tail shifts down by one
        assertEquals("Feels: 20°C", view.text(R.id.widget_line3))
        assertEquals("Humidity: 58%", view.text(R.id.widget_line5))
        assertEquals("Rain: 10%", view.text(R.id.widget_line6))
        assertEquals("UV: 3", view.text(R.id.widget_line7))
        assertEquals("Wind: 12 km/h NW", view.text(R.id.widget_line8))
        assertEquals("AQI: 34", view.text(R.id.widget_line9))
        assertEquals("Sun: 06:12 → 20:35", view.text(R.id.widget_line10))
        assertEquals("# last_sync: 22:13", view.text(R.id.widget_line11))
        assertEquals(View.VISIBLE, view.visibility(R.id.widget_line11))
    }

    @Test
    fun tokenRolesTravelAsForegroundColorSpans() {
        val view = inflate(content(WidgetTier.Terminal(4)), WidgetTier.Terminal(4))

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
        val faded = inflate(content(WidgetTier.Terminal(4)), WidgetTier.Terminal(4), opacityPct = 70)
        assertEquals(178, faded.findViewById<ImageView>(R.id.widget_bg_fill).imageAlpha)
        // The 1px frame must stay crisp whatever the user picked for the fill.
        assertEquals(255, faded.findViewById<ImageView>(R.id.widget_bg_border).imageAlpha)

        val opaque = inflate(content(WidgetTier.Terminal(4)), WidgetTier.Terminal(4), opacityPct = 100)
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
        val root = inflate(content(WidgetTier.Terminal(4)), WidgetTier.Terminal(4), opacityPct)
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
        val view = inflate(content(WidgetTier.Terminal(4), snapshot = null), WidgetTier.Terminal(4))

        assertTrue(view.text(R.id.widget_line1).startsWith("# no data yet"))
        assertEquals(palette.comment, view.tokenColorAt(R.id.widget_line1, 0))
        assertEquals(View.GONE, view.visibility(R.id.widget_line2))
        // No weather, no glyph: the emoji slot collapses instead of showing "".
        assertEquals(View.GONE, view.visibility(R.id.widget_emoji))
    }

    /**
     * The tap's acknowledgment has to reach the sizes people actually place, and
     * `# last_sync` is last in the transcript — the medium tier cuts it. So the glyph
     * carries it, on every tier, and comes back on the next repaint.
     */
    @Test
    fun theRefreshGlyphWearsTheTapOnEveryTier() {
        val idle = context.getString(R.string.widget_refresh_glyph)
        val busy = context.getString(R.string.widget_refresh_glyph_busy)

        listOf(WidgetTier.Small, WidgetTier.Terminal(4), WidgetTier.Terminal(11)).forEach { tier ->
            val waiting = inflate(content(tier), tier, syncing = true)
            assertEquals(busy, waiting.text(R.id.widget_refresh))
            assertEquals(palette.comment, waiting.textColor(R.id.widget_refresh))
            assertEquals(
                context.getString(R.string.cd_widget_refresh_busy),
                waiting.findViewById<View>(R.id.widget_refresh).contentDescription
            )

            val settled = inflate(content(tier), tier)
            assertEquals(idle, settled.text(R.id.widget_refresh))
            assertEquals(palette.plain, settled.textColor(R.id.widget_refresh))
            assertEquals(
                context.getString(R.string.cd_widget_refresh),
                settled.findViewById<View>(R.id.widget_refresh).contentDescription
            )
        }
    }

    @Test
    fun smallBindsTemperatureAndCity() {
        val view = inflate(content(WidgetTier.Small), WidgetTier.Small)

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

            val visibleLines = (if (tier == WidgetTier.Small) {
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

    /**
     * A rung that is taller than the transcript actually needs is not harmless: the
     * launcher only picks a rung that fits, so every wasted dp is a line the user
     * paid for in screen space and did not get. Binary-search the real minimum and
     * hold the promised height close to it.
     */
    @Test
    fun noRungClaimsMoreHeightThanItsTranscriptNeeds() {
        val density = context.resources.displayMetrics.density
        val widthPx = (200 * density).toInt()

        val slack = WidgetRenderer.breakpoints()
            .filterKeys { it is WidgetTier.Terminal }
            .toSortedMap(compareBy { WidgetContentBuilder.bodyLineBudget(it) })
            .map { (tier, size) ->
                val lines = WidgetContentBuilder.bodyLineBudget(tier)
                var low = 0
                var high = (500 * density).toInt()
                while (low < high) {
                    val mid = (low + high) / 2
                    if (fitsAt(tier, widthPx, mid, lines)) high = mid else low = mid + 1
                }
                Triple(lines, low / density, size.height)
            }

        // Never below the measured minimum, and never wildly above it: the deliberate
        // safety margin for the device's own font is worth ~2dp a line, the bug this
        // test was written for was worth 30.
        val wrong = slack.filter { (lines, needed, promised) ->
            promised < needed || promised - needed > 6f + 2.5f * lines
        }
        assertTrue(
            "rungs out of step (lines, needed dp, promised dp): $wrong — all: $slack",
            wrong.isEmpty()
        )
    }

    /** True when every bound line is fully inside a widget of [heightPx]. */
    private fun fitsAt(tier: WidgetTier, widthPx: Int, heightPx: Int, lines: Int): Boolean {
        val root = inflate(content(tier, timestampEpochSeconds = 1_700_000_000L), tier)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, widthPx, heightPx)
        return (1..lines).all { slot ->
            val id = context.resources.getIdentifier(
                "widget_line$slot", "id", context.packageName
            )
            val view = root.findViewById<View>(id) ?: return@all true
            if (view.visibility != View.VISIBLE) return@all true
            val bottom = IntArray(2).also { view.getLocationInWindow(it) }[1] + view.height
            view.height > 0 && bottom <= heightPx
        }
    }

    @Test
    fun theLadderHasARungPerTranscriptLine() {
        val rungs = WidgetRenderer.breakpoints()
            .filterKeys { it is WidgetTier.Terminal }
            .mapKeys { (tier, _) -> (tier as WidgetTier.Terminal).lines }

        // a gap would strand a widget on a shorter transcript than it has room for
        assertEquals((4..11).toList(), rungs.keys.sorted())

        // every rung must be reachable: equal heights would make one of them dead
        val heights = rungs.toSortedMap().values.map { it.height }
        assertEquals(heights.sorted(), heights)
        assertEquals(heights.distinct(), heights)
    }

    /**
     * With both controls in the title bar nothing floats over the body any more, so
     * no line owes width to a glyph — the reason the values used to truncate.
     */
    @Test
    fun everyBodyLineRunsTheFullWidth() {
        val view = inflate(content(WidgetTier.Terminal(4)), WidgetTier.Terminal(4))

        listOf(R.id.widget_line1, R.id.widget_line2, R.id.widget_line3, R.id.widget_line4)
            .forEach { id ->
                val params = view.findViewById<View>(id).layoutParams
                        as ViewGroup.MarginLayoutParams
                assertEquals("line $id still reserves a gutter", 0, params.marginEnd)
            }
    }

    @Test
    fun layoutForMapsEveryTier() {
        assertEquals(R.layout.widget_tweather_small, WidgetRenderer.layoutFor(WidgetTier.Small))
        assertEquals(R.layout.widget_tweather_medium, WidgetRenderer.layoutFor(WidgetTier.Terminal(4)))
        // EXTENDED borrows the large layout: same slots, fewer of them filled
        assertEquals(R.layout.widget_tweather_large, WidgetRenderer.layoutFor(WidgetTier.Terminal(5)))
        assertEquals(R.layout.widget_tweather_large, WidgetRenderer.layoutFor(WidgetTier.Terminal(11)))
    }
}
