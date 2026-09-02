package com.callbackdev.chiaro.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.callbackdev.chiaro.domain.model.GpsCityId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WidgetCityStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Arbitrary Open-Meteo geocoding ids; only their identity matters here
    private val turinId = 3_165_524L
    private val romeId = 3_169_070L

    private fun store(): WidgetCityStore = WidgetCityStore(
        PreferenceDataStoreFactory.create(scope = scope) {
            tmp.newFile("widget-cities-${System.nanoTime()}.preferences_pb")
        }
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `an empty store pins nothing, so every widget follows the app`() = runBlocking {
        assertTrue(store().current().isEmpty())
    }

    @Test
    fun `pin maps the widget to the city and pinning again overwrites`() = runBlocking {
        val store = store()
        store.pin(appWidgetId = 42, cityId = turinId)
        assertEquals(mapOf(42 to turinId), store.current())

        // Reconfiguring a placed widget must replace the pin, not add a second one
        store.pin(appWidgetId = 42, cityId = romeId)
        assertEquals(mapOf(42 to romeId), store.current())
    }

    @Test
    fun `widgets are pinned independently, gps included`() = runBlocking {
        val store = store()
        store.pin(1, turinId)
        store.pin(2, romeId)
        store.pin(3, GpsCityId) // the GPS source is a legal pin, not a sentinel for "unpinned"
        assertEquals(mapOf(1 to turinId, 2 to romeId, 3 to GpsCityId), store.current())
    }

    @Test
    fun `unpin only clears the widget it names`() = runBlocking {
        val store = store()
        store.pin(1, turinId)
        store.pin(2, romeId)
        store.unpin(1)
        assertEquals(mapOf(2 to romeId), store.current())
    }

    @Test
    fun `forget clears every deleted widget in one edit and spares the rest`() = runBlocking {
        val store = store()
        store.pin(1, turinId)
        store.pin(2, romeId)
        store.pin(3, GpsCityId)
        store.forget(intArrayOf(1, 3))
        assertEquals(mapOf(2 to romeId), store.current())
    }

    @Test
    fun `forget ignores ids that were never pinned`() = runBlocking {
        // onDeleted also fires for widgets added without configuring, which have no entry
        val store = store()
        store.pin(1, turinId)
        store.forget(intArrayOf(7, 8, 9))
        assertEquals(mapOf(1 to turinId), store.current())
    }

    @Test
    fun `the pinned flow reports the map after a pin`() = runBlocking {
        // The updater observes `pinned` rather than polling, so the edit must reach the flow
        val store = store()
        store.pin(11, turinId)
        assertEquals(mapOf(11 to turinId), store.pinned.first())
    }
}
