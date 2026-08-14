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
 * Fingerprints of the last notified alerts, one key per [AlertKind]. Deliberately
 * its own DataStore: [SettingsStore]'s setters bump the user-visible
 * `// Last modified:` header, and this is engine bookkeeping, not a user edit.
 */
class AlertStateStore(private val dataStore: DataStore<Preferences>) {

    val state: Flow<AlertState> = dataStore.data
        .map { prefs ->
            AlertState(
                severeFingerprint = prefs[SevereFingerprint],
                precipFingerprint = prefs[PrecipFingerprint],
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
                AlertKind.SEVERE -> prefs[SevereFingerprint] = alert.fingerprint
                AlertKind.PRECIPITATION -> prefs[PrecipFingerprint] = alert.fingerprint
                // The summary fingerprint IS the ISO date (see AlertEngine)
                AlertKind.DAILY_SUMMARY -> prefs[SummaryDate] = alert.fingerprint
            }
        }
    }

    companion object {
        private val SevereFingerprint = stringPreferencesKey("alert_fp_severe")
        private val PrecipFingerprint = stringPreferencesKey("alert_fp_precip")
        private val SummaryDate = stringPreferencesKey("alert_summary_date")

        fun create(context: Context) = AlertStateStore(context.alertsDataStore)
    }
}
