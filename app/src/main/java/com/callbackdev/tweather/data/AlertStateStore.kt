package com.callbackdev.tweather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.callbackdev.tweather.domain.Alert
import com.callbackdev.tweather.domain.AlertKind
import com.callbackdev.tweather.domain.AlertState
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.alertsDataStore by preferencesDataStore(name = "alerts")

/**
 * Fingerprints of the recently notified alerts, one key per [AlertKind]. Severe and
 * precipitation keep a newest-first bounded list (joined string, so a pre-existing
 * single-fingerprint value reads back as a list of one). Deliberately its own
 * DataStore: [SettingsStore]'s setters bump the user-visible `// Last modified:`
 * header, and this is engine bookkeeping, not a user edit.
 */
class AlertStateStore(private val dataStore: DataStore<Preferences>) {

    val state: Flow<AlertState> = dataStore.data
        .map { prefs ->
            AlertState(
                severeFingerprints = prefs[SevereFingerprint].toFingerprints(),
                precipFingerprints = prefs[PrecipFingerprint].toFingerprints(),
                summaryDate = prefs[SummaryDate]?.let {
                    runCatching { LocalDate.parse(it) }.getOrNull()
                }
            )
        }
        .distinctUntilChanged()

    /** Called only after a successful notify — an unposted alert can retry later. */
    suspend fun record(alert: Alert) {
        dataStore.edit { prefs ->
            when (alert.kind) {
                AlertKind.SEVERE ->
                    prefs[SevereFingerprint] = prepend(prefs[SevereFingerprint], alert.fingerprint)
                AlertKind.PRECIPITATION ->
                    prefs[PrecipFingerprint] = prepend(prefs[PrecipFingerprint], alert.fingerprint)
                // The summary fingerprint IS the ISO date (see AlertEngine)
                AlertKind.DAILY_SUMMARY -> prefs[SummaryDate] = alert.fingerprint
            }
        }
    }

    private fun String?.toFingerprints(): Set<String> =
        this?.split(SEPARATOR)?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    private fun prepend(existing: String?, fingerprint: String): String =
        (listOf(fingerprint) + existing.toFingerprints())
            .distinct()
            .take(MAX_FINGERPRINTS)
            .joinToString(SEPARATOR)

    companion object {
        private val SevereFingerprint = stringPreferencesKey("alert_fp_severe")
        private val PrecipFingerprint = stringPreferencesKey("alert_fp_precip")
        private val SummaryDate = stringPreferencesKey("alert_summary_date")

        /** Enough for several cities × hazard buckets over the ~2 days a fingerprint
         * stays relevant (each embeds its date); older ones fall off the end. */
        private const val MAX_FINGERPRINTS = 16

        /** Never appears in a fingerprint (city keys, buckets and ISO dates). */
        private const val SEPARATOR = "\n"

        fun create(context: Context) = AlertStateStore(context.alertsDataStore)
    }
}
