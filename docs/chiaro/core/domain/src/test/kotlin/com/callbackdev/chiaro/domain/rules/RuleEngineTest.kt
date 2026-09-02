package com.callbackdev.chiaro.domain.rules

import com.callbackdev.chiaro.domain.model.WeatherReport
import com.callbackdev.chiaro.domain.sample.sampleWeatherReport
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private val now: LocalDateTime = LocalDateTime.of(2023, 10, 27, 14, 30)
    private val report: WeatherReport = sampleWeatherReport() // temp 18.5, uv 4
    private val cityKey = "3173435"

    private fun rule(
        vararg conditions: RuleCondition,
        id: Long = 1,
        enabled: Boolean = true
    ) = NotificationRule(
        id = id,
        name = "rule_$id",
        enabled = enabled,
        conditions = conditions.toList(),
        message = "msg"
    )

    private fun evaluate(
        vararg rules: NotificationRule,
        state: RuleEngineState = RuleEngineState(),
        at: LocalDateTime = now
    ) = RuleEngine.evaluate(rules.toList(), report, state, at, cityKey)

    // --- check (the dry run's engine) ---

    @Test
    fun `every operator compares as written`() {
        fun fires(op: RuleOp, threshold: Double): Boolean =
            RuleEngine.check(
                rule(RuleCondition("current.temp_c", op, threshold)), report, now
            ) is RuleCheck.Fires
        assertTrue(fires(RuleOp.GT, 18.0))
        assertTrue(!fires(RuleOp.GT, 18.5))
        assertTrue(fires(RuleOp.GTE, 18.5))
        assertTrue(fires(RuleOp.LT, 19.0))
        assertTrue(!fires(RuleOp.LT, 18.5))
        assertTrue(fires(RuleOp.LTE, 18.5))
        assertTrue(fires(RuleOp.EQ, 18.5))
        assertTrue(fires(RuleOp.NEQ, 20.0))
    }

    @Test
    fun `and requires both conditions`() {
        val both = rule(
            RuleCondition("current.temp_c", RuleOp.GT, 10.0),
            RuleCondition("current.uv_index", RuleOp.GTE, 4.0)
        )
        assertTrue(RuleEngine.check(both, report, now) is RuleCheck.Fires)
        val secondFails = rule(
            RuleCondition("current.temp_c", RuleOp.GT, 10.0),
            RuleCondition("current.uv_index", RuleOp.GTE, 7.0)
        )
        assertEquals(RuleCheck.Passes, RuleEngine.check(secondFails, report, now))
    }

    @Test
    fun `trigger value and hour come from the first condition`() {
        val fires = RuleEngine.check(
            rule(
                RuleCondition("next_6h.temp_c_min", RuleOp.LT, 15.0),
                RuleCondition("current.temp_c", RuleOp.GT, 0.0)
            ),
            report, now
        ) as RuleCheck.Fires
        assertEquals(14.0, fires.value, 0.0) // the window minimum, not current temp
        assertEquals(LocalDateTime.of(2023, 10, 27, 19, 0), fires.at)
    }

    @Test
    fun `an unresolvable variable is unavailable, not false`() {
        val noAq = report.copy(airQuality = null)
        val check = RuleEngine.check(
            rule(RuleCondition("current.aqi_index", RuleOp.GT, 100.0)), noAq, now
        )
        assertEquals(RuleCheck.Unavailable("current.aqi_index"), check)
        // unknown id (e.g. from a future version) degrades the same way
        assertEquals(
            RuleCheck.Unavailable("current.made_up"),
            RuleEngine.check(rule(RuleCondition("current.made_up", RuleOp.GT, 0.0)), report, now)
        )
    }

    // --- evaluate: edge trigger for instant rules ---

    @Test
    fun `an instant rule fires once and latches until false`() {
        val warm = rule(RuleCondition("current.temp_c", RuleOp.GT, 15.0))
        val first = evaluate(warm)
        val trigger = first.triggers.single()
        assertEquals(RuleEngine.latchKey(cityKey, warm.id), trigger.latchKey)
        assertNull(trigger.fingerprint)
        // latched (recorded after the notify) → same truth, no re-fire
        val latched = RuleEngineState(latched = setOf(trigger.latchKey!!))
        assertTrue(evaluate(warm, state = latched).triggers.isEmpty())
    }

    @Test
    fun `a false instant rule re-arms its latch`() {
        val cold = rule(RuleCondition("current.temp_c", RuleOp.LT, 5.0)) // 18.5 → false
        val latchKey = RuleEngine.latchKey(cityKey, cold.id)
        val evaluation = evaluate(cold, state = RuleEngineState(latched = setOf(latchKey)))
        assertTrue(evaluation.triggers.isEmpty())
        assertEquals(setOf(latchKey), evaluation.unlatch)
        // an unlatched rule that reads false stays silent without churn
        assertTrue(evaluate(cold).unlatch.isEmpty())
    }

    @Test
    fun `unavailable data keeps the latch untouched`() {
        val aqi = rule(RuleCondition("current.aqi_index", RuleOp.GT, 100.0))
        val latchKey = RuleEngine.latchKey(cityKey, aqi.id)
        val noAqReport = report.copy(airQuality = null)
        val evaluation = RuleEngine.evaluate(
            listOf(aqi), noAqReport, RuleEngineState(latched = setOf(latchKey)), now, cityKey
        )
        assertTrue(evaluation.triggers.isEmpty())
        assertTrue(evaluation.unlatch.isEmpty())
    }

    // --- evaluate: fingerprints for windowed rules ---

    @Test
    fun `a windowed rule dedups per half-day and re-fires in the next bucket`() {
        // 19° peak at 15:00: true from a morning poll AND from an afternoon one
        val mild = rule(RuleCondition("next_6h.temp_c_max", RuleOp.GTE, 19.0))
        val trigger = evaluate(mild).triggers.single()
        assertNull(trigger.latchKey)
        assertEquals("$cityKey:rule:${mild.id}:2023-10-27:PM", trigger.fingerprint)
        val fired = RuleEngineState(firedFingerprints = setOf(trigger.fingerprint!!))
        assertTrue(evaluate(mild, state = fired).triggers.isEmpty())
        // a morning evaluation is a different half-day bucket → re-fires
        val morning = evaluate(mild, state = fired, at = now.withHour(9))
        assertEquals(
            "$cityKey:rule:${mild.id}:2023-10-27:AM",
            morning.triggers.single().fingerprint
        )
    }

    @Test
    fun `mixing a current condition with a windowed one uses fingerprints`() {
        val mixed = rule(
            RuleCondition("current.temp_c", RuleOp.GT, 0.0),
            RuleCondition("next_6h.precip_chance_max", RuleOp.GTE, 10.0)
        )
        val trigger = evaluate(mixed).triggers.single()
        assertNull(trigger.latchKey)
        assertTrue(trigger.fingerprint!!.contains(":rule:${mixed.id}:"))
    }

    // --- gating ---

    @Test
    fun `disabled rules are skipped entirely`() {
        val off = rule(RuleCondition("current.temp_c", RuleOp.GT, 0.0), enabled = false)
        val evaluation = evaluate(off, state = RuleEngineState(
            latched = setOf(RuleEngine.latchKey(cityKey, off.id))
        ))
        assertTrue(evaluation.triggers.isEmpty())
        assertTrue(evaluation.unlatch.isEmpty())
    }

    @Test
    fun `rules evaluate independently`() {
        val fires = rule(RuleCondition("current.temp_c", RuleOp.GT, 0.0), id = 1)
        val silent = rule(RuleCondition("current.temp_c", RuleOp.LT, 0.0), id = 2)
        val evaluation = evaluate(fires, silent)
        assertEquals(listOf(1L), evaluation.triggers.map { it.rule.id })
    }
}
