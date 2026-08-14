package com.callbackdev.tweather.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.callbackdev.tweather.domain.Alert
import com.callbackdev.tweather.domain.AlertKind
import com.callbackdev.tweather.domain.AlertState
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
                severeFingerprint = "k:sev:THUNDER:2023-10-27",
                precipFingerprint = "k:pre:2023-10-27:AM",
                summaryDate = LocalDate.of(2023, 10, 27)
            ),
            store.state.first()
        )
    }

    @Test
    fun `recording again overwrites the previous fingerprint`() = runBlocking {
        val store = store()
        store.record(alert(AlertKind.SEVERE, "old"))
        store.record(alert(AlertKind.SEVERE, "new"))
        assertEquals("new", store.state.first().severeFingerprint)
    }
}
