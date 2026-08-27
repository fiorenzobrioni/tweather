package com.callbackdev.tweather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.callbackdev.tweather.domain.sky.SkyJobCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.skyDataStore by preferencesDataStore(name = "sky")

/**
 * One line of `sky.crontab`: which catalog job, whether its line is commented out,
 * and its own reminder lead if it has one.
 *
 * [notifyLeadMinutes] null means "follow `notify_default`", not "no reminder" —
 * every other value is an override this line was given by tapping its `--notify`
 * token (Fase 16f).
 */
@Serializable
data class SkySubscription(
    val jobId: String,
    val enabled: Boolean = true,
    val notifyLeadMinutes: Int? = null
)

/**
 * The user's `sky.crontab` (Fase 16c): which jobs are in the file and which of them
 * are commented out. Same DataStore-plus-JSON shape as [RuleStore] and [CityStore].
 *
 * **Subscriptions are global, not per city** (`VISION_SKY.md` §13). Somebody who
 * cares about golden hour cares about it in every city; what is per city is the
 * schedule those subscriptions RESOLVE to, which is computed and never stored. The
 * run history of Fase 16e will be per city — that one is a record of something that
 * happened somewhere.
 *
 * First run seeds [SkyJobCatalog.defaults], four lines. The seeding is marked with
 * its own flag rather than inferred from an empty list: without it, removing the
 * last job would look exactly like a fresh install and the four defaults would grow
 * back — the same distinction `CityStore.migrateFirstRun` draws between "new" and
 * "emptied".
 */
class SkySubscriptionStore(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {

    val subscriptions: Flow<List<SkySubscription>> = dataStore.data
        .map(::decode)
        .distinctUntilChanged()

    /** Adds [jobId] to the file, enabled; a no-op when it is already a line. */
    suspend fun add(jobId: String) {
        if (SkyJobCatalog.byId(jobId) == null) return
        edit { current ->
            if (current.any { it.jobId == jobId }) current
            else current + SkySubscription(jobId)
        }
    }

    /** Removes the line. The job goes back to the catalog, its lead forgotten. */
    suspend fun remove(jobId: String) {
        edit { current -> current.filterNot { it.jobId == jobId } }
    }

    /** Comments the line out, or back in — the leading `#` of a real crontab. */
    suspend fun setEnabled(jobId: String, enabled: Boolean) {
        edit { current ->
            current.map { if (it.jobId == jobId) it.copy(enabled = enabled) else it }
        }
    }

    /**
     * This line's own reminder. Null does NOT mean "no reminder": it means the line
     * carries none of its own and follows `notify_default` (Fase 16f), which is what
     * lets one setting switch the whole file's reminders on.
     */
    suspend fun setNotifyLead(jobId: String, leadMinutes: Int?) {
        edit { current ->
            current.map { if (it.jobId == jobId) it.copy(notifyLeadMinutes = leadMinutes) else it }
        }
    }

    private suspend fun edit(transform: (List<SkySubscription>) -> List<SkySubscription>) {
        dataStore.edit { prefs ->
            // Read BEFORE marking seeded. The other order looks equivalent and is
            // not: `decode` returns the defaults only while the flag is false, so
            // setting it first made the first edit of a fresh install decode an
            // empty file — one tap on one line and the four seeded jobs were gone.
            val current = decode(prefs)
            prefs[Seeded] = true
            prefs[SubscriptionsJson] = json.encodeToString(transform(current))
        }
    }

    private fun decode(prefs: Preferences): List<SkySubscription> {
        if (prefs[Seeded] != true) return SkyJobCatalog.defaults.map { SkySubscription(it.id) }
        val stored = prefs[SubscriptionsJson]
            ?.let { runCatching { json.decodeFromString<List<SkySubscription>>(it) }.getOrNull() }
            ?: return emptyList()
        // A job that left the catalog between two app versions is dropped rather than
        // rendered as a line nothing can resolve: the file only ever shows jobs the
        // app still knows how to compute.
        return stored.filter { SkyJobCatalog.byId(it.jobId) != null }
    }

    companion object {
        private val SubscriptionsJson = stringPreferencesKey("subscriptions_json")
        private val Seeded = booleanPreferencesKey("seeded")

        fun create(context: Context, json: Json) =
            SkySubscriptionStore(context.skyDataStore, json)
    }
}
