package com.callbackdev.tweather.widget

import com.callbackdev.tweather.data.DefaultUpdateFrequencyMin
import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.data.WindSpeedUnit
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure snapshot → terminal transcript mapping behind every widget tier. */
class WidgetContentBuilderTest {

    /** Shape and values of a real WeatherSnapshots.flatten map. */
    private val sample = mapOf(
        "location" to "Milano, Lombardia",
        "current.status" to "Partly Cloudy ⛅",
        "current.temp_c" to "18.5",
        "current.feels_like_c" to "16.0",
        "current.humidity_pct" to "65",
        "current.pressure_mb" to "1013.0",
        "current.uv_index" to "3.0",
        "current.wind_kph" to "24.0",
        "current.wind_dir" to "NE",
        "current.precip_chance_pct" to "10",
        "air_quality.aqi" to "42",
        "astronomical.sunrise" to "07:42",
        "astronomical.sunset" to "18:15",
        "astronomical.moon_phase" to "Waxing Gibbous 🌔"
    )

    private val rome = ZoneId.of("Europe/Rome")

    // 12:00 UTC on a day Rome is still on CEST (UTC+2): a wrong zone shows up as 12:00.
    private val syncEpoch = LocalDateTime.of(2023, 10, 27, 12, 0).toEpochSecond(ZoneOffset.UTC)

    private fun build(
        snapshot: Map<String, String>? = sample,
        tier: WidgetTier = WidgetTier.Terminal(4),
        temperature: TemperatureUnit = TemperatureUnit.CELSIUS,
        windSpeed: WindSpeedUnit = WindSpeedUnit.KMH,
        timestampEpochSeconds: Long? = null,
        translate: (String) -> String = { it },
        zone: ZoneId = ZoneOffset.UTC,
        updateFrequencyMin: Int = DefaultUpdateFrequencyMin,
        now: Instant? = null
    ) = WidgetContentBuilder.build(
        snapshot, timestampEpochSeconds, temperature, windSpeed, tier, translate, zone,
        updateFrequencyMin, now
    )

    private fun WidgetContent.keys(): List<String> = bodyLines.map { it.tokens.first().text }

    private fun WidgetContent.line(key: String): TerminalLine? =
        bodyLines.find { it.tokens.first().text == key }

    /** Value token of a `key: value` line — index 2 is past the KEY and the `": "` separator. */
    private fun WidgetContent.value(key: String): String? = line(key)?.tokens?.get(2)?.text

    private val TerminalLine.texts: List<String> get() = tokens.map { it.text }
    private val TerminalLine.roles: List<TokenRole> get() = tokens.map { it.role }

    // --- medium tier ---

    @Test
    fun `medium tier renders exactly the four core lines as key colon value tokens`() {
        // the timestamp is passed on purpose: only LARGE spends a line on last_sync
        val content = build(timestampEpochSeconds = syncEpoch)

        assertEquals(WidgetContentBuilder.HEADER, content.headerTitle)
        assertEquals(
            listOf(
                TerminalLine(
                    listOf(
                        WidgetToken("Location", TokenRole.KEY),
                        WidgetToken(": ", TokenRole.PLAIN),
                        WidgetToken("\"Milano\"", TokenRole.STRING)
                    )
                ),
                TerminalLine(
                    listOf(
                        WidgetToken("Temp", TokenRole.KEY),
                        WidgetToken(": ", TokenRole.PLAIN),
                        WidgetToken("19°C", TokenRole.NUMBER)
                    )
                ),
                TerminalLine(
                    listOf(
                        WidgetToken("Feels", TokenRole.KEY),
                        WidgetToken(": ", TokenRole.PLAIN),
                        WidgetToken("16°C", TokenRole.NUMBER)
                    )
                ),
                TerminalLine(
                    listOf(
                        WidgetToken("Status", TokenRole.KEY),
                        WidgetToken(": ", TokenRole.PLAIN),
                        WidgetToken("\"Partly Cloudy\"", TokenRole.STRING)
                    )
                )
            ),
            content.bodyLines
        )
    }

    @Test
    fun `the prompt line is a terminal transcript with the command dimmed`() {
        val prompt = build().promptLine

        assertEquals(listOf("sys@tweather", ":~", "\$ ", "get weather -current"), prompt.texts)
        assertEquals(
            listOf(TokenRole.PROMPT, TokenRole.PLAIN, TokenRole.PLAIN, TokenRole.DIM),
            prompt.roles
        )
        assertEquals("sys@tweather:~\$ get weather -current", prompt.text)
    }

    // --- units ---

    @Test
    fun `celsius and kmh keep the snapshot units untouched`() {
        val content = build(tier = WidgetTier.Terminal(11))

        assertEquals("19°C", content.value("Temp"))
        assertEquals("16°C", content.value("Feels"))
        assertEquals("24 km/h NE", content.value("Wind"))
    }

    @Test
    fun `fahrenheit and mph convert temperatures and wind`() {
        val content = build(
            tier = WidgetTier.Terminal(11),
            temperature = TemperatureUnit.FAHRENHEIT,
            windSpeed = WindSpeedUnit.MPH
        )

        assertEquals("65°F", content.value("Temp")) // 18.5 °C
        assertEquals("61°F", content.value("Feels")) // 16.0 °C
        assertEquals("15 mph NE", content.value("Wind")) // 24.0 kph
        assertEquals("65°F", content.smallTemp.text)
    }

    // --- status ---

    @Test
    fun `status splits the trailing emoji off the description`() {
        val content = build()

        assertEquals("\"Partly Cloudy\"", content.value("Status"))
        assertEquals("⛅", content.emoji)
    }

    @Test
    fun `a multi word status keeps the whole phrase as description`() {
        val content = build(sample + ("current.status" to "Thunderstorm w/ Hail ⛈️"))

        assertEquals("\"Thunderstorm w/ Hail\"", content.value("Status"))
        assertEquals("⛈️", content.emoji)
    }

    @Test
    fun `a status without an emoji stays whole and yields no emoji`() {
        val content = build(sample + ("current.status" to "Unknown"))

        assertEquals("\"Unknown\"", content.value("Status"))
        assertNull(content.emoji)
    }

    // --- localization ---

    @Test
    fun `translate localizes the status description only`() {
        val seen = mutableListOf<String>()
        val content = build(
            tier = WidgetTier.Terminal(11),
            timestampEpochSeconds = syncEpoch,
            translate = { seen += it; "IT:$it" }
        )

        assertEquals(listOf("Partly Cloudy"), seen)
        assertEquals("\"IT:Partly Cloudy\"", content.value("Status"))
        // keys, location and the last_sync comment are code, not data
        assertEquals("\"Milano\"", content.value("Location"))
        assertTrue(content.keys().none { it.startsWith("IT:") })
        assertTrue(content.bodyLines.last().text.startsWith("# last_sync"))
    }

    // --- the line budget ---

    @Test
    fun `each rung keeps the previous transcript and adds the next line`() {
        // the whole point of a rung per line: growing the widget never reshuffles
        // what was already on screen, it only appends
        val budgets = (4..11).map {
            build(tier = WidgetTier.Terminal(it), timestampEpochSeconds = syncEpoch).keys()
        }

        budgets.zipWithNext { shorter, longer ->
            assertEquals(shorter, longer.take(shorter.size))
            assertEquals(shorter.size + 1, longer.size)
        }
        assertEquals(listOf("Location", "Temp", "Feels", "Status"), budgets.first())
        assertEquals(
            listOf(
                "Location", "Temp", "Feels", "Status", "Humidity",
                "Rain", "UV", "Wind", "AQI", "Sun", "# last_sync: 12:00"
            ),
            budgets.last()
        )
    }

    @Test
    fun `the region never makes it into the widget, at any size`() {
        // "Milano, Lombardia" would push the city name itself into the ellipsis, and
        // the region is the part the user already knows
        (4..11).forEach { lines ->
            assertEquals("\"Milano\"", build(tier = WidgetTier.Terminal(lines)).value("Location"))
        }
    }

    @Test
    fun `rain outranks uv, and both outrank the enthusiast readings`() {
        val content = build(tier = WidgetTier.Terminal(7))

        // "will it rain?" is why a weather widget gets read
        assertEquals(listOf("Location", "Temp", "Feels", "Status", "Humidity", "Rain", "UV"), content.keys())
        assertEquals("10%", content.value("Rain"))
        assertEquals("3", content.value("UV")) // whole number, not 3.0
    }

    // --- large tier ---

    @Test
    fun `large tier adds feels wind aqi sun and the last_sync comment`() {
        val content = build(tier = WidgetTier.Terminal(11), timestampEpochSeconds = syncEpoch)

        assertEquals(
            listOf(
                "Location", "Temp", "Feels", "Status", "Humidity",
                "Rain", "UV", "Wind", "AQI", "Sun", "# last_sync: 12:00"
            ),
            content.keys()
        )
        assertEquals("42", content.value("AQI"))
        assertEquals("07:42 → 18:15", content.value("Sun"))
        val comment = content.bodyLines.last()
        assertEquals(listOf(TokenRole.COMMENT), comment.roles)
    }

    @Test
    fun `last_sync is formatted in the zone passed in`() {
        val utc = build(tier = WidgetTier.Terminal(11), timestampEpochSeconds = syncEpoch)
        val local = build(tier = WidgetTier.Terminal(11), timestampEpochSeconds = syncEpoch, zone = rome)

        assertEquals("# last_sync: 12:00", utc.bodyLines.last().text)
        assertEquals("# last_sync: 14:00", local.bodyLines.last().text)
    }

    @Test
    fun `large tier without a timestamp shows no last_sync line`() {
        val content = build(tier = WidgetTier.Terminal(11))

        assertTrue(content.bodyLines.none { it.text.startsWith("# last_sync") })
    }

    @Test
    fun `the aqi line appears only when the snapshot carries it`() {
        assertEquals("42", build(tier = WidgetTier.Terminal(11)).value("AQI"))
        // outside Europe / on a failed air-quality call the key is simply absent
        val noAqi = build(sample - "air_quality.aqi", tier = WidgetTier.Terminal(11))
        assertNull(noAqi.line("AQI"))
        assertEquals(listOf("Location", "Temp", "Feels", "Status", "Humidity", "Rain", "UV", "Wind", "Sun"), noAqi.keys())
    }

    // --- degenerate snapshots ---

    @Test
    fun `a missing or empty snapshot degrades to the no data transcript`() {
        for (content in listOf(build(snapshot = null), build(snapshot = emptyMap()))) {
            assertEquals(
                listOf(TerminalLine(listOf(WidgetToken("# no data yet — open tweather", TokenRole.COMMENT)))),
                content.bodyLines
            )
            assertNull(content.emoji)
            assertEquals("--°", content.smallTemp.text)
            // a comment, not a fake city: role and wording both say "no data"
            assertEquals("# no data", content.smallLocation.text)
            assertEquals(
                listOf(TokenRole.COMMENT),
                content.smallLocation.tokens.map { it.role }
            )
            // the shell chrome stays put so the widget never looks broken
            assertEquals(WidgetContentBuilder.HEADER, content.headerTitle)
            assertEquals("sys@tweather:~\$ get weather -current", content.promptLine.text)
        }
    }

    @Test
    fun `an unparsable temperature drops the line and falls back to the small placeholder`() {
        val content = build(sample + ("current.temp_c" to "n/a"))

        assertEquals(listOf("Location", "Feels", "Status", "Humidity"), content.keys())
        assertEquals("--°", content.smallTemp.text)
    }

    // --- small tier ---

    @Test
    fun `small tier exposes the temperature and the city part of the location`() {
        val content = build(tier = WidgetTier.Small)

        assertEquals("19°C", content.smallTemp.text)
        assertEquals("Milano", content.smallLocation.text) // region dropped, it never fits
        // plain, not comment: a city name is data, and comment gray is ~3:1 on Dracula
        assertEquals(listOf(TokenRole.PLAIN), content.smallLocation.tokens.map { it.role })
        assertEquals(listOf(TokenRole.NUMBER), content.smallTemp.tokens.map { it.role })
        assertEquals("⛅", content.emoji)
    }

    // --- stale indicator ---

    /** `now` is always an offset from the fixed sync time, so no test reads the wall clock. */
    private fun afterSync(minutes: Long): Instant =
        Instant.ofEpochSecond(syncEpoch).plus(Duration.ofMinutes(minutes))

    /** Every token the renderer would paint red, across the body and the SMALL tier. */
    private fun WidgetContent.alerts(): List<WidgetToken> =
        (bodyLines + promptLine + smallTemp + smallLocation)
            .flatMap { it.tokens }
            .filter { it.role == TokenRole.ALERT }

    /** The indicator as a boolean — whichever tier happens to carry it. */
    private fun WidgetContent.showsStale(): Boolean = alerts().isNotEmpty()

    @Test
    fun `data younger than two update periods shows no alert anywhere`() {
        val content = build(
            tier = WidgetTier.Terminal(11),
            timestampEpochSeconds = syncEpoch,
            updateFrequencyMin = 60,
            now = afterSync(119)
        )

        assertEquals(emptyList<WidgetToken>(), content.alerts())
        assertEquals(listOf(TokenRole.COMMENT), content.bodyLines.last().roles)
    }

    @Test
    fun `stale data appends the marker to the temp line and leaves the rest alone`() {
        val fresh = build(timestampEpochSeconds = syncEpoch, updateFrequencyMin = 60, now = afterSync(1))
        val stale = build(timestampEpochSeconds = syncEpoch, updateFrequencyMin = 60, now = afterSync(121))

        val temp = stale.line("Temp")!!
        assertEquals(listOf("Temp", ": ", "19°C", WidgetContentBuilder.STALE_MARKER), temp.texts)
        assertEquals(listOf(TokenRole.KEY, TokenRole.PLAIN, TokenRole.NUMBER, TokenRole.ALERT), temp.roles)
        // the marker is the only body change: Location/Status/Humidity render identically
        assertEquals(
            fresh.bodyLines.filterNot { it.tokens.first().text == "Temp" },
            stale.bodyLines.filterNot { it.tokens.first().text == "Temp" }
        )
        assertEquals(1, stale.bodyLines.flatMap { it.tokens }.count { it.role == TokenRole.ALERT })
    }

    @Test
    fun `stale data turns the whole last_sync line into one alert token`() {
        val content = build(
            tier = WidgetTier.Terminal(11),
            timestampEpochSeconds = syncEpoch,
            updateFrequencyMin = 60,
            now = afterSync(121)
        )

        // the stamp itself is the evidence, so it is the line that turns red — not a suffix
        assertEquals(
            listOf(WidgetToken("# last_sync: 12:00", TokenRole.ALERT)),
            content.bodyLines.last().tokens
        )
    }

    @Test
    fun `stale data appends the marker to the small temperature`() {
        val content = build(
            tier = WidgetTier.Small,
            timestampEpochSeconds = syncEpoch,
            updateFrequencyMin = 60,
            now = afterSync(121)
        )

        assertEquals(listOf("19°C", WidgetContentBuilder.STALE_MARKER), content.smallTemp.texts)
        assertEquals(listOf(TokenRole.NUMBER, TokenRole.ALERT), content.smallTemp.roles)
        // fresh keeps the bare temperature: no stray empty token to eat the tiny cell
        val fresh = build(
            tier = WidgetTier.Small,
            timestampEpochSeconds = syncEpoch,
            updateFrequencyMin = 60,
            now = afterSync(1)
        )
        assertEquals(listOf("19°C"), fresh.smallTemp.texts)
    }

    @Test
    fun `two periods exactly is still fresh — the comparison is strict`() {
        fun atAgeSeconds(seconds: Long) = build(
            tier = WidgetTier.Terminal(11),
            timestampEpochSeconds = syncEpoch,
            updateFrequencyMin = 60,
            now = Instant.ofEpochSecond(syncEpoch + seconds)
        )

        assertFalse(atAgeSeconds(2 * 60 * 60).showsStale())
        assertTrue(atAgeSeconds(2 * 60 * 60 + 1).showsStale())
    }

    @Test
    fun `without a clock nothing is ever stale`() {
        // `now` defaults to null: a caller with no clock must not guess, whatever the age
        val content = build(tier = WidgetTier.Terminal(11), timestampEpochSeconds = syncEpoch, updateFrequencyMin = 15)

        assertFalse(content.showsStale())
        assertEquals(listOf(TokenRole.COMMENT), content.bodyLines.last().roles)
    }

    @Test
    fun `a snapshot without a timestamp is never stale`() {
        val content = build(
            tier = WidgetTier.Terminal(11),
            timestampEpochSeconds = null,
            updateFrequencyMin = 15,
            now = afterSync(60 * 24)
        )

        assertFalse(content.showsStale())
        assertTrue(content.bodyLines.none { it.text.startsWith("# last_sync") })
    }

    @Test
    fun `the threshold follows the update frequency`() {
        val now = afterSync(90)

        // 90 min is six periods at 15 min, well under one at 120
        assertTrue(build(timestampEpochSeconds = syncEpoch, updateFrequencyMin = 15, now = now).showsStale())
        assertFalse(build(timestampEpochSeconds = syncEpoch, updateFrequencyMin = 120, now = now).showsStale())
    }

    // ---- the register rule (Fase 18) --------------------------------------

    /**
     * The widget's two empty-state lines are sentences to whoever is looking at a
     * home screen, so they arrive already translated — the same way the values do.
     * They come in as strings because this builder is a pure value, and the English
     * written into the defaults is what a caller with no resources would get.
     *
     * That English is a second copy of `values/strings.xml`, so it is tied to it:
     * see `WidgetNotesTest`. Here, what matters is that the words are the caller's
     * to choose and the marker is not.
     */
    @Test
    fun `the empty lines are the caller's words behind the file's own marker`() {
        val content = WidgetContentBuilder.build(
            snapshot = null,
            timestampEpochSeconds = null,
            temperature = TemperatureUnit.CELSIUS,
            windSpeed = WindSpeedUnit.KMH,
            tier = WidgetTier.Terminal(4),
            noDataYet = "ancora nessun dato — apri tweather",
            noData = "nessun dato"
        )
        assertEquals(
            "# ancora nessun dato — apri tweather",
            content.bodyLines.single().tokens.single().text
        )
        assertEquals("# nessun dato", content.smallLocation?.tokens?.single()?.text)
        // The command line above them is a command and never moves.
        assertEquals("tweather --now", content.headerTitle)
    }
}
