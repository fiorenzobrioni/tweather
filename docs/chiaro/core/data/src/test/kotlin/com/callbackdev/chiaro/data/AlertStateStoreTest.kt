package com.callbackdev.chiaro.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.callbackdev.chiaro.domain.Alert
import com.callbackdev.chiaro.domain.AlertKind
import com.callbackdev.chiaro.domain.AlertState
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AlertStateStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun store(): AlertStateStore = AlertStateStore(
        PreferenceDataStoreFactory.create(scope = scope) {
            tmp.newFile("alerts-${System.nanoTime()}.preferences_pb")
        }
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun alert(kind: AlertKind, fingerprint: String) =
        Alert(kind = kind, fingerprint = fingerprint, cityLabel = "Milan")

    @Test
    fun `empty store yields blank state`() = runBlocking {
        assertEquals(AlertState(), store().state.first())
    }

    @Test
    fun `each kind records into its own slot`() = runBlocking {
        val store = store()
        store.record(alert(AlertKind.SEVERE, "k:sev:THUNDER:2023-10-27"))
        store.record(alert(AlertKind.PRECIPITATION, "k:pre:2023-10-27:AM"))
        store.record(alert(AlertKind.DAILY_SUMMARY, "2023-10-27"))
        assertEquals(
            AlertState(
                severeFingerprints = setOf("k:sev:THUNDER:2023-10-27"),
                precipFingerprints = setOf("k:pre:2023-10-27:AM"),
                summaryDate = LocalDate.of(2023, 10, 27)
            ),
            store.state.first()
        )
    }

    @Test
    fun `recording again keeps the previous fingerprints - one per city, not one slot`() =
        runBlocking {
            val store = store()
            store.record(alert(AlertKind.SEVERE, "milan:sev:THUNDER:2023-10-27"))
            store.record(alert(AlertKind.SEVERE, "rome:sev:THUNDER:2023-10-27"))
            assertEquals(
                setOf("milan:sev:THUNDER:2023-10-27", "rome:sev:THUNDER:2023-10-27"),
                store.state.first().severeFingerprints
            )
        }

    @Test
    fun `the fingerprint history is bounded, oldest out first`() = runBlocking {
        val store = store()
        repeat(20) { store.record(alert(AlertKind.SEVERE, "fp-$it")) }
        val severe = store.state.first().severeFingerprints
        assertEquals(16, severe.size)
        assertTrue("newest must survive", "fp-19" in severe)
        assertFalse("oldest must fall off", "fp-0" in severe)
    }
}
