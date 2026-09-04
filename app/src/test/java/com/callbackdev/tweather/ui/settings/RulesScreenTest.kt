package com.callbackdev.tweather.ui.settings

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.callbackdev.tweather.data.AppSettings
import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.data.UnitSettings
import com.callbackdev.tweather.domain.rules.NotificationRule
import com.callbackdev.tweather.domain.rules.RuleCondition
import com.callbackdev.tweather.domain.rules.RuleOp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The `alerts.rules` file: token editing, picker, dry run (Fase 11). */
@RunWith(RobolectricTestRunner::class)
class RulesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val umbrella = NotificationRule(
        id = 1,
        name = "umbrella",
        enabled = true,
        conditions = listOf(RuleCondition("next_6h.precip_chance_max", RuleOp.GTE, 60.0)),
        message = "Take an umbrella"
    )

    private class Recorder {
        var startedEdit: RuleEdit? = null
        var added = 0
        var removed: NotificationRule? = null
        var toggled: NotificationRule? = null
        var variableSet: Triple<Long, Int, String>? = null
        var opCycled: Pair<Long, Int>? = null
        var conditionAdded: NotificationRule? = null
        var conditionRemoved: NotificationRule? = null
        var ran = 0

        val actions = RulesActions(
            onStartEdit = { startedEdit = it },
            onStopEdit = {},
            onAdd = { added++ },
            onRemove = { removed = it },
            onToggleEnabled = { toggled = it },
            onRename = { _, _ -> },
            onSetVariable = { rule, index, id -> variableSet = Triple(rule.id, index, id) },
            onCycleOp = { rule, index -> opCycled = rule.id to index },
            onSetThreshold = { _, _, _ -> },
            onToggleBooleanThreshold = { _, _ -> },
            onAddCondition = { conditionAdded = it },
            onRemoveCondition = { conditionRemoved = it },
            onSetMessage = { _, _ -> },
            onRunRules = { ran++ }
        )
    }

    private fun setScreen(
        rules: List<NotificationRule> = listOf(umbrella),
        units: UnitSettings = UnitSettings(),
        userRulesEnabled: Boolean = true,
        editing: RuleEdit? = null,
        dryRun: DryRunUi? = null,
        recorder: Recorder = Recorder()
    ): Recorder {
        compose.setContent {
            com.callbackdev.tweather.ui.theme.TweatherTheme {
                RulesScreen(
                    rules = rules,
                    units = units,
                    userRulesEnabled = userRulesEnabled,
                    editing = editing,
                    dryRun = dryRun,
                    actions = recorder.actions
                )
            }
        }
        return recorder
    }

    private fun onLine(text: String, substring: Boolean = false): SemanticsNodeInteraction {
        compose.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(text, substring = substring))
        return compose.onNode(hasText(text, substring = substring)).assertExists()
    }

    @Test
    fun `a rule renders as tokens of source`() {
        setScreen()
        compose.onNodeWithText("umbrella").assertExists()
        compose.onNodeWithText("enabled: true").assertExists()
        compose.onNodeWithText("if: ").assertExists()
        compose.onNodeWithText("next_6h.precip_chance_max").assertExists()
        compose.onNodeWithText(">=").assertExists()
        compose.onNodeWithText("60").assertExists()
        compose.onNodeWithText("Take an umbrella").assertExists()
        compose.onNodeWithText("+ and …").assertExists()
    }

    @Test
    fun `variable names and thresholds render in the user's units`() {
        val cold = umbrella.copy(
            conditions = listOf(RuleCondition("current.temp_c", RuleOp.LT, 5.0))
        )
        setScreen(
            rules = listOf(cold),
            units = UnitSettings(temperature = TemperatureUnit.FAHRENHEIT)
        )
        compose.onNodeWithText("current.temp_f").assertExists()
        compose.onNodeWithText("41").assertExists() // 5 °C shown as 41 °F
    }

    @Test
    fun `enabled toggles, header removes, add appends`() {
        val recorder = setScreen()
        compose.onNodeWithText("enabled: true").performClick()
        assertEquals(umbrella, recorder.toggled)
        compose.onNodeWithText("[rm]").performClick()
        assertEquals(umbrella, recorder.removed)
        onLine("+ add rule").performClick()
        assertEquals(1, recorder.added)
    }

    @Test
    fun `tapping the variable asks for its picker`() {
        val recorder = setScreen()
        compose.onNodeWithText("next_6h.precip_chance_max").performClick()
        assertEquals(RuleEdit.Variable(1, 0), recorder.startedEdit)
    }

    @Test
    fun `the open picker lists the registry and picks on tap`() {
        val recorder = setScreen(editing = RuleEdit.Variable(1, 0))
        onLine("current.uv_index").performClick()
        assertEquals(Triple(1L, 0, "current.uv_index"), recorder.variableSet)
        // the current variable is marked
        onLine("next_6h.precip_chance_max  // selected")
    }

    @Test
    fun `the operator cycles on tap`() {
        val recorder = setScreen()
        compose.onNodeWithText(">=").performClick()
        assertEquals(1L to 0, recorder.opCycled)
    }

    @Test
    fun `a second condition is one tap away`() {
        val recorder = setScreen()
        compose.onNodeWithText("+ and …").performClick()
        assertEquals(umbrella, recorder.conditionAdded)
    }

    @Test
    fun `the and condition renders and removes with its own rm`() {
        val two = umbrella.copy(
            conditions = umbrella.conditions +
                RuleCondition("current.temp_c", RuleOp.LT, 20.0)
        )
        val recorder = setScreen(rules = listOf(two))
        compose.onNodeWithText("and: ").assertExists()
        // the second [rm] belongs to the and-condition (the first is the rule's)
        compose.onAllNodes(hasText("[rm]"))[1].performClick()
        assertEquals(two, recorder.conditionRemoved)
    }

    @Test
    fun `the run command wants a second tap, then runs`() {
        val recorder = setScreen()
        onLine("tweather run rules", substring = true).performClick()
        assertEquals(0, recorder.ran)
        onLine("// tap again to confirm", substring = true).performClick()
        assertEquals(1, recorder.ran)
    }

    @Test
    fun `dry run results render one check line per rule`() {
        setScreen(
            rules = listOf(umbrella, umbrella.copy(id = 2, name = "sunscreen")),
            dryRun = DryRunUi.Done(
                mapOf(
                    1L to DryRunResult.Fires("Take an umbrella — 78%"),
                    2L to DryRunResult.Passes
                )
            )
        )
        onLine("// ✗ notify: \"Take an umbrella — 78%\"")
        onLine("// ✓ pass")
    }

    /**
     * The completion inside the string: `RuleMessages` interpolates every name of
     * the registry, and the file is the only place that can say so.
     */
    @Test
    fun `the message picker lists every placeholder and inserts one on tap`() {
        setScreen(editing = RuleEdit.Message(1))
        onLine("// tap a value to put it in the message", substring = true)
        onLine("{trigger.value}")
        onLine("{trigger.time}")
        onLine("{today.uv_max}")
        onLine("{trigger.value}").performClick()
        // The draft, not the stored rule: nothing is committed until IME Done.
        onLine("Take an umbrella{trigger.value}")
    }

    @Test
    fun `a placeholder is offered in the reader's units`() {
        setScreen(
            editing = RuleEdit.Message(1),
            units = UnitSettings(temperature = TemperatureUnit.FAHRENHEIT)
        )
        onLine("{current.temp_f}")
    }

    @Test
    fun `a disabled master toggle warns at the top of the file`() {
        setScreen(userRulesEnabled = false)
        onLine("// WARN: \"user_rules\" is false in settings.config — rules won't notify")
    }

    @Test
    fun `empty file explains itself and hides the run command`() {
        setScreen(rules = emptyList())
        onLine("// no rules yet")
        compose.onNode(hasText("tweather run rules", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `settings config exposes the rules tab and the user_rules toggle`() {
        var selected = -1
        compose.setContent {
            com.callbackdev.tweather.ui.theme.TweatherTheme {
                SettingsScreen(
                    settings = AppSettings(),
                    actions = SettingsActions(
                        {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}
                    ),
                    onSelectFile = { selected = it }
                )
            }
        }
        compose.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("\"user_rules\": true  // alerts.rules"))
        compose.onNodeWithText("\"user_rules\": true  // alerts.rules").assertExists()
        compose.onNodeWithText("alerts.rules").performClick()
        assertEquals(1, selected)
    }


    /**
     * `alerts.rules` under the register rule (Fase 18). The banner is the file's
     * own signature and stays; the cross-reference under it is a sentence and
     * moves, with `settings.config` and the three builtin kinds coming through it
     * unchanged — they are what the reader would go looking for.
     */
    @Test
    @Config(qualifiers = "it")
    fun `the banner stays and the sentence under it speaks Italian`() {
        setScreen(rules = emptyList())
        onLine("// Tweather CI — user-defined notification rules")
        onLine("// gli avvisi predefiniti (severe, precip, daily) stanno in settings.config")
        onLine("// ancora nessuna regola")
    }

}
