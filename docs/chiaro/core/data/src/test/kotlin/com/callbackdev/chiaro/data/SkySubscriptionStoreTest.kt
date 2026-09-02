package com.callbackdev.chiaro.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.callbackdev.chiaro.domain.sky.SkyJobCatalog
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SkySubscriptionStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /**
     * One store instance per block, its scope cancelled at the end. DataStore
     * refuses two live instances over one file, so "close the app and come back"
     * has to be exactly that in a test: the second instance cannot exist until the
     * first has let go.
     */
    private fun <T> withStore(file: File, block: suspend (SkySubscriptionStore) -> T): T {
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        return runBlocking {
            try {
                block(
                    SkySubscriptionStore(
                        PreferenceDataStoreFactory.create(scope = scope) { file },
                        Json
                    )
                )
            } finally {
                // cancelAndJoin, not cancel: DataStore clears its active-files
                // registry when the scope's job COMPLETES, so a plain cancel leaves
                // the next instance racing a lock that is on its way out.
                job.cancelAndJoin()
            }
        }
    }

    private fun file(name: String = "sky.preferences_pb"): File = tmp.newFile(name)

    @Test
    fun `a fresh install starts on the catalog's four defaults`() = withStore(file()) { store ->
        assertEquals(
            SkyJobCatalog.defaults.map { it.id },
            store.subscriptions.first().map { it.jobId }
        )
    }

    /**
     * The first edit of a fresh install must keep the other three defaults. It did
     * not: the seeded flag was written before the current list was read, so one tap
     * on one line decoded an empty file and saved it over the seed.
     */
    @Test
    fun `the first edit keeps the rest of the seeded lines`() {
        val f = file("sky-first-edit.preferences_pb")
        withStore(f) { it.setEnabled("sun.rise", false) }
        withStore(f) { store ->
            assertEquals(
                SkyJobCatalog.defaults.map { it.id },
                store.subscriptions.first().map { it.jobId }
            )
            assertFalse(store.subscriptions.first().first { it.jobId == "sun.rise" }.enabled)
        }
    }

    /**
     * The distinction the `seeded` flag exists for. Removing the last line is a
     * choice; a fresh install is a state. Without the flag they look identical from
     * the stored data, and the four defaults would grow back over the user's empty
     * file every time the app restarted.
     */
    @Test
    fun `emptying the file is not the same as never having opened it`() {
        val f = file("sky-empty.preferences_pb")
        withStore(f) { store ->
            SkyJobCatalog.defaults.forEach { store.remove(it.id) }
            assertTrue(store.subscriptions.first().isEmpty())
        }
        // A second instance over the same file is the app coming back tomorrow.
        withStore(f) { assertTrue(it.subscriptions.first().isEmpty()) }
    }

    @Test
    fun `adding a job appends it once`() = withStore(file()) { store ->
        store.add("blue_hour.pm")
        store.add("blue_hour.pm")
        assertEquals(1, store.subscriptions.first().count { it.jobId == "blue_hour.pm" })
    }

    @Test
    fun `a job the catalog does not know is not addable`() = withStore(file()) { store ->
        val before = store.subscriptions.first()
        store.add("sun.explodes")
        assertEquals(before, store.subscriptions.first())
    }

    @Test
    fun `disabling comments the line out without removing it`() = withStore(file()) { store ->
        store.setEnabled("sun.rise", false)
        assertFalse(store.subscriptions.first().first { it.jobId == "sun.rise" }.enabled)
        // Still a line of the file: `#` and `[rm]` are different on purpose.
        assertTrue(store.subscriptions.first().any { it.jobId == "sun.rise" })
    }

    @Test
    fun `removing takes the line out and forgets its lead`() = withStore(file()) { store ->
        store.setNotifyLead("sun.set", 30)
        store.remove("sun.set")
        assertTrue(store.subscriptions.first().none { it.jobId == "sun.set" })
        store.add("sun.set")
        assertNull(store.subscriptions.first().first { it.jobId == "sun.set" }.notifyLeadMinutes)
    }

    /**
     * Written in 16c, read in 16f. The file does not render a `--notify` token yet:
     * a token promising a reminder the app cannot send would be the first thing this
     * module lies about.
     */
    @Test
    fun `the notify lead persists ahead of the phase that uses it`() {
        val f = file("sky-lead.preferences_pb")
        withStore(f) { it.setNotifyLead("sun.set", 30) }
        withStore(f) { store ->
            assertEquals(
                30,
                store.subscriptions.first().first { it.jobId == "sun.set" }.notifyLeadMinutes
            )
        }
    }

    /**
     * A job that leaves the catalog between two app versions must not survive as a
     * line nothing can resolve — the file only ever shows jobs the app still knows
     * how to compute.
     */
    @Test
    fun `a line whose job left the catalog is dropped on read`() {
        val f = file("sky-stale.preferences_pb")
        // The raw JSON a previous app version could have left behind, naming a job
        // this version no longer has.
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        runBlocking {
            try {
                PreferenceDataStoreFactory.create(scope = scope) { f }.edit { prefs ->
                    prefs[booleanPreferencesKey("seeded")] = true
                    prefs[stringPreferencesKey("subscriptions_json")] =
                        """[{"jobId":"sun.rise"},{"jobId":"comet.halley.pass"}]"""
                }
            } finally {
                job.cancelAndJoin()
            }
        }
        withStore(f) { store ->
            assertEquals(listOf("sun.rise"), store.subscriptions.first().map { it.jobId })
        }
    }
}
