package com.callbackdev.chiaro.domain.sky

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The metaphor, enforced (`VISION_SKY.md` §3 and §14).
 *
 * `sky.crontab` claims to be a crontab. That claim is either true of every line it
 * renders or the file is decoration, so an OUTSIDE parser — not our own opinion of
 * what cron looks like — has to accept every expression in the catalog. `cron-utils`
 * is a `testImplementation` dependency and only that: the app emits cron, it never
 * reads any.
 */
class SkyJobCatalogTest {

    /**
     * A real vixie crontab: the five standard fields PLUS the `@`-nicknames, which
     * `CronType.UNIX` alone rejects ("Nicknames not supported!"). That rejection was
     * worth meeting — the module's whole §3 argument rests on `@daily` being a thing
     * a crontab actually accepts, and this definition is where that is asserted
     * rather than assumed.
     */
    private val parser = CronParser(
        CronDefinitionBuilder.defineCron()
            .withMinutes().and()
            .withHours().and()
            .withDayOfMonth().and()
            .withMonth().and()
            .withDayOfWeek().withValidRange(0, 7).withMondayDoWValue(1).and()
            .withSupportedNicknameYearly()
            .withSupportedNicknameAnnually()
            .withSupportedNicknameMonthly()
            .withSupportedNicknameWeekly()
            .withSupportedNicknameDaily()
            .withSupportedNicknameMidnight()
            .withSupportedNicknameHourly()
            .instance()
    )
    private val quartzParser = CronParser(
        CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)
    )

    @Test
    fun `every expression the catalog can render parses as unix cron`() {
        SkyJobCatalog.all.forEach { job ->
            // `validate()` is the strict pass: parsing alone accepts more than a real
            // crontab would.
            val cron = parser.parse(job.expression).validate()
            assertNotNull("${job.id} produced no cron", cron)
        }
    }

    @Test
    fun `the three kinds cover the catalog and nothing else`() {
        assertEquals(
            setOf("@daily", "@yearly", "*/30 * * * *"),
            SkyJobCatalog.all.map { it.expression }.toSet()
        )
        SkyJobKind.entries.forEach { kind ->
            assertNotNull(parser.parse(kind.expression).validate())
        }
    }

    /**
     * The polling expression is spelled out here rather than trusted from the enum:
     * it is the one expression in the module a human wrote character by character,
     * and it is also the one that cannot appear in a Kotlin block comment, which is
     * exactly the sort of string that gets quietly mangled.
     */
    @Test
    fun `the polling expression is every thirty minutes and says so to two parsers`() {
        assertEquals("*/30 * * * *", SkyJobKind.POLLING.expression)
        assertNotNull(parser.parse(SkyJobKind.POLLING.expression).validate())
        // Quartz has a seconds field, so the same string must NOT parse there — a
        // check that the parser is really reading the expression and not shrugging.
        runCatching { quartzParser.parse(SkyJobKind.POLLING.expression).validate() }
            .onSuccess { throw AssertionError("a 5-field expression parsed as Quartz") }
    }

    @Test
    fun `job ids are unique, dotted and lowercase`() {
        val ids = SkyJobCatalog.all.map { it.id }
        assertEquals("duplicate job ids", ids.size, ids.toSet().size)
        ids.forEach { id ->
            assertTrue("$id is not a dotted lowercase name", id.matches(Regex("[a-z0-9_]+(\\.[a-z0-9_]+)+")))
        }
    }

    @Test
    fun `the catalog holds every job the file can show, in file order`() {
        // The order is the FILE's, so it is asserted rather than derived: a crontab
        // does not re-sort itself by whatever fires next.
        assertEquals(SkyJobCatalog.SunRise, SkyJobCatalog.all.first())
        assertEquals(
            SkyJobCatalog.all.map { it.id },
            SkyJobCatalog.all.sortedBy { SkyJobCatalog.orderOf(it) }.map { it.id }
        )
        assertEquals(MeteorShowerTable.all.size + 22, SkyJobCatalog.all.size)
    }

    @Test
    fun `a fresh install subscribes to four lines`() {
        // Not a style choice: a user who opens the tab and finds thirty-two jobs
        // closes it. The catalog is what the file CAN hold.
        assertEquals(4, SkyJobCatalog.defaults.size)
        SkyJobCatalog.defaults.forEach { assertNotNull(SkyJobCatalog.byId(it.id)) }
    }

    @Test
    fun `only the jobs that depend on a clear sky are marked so`() {
        val visibility = SkyJobCatalog.all.filter { it.visibilityDependent }.map { it.id }.toSet()
        assertTrue("golden hour needs a clear sky", "golden_hour.pm" in visibility)
        assertTrue("a shower needs a clear sky", "meteor.perseids.peak" in visibility)
        assertTrue("darkness needs a clear sky", "darkness.window" in visibility)
        // The sun rises whether or not anyone can see it: a reminder for it is never
        // suppressed by cloud (Fase 16f).
        assertTrue("sunrise happens regardless", "sun.rise" !in visibility)
        assertTrue("the phase happens regardless", "moon.phase" !in visibility)
    }

    @Test
    fun `byId knows the catalog and nothing beyond it`() {
        assertNotNull(SkyJobCatalog.byId("sun.set"))
        assertNull(SkyJobCatalog.byId("sun.explodes"))
    }
}
