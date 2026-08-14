package com.callbackdev.tweather.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.GeoFix
import com.callbackdev.tweather.domain.model.GpsCityId
import com.callbackdev.tweather.domain.model.toGpsCity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CityStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val milan = City(3_173_435, "Milano", "Lombardia", "Italy",
        Coordinates(45.4643, 9.1895), "Europe/Rome")
    private val gpsCity = GeoFix(Coordinates(45.46, 9.19), "Milano", null, "Italy").toGpsCity()

    private fun store(): CityStore = CityStore(
        PreferenceDataStoreFactory.create(scope = scope) {
            tmp.newFile("cities-${System.nanoTime()}.preferences_pb")
        },
        Json
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `default source is the seeded city with gps off`() = runBlocking {
        val store = store()
        assertEquals(
            ActiveSource.Saved(CityStore.DefaultCity),
            store.activeSource.first()
        )
        assertFalse(store.locationSettings.first().useGps)
    }

    @Test
    fun `enabling gps selects it immediately, before any fix exists`() = runBlocking {
        val store = store()
        store.setUseGps(true)
        assertEquals(ActiveSource.Gps(lastFix = null), store.activeSource.first())
        assertTrue(store.locationSettings.first().useGps)
    }

    @Test
    fun `updateGpsCity persists the fix without touching the saved list`() = runBlocking {
        val store = store()
        store.setUseGps(true)
        store.updateGpsCity(gpsCity)
        assertEquals(ActiveSource.Gps(gpsCity), store.activeSource.first())
        assertEquals(listOf(CityStore.DefaultCity), store.cities.first())
    }

    @Test
    fun `adding a searched city switches away from gps but keeps it enabled`() = runBlocking {
        val store = store()
        store.setUseGps(true)
        store.add(milan)
        assertEquals(ActiveSource.Saved(milan), store.activeSource.first())
        assertTrue(store.locationSettings.first().useGps)
    }

    @Test
    fun `setActiveGps reselects gps only while enabled`() = runBlocking {
        val store = store()
        store.setActiveGps() // toggle off: must be a no-op
        assertEquals(ActiveSource.Saved(CityStore.DefaultCity), store.activeSource.first())

        store.setUseGps(true)
        store.updateGpsCity(gpsCity)
        store.add(milan)
        store.setActiveGps()
        assertEquals(ActiveSource.Gps(gpsCity), store.activeSource.first())
    }

    @Test
    fun `disabling gps while active falls back to the first saved city`() = runBlocking {
        val store = store()
        store.setUseGps(true)
        store.setUseGps(false)
        assertEquals(ActiveSource.Saved(CityStore.DefaultCity), store.activeSource.first())
        assertFalse(store.locationSettings.first().useGps)
    }

    @Test
    fun `gps pseudo-city never appears in cities and cannot be added`() = runBlocking {
        val store = store()
        store.setUseGps(true)
        store.updateGpsCity(gpsCity)
        assertNull(store.cities.first().find { it.id == GpsCityId })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `updateGpsCity rejects a regular city`(): Unit = runBlocking {
        store().updateGpsCity(milan)
    }
}
