package com.callbackdev.tweather.ui.logs

import com.callbackdev.tweather.domain.sky.SkyRun
import com.callbackdev.tweather.domain.sky.SkyVerdictKind
import com.callbackdev.tweather.ui.components.CanvasLine
import com.callbackdev.tweather.ui.components.CodeLine
import com.callbackdev.tweather.ui.components.commentLine
import com.callbackdev.tweather.ui.theme.SyntaxColors
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * `sky_runs.log` — the third file of the Logs strip (Fase 16e).
 *
 * Not a `.diff`: this file records OUTCOMES, not changes, and calling it a diff would
 * be the same kind of lie the crontab avoided when it refused to write a fixed minute
 * field. It is a journal transcript, newest first, one line per job the app observed
 * as having run.
 *
 * It is a **second view of the commits**, not a second store: every row here comes
 * from the `sky_runs` column of a `weather_history` commit, which is also where the
 * `✓ …` check lines in `weather_history.diff` come from. Two files, one truth, no
 * reconciliation test to write — and the retention solves itself, because runs age
 * out with the 200 commits the history already keeps.
 */
object SkyRunsLog {

    private val DayHeader = DateTimeFormatter.ofPattern("MMM d")
    private val ClockTime = DateTimeFormatter.ofPattern("HH:mm")

    /** One observed run, with the commit that observed it. */
    data class Row(val run: SkyRun, val observedAtEpochSeconds: Long)

    fun build(rows: List<Row>, zone: ZoneId, syntax: SyntaxColors): List<CanvasLine> {
        if (rows.isEmpty()) {
            return listOf(
                commentLine("# no runs recorded yet", syntax),
                commentLine("# a job is logged the first time a fetch sees it has passed", syntax)
            )
        }
        val nameWidth = rows.maxOf { it.run.jobId.length }
        return buildList {
            rows
                .sortedByDescending { it.run.atEpochSeconds }
                .groupBy { Instant.ofEpochSecond(it.run.atEpochSeconds).atZone(zone).toLocalDate() }
                .forEach { (date, ofDay) ->
                    if (isNotEmpty()) add(CodeLine(AnnotatedString("")))
                    add(commentLine("# ${date.format(DayHeader)}", syntax))
                    ofDay.forEach { add(runLine(it, date, zone, nameWidth, syntax)) }
                    add(commentLine("# ${summary(ofDay)}", syntax))
                }
        }
    }

    private fun runLine(
        row: Row,
        date: LocalDate,
        zone: ZoneId,
        nameWidth: Int,
        syntax: SyntaxColors
    ): CodeLine {
        val at = Instant.ofEpochSecond(row.run.atEpochSeconds).atZone(zone)
        val text = buildString {
            append(at.format(ClockTime)).append("  ")
            append(row.run.jobId.padEnd(nameWidth + 2))
            append(verdictText(row.run).padEnd(VERDICT_COLUMN))
            row.run.cloudPct?.let { append("cloud ").append(it.toString().padStart(3)).append("%  ") }
            // How far the observing fetch was from the event. Printed because a
            // verdict resolved from a reading forty minutes away is a weaker claim
            // than one from a reading five minutes away, and hiding that distance
            // would be dishonest.
            append("obs +").append(row.run.obsMinutes).append("m")
        }
        return CodeLine(AnnotatedString(text, SpanStyle(color = color(row.run, syntax))))
    }

    private fun verdictText(run: SkyRun): String = when (run.verdict) {
        SkyVerdictKind.PASS -> "✓ pass"
        SkyVerdictKind.UNSTABLE -> "~ unstable"
        SkyVerdictKind.FAIL -> "✗ fail"
        // The coverage state: no fetch came near enough to the event to have an
        // opinion, so none was invented. These count in no statistic.
        else -> "– skipped"
    }

    private fun color(run: SkyRun, syntax: SyntaxColors) = when (run.verdict) {
        SkyVerdictKind.PASS -> syntax.diffAdd
        SkyVerdictKind.FAIL -> syntax.diffDel
        SkyVerdictKind.UNSTABLE -> syntax.number
        else -> syntax.comment
    }

    /** `4 passed · 1 unstable · 1 skipped` — and a skipped run counts nowhere else. */
    private fun summary(rows: List<Row>): String {
        val counts = rows.groupingBy { it.run.verdict }.eachCount()
        return buildList {
            counts[SkyVerdictKind.PASS]?.let { add("$it passed") }
            counts[SkyVerdictKind.UNSTABLE]?.let { add("$it unstable") }
            counts[SkyVerdictKind.FAIL]?.let { add("$it failed") }
            rows.count { it.run.skipped }.takeIf { it > 0 }?.let { add("$it skipped") }
        }.joinToString(" · ")
    }

    /** Wide enough for `~ unstable`, so the numbers line up under each other. */
    private const val VERDICT_COLUMN = 12
}
