package com.callbackdev.chiaro.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.callbackdev.chiaro.domain.rules.MaxRules
import com.callbackdev.chiaro.domain.rules.NotificationRule
import com.callbackdev.chiaro.domain.rules.RuleCondition
import com.callbackdev.chiaro.domain.rules.RuleOp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.rulesDataStore by preferencesDataStore(name = "rules")

/**
 * The user's `alerts.rules` file (Fase 11): the rule list as a JSON array plus a
 * monotonic id counter, same DataStore pattern as the saved cities. Ids never
 * recycle — they key notification ids, latch keys and fingerprints, and a new rule
 * must never inherit a removed rule's state.
 */
class RuleStore(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {

    val rules: Flow<List<NotificationRule>> = dataStore.data.map(::decode).distinctUntilChanged()

    /** Appends a template rule the user then edits token by token; no-op at the
     * [MaxRules] ceiling (the UI hides `+ add rule` there too). */
    suspend fun add() {
        dataStore.edit { prefs ->
            val rules = decode(prefs)
            if (rules.size >= MaxRules) return@edit
            val id = prefs[NextRuleId] ?: 1L
            prefs[NextRuleId] = id + 1
            prefs[RulesJson] = json.encodeToString(rules + template(id))
        }
    }

    suspend fun update(rule: NotificationRule) {
        dataStore.edit { prefs ->
            val rules = decode(prefs)
            prefs[RulesJson] = json.encodeToString(rules.map { if (it.id == rule.id) rule else it })
        }
    }

    suspend fun remove(id: Long) {
        dataStore.edit { prefs ->
            prefs[RulesJson] = json.encodeToString(decode(prefs).filterNot { it.id == id })
        }
    }

    private fun decode(prefs: Preferences): List<NotificationRule> =
        prefs[RulesJson]
            ?.let { runCatching { json.decodeFromString<List<NotificationRule>>(it) }.getOrNull() }
            ?: emptyList()

    companion object {
        private val RulesJson = stringPreferencesKey("rules_json")
        private val NextRuleId = longPreferencesKey("next_rule_id")

        /** New rules start as the umbrella example — a working rule to edit beats
         * an empty skeleton to fill in. */
        fun template(id: Long) = NotificationRule(
            id = id,
            name = "rule_$id",
            enabled = true,
            conditions = listOf(
                RuleCondition("next_6h.precip_chance_max", RuleOp.GTE, 60.0)
            ),
            message = "Take an umbrella — {trigger.value}% rain at {trigger.time}"
        )

        fun create(context: Context, json: Json) = RuleStore(context.rulesDataStore, json)
    }
}
