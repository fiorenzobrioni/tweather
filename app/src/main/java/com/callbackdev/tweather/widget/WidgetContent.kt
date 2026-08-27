package com.callbackdev.tweather.widget

import com.callbackdev.tweather.data.DefaultUpdateFrequencyMin
import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.data.WindSpeedUnit
import com.callbackdev.tweather.domain.WeatherFreshness
import com.callbackdev.tweather.ui.weather.convert
import com.callbackdev.tweather.ui.weather.symbol
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * How much the launcher has room for, chosen through the RemoteViews sizes map.
 *
 * The terminal tiers differ only in how many transcript lines fit, so the ladder is
 * simply a line budget with one rung per line. A coarse ladder would be worse than
 * it sounds: the map only ever picks a tier that FITS, so a widget with room for
 * seven lines but no seven-line rung silently falls back to the five-line one.
 */
sealed interface WidgetTier {
    /** The glanceable strip: emoji, temperature, city. Its own layout. */
    data object Small : WidgetTier

    data class Terminal(val lines: Int) : WidgetTier
}

/** Semantic color role of a token; the renderer maps roles to [WidgetPalette] ints. */
enum class TokenRole { PROMPT, PLAIN, DIM, KEY, STRING, NUMBER, COMMENT, ALERT }

data class WidgetToken(val text: String, val role: TokenRole)

data class TerminalLine(val tokens: List<WidgetToken>) {
    val text: String get() = tokens.joinToString("") { it.text }
}

/**
 * Everything a widget layout binds. [bodyLines] is what varies per tier; SMALL
 * ignores it and uses [smallTemp]/[smallLocation] instead. Those are lines, not
 * strings, so the SMALL tier gets its colors from the same token roles as the rest
 * (the empty state reads as a comment, a real city name as plain text).
 */
data class WidgetContent(
    val headerTitle: String,
    val promptLine: TerminalLine,
    val bodyLines: List<TerminalLine>,
    val emoji: String?,
    val smallTemp: TerminalLine,
    val smallLocation: TerminalLine
)

/**
 * Pure mapping from a persisted history snapshot (WeatherSnapshots.flatten keys,
 * canonical English + Celsius/kph) to the terminal transcript the widget shows.
 * [translate] localizes data values only (same rule as the main screen); keys,
 * prompt and comments are code and stay English.
 */
object WidgetContentBuilder {

    const val HEADER = "tweather --now"

    /** Terminal shorthand, so it stays English like every other `#` comment. */
    const val STALE_MARKER = "  # stale"

    private val SyncTime = DateTimeFormatter.ofPattern("HH:mm")

    fun build(
        snapshot: Map<String, String>?,
        timestampEpochSeconds: Long?,
        temperature: TemperatureUnit,
        windSpeed: WindSpeedUnit,
        tier: WidgetTier,
        translate: (String) -> String = { it },
        zone: ZoneId = ZoneId.systemDefault(),
        updateFrequencyMin: Int = DefaultUpdateFrequencyMin,
        now: Instant? = null,
        /**
         * The optional sky line (Fase 16e), already rendered. Off by default and
         * LAST in the transcript, so it is the first line the budget drops: the
         * temperature is why a weather widget exists and this line is not.
         */
        skyLine: String? = null
    ): WidgetContent {
        val prompt = TerminalLine(
            listOf(
                WidgetToken("sys@tweather", TokenRole.PROMPT),
                WidgetToken(":~", TokenRole.PLAIN),
                WidgetToken("$ ", TokenRole.PLAIN),
                WidgetToken("get weather -current", TokenRole.DIM)
            )
        )
        if (snapshot.isNullOrEmpty()) {
            return WidgetContent(
                headerTitle = HEADER,
                promptLine = prompt,
                bodyLines = listOf(comment("# no data yet — open tweather")),
                emoji = null,
                smallTemp = token("--°", TokenRole.NUMBER),
                smallLocation = comment("# no data")
            )
        }

        // City only, never "city, region": the region is what the user already knows
        // and it is what pushes the city name itself into the ellipsis.
        val city = snapshot["location"]?.substringBefore(",")
        val (statusDesc, emoji) = splitStatus(snapshot["current.status"])
        val temp = snapshot["current.temp_c"].formatTemp(temperature)
        val stale = isStale(timestampEpochSeconds, updateFrequencyMin, now)
        val syncLine = timestampEpochSeconds?.let {
            val stamp = SyncTime.format(Instant.ofEpochSecond(it).atZone(zone))
            // the timestamp is its own evidence, so the whole line turns red
            token("# last_sync: $stamp", if (stale) TokenRole.ALERT else TokenRole.COMMENT)
        }

        // The whole transcript, most useful first. The tier decides how much of it
        // is shown — no per-tier branching, so a new rung needs no new code here.
        val transcript = buildList {
            city?.let { add(kvString("Location", it)) }
            temp?.let { add(kvNumber("Temp", it)) }
            snapshot["current.feels_like_c"].formatTemp(temperature)?.let {
                add(kvNumber("Feels", it))
            }
            statusDesc?.let { add(kvString("Status", translate(it))) }
            snapshot["current.humidity_pct"]?.let { add(kvNumber("Humidity", "$it%")) }
            // "will it rain?" outranks the rest — it is why a weather widget is read
            snapshot["current.precip_chance_pct"]?.let { add(kvNumber("Rain", "$it%")) }
            snapshot["current.uv_index"]?.trimDecimal()?.let { add(kvNumber("UV", it)) }
            formatWind(snapshot, windSpeed)?.let { add(kvNumber("Wind", it)) }
            snapshot["air_quality.aqi"]?.let { add(kvNumber("AQI", it)) }
            formatSun(snapshot)?.let { add(kvNumber("Sun", it)) }
            syncLine?.let { add(it) }
            skyLine?.let { add(comment(it)) }
        }

        val lines = transcript.take(bodyLineBudget(tier)).toMutableList()
        // The stale marker rides the Temp line on the tiers too short for the
        // last_sync line — that is where the eye lands, and a trailing comment is the
        // first thing `ellipsize` drops on a narrow widget, never the value.
        if (stale && lines.none { it === syncLine }) {
            val temperatureLine = lines.indexOfFirst { it.tokens.firstOrNull()?.text == "Temp" }
            if (temperatureLine >= 0) {
                lines[temperatureLine] = TerminalLine(
                    lines[temperatureLine].tokens + WidgetToken(STALE_MARKER, TokenRole.ALERT)
                )
            }
        }
        return WidgetContent(
            headerTitle = HEADER,
            promptLine = prompt,
            bodyLines = lines,
            emoji = emoji,
            smallTemp = TerminalLine(
                listOfNotNull(
                    WidgetToken(temp ?: "--°", TokenRole.NUMBER),
                    WidgetToken(STALE_MARKER, TokenRole.ALERT).takeIf { stale }
                )
            ),
            // plain, not comment: at 11sp a city name is data, and the comment gray
            // only clears ~3:1 against the Dracula/Monokai backgrounds
            smallLocation = city?.let { token(it, TokenRole.PLAIN) } ?: comment("# no data")
        )
    }

    /** Body lines the tier has room for; [WidgetTier.Small] renders none of them. */
    fun bodyLineBudget(tier: WidgetTier): Int = when (tier) {
        is WidgetTier.Small -> 0
        is WidgetTier.Terminal -> tier.lines
    }

    /** `3.0` → `3`: the UV index is read as a whole number. */
    private fun String.trimDecimal(): String? =
        toDoubleOrNull()?.roundToInt()?.toString()

    /**
     * Two whole polling periods without a commit means something is wrong (no
     * network, job throttled, permission revoked) — the widget has to say so
     * instead of presenting hours-old numbers as current.
     */
    /** The rule moved to [WeatherFreshness] in Fase 16d; the sky module reads it too. */
    private fun isStale(
        timestampEpochSeconds: Long?,
        updateFrequencyMin: Int,
        now: Instant?
    ): Boolean {
        if (timestampEpochSeconds == null || now == null) return false
        return WeatherFreshness.isStale(
            Instant.ofEpochSecond(timestampEpochSeconds), updateFrequencyMin, now
        )
    }

    /**
     * `"Partly Cloudy ⛅"` → desc + emoji. Defensive: if the last token reads as a
     * word (ASCII letters), the whole value is the description and there is no emoji.
     */
    private fun splitStatus(status: String?): Pair<String?, String?> {
        if (status.isNullOrBlank()) return null to null
        val last = status.substringAfterLast(' ')
        return if (last == status || last.any { it in 'A'..'Z' || it in 'a'..'z' }) {
            status to null
        } else {
            status.substringBeforeLast(' ') to last
        }
    }

    private fun String?.formatTemp(unit: TemperatureUnit): String? =
        this?.toDoubleOrNull()?.let { "${unit.convert(it).roundToInt()}${unit.symbol}" }

    private fun formatWind(snapshot: Map<String, String>, unit: WindSpeedUnit): String? {
        val speed = snapshot["current.wind_kph"]?.toDoubleOrNull() ?: return null
        val dir = snapshot["current.wind_dir"]?.let { " $it" } ?: ""
        return "${unit.convert(speed).roundToInt()} ${unit.symbol}$dir"
    }

    private fun formatSun(snapshot: Map<String, String>): String? {
        val sunrise = snapshot["astronomical.sunrise"] ?: return null
        val sunset = snapshot["astronomical.sunset"] ?: return null
        return "$sunrise → $sunset"
    }

    private fun kvString(key: String, value: String) = TerminalLine(
        listOf(
            WidgetToken(key, TokenRole.KEY),
            WidgetToken(": ", TokenRole.PLAIN),
            WidgetToken("\"$value\"", TokenRole.STRING)
        )
    )

    private fun kvNumber(key: String, value: String, trailing: String? = null) = TerminalLine(
        listOfNotNull(
            WidgetToken(key, TokenRole.KEY),
            WidgetToken(": ", TokenRole.PLAIN),
            WidgetToken(value, TokenRole.NUMBER),
            trailing?.let { WidgetToken(it, TokenRole.ALERT) }
        )
    )

    private fun comment(text: String) = token(text, TokenRole.COMMENT)

    private fun token(text: String, role: TokenRole) =
        TerminalLine(listOf(WidgetToken(text, role)))
}
