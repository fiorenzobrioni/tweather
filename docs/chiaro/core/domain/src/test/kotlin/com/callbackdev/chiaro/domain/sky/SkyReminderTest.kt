package com.callbackdev.chiaro.domain.sky

import com.callbackdev.chiaro.domain.model.Coordinates
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reminders of Fase 16f: which one is next, and whether it is worth posting.
 * Both halves are pure, which is the point — the alarm plumbing has no opinions.
 */
class SkyReminderTest {

    private val milan = Coordinates(45.4642, 9.19)
    private val rome: ZoneId = ZoneId.of("Europe/Rome")

    private fun at(local: String): Instant =
        java.time.LocalDateTime.parse(local).atZone(rome).toInstant()

    private fun plan(
        now: String,
        vararg jobs: Pair<SkyJob, SkyLead>
    ) = SkyReminderPlanner.next(jobs.toList(), at(now), rome, milan)

    // ------------------------------------------------------------- the leads

    /**
     * There is no five-minute lead and there never will be. These ride inexact
     * alarms, which drift about ten minutes, so a five-minute lead can be delivered
     * AFTER the thing it announces. `VISION_SKY.md` offered `5m` in one section and
     * forbade it in another; the section that forbade it was right.
     */
    @Test
    fun `the shortest lead the app offers is fifteen minutes`() {
        val shortest = SkyLead.entries.mapNotNull { it.minutes }.min()
        assertEquals(15, shortest)
    }

    @Test
    fun `the leads cycle back round to off`() {
        var lead = SkyLead.OFF
        val seen = mutableListOf<String>()
        repeat(SkyLead.entries.size) {
            lead = lead.next()
            seen += lead.label
        }
        assertEquals(listOf("15m", "30m", "1h", "3h", "1d", "off"), seen)
    }

    // ---------------------------------------------------------- the planning

    @Test
    fun `the reminder lands its lead ahead of the occurrence`() {
        // Milan sets at 20:12 on 26 Aug 2026.
        val reminder = plan("2026-08-26T12:00", SkyJobCatalog.SunSet to SkyLead.THIRTY)!!
        assertEquals("sun.set", reminder.jobId)
        assertEquals(
            30,
            Duration.between(reminder.fireAt, reminder.occurrenceAt).toMinutes()
        )
    }

    @Test
    fun `a job with no lead schedules nothing`() {
        assertNull(plan("2026-08-26T12:00", SkyJobCatalog.SunSet to SkyLead.OFF))
    }

    @Test
    fun `the nearest reminder wins, whichever job it belongs to`() {
        val reminder = plan(
            "2026-08-26T12:00",
            SkyJobCatalog.SunSet to SkyLead.THIRTY,
            SkyJobCatalog.GoldenPm to SkyLead.FIFTEEN
        )!!
        // Golden hour opens at 19:32, so its 15-minute reminder (19:17) beats the
        // sunset's (19:42).
        assertEquals("golden_hour.pm", reminder.jobId)
    }

    /**
     * With a long lead the NEXT occurrence's reminder is already behind us, and the
     * honest answer is the one after it rather than nothing at all.
     */
    @Test
    fun `a lead longer than the gap looks past the next occurrence`() {
        // 18:00, sunset at 20:12, one-day lead: today's reminder was due yesterday.
        val reminder = plan("2026-08-26T18:00", SkyJobCatalog.SunSet to SkyLead.ONE_DAY)!!
        assertTrue(reminder.fireAt.isAfter(at("2026-08-26T18:00")))
        assertEquals(27, reminder.occurrenceAt.atZone(rome).dayOfMonth)
    }

    /**
     * One notification per job per occurrence, and the occurrence is identified to
     * the MINUTE: the engine's answer for one sunset moves by fractions of a second
     * between two evaluations, and a fingerprint that moved with it would dedup
     * nothing at all.
     */
    @Test
    fun `the fingerprint survives the engine answering twice`() {
        val first = plan("2026-08-26T12:00", SkyJobCatalog.SunSet to SkyLead.THIRTY)!!
        val again = plan("2026-08-26T12:34", SkyJobCatalog.SunSet to SkyLead.THIRTY)!!
        assertEquals(first.fingerprint, again.fingerprint)
    }

    @Test
    fun `two jobs at the same instant keep separate fingerprints`() {
        val sunset = plan("2026-08-26T12:00", SkyJobCatalog.SunSet to SkyLead.THIRTY)!!
        val golden = plan("2026-08-26T12:00", SkyJobCatalog.GoldenPm to SkyLead.THIRTY)!!
        assertTrue(sunset.fingerprint != golden.fingerprint)
    }

    // ------------------------------------------------------------ the policy

    private fun verdict(kind: SkyVerdictKind) = SkyVerdict(kind, cloudPct = 50)

    @Test
    fun `a reminder for something you cannot see is noise, unless you asked for it`() {
        assertEquals(
            SkyReminderPolicy.Decision.SUPPRESSED_FAIL,
            SkyReminderPolicy.decide(
                SkyJobCatalog.SunSet, verdict(SkyVerdictKind.FAIL), notifyOnFail = false
            )
        )
        assertEquals(
            SkyReminderPolicy.Decision.SEND,
            SkyReminderPolicy.decide(
                SkyJobCatalog.SunSet, verdict(SkyVerdictKind.FAIL), notifyOnFail = true
            )
        )
    }

    /**
     * The `? unknown` rule, and the distinction is `visibilityDependent` rather than
     * `observable`: a sunset happens whether or not anyone can see it, and you may
     * have somewhere to be at dusk; a shower's peak is only an event if the sky is
     * clear, so a reminder the app cannot vouch for is noise.
     */
    @Test
    fun `an unknown verdict is sent for what happens anyway and withheld for the rest`() {
        assertEquals(
            SkyReminderPolicy.Decision.SEND,
            SkyReminderPolicy.decide(
                SkyJobCatalog.SunSet, verdict(SkyVerdictKind.UNKNOWN), notifyOnFail = false
            )
        )
        assertEquals(
            SkyReminderPolicy.Decision.SUPPRESSED_UNKNOWN,
            SkyReminderPolicy.decide(
                requireNotNull(SkyJobCatalog.byId("meteor.perseids.peak")),
                verdict(SkyVerdictKind.UNKNOWN),
                notifyOnFail = false
            )
        )
    }

    @Test
    fun `a pass and an unstable sky both go out`() {
        listOf(SkyVerdictKind.PASS, SkyVerdictKind.UNSTABLE).forEach { kind ->
            assertEquals(
                kind.toString(),
                SkyReminderPolicy.Decision.SEND,
                SkyReminderPolicy.decide(SkyJobCatalog.SunSet, verdict(kind), false)
            )
        }
    }

    @Test
    fun `a job with no verdict at all is never suppressed`() {
        // The moments of pure geometry: nothing about the sky can spoil them, so
        // there is nothing for the policy to withhold them over.
        assertEquals(
            SkyReminderPolicy.Decision.SEND,
            SkyReminderPolicy.decide(SkyJobCatalog.SolsticeWinter, null, notifyOnFail = false)
        )
    }
}
