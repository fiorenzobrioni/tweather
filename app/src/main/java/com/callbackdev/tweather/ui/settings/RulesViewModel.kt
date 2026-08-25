package com.callbackdev.tweather.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.callbackdev.tweather.data.ActiveSource
import com.callbackdev.tweather.data.AppSettings
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.data.RuleStateStore
import com.callbackdev.tweather.data.RuleStore
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.data.SettingsStore
import com.callbackdev.tweather.data.WeatherRepository
import com.callbackdev.tweather.domain.WeatherException
import com.callbackdev.tweather.domain.rules.MaxConditions
import com.callbackdev.tweather.domain.rules.NotificationRule
import com.callbackdev.tweather.domain.rules.RuleCheck
import com.callbackdev.tweather.domain.rules.RuleCondition
import com.callbackdev.tweather.domain.rules.RuleEngine
import com.callbackdev.tweather.domain.rules.RuleMessages
import com.callbackdev.tweather.domain.rules.RuleOp
import com.callbackdev.tweather.domain.rules.RuleVariableKind
import com.callbackdev.tweather.domain.rules.RuleVariables
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which token of `alerts.rules` is being edited inline (one at a time). */
sealed interface RuleEdit {
    val ruleId: Long

    data class Name(override val ruleId: Long) : RuleEdit
    data class Variable(override val ruleId: Long, val index: Int) : RuleEdit
    data class Threshold(override val ruleId: Long, val index: Int) : RuleEdit
    data class Message(override val ruleId: Long) : RuleEdit
}

/** One rule's dry-run outcome — the inline `//` line under its block. */
sealed interface DryRunResult {
    /** The rule would notify; [message] is already interpolated. */
    data class Fires(val message: String) : DryRunResult
    data object Passes : DryRunResult
    data class Unavailable(val variable: String) : DryRunResult
}

/** `$ tweather run rules` state; null = never run (or invalidated by an edit). */
sealed interface DryRunUi {
    data object Running : DryRunUi
    data class Done(val results: Map<Long, DryRunResult>) : DryRunUi
    data class Error(val message: String) : DryRunUi
}

/**
 * State and actions of the `alerts.rules` tab (Fase 11). Every condition edit also
 * clears the rule's engine state (a still-latched rule with a new threshold would
 * stay silent) and any dry-run results (they describe rules that no longer exist).
 */
class RulesViewModel(
    private val ruleStore: RuleStore,
    private val ruleStateStore: RuleStateStore,
    private val settingsStore: SettingsStore,
    private val cityStore: CityStore,
    private val repository: WeatherRepository
) : ViewModel() {

    val rules: StateFlow<List<NotificationRule>> = ruleStore.rules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _editing = MutableStateFlow<RuleEdit?>(null)
    val editing: StateFlow<RuleEdit?> = _editing

    private val _dryRun = MutableStateFlow<DryRunUi?>(null)
    val dryRun: StateFlow<DryRunUi?> = _dryRun

    fun startEdit(edit: RuleEdit) {
        _editing.value = edit
    }

    fun stopEdit() {
        _editing.value = null
    }

    fun addRule() = mutate { ruleStore.add() }

    fun removeRule(rule: NotificationRule) = mutate {
        ruleStore.remove(rule.id)
        ruleStateStore.clearRule(rule.id)
        if (_editing.value?.ruleId == rule.id) _editing.value = null
    }

    fun toggleEnabled(rule: NotificationRule) = mutate {
        ruleStore.update(rule.copy(enabled = !rule.enabled))
    }

    /** Commits the name editor; empty/garbage input falls back to `rule_<id>`. */
    fun rename(rule: NotificationRule, raw: String) = mutate {
        ruleStore.update(rule.copy(name = sanitizeName(raw, rule.id)))
    }

    fun setVariable(rule: NotificationRule, index: Int, variableId: String) = mutate {
        val kind = RuleVariables.byId(variableId)?.kind ?: return@mutate
        val old = rule.conditions.getOrNull(index) ?: return@mutate
        val oldKind = RuleVariables.byId(old.variable)?.kind
        val condition = when {
            // A boolean only compares for equality, and the only sane threshold is true
            kind == RuleVariableKind.BOOLEAN ->
                old.copy(
                    variable = variableId,
                    op = if (old.op == RuleOp.NEQ) RuleOp.NEQ else RuleOp.EQ,
                    threshold = 1.0
                )
            // Leaving a boolean: 0/1 is meaningless as a numeric threshold
            oldKind == RuleVariableKind.BOOLEAN ->
                old.copy(variable = variableId, threshold = 0.0)
            else -> old.copy(variable = variableId)
        }
        updateCondition(rule, index, condition)
    }

    fun cycleOp(rule: NotificationRule, index: Int) = mutate {
        val condition = rule.conditions.getOrNull(index) ?: return@mutate
        val kind = RuleVariables.byId(condition.variable)?.kind
        val op = if (kind == RuleVariableKind.BOOLEAN) {
            condition.op.nextBoolean()
        } else {
            condition.op.next()
        }
        updateCondition(rule, index, condition.copy(op = op))
    }

    /** Commits the threshold editor: [raw] is in the user's units ("," accepted as
     * decimal separator); unparseable input leaves the stored value untouched. */
    fun setThreshold(rule: NotificationRule, index: Int, raw: String) = mutate {
        val condition = rule.conditions.getOrNull(index) ?: return@mutate
        val displayed = raw.trim().replace(',', '.').toDoubleOrNull() ?: return@mutate
        val kind = RuleVariables.byId(condition.variable)?.kind ?: return@mutate
        val canonical = RuleVariables.canonicalValue(kind, displayed, settings.value.units)
        updateCondition(rule, index, condition.copy(threshold = canonical))
    }

    /** Boolean thresholds don't open an editor — `true`/`false` flips on tap. */
    fun toggleBooleanThreshold(rule: NotificationRule, index: Int) = mutate {
        val condition = rule.conditions.getOrNull(index) ?: return@mutate
        updateCondition(
            rule, index,
            condition.copy(threshold = if (condition.threshold != 0.0) 0.0 else 1.0)
        )
    }

    fun addCondition(rule: NotificationRule) = mutate {
        if (rule.conditions.size >= MaxConditions) return@mutate
        val added = rule.copy(
            conditions = rule.conditions + RuleCondition("current.temp_c", RuleOp.LT, 0.0)
        )
        ruleStore.update(added)
        ruleStateStore.clearRule(rule.id)
    }

    fun removeCondition(rule: NotificationRule) = mutate {
        if (rule.conditions.size <= 1) return@mutate
        ruleStore.update(rule.copy(conditions = rule.conditions.take(1)))
        ruleStateStore.clearRule(rule.id)
    }

    fun setMessage(rule: NotificationRule, raw: String) = mutate {
        val message = raw.replace("\n", " ").trim().take(MAX_MESSAGE_LENGTH)
        ruleStore.update(rule.copy(message = message.ifEmpty { rule.message }))
    }

    /**
     * `$ tweather run rules`: evaluates every rule (disabled ones too — the file is
     * being tested, not the scheduler) against the current data of the active
     * source, statelessly: no fingerprints burned, no latches touched, nothing
     * posted. Uses the normal cache/TTL path, so it usually costs zero GETs.
     */
    fun runRules() {
        if (_dryRun.value == DryRunUi.Running) return
        viewModelScope.launch {
            _dryRun.value = DryRunUi.Running
            try {
                val source = cityStore.activeSource.first()
                if (source is ActiveSource.None) {
                    _dryRun.value = DryRunUi.Error("no location configured")
                    return@launch
                }
                val city = when (source) {
                    is ActiveSource.Saved -> source.city
                    is ActiveSource.Gps -> source.lastFix
                    ActiveSource.None -> null
                }
                if (city == null) {
                    _dryRun.value = DryRunUi.Error("gps::no position fix yet")
                    return@launch
                }
                val appSettings = settingsStore.settings.first()
                val report = repository.getWeather(
                    city,
                    ttl = Duration.ofMinutes(appSettings.updateFrequencyMin.toLong())
                )
                val zone = runCatching { ZoneId.of(report.location.timezone) }
                    .getOrDefault(ZoneId.systemDefault())
                val now = ZonedDateTime.now(zone).toLocalDateTime()
                val results = ruleStore.rules.first().associate { rule ->
                    rule.id to when (val check = RuleEngine.check(rule, report, now)) {
                        is RuleCheck.Fires -> DryRunResult.Fires(
                            RuleMessages.interpolate(
                                rule.message, rule, check.value, check.at,
                                report, now, appSettings.units
                            )
                        )
                        RuleCheck.Passes -> DryRunResult.Passes
                        is RuleCheck.Unavailable -> DryRunResult.Unavailable(check.variable)
                    }
                }
                _dryRun.value = DryRunUi.Done(results)
            } catch (e: CancellationException) {
                throw e
            } catch (e: WeatherException) {
                _dryRun.value = DryRunUi.Error(e.terminalMessage)
            } catch (e: Throwable) {
                // A dry run must never crash the app: whatever blew up becomes an
                // error line in the file, naming the exception — the terminal way
                // to turn "it crashed" into a diagnosis. Throwable, not Exception:
                // a class-initialization failure surfaces as an Error.
                _dryRun.value = DryRunUi.Error("panic: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private suspend fun updateCondition(
        rule: NotificationRule,
        index: Int,
        condition: RuleCondition
    ) {
        ruleStore.update(
            rule.copy(
                conditions = rule.conditions.mapIndexed { i, c -> if (i == index) condition else c }
            )
        )
        ruleStateStore.clearRule(rule.id)
    }

    /** Every mutation invalidates dry-run results: they describe the old rules. */
    private fun mutate(block: suspend () -> Unit) {
        _dryRun.value = null
        viewModelScope.launch { block() }
    }

    companion object {
        private const val MAX_MESSAGE_LENGTH = 120

        /**
         * Rule names are slugs by design (they name a command and a fingerprint):
         * lowercased, spaces to underscores, `[a-z0-9_-]` only, bounded length.
         */
        fun sanitizeName(raw: String, ruleId: Long): String =
            raw.trim().lowercase()
                .replace(' ', '_')
                .filter { it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' }
                .take(24)
                .ifEmpty { "rule_$ruleId" }

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[AndroidViewModelFactory.APPLICATION_KEY])
                RulesViewModel(
                    ruleStore = ServiceLocator.ruleStore(app),
                    ruleStateStore = ServiceLocator.ruleStateStore(app),
                    settingsStore = ServiceLocator.settingsStore(app),
                    cityStore = ServiceLocator.cityStore(app),
                    repository = ServiceLocator.weatherRepository(app)
                )
            }
        }
    }
}
