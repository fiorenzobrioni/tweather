package com.callbackdev.tweather.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.callbackdev.tweather.domain.rules.MaxRules
import com.callbackdev.tweather.domain.rules.RuleOp
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuleStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun store(file: File = tmp.newFile("rules.preferences_pb")) = RuleStore(
        PreferenceDataStoreFactory.create(scope = scope) { file },
        Json
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `starts empty and add appends the umbrella template`() = runBlocking {
        val store = store()
        assertTrue(store.rules.first().isEmpty())
        store.add()
        val rule = store.rules.first().single()
        assertEquals("rule_1", rule.name)
        assertTrue(rule.enabled)
        val condition = rule.conditions.single()
        assertEquals("next_6h.precip_chance_max", condition.variable)
        assertEquals(RuleOp.GTE, condition.op)
        assertEquals(60.0, condition.threshold, 0.0)
    }

    @Test
    fun `ids are monotonic and never recycled`() = runBlocking {
        val store = store()
        store.add() // id 1
        store.add() // id 2
        store.remove(2)
        store.add() // must be id 3, not a recycled 2
        assertEquals(listOf(1L, 3L), store.rules.first().map { it.id })
    }

    @Test
    fun `update replaces only the matching rule`() = runBlocking {
        val store = store()
        store.add()
        store.add()
        val second = store.rules.first()[1]
        store.update(second.copy(name = "sunscreen", enabled = false))
        val rules = store.rules.first()
        assertEquals("rule_1", rules[0].name)
        assertEquals("sunscreen", rules[1].name)
        assertTrue(!rules[1].enabled)
    }

    @Test
    fun `add is a no-op at the ceiling`() = runBlocking {
        val store = store()
        repeat(MaxRules + 2) { store.add() }
        assertEquals(MaxRules, store.rules.first().size)
    }

    @Test
    fun `rules survive a restart (new store on the same file)`() = runBlocking {
        val file = tmp.newFile("persist.preferences_pb")
        val firstRunJob = SupervisorJob()
        val firstRun = CoroutineScope(Dispatchers.IO + firstRunJob)
        RuleStore(PreferenceDataStoreFactory.create(scope = firstRun) { file }, Json).add()
        firstRunJob.cancelAndJoin()
        val reopened = store(file)
        assertEquals("rule_1", reopened.rules.first().single().name)
    }
}
