package com.callbackdev.tweather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.skyAlertsDataStore by preferencesDataStore(name = "sky_alerts")

/**
 * Fingerprints of the sky reminders already posted (Fase 16f) — one notification per
 * job per occurrence, in the same shape [AlertStateStore] and [RuleStateStore] use.
 *
 * Deliberately its own DataStore, for the reason the other two are: [SettingsStore]'s
 * setters bump the user-visible `// Last modified:` header of `settings.config`, and
 * this is engine bookkeeping, not something the user edited.
 *
 * Bounded and newest-first. A reminder fires at most a few times a day, so the bound
 * is generous — it is here because a set that only grows is a slow leak that never
 * announces itself.
 */
class SkyAlertStateStore(private val dataStore: DataStore<Preferences>) {

    val posted: Flow<Set<String>> = dataStore.data
        .map { it[Posted].toFingerprints() }
        .distinctUntilChanged()

    suspend fun wasPosted(fingerprint: String): Boolean = posted.first().contains(fingerprint)

    /** Called only after a successful post: an unposted reminder can still fire. */
    suspend fun record(fingerprint: String) {
        dataStore.edit { prefs ->
            prefs[Posted] = (listOf(fingerprint) + prefs[Posted].toFingerprints())
                .distinct()
                .take(MAX_FINGERPRINTS)
                .joinToString(SEPARATOR)
        }
    }

    private fun String?.toFingerprints(): Set<String> =
        this?.split(SEPARATOR)?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    companion object {
        private val Posted = stringPreferencesKey("posted_fingerprints")
        private const val SEPARATOR = "|"
        private const val MAX_FINGERPRINTS = 40

        fun create(context: Context) = SkyAlertStateStore(context.skyAlertsDataStore)
    }
}
