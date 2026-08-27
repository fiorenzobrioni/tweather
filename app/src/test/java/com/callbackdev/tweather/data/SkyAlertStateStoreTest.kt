package com.callbackdev.tweather.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The sky reminders' dedup (Fase 16f): one notification per job per occurrence. */
class SkyAlertStateStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun <T> withStore(file: File, block: suspend (SkyAlertStateStore) -> T): T {
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        return runBlocking {
            try {
                block(
                    SkyAlertStateStore(PreferenceDataStoreFactory.create(scope = scope) { file })
                )
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    @Test
    fun `a fingerprint is unknown until it is recorded`() {
        withStore(tmp.newFile("a.preferences_pb")) { store ->
            assertFalse(store.wasPosted("sun.set@1"))
            store.record("sun.set@1")
            assertTrue(store.wasPosted("sun.set@1"))
            assertFalse("another occurrence is another reminder", store.wasPosted("sun.set@2"))
        }
    }

    /**
     * The alarm can fire twice for one occurrence — a reboot re-arms one that already
     * went out — and the fingerprint is what makes the second one a no-op. It has to
     * survive the process, not just the receiver.
     */
    @Test
    fun `the fingerprint survives a restart`() {
        val file = tmp.newFile("b.preferences_pb")
        withStore(file) { it.record("golden_hour.pm@42") }
        withStore(file) { assertTrue(it.wasPosted("golden_hour.pm@42")) }
    }

    @Test
    fun `recording the same fingerprint twice does not grow the set`() {
        withStore(tmp.newFile("c.preferences_pb")) { store ->
            store.record("sun.set@1")
            store.record("sun.set@1")
            assertEquals(1, store.posted.first().size)
        }
    }

    /**
     * Bounded and newest-first. A set that only grew would be a slow leak that never
     * announces itself.
     */
    @Test
    fun `the set is bounded and keeps the newest`() {
        withStore(tmp.newFile("d.preferences_pb")) { store ->
            repeat(60) { store.record("job@$it") }
            val posted = store.posted.first()
            assertTrue("bounded, got ${posted.size}", posted.size <= 40)
            assertTrue("the newest survives", posted.contains("job@59"))
            assertFalse("the oldest is gone", posted.contains("job@0"))
        }
    }
}
