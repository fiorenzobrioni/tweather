package com.callbackdev.chiaro.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The `recent_searches` array behind the Search screen. */
class SearchHistoryStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun store(): SearchHistoryStore = SearchHistoryStore(
        PreferenceDataStoreFactory.create(scope = scope) {
            tmp.newFile("search-history-${System.nanoTime()}.preferences_pb")
        },
        Json
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun SearchHistoryStore.entries() = recentSearches.first()

    @Test
    fun `the newest search comes first`() = runBlocking {
        val store = store()
        store.add("Milano")
        store.add("Torino")

        assertEquals(listOf("Torino", "Milano"), store.entries())
    }

    @Test
    fun `searching the same place again moves it up instead of duplicating it`() = runBlocking {
        val store = store()
        store.add("Milano")
        store.add("Torino")
        store.add("milano") // same place, different casing

        assertEquals(listOf("milano", "Torino"), store.entries())
    }

    @Test
    fun `the list stops at five and drops the oldest`() = runBlocking {
        val store = store()
        val searched = (1..SearchHistoryStore.MAX_ENTRIES + 2).map { "City $it" }
        searched.forEach { store.add(it) }

        assertEquals(SearchHistoryStore.MAX_ENTRIES, store.entries().size)
        assertEquals(searched.takeLast(SearchHistoryStore.MAX_ENTRIES).reversed(), store.entries())
    }

    @Test
    fun `blank terms are not history`() = runBlocking {
        val store = store()
        store.add("   ")

        assertEquals(emptyList<String>(), store.entries())
    }

    @Test
    fun `clear forgets every search`() = runBlocking {
        val store = store()
        store.add("Milano")
        store.add("Torino")

        store.clear()

        assertEquals(emptyList<String>(), store.entries())
    }
}
