package com.callbackdev.tweather.widget

import com.callbackdev.tweather.data.DefaultUpdateFrequencyMin
import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.data.WindSpeedUnit
import com.callbackdev.tweather.ui.weather.convert
import com.callbackdev.tweather.ui.weather.symbol
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Layout tiers picked by the launcher via the RemoteViews sizes map: SMALL is a
 * glanceable emoji+temp strip, MEDIUM is the mockup terminal window, LARGE adds
 * the extra readings plus a `# last_sync` freshness line.
 */
enum class WidgetTier { SMALL, MEDIUM, LARGE }

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
        now: Instant? = null
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

        val location = snapshot["location"]
        val (statusDesc, emoji) = splitStatus(snapshot["current.status"])
        val temp = snapshot["current.temp_c"].formatTemp(temperature)
        val humidity = snapshot["current.humidity_pct"]
        val stale = isStale(timestampEpochSeconds, updateFrequencyMin, now)

        val lines = buildList {
            location?.let { add(kvString("Location", it)) }
            // The stale marker rides the Temp line on the tiers with no room for a
            // last_sync line — that is where the eye lands, and a trailing comment is
            // the first thing `ellipsize` drops on a narrow widget, never the value.
            // LARGE says it once, on its own last_sync line, instead of twice.
            val tempMarker = STALE_MARKER.takeIf { stale && tier != WidgetTier.LARGE }
            temp?.let { add(kvNumber("Temp", it, trailing = tempMarker)) }
            statusDesc?.let { add(kvString("Status", translate(it))) }
            humidity?.let { add(kvNumber("Humidity", "$it%")) }
            if (tier == WidgetTier.LARGE) {
                snapshot["current.feels_like_c"].formatTemp(temperature)?.let {
                    add(kvNumber("Feels", it))
                }
                formatWind(snapshot, windSpeed)?.let { add(kvNumber("Wind", it)) }
                snapshot["air_quality.aqi"]?.let { add(kvNumber("AQI", it)) }
                formatSun(snapshot)?.let { add(kvNumber("Sun", it)) }
                timestampEpochSeconds?.let {
                    val stamp = SyncTime.format(Instant.ofEpochSecond(it).atZone(zone))
                    // the timestamp is its own evidence, so the whole line turns red
                    add(token("# last_sync: $stamp", if (stale) TokenRole.ALERT else TokenRole.COMMENT))
                }
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
            smallLocation = location?.substringBefore(",")
                ?.let { token(it, TokenRole.PLAIN) }
                ?: comment("# no data")
        )
    }

    /**
     * Two whole polling periods without a commit means something is wrong (no
     * network, job throttled, permission revoked) — the widget has to say so
     * instead of presenting hours-old numbers as current.
     */
    private fun isStale(
        timestampEpochSeconds: Long?,
        updateFrequencyMin: Int,
        now: Instant?
    ): Boolean {
        if (timestampEpochSeconds == null || now == null) return false
        val age = Duration.between(Instant.ofEpochSecond(timestampEpochSeconds), now)
        return age > Duration.ofMinutes(2L * updateFrequencyMin)
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
