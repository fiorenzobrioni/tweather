package com.callbackdev.tweather.ui.settings

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.tweather.R
import com.callbackdev.tweather.data.UnitSettings
import com.callbackdev.tweather.domain.rules.MaxRules
import com.callbackdev.tweather.domain.rules.NotificationRule
import com.callbackdev.tweather.domain.rules.RuleCondition
import com.callbackdev.tweather.domain.rules.RuleOp
import com.callbackdev.tweather.domain.rules.RuleVariableKind
import com.callbackdev.tweather.domain.rules.RuleVariables
import com.callbackdev.tweather.ui.components.CanvasLine
import com.callbackdev.tweather.ui.components.CodeCanvas
import com.callbackdev.tweather.ui.components.CodeLine
import com.callbackdev.tweather.ui.components.EditorTabs
import com.callbackdev.tweather.ui.components.StatusBarDivider
import com.callbackdev.tweather.ui.components.TerminalInput
import com.callbackdev.tweather.ui.components.TerminalStatusBar
import com.callbackdev.tweather.ui.components.WidgetLine
import com.callbackdev.tweather.ui.components.commentLine
import com.callbackdev.tweather.ui.theme.SyntaxColors
import com.callbackdev.tweather.ui.theme.TweatherTheme
import kotlinx.coroutines.delay

/** Everything the rules file can do, bundled for [buildRulesLines]. */
class RulesActions(
    val onStartEdit: (RuleEdit) -> Unit,
    val onStopEdit: () -> Unit,
    val onAdd: () -> Unit,
    val onRemove: (NotificationRule) -> Unit,
    val onToggleEnabled: (NotificationRule) -> Unit,
    val onRename: (NotificationRule, String) -> Unit,
    val onSetVariable: (NotificationRule, Int, String) -> Unit,
    val onCycleOp: (NotificationRule, Int) -> Unit,
    val onSetThreshold: (NotificationRule, Int, String) -> Unit,
    val onToggleBooleanThreshold: (NotificationRule, Int) -> Unit,
    val onAddCondition: (NotificationRule) -> Unit,
    val onRemoveCondition: (NotificationRule) -> Unit,
    val onSetMessage: (NotificationRule, String) -> Unit,
    val onRunRules: () -> Unit
)

/**
 * The `alerts.rules` tab of the Settings screen — "Weather CI" (Fase 11). The file
 * *looks like* source but *is* a structure edited token by token, the app's
 * "controls rendered as text" taken to its logical end: the variable opens an
 * inline picker (an IDE autocomplete, drawn as lines), the operator cycles on tap,
 * threshold and message become terminal inputs in place. A syntax error is not
 * writable. Variable names and thresholds render in the user's units
 * (`current.temp_c` ↔ `current.temp_f`) while storage stays metric — the file
 * doesn't lie about its units, and neither does the DataStore.
 */
@Composable
fun RulesScreen(
    onSelectFile: (Int) -> Unit,
    canvasState: LazyListState,
    viewModel: RulesViewModel = viewModel(factory = RulesViewModel.Factory)
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val dryRun by viewModel.dryRun.collectAsStateWithLifecycle()
    RulesScreen(
        rules = rules,
        units = settings.units,
        userRulesEnabled = settings.notifications.userRules,
        editing = editing,
        dryRun = dryRun,
        actions = RulesActions(
            onStartEdit = viewModel::startEdit,
            onStopEdit = viewModel::stopEdit,
            onAdd = viewModel::addRule,
            onRemove = viewModel::removeRule,
            onToggleEnabled = viewModel::toggleEnabled,
            onRename = viewModel::rename,
            onSetVariable = viewModel::setVariable,
            onCycleOp = viewModel::cycleOp,
            onSetThreshold = viewModel::setThreshold,
            onToggleBooleanThreshold = viewModel::toggleBooleanThreshold,
            onAddCondition = viewModel::addCondition,
            onRemoveCondition = viewModel::removeCondition,
            onSetMessage = viewModel::setMessage,
            onRunRules = viewModel::runRules
        ),
        onSelectFile = onSelectFile,
        canvasState = canvasState
    )
}

@Composable
fun RulesScreen(
    rules: List<NotificationRule>,
    units: UnitSettings,
    userRulesEnabled: Boolean,
    editing: RuleEdit?,
    dryRun: DryRunUi?,
    actions: RulesActions,
    onSelectFile: (Int) -> Unit = {},
    canvasState: LazyListState = rememberLazyListState()
) {
    val syntax = TweatherTheme.syntax
    val resources = LocalContext.current.resources
    // Two-tap confirm for the run command, like every $ command in the app.
    var runArmed by remember { mutableStateOf(false) }
    LaunchedEffect(runArmed) {
        if (runArmed) {
            delay(4_000)
            runArmed = false
        }
    }
    val lines = buildRulesLines(
        rules, units, userRulesEnabled, editing, dryRun, syntax, resources, actions,
        runArmed = runArmed,
        onRunLine = {
            if (runArmed) {
                runArmed = false
                actions.onRunRules()
            } else {
                runArmed = true
            }
        }
    )
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            EditorTabs(
                fileNames = SettingsFiles,
                activeIndex = 1,
                onSelect = onSelectFile
            )
            CodeCanvas(
                lines = lines,
                state = canvasState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            )
            TerminalStatusBar {
                Text("⎇ rules")
                StatusBarDivider()
                Text(stringResource(R.string.status_rules, rules.size))
                Spacer(Modifier.weight(1f))
                Text("UTF-8")
            }
        }
    }
}

private fun buildRulesLines(
    rules: List<NotificationRule>,
    units: UnitSettings,
    userRulesEnabled: Boolean,
    editing: RuleEdit?,
    dryRun: DryRunUi?,
    syntax: SyntaxColors,
    resources: Resources,
    actions: RulesActions,
    runArmed: Boolean,
    onRunLine: () -> Unit
): List<CanvasLine> = buildList {
    add(commentLine("// Tweather CI — user-defined notification rules", syntax))
    // The one cross-reference left of the (rejected) builtin unification
    add(commentLine("// builtin alerts (severe, precip, daily) live in settings.config", syntax))
    if (!userRulesEnabled) {
        add(
            CodeLine(
                AnnotatedString(
                    "// WARN: \"user_rules\" is false in settings.config — rules won't notify",
                    SpanStyle(color = syntax.diffDel)
                )
            )
        )
    }
    add(CodeLine(AnnotatedString("")))

    if (rules.isEmpty()) {
        add(commentLine("// no rules yet", syntax))
    }
    rules.forEachIndexed { i, rule ->
        if (i > 0) add(CodeLine(AnnotatedString("")))
        addAll(ruleBlock(rule, units, editing, dryRun, syntax, resources, actions))
    }

    add(CodeLine(AnnotatedString("")))
    if (rules.size < MaxRules) {
        add(
            CodeLine(
                AnnotatedString("+ add rule", SpanStyle(color = syntax.diffAdd)),
                onClick = actions.onAdd,
                onClickLabel = resources.getString(R.string.cd_add_rule)
            )
        )
    } else {
        add(commentLine("// max $MaxRules rules", syntax))
    }

    // The dry run: evaluate everything against current data, notify nothing.
    if (rules.isNotEmpty()) {
        add(CodeLine(AnnotatedString("")))
        add(commentLine("// run all rules against current data:", syntax))
        add(
            CodeLine(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = syntax.comment)) { append("$ ") }
                    append("tweather run rules")
                    if (runArmed) {
                        withStyle(SpanStyle(color = syntax.diffDel)) {
                            append("  // tap again to confirm")
                        }
                    }
                },
                onClick = onRunLine,
                onClickLabel = resources.getString(
                    if (runArmed) R.string.cd_confirm_run_rules else R.string.cd_run_rules
                )
            )
        )
        when (dryRun) {
            DryRunUi.Running ->
                add(commentLine("// evaluating against weather_data.json …", syntax))
            is DryRunUi.Error ->
                add(
                    CodeLine(
                        AnnotatedString(
                            "// ERROR: ${dryRun.message}",
                            SpanStyle(color = syntax.diffDel)
                        )
                    )
                )
            else -> Unit
        }
    }
}

/** One `rule "name" { … }` block, plus its inline picker and dry-run lines. */
private fun ruleBlock(
    rule: NotificationRule,
    units: UnitSettings,
    editing: RuleEdit?,
    dryRun: DryRunUi?,
    syntax: SyntaxColors,
    resources: Resources,
    actions: RulesActions
): List<CanvasLine> = buildList {
    add(
        WidgetLine(indent = 0) {
            RuleHeaderLine(
                rule = rule,
                editingName = editing == RuleEdit.Name(rule.id),
                onNameTap = { actions.onStartEdit(RuleEdit.Name(rule.id)) },
                onRename = { text ->
                    actions.onRename(rule, text)
                    actions.onStopEdit()
                },
                onRemove = { actions.onRemove(rule) }
            )
        }
    )

    add(
        CodeLine(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = syntax.key)) { append("enabled") }
                withStyle(SpanStyle(color = syntax.comment)) { append(": ") }
                withStyle(SpanStyle(color = syntax.number)) { append(rule.enabled.toString()) }
            },
            indent = 1,
            onClick = { actions.onToggleEnabled(rule) },
            onClickLabel = resources.getString(R.string.cd_toggle_rule, rule.name)
        )
    )

    rule.conditions.forEachIndexed { index, condition ->
        val pickerOpen = editing == RuleEdit.Variable(rule.id, index)
        add(
            WidgetLine(indent = 1) {
                ConditionLine(
                    keyword = if (index == 0) "if" else "and",
                    condition = condition,
                    units = units,
                    pickerOpen = pickerOpen,
                    editingThreshold = editing == RuleEdit.Threshold(rule.id, index),
                    removable = index > 0,
                    onVariableTap = {
                        if (pickerOpen) {
                            actions.onStopEdit()
                        } else {
                            actions.onStartEdit(RuleEdit.Variable(rule.id, index))
                        }
                    },
                    onOpTap = { actions.onCycleOp(rule, index) },
                    onThresholdTap = {
                        val kind = RuleVariables.byId(condition.variable)?.kind
                        if (kind == RuleVariableKind.BOOLEAN) {
                            actions.onToggleBooleanThreshold(rule, index)
                        } else {
                            actions.onStartEdit(RuleEdit.Threshold(rule.id, index))
                        }
                    },
                    onThresholdCommit = { text ->
                        actions.onSetThreshold(rule, index, text)
                        actions.onStopEdit()
                    },
                    onRemove = { actions.onRemoveCondition(rule) }
                )
            }
        )
        if (pickerOpen) {
            // The IDE autocomplete, drawn as lines: the finite variable registry
            RuleVariables.all.forEach { variable ->
                val display = RuleVariables.displayId(variable, units)
                add(
                    CodeLine(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = syntax.key)) { append(display) }
                            if (variable.id == condition.variable) {
                                withStyle(SpanStyle(color = syntax.comment)) {
                                    append("  // selected")
                                }
                            }
                        },
                        indent = 2,
                        onClick = {
                            actions.onSetVariable(rule, index, variable.id)
                            actions.onStopEdit()
                        },
                        onClickLabel = resources.getString(R.string.cd_pick_variable, display)
                    )
                )
            }
        }
    }

    if (rule.conditions.size == 1) {
        add(
            CodeLine(
                AnnotatedString("+ and …", SpanStyle(color = syntax.diffAdd)),
                indent = 1,
                onClick = { actions.onAddCondition(rule) },
                onClickLabel = resources.getString(R.string.cd_add_condition)
            )
        )
    }

    add(
        WidgetLine(indent = 1) {
            MessageLine(
                rule = rule,
                editing = editing == RuleEdit.Message(rule.id),
                onTap = { actions.onStartEdit(RuleEdit.Message(rule.id)) },
                onCommit = { text ->
                    actions.onSetMessage(rule, text)
                    actions.onStopEdit()
                }
            )
        }
    )

    (dryRun as? DryRunUi.Done)?.results?.get(rule.id)?.let { result ->
        add(
            when (result) {
                is DryRunResult.Fires -> CodeLine(
                    AnnotatedString(
                        "// ✗ notify: \"${result.message}\"",
                        SpanStyle(color = syntax.diffDel)
                    ),
                    indent = 1
                )
                DryRunResult.Passes -> CodeLine(
                    AnnotatedString("// ✓ pass", SpanStyle(color = syntax.diffAdd)),
                    indent = 1
                )
                is DryRunResult.Unavailable ->
                    commentLine("// ? unavailable: ${result.variable}", syntax, indent = 1)
            }
        )
    }

    add(CodeLine(AnnotatedString("}", SpanStyle(color = syntax.comment))))
}

/** `rule "umbrella" {   [rm]` — the name edits inline, `[rm]` deletes the rule. */
@Composable
private fun RuleHeaderLine(
    rule: NotificationRule,
    editingName: Boolean,
    onNameTap: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit
) {
    val syntax = TweatherTheme.syntax
    val style = MaterialTheme.typography.bodySmall
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("rule ", style = style, color = syntax.key)
        Text("\"", style = style, color = syntax.string)
        if (editingName) {
            InlineEditor(initial = rule.name, onCommit = onRename)
        } else {
            Text(
                text = rule.name,
                style = style,
                color = syntax.string,
                modifier = Modifier.clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.cd_edit_rule_name, rule.name)
                ) { onNameTap() }
            )
        }
        Text("\"", style = style, color = syntax.string)
        Text(" {", style = style, color = syntax.comment)
        Text(
            text = "[rm]",
            style = style,
            color = syntax.diffDel,
            modifier = Modifier
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.cd_remove_rule, rule.name)
                ) { onRemove() }
                .padding(horizontal = 8.dp)
        )
    }
}

/** `if: next_6h.precip_chance_max >= 60` — three tokens, three separate taps. */
@Composable
private fun ConditionLine(
    keyword: String,
    condition: RuleCondition,
    units: UnitSettings,
    pickerOpen: Boolean,
    editingThreshold: Boolean,
    removable: Boolean,
    onVariableTap: () -> Unit,
    onOpTap: () -> Unit,
    onThresholdTap: () -> Unit,
    onThresholdCommit: (String) -> Unit,
    onRemove: () -> Unit
) {
    val syntax = TweatherTheme.syntax
    val style = MaterialTheme.typography.bodySmall
    val variable = RuleVariables.byId(condition.variable)
    val kind = variable?.kind ?: RuleVariableKind.NUMBER
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$keyword: ", style = style, color = syntax.key)
        Text(
            text = variable?.let { RuleVariables.displayId(it, units) } ?: condition.variable,
            style = style,
            // Identifiers are default-colored like in an editor; primary marks the
            // one whose autocomplete is open
            color = if (pickerOpen) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.cd_edit_variable)
            ) { onVariableTap() }
        )
        Text(
            text = condition.op.symbol,
            style = style,
            color = syntax.comment,
            modifier = Modifier
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.cd_cycle_operator)
                ) { onOpTap() }
                .padding(horizontal = 6.dp)
        )
        if (editingThreshold) {
            InlineEditor(
                initial = RuleVariables.formatValue(kind, condition.threshold, units),
                onCommit = onThresholdCommit
            )
        } else {
            Text(
                text = RuleVariables.formatValue(kind, condition.threshold, units),
                style = style,
                color = syntax.number,
                modifier = Modifier.clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.cd_edit_threshold)
                ) { onThresholdTap() }
            )
        }
        if (removable) {
            Text(
                text = "[rm]",
                style = style,
                color = syntax.diffDel,
                modifier = Modifier
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.cd_remove_condition)
                    ) { onRemove() }
                    .padding(horizontal = 8.dp)
            )
        }
    }
}

/** `notify: "Take an umbrella — {trigger.value}%"` — the message edits inline. */
@Composable
private fun MessageLine(
    rule: NotificationRule,
    editing: Boolean,
    onTap: () -> Unit,
    onCommit: (String) -> Unit
) {
    val syntax = TweatherTheme.syntax
    val style = MaterialTheme.typography.bodySmall
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("notify: ", style = style, color = syntax.key)
        Text("\"", style = style, color = syntax.string)
        if (editing) {
            InlineEditor(initial = rule.message, onCommit = onCommit)
        } else {
            Text(
                text = rule.message,
                style = style,
                color = syntax.string,
                modifier = Modifier.clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.cd_edit_message)
                ) { onTap() }
            )
        }
        Text("\"", style = style, color = syntax.string)
    }
}

/** A token replaced in place by a terminal input; IME Done commits. */
@Composable
private fun InlineEditor(
    initial: String,
    onCommit: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TerminalInput(
        value = text,
        onValueChange = { text = it },
        prompt = "",
        modifier = Modifier.focusRequester(focusRequester),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) })
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 640)
@Composable
private fun RulesScreenPreview() {
    TweatherTheme {
        RulesScreen(
            rules = listOf(
                NotificationRule(
                    id = 1,
                    name = "umbrella",
                    enabled = true,
                    conditions = listOf(
                        RuleCondition("next_6h.precip_chance_max", RuleOp.GTE, 60.0)
                    ),
                    message = "Take an umbrella — {trigger.value}% rain at {trigger.time}"
                ),
                NotificationRule(
                    id = 2,
                    name = "sunscreen",
                    enabled = false,
                    conditions = listOf(
                        RuleCondition("current.uv_index", RuleOp.GTE, 7.0),
                        RuleCondition("today.high_c", RuleOp.GT, 25.0)
                    ),
                    message = "Sunscreen time — UV {current.uv_index}"
                )
            ),
            units = UnitSettings(),
            userRulesEnabled = true,
            editing = null,
            dryRun = null,
            actions = RulesActions(
                {}, {}, {}, {}, {}, { _, _ -> }, { _, _, _ -> }, { _, _ -> },
                { _, _, _ -> }, { _, _ -> }, {}, {}, { _, _ -> }, {}
            )
        )
    }
}
