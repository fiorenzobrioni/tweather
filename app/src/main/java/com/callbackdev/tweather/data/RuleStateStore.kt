package com.callbackdev.tweather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.callbackdev.tweather.domain.rules.RuleEngineState
import com.callbackdev.tweather.domain.rules.RuleTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.ruleStateDataStore by preferencesDataStore(name = "rule_state")

/**
 * RuleEngine bookkeeping (Fase 11), alongside [AlertStateStore] and separate from
 * [SettingsStore] for the same reason: it must not bump the user-visible
 * `// Last modified:` header. Latches persist as a joined set, fingerprints as a
 * newest-first bounded list.
 */
class RuleStateStore(private val dataStore: DataStore<Preferences>) {

    val state: Flow<RuleEngineState> = dataStore.data
        .map { prefs ->
            RuleEngineState(
                latched = prefs[Latched].toEntries(),
                firedFingerprints = prefs[Fingerprints].toEntries()
            )
        }
        .distinctUntilChanged()

    /** Called only after a successful notify — an unposted trigger can retry later. */
    suspend fun record(trigger: RuleTrigger) {
        dataStore.edit { prefs ->
            trigger.latchKey?.let { key ->
                prefs[Latched] = (prefs[Latched].toEntries() + key).joinToString(SEPARATOR)
            }
            trigger.fingerprint?.let { fingerprint ->
                prefs[Fingerprints] = (listOf(fingerprint) + prefs[Fingerprints].toEntries())
                    .distinct()
                    .take(MAX_FINGERPRINTS)
                    .joinToString(SEPARATOR)
            }
        }
    }

    /** Re-arms edge-triggered rules whose condition reads false again. */
    suspend fun unlatch(keys: Set<String>) {
        if (keys.isEmpty()) return
        dataStore.edit { prefs ->
            prefs[Latched] = (prefs[Latched].toEntries() - keys).joinToString(SEPARATOR)
        }
    }

    /**
     * Editing a rule's conditions makes its state stale (a still-latched rule with
     * a new threshold would stay silent); the UI clears it on every condition edit
     * and on removal. Latch keys are `cityKey:ruleId`, fingerprints embed
     * `:rule:<id>:`.
     */
    suspend fun clearRule(ruleId: Long) {
        dataStore.edit { prefs ->
            prefs[Latched] = prefs[Latched].toEntries()
                .filterNot { it.endsWith(":$ruleId") }
                .joinToString(SEPARATOR)
            prefs[Fingerprints] = prefs[Fingerprints].toEntries()
                .filterNot { it.contains(":rule:$ruleId:") }
                .joinToString(SEPARATOR)
        }
    }

    private fun String?.toEntries(): Set<String> =
        this?.split(SEPARATOR)?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    companion object {
        private val Latched = stringPreferencesKey("rule_latched")
        private val Fingerprints = stringPreferencesKey("rule_fingerprints")

        /** 10 rules × a few half-day buckets before the dated entries go stale. */
        private const val MAX_FINGERPRINTS = 32

        /** Never appears in latch keys or fingerprints. */
        private const val SEPARATOR = "\n"

        fun create(context: Context) = RuleStateStore(context.ruleStateDataStore)
    }
}
