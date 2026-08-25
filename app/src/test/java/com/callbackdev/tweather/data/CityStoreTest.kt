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

    // Distinct from DefaultCity (Milan) so add/setActive really change the store
    private val turin = City(3_165_524, "Turin", "Piedmont", "Italy",
        Coordinates(45.0703, 7.6869), "Europe/Rome")
    private val gpsCity = GeoFix(Coordinates(45.46, 9.19), "Milano", null, "Italy").toGpsCity()
    private val milan = CityStore.DefaultCity

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

    /** Fase 14b: no seeded city any more — "nothing configured" is a real state. */
    @Test
    fun `a fresh store has no location at all`() = runBlocking {
        val store = store()
        assertEquals(ActiveSource.None, store.activeSource.first())
        assertEquals(emptyList<City>(), store.cities.first())
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
        assertEquals(emptyList<City>(), store.cities.first())
    }

    @Test
    fun `adding a searched city switches away from gps but keeps it enabled`() = runBlocking {
        val store = store()
        store.setUseGps(true)
        store.add(turin)
        assertEquals(ActiveSource.Saved(turin), store.activeSource.first())
        assertTrue(store.locationSettings.first().useGps)
    }

    @Test
    fun `setActiveGps reselects gps only while enabled`() = runBlocking {
        val store = store()
        store.setActiveGps() // toggle off: must be a no-op
        assertEquals(ActiveSource.None, store.activeSource.first())

        store.setUseGps(true)
        store.updateGpsCity(gpsCity)
        store.add(turin)
        store.setActiveGps()
        assertEquals(ActiveSource.Gps(gpsCity), store.activeSource.first())
    }

    @Test
    fun `disabling gps while active falls back to the first saved city`() = runBlocking {
        val store = store()
        store.add(turin)
        store.setUseGps(true)
        store.setUseGps(false)
        assertEquals(ActiveSource.Saved(turin), store.activeSource.first())
        assertFalse(store.locationSettings.first().useGps)
    }

    /** Nothing to fall back TO is no longer a crash, nor a city out of nowhere. */
    @Test
    fun `disabling gps with an empty list leaves no source at all`() = runBlocking {
        val store = store()
        store.setUseGps(true)
        store.setUseGps(false)
        assertEquals(ActiveSource.None, store.activeSource.first())
    }

    @Test
    fun `the last city can be removed and leaves cities json empty`() = runBlocking {
        val store = store()
        store.add(turin)

        store.remove(turin)

        assertEquals(emptyList<City>(), store.cities.first())
        assertEquals(ActiveSource.None, store.activeSource.first())
    }

    @Test
    fun `gps pseudo-city never appears in cities and cannot be added`() = runBlocking {
        val store = store()
        store.setUseGps(true)
        store.updateGpsCity(gpsCity)
        assertNull(store.cities.first().find { it.id == GpsCityId })
    }

    /**
     * Fase 14a: same id, fresher record. The seeded Milan is the case that surfaced it
     * — "Milano" searched in Italian is GeoNames 3173435 like the English "Milan", so
     * the old add() skipped it and the file stayed milan.json.
     */
    @Test
    fun `re-adding a saved city refreshes its record in place`() = runBlocking {
        val store = store()
        store.add(milan)
        store.add(turin)
        val italian = milan.copy(name = "Milano", region = "Lombardia", country = "Italia")

        store.add(italian)

        // replaced where it was, not moved to the end: re-adding is not a reorder
        assertEquals(listOf(italian, turin), store.cities.first())
        assertEquals(ActiveSource.Saved(italian), store.activeSource.first())
    }

    // ---- Fase 14b: which installs inherit a city, and which get `tweather init` ----

    @Test
    fun `the shell draws nothing until the legacy check has run`() = runBlocking {
        assertEquals(FirstRun.Unknown, store().firstRun.first())
    }

    @Test
    fun `a fresh install is sent to init with no city`() = runBlocking {
        val store = store()

        store.migrateFirstRun(hasHistory = false)

        assertEquals(FirstRun.Pending, store.firstRun.first())
        assertEquals(emptyList<City>(), store.cities.first())
    }

    /**
     * The install that never touched cities.json but has been watching the seeded
     * Milan for months: it must keep it, and must not be asked to configure anything.
     */
    @Test
    fun `an install that has been fetching keeps the city it was watching`() = runBlocking {
        val store = store()

        store.migrateFirstRun(hasHistory = true)

        assertEquals(FirstRun.Done, store.firstRun.first())
        assertEquals(listOf(CityStore.DefaultCity), store.cities.first())
        assertEquals(ActiveSource.Saved(CityStore.DefaultCity), store.activeSource.first())
    }

    @Test
    fun `an install with its own list keeps it and skips init`() = runBlocking {
        val store = store()
        store.add(turin)

        store.migrateFirstRun(hasHistory = false)

        assertEquals(FirstRun.Done, store.firstRun.first())
        assertEquals(listOf(turin), store.cities.first())
    }

    /** Once decided, never revisited: a later fetch must not re-seed a skipped install. */
    @Test
    fun `the legacy check runs exactly once`() = runBlocking {
        val store = store()
        store.migrateFirstRun(hasHistory = false)

        store.migrateFirstRun(hasHistory = true)

        assertEquals(FirstRun.Pending, store.firstRun.first())
        assertEquals(emptyList<City>(), store.cities.first())
    }

    @Test
    fun `skipping init still counts as answering it`() = runBlocking {
        val store = store()
        store.migrateFirstRun(hasHistory = false)

        store.markInitDone()

        assertEquals(FirstRun.Done, store.firstRun.first())
        assertEquals(ActiveSource.None, store.activeSource.first())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `updateGpsCity rejects a regular city`(): Unit = runBlocking {
        store().updateGpsCity(turin)
    }
}
