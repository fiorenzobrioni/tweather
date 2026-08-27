package com.callbackdev.tweather.ui.sky

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.callbackdev.tweather.R
import com.callbackdev.tweather.data.SkySubscription
import com.callbackdev.tweather.domain.sky.SkyJob
import com.callbackdev.tweather.domain.sky.SkyLead
import com.callbackdev.tweather.ui.components.CanvasLine
import com.callbackdev.tweather.ui.components.CodeLine
import com.callbackdev.tweather.ui.components.WidgetLine
import com.callbackdev.tweather.ui.components.commentLine
import com.callbackdev.tweather.ui.theme.SyntaxColors
import com.callbackdev.tweather.ui.theme.TweatherTheme
import kotlinx.coroutines.delay

/** Everything the crontab can do, bundled for [buildSkyLines]. */
class SkyActions(
    val onToggleEnabled: (SkySubscription) -> Unit,
    val onRemove: (String) -> Unit,
    val onAdd: (String) -> Unit,
    val onRunSky: () -> Unit = {},
    val onCycleLead: (SkySubscription) -> Unit = {}
)

/** Which line is mid-interaction: the catalog picker, an armed `[rm]`, an armed run. */
sealed interface SkyEdit {
    data object Catalog : SkyEdit

    /** `[rm]` tapped once; a second tap inside the window removes the line. */
    data class ConfirmRemove(val jobId: String) : SkyEdit

    /** `$ tweather run sky` tapped once — two-tap, like every `$` in the app. */
    data object ConfirmRun : SkyEdit
}

/**
 * `sky.crontab` — the third tab of the editor strip (Fase 16c).
 *
 * The file *looks like* a crontab and *is* a subscription list edited token by
 * token, the same shape `alerts.rules` took in Fase 11: the job name toggles the
 * leading `#`, `[rm]` takes the line out of the file, `+ add job` opens the catalog
 * as an IDE-style autocomplete drawn as lines. There is no text field and no parser,
 * so an invalid cron line is not a thing a user can write — the states the app has
 * to handle are the states the app defines.
 *
 * Nothing here reaches the network. The schedule is local computation from a
 * latitude and a date, which is why this tab still renders in airplane mode with the
 * rest of the app showing `// ERROR: no network`.
 */
fun buildSkyLines(
    state: SkyUiState,
    editing: SkyEdit?,
    syntax: SyntaxColors,
    actions: SkyActions,
    labels: SkyLabels,
    onStartEdit: (SkyEdit?) -> Unit
): List<CanvasLine> = buildList {
    val runArmed = editing == SkyEdit.ConfirmRun
    val document = state.document
    if (document == null) {
        // Same surface as the editor's other two files with no city (Fase 14b), in
        // the comment channel this file uses: `#`, because that is what a crontab
        // comments with.
        add(commentLine("# no location configured", syntax))
        add(commentLine("# hint: open cities.json and search a city", syntax))
        return@buildList
    }

    document.header.forEach { add(commentLine(it, syntax)) }
    add(CodeLine(AnnotatedString("")))

    if (document.rows.isEmpty()) {
        add(commentLine("# no jobs — the sky runs them anyway, this file just watches", syntax))
    }
    document.rows.forEach { row ->
        val subscription = state.subscriptions.first { it.jobId == row.job.id }
        val armed = editing == SkyEdit.ConfirmRemove(row.job.id)
        add(
            WidgetLine(indent = 0, measureText = measureOf(row, document)) {
                CrontabLine(
                    row = row,
                    document = document,
                    armed = armed,
                    onToggle = { actions.onToggleEnabled(subscription) },
                    onCycleLead = { actions.onCycleLead(subscription) },
                    onRemove = {
                        if (armed) {
                            onStartEdit(null)
                            actions.onRemove(row.job.id)
                        } else {
                            onStartEdit(SkyEdit.ConfirmRemove(row.job.id))
                        }
                    },
                    labels = labels
                )
            }
        )
    }

    add(CodeLine(AnnotatedString("")))
    if (document.available.isEmpty()) {
        add(commentLine("# every job in the catalog is already a line", syntax))
    } else {
        add(
            CodeLine(
                AnnotatedString("+ add job", SpanStyle(color = syntax.diffAdd)),
                onClick = {
                    onStartEdit(if (editing == SkyEdit.Catalog) null else SkyEdit.Catalog)
                },
                onClickLabel = labels.addJob
            )
        )
        if (editing == SkyEdit.Catalog) {
            // The catalog as autocomplete: a finite list, drawn as lines, exactly
            // like the variable picker of `alerts.rules`.
            document.available.forEach { job ->
                add(
                    CodeLine(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = syntax.key)) { append(job.id) }
                            withStyle(SpanStyle(color = syntax.comment)) {
                                append("  ")
                                append(job.expression)
                            }
                        },
                        indent = 1,
                        onClick = {
                            actions.onAdd(job.id)
                            onStartEdit(null)
                        },
                        onClickLabel = labels.pickJob(job)
                    )
                )
            }
        }
    }

    // The dry run: evaluate everything against the forecast in hand, notify nothing,
    // record nothing. A second view of facts the rows already carry, and that is the
    // point — a resolved crontab row is wide enough to pan sideways, so this is the
    // one place the verdicts line up under each other.
    if (document.rows.any { it.enabled }) {
        add(CodeLine(AnnotatedString("")))
        add(commentLine("// evaluate every enabled job against the forecast in hand:", syntax))
        add(
            CodeLine(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = syntax.comment)) { append("$ ") }
                    append("tweather run sky")
                    if (runArmed) {
                        withStyle(SpanStyle(color = syntax.diffDel)) {
                            append("  // tap again to confirm")
                        }
                    }
                },
                onClick = {
                    if (runArmed) {
                        onStartEdit(null)
                        actions.onRunSky()
                    } else {
                        onStartEdit(SkyEdit.ConfirmRun)
                    }
                },
                onClickLabel = if (runArmed) labels.confirmRun else labels.runSky
            )
        )
        state.dryRun?.forEach { add(commentLine(it, syntax)) }
    }

    add(CodeLine(AnnotatedString("")))
    document.footer.forEach { add(commentLine(it, syntax)) }
}

/**
 * `@daily         sun.set                 # 20:12`
 *
 * Three tap targets and one of them is the whole line's meaning: tapping the NAME
 * comments the line out, which is how everybody disables a cron job in real life.
 * The expression is not tappable — it is a property of the job, not a setting.
 */
@Composable
private fun CrontabLine(
    row: SkyRow,
    document: SkyDocument,
    armed: Boolean,
    onToggle: () -> Unit,
    onCycleLead: () -> Unit,
    onRemove: () -> Unit,
    labels: SkyLabels
) {
    val syntax = TweatherTheme.syntax
    val style = MaterialTheme.typography.bodySmall
    // A disabled line is grey from end to end, including its cron field: it is a
    // comment now, and a comment does not keep half its syntax colouring.
    val expressionColor = if (row.enabled) syntax.number else syntax.comment
    val nameColor = if (row.enabled) MaterialTheme.colorScheme.onSurface else syntax.comment
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = expressionCell(row, document),
            style = style,
            color = expressionColor
        )
        Text(
            text = row.job.id.padEnd(document.nameColumnWidth + 1),
            style = style,
            color = nameColor,
            modifier = Modifier.clickable(
                role = Role.Switch,
                onClickLabel = labels.toggle(row)
            ) { onToggle() }
        )
        // The `--notify` argument, exactly where a crontab puts a job's arguments:
        // after the command. It cycles on tap like every other value in the app, and
        // the column only exists when some line in the file has a reminder.
        if (document.leadColumnWidth > 0) {
            Text(
                text = leadCell(row, document),
                style = style,
                color = if (row.enabled && row.lead != SkyLead.OFF) syntax.string else syntax.comment,
                modifier = Modifier.clickable(
                    role = Role.Button,
                    onClickLabel = labels.cycleLead(row)
                ) { onCycleLead() }
            )
        }
        // `[rm]` sits BEFORE the comment, not at the end of the line as
        // `VISION_SKY.md` §4 sketched it. Fase 11d settled this argument once
        // already, for the README's tables: the comment is variable-length and can
        // afford to clip off the right edge, a tap target cannot. At the end of a
        // resolved crontab row it landed past the edge of a 360dp screen, reachable
        // only by panning — which is not a control, it is a rumour of one.
        Text(
            text = if (armed) "[rm?]" else "[rm]",
            style = style,
            color = if (armed) syntax.diffDel else syntax.comment,
            modifier = Modifier
                .clickable(role = Role.Button, onClickLabel = labels.remove(row, armed)) {
                    onRemove()
                }
                .padding(horizontal = 8.dp)
        )
        // The confirm is the token itself, `[rm]` → `[rm?]` in red, rather than a
        // `// tap again` comment appended after it: it changes under the finger that
        // just tapped it, and it costs the row no width. The words are in the click
        // label, where a screen reader will read them.
        if (row.comment.isNotEmpty()) {
            Text(text = "# ${row.comment}", style = style, color = syntax.comment)
        }
    }
}

/**
 * The cron field, padded so the name column lines up whether or not the line is
 * commented out. The `#` lives INSIDE the padding rather than in front of it: a
 * commented crontab line keeps its columns, and a file whose rows shifted sideways
 * every time one was disabled would be unreadable at exactly the moment you were
 * comparing them.
 */
private fun expressionCell(row: SkyRow, document: SkyDocument): String {
    val prefix = if (row.enabled) "" else SkyDocumentBuilder.DISABLED_PREFIX
    return (prefix + row.expression).padEnd(document.expressionColumnWidth + 2)
}

/**
 * `--notify=30m`, padded to the file's own widest, or blank space of the same width
 * on the lines with no reminder — so the comments stay in one column whether or not
 * a given job is set to remind you.
 */
private fun leadCell(row: SkyRow, document: SkyDocument): String {
    val text = if (row.lead == SkyLead.OFF) "" else "--notify=${row.lead.label}"
    return text.padEnd(document.leadColumnWidth + 2)
}

/** What [CodeCanvas] measures the row as; the widths must match [CrontabLine]. */
private fun measureOf(row: SkyRow, document: SkyDocument): String =
    expressionCell(row, document) +
        row.job.id.padEnd(document.nameColumnWidth + 1) +
        leadCell(row, document) +
        "  [rm?]   " + // the armed form, so the row does not resize under the tap
        (if (row.comment.isEmpty()) "" else "# ${row.comment}")

/**
 * The spoken form of each tap target. Screen readers get words, not glyphs: `[rm]`
 * announced as "left bracket r m" is the bar Fase 16b's plan set for this module.
 */
class SkyLabels(
    val addJob: String,
    val pickJob: (SkyJob) -> String,
    val toggle: (SkyRow) -> String,
    val remove: (SkyRow, Boolean) -> String,
    val runSky: String,
    val confirmRun: String,
    val cycleLead: (SkyRow) -> String
)

@Composable
fun rememberSkyLabels(): SkyLabels {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    return remember(resources) {
        SkyLabels(
            addJob = resources.getString(R.string.cd_sky_add_job),
            pickJob = { resources.getString(R.string.cd_sky_pick_job, it.id) },
            toggle = { row ->
                resources.getString(
                    if (row.enabled) R.string.cd_sky_disable else R.string.cd_sky_enable,
                    row.job.id
                )
            },
            remove = { row, armed ->
                resources.getString(
                    if (armed) R.string.cd_sky_confirm_remove else R.string.cd_sky_remove,
                    row.job.id
                )
            },
            runSky = resources.getString(R.string.cd_run_sky),
            confirmRun = resources.getString(R.string.cd_confirm_run_sky),
            cycleLead = { resources.getString(R.string.cd_sky_cycle_lead, it.job.id) }
        )
    }
}

/** Hoisted so a caller can keep the arming state out of a test's way. */
@Composable
fun rememberSkyEdit(): androidx.compose.runtime.MutableState<SkyEdit?> {
    val state = remember { mutableStateOf<SkyEdit?>(null) }
    val armed = state.value.takeIf { it is SkyEdit.ConfirmRemove || it is SkyEdit.ConfirmRun }
    // The confirm disarms itself, like every other two-tap in the app: an armed
    // `[rm]` left sitting behind a tab switch is a trap for the next tap.
    LaunchedEffect(armed) {
        if (armed != null) {
            delay(4_000)
            if (state.value == armed) state.value = null
        }
    }
    return state
}
