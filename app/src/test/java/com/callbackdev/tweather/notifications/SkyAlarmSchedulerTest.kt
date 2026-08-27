package com.callbackdev.tweather.notifications

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.data.SettingsStore
import com.callbackdev.tweather.data.SkySubscriptionStore
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.Coordinates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowAlarmManager

/**
 * The alarm half of Fase 16f: what gets armed, how, and what happens when the module
 * is off. The decisions live in [com.callbackdev.tweather.domain.sky.SkyReminderPlanner]
 * and are tested there; this is the plumbing.
 */
@RunWith(RobolectricTestRunner::class)
class SkyAlarmSchedulerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val shadow = Shadows.shadowOf(alarms)

    private val milan = City(
        id = 3173435, name = "Milan", region = "Lombardy", country = "Italy",
        coordinates = Coordinates(45.4642, 9.19), timezone = "Europe/Rome"
    )

    private fun store(name: String) =
        PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile(name) }

    private val settingsStore = SettingsStore(store("settings.preferences_pb"))
    private val cityStore = CityStore(store("cities.preferences_pb"), Json)
    private val skyStore = SkySubscriptionStore(store("sky.preferences_pb"), Json)

    private fun setUpStores(withCity: Boolean = true) {
        ServiceLocator.overrideForTests(
            cityStore = cityStore,
            settingsStore = settingsStore,
            skySubscriptionStore = skyStore
        )
        if (withCity) runBlocking { cityStore.add(milan) }
    }

    @After
    fun tearDown() {
        ServiceLocator.overrideForTests()
        scope.cancel()
    }

    @Test
    fun `a subscribed job with a lead arms one alarm`() = runBlocking {
        setUpStores()
        skyStore.setNotifyLead("sun.set", 30)
        SkyAlarmScheduler.reschedule(context)

        val alarm = shadow.peekNextScheduledAlarm()
        assertNotNull("nothing was armed", alarm)
        assertEquals(1, shadow.scheduledAlarms.size)
    }

    /**
     * Inexact, always. `setAndAllowWhileIdle`, never `setExact*` and never
     * `SCHEDULE_EXACT_ALARM`: battery is a feature and a sunset is not an alarm
     * clock. That is also why the shortest selectable lead is fifteen minutes.
     */
    @Test
    fun `the alarm is inexact and wakes the device`() = runBlocking {
        setUpStores()
        skyStore.setNotifyLead("sun.set", 30)
        SkyAlarmScheduler.reschedule(context)

        val alarm = requireNotNull(shadow.peekNextScheduledAlarm())
        assertTrue("must be allowed while idle, or Doze eats it", alarm.allowWhileIdle)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        // WINDOW_HEURISTIC (-1) is the system picking the window, which is what makes
        // the alarm inexact; WINDOW_EXACT (0) is the thing this app must never ask for.
        assertEquals("not an exact alarm", ShadowAlarmManager.WINDOW_HEURISTIC, alarm.windowLengthMs)
    }

    @Test
    fun `no lead on any line arms nothing`() = runBlocking {
        setUpStores()
        SkyAlarmScheduler.reschedule(context)
        assertNull(shadow.peekNextScheduledAlarm())
    }

    /**
     * The file and the alarm resolve `notify_default` the same way, or `sky.crontab`
     * would print a `--notify=30m` that nothing was ever armed for.
     */
    @Test
    fun `notify_default arms a line that has no lead of its own`() = runBlocking {
        setUpStores()
        settingsStore.setSkyNotifyDefault(30)
        SkyAlarmScheduler.reschedule(context)
        assertNotNull("the default was not followed", shadow.peekNextScheduledAlarm())
    }

    @Test
    fun `with the module switched off nothing is armed`() = runBlocking {
        setUpStores()
        skyStore.setNotifyLead("sun.set", 30)
        settingsStore.setSkyEnabled(false)
        SkyAlarmScheduler.reschedule(context)
        assertNull(shadow.peekNextScheduledAlarm())
    }

    /**
     * No city, no schedule: the whole module needs a latitude, and a reminder for
     * nowhere is not a reminder.
     */
    @Test
    fun `with no location configured nothing is armed`() = runBlocking {
        setUpStores(withCity = false)
        skyStore.setNotifyLead("sun.set", 30)
        SkyAlarmScheduler.reschedule(context)
        assertNull(shadow.peekNextScheduledAlarm())
    }

    /**
     * One alarm at a time. Re-arming replaces rather than adds — otherwise every
     * fetch and every tap on a `--notify` token would leave one more behind.
     */
    @Test
    fun `re-arming replaces the alarm instead of stacking another`() = runBlocking {
        setUpStores()
        skyStore.setNotifyLead("sun.set", 30)
        SkyAlarmScheduler.reschedule(context)
        SkyAlarmScheduler.reschedule(context)
        SkyAlarmScheduler.reschedule(context)
        assertEquals(1, shadow.scheduledAlarms.size)
    }

    @Test
    fun `a commented-out line schedules nothing`() = runBlocking {
        setUpStores()
        skyStore.setNotifyLead("sun.set", 30)
        skyStore.setEnabled("sun.set", false)
        SkyAlarmScheduler.reschedule(context)
        assertNull(shadow.peekNextScheduledAlarm())
    }

    /**
     * Switching the module off must take the pending alarm with it. Left behind it
     * would survive until it fired, wake the device, find `sky.enabled` false and
     * cancel itself: correct, but one pointless wakeup after the user said no.
     */
    @Test
    fun `switching the module off cancels an armed alarm`() = runBlocking {
        setUpStores()
        skyStore.setNotifyLead("sun.set", 30)
        SkyAlarmScheduler.reschedule(context)
        assertNotNull("precondition: nothing was armed", shadow.peekNextScheduledAlarm())

        settingsStore.setSkyEnabled(false)
        SkyAlarmScheduler.reschedule(context)
        assertNull("the alarm outlived the switch", shadow.peekNextScheduledAlarm())
    }

    /**
     * Alarms die with the device. Nothing else would notice: the periodic sync re-arms
     * too, but not until its next run, so a reboot at dusk would eat the evening.
     */
    @Test
    fun `boot re-arms the alarm`() = runBlocking {
        setUpStores()
        skyStore.setNotifyLead("sun.set", 30)
        SkyAlarmReceiver().handle(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNotNull("boot left nothing armed", shadow.peekNextScheduledAlarm())
    }

    /**
     * The `finally` earns its keep: a reminder that cannot be delivered at all — here a
     * job id no longer in the catalog, but a thrown exception reads the same — must
     * still leave the next alarm behind, or the reminders end silently and forever.
     */
    @Test
    fun `an undeliverable reminder still arms the next one`() = runBlocking {
        setUpStores()
        skyStore.setNotifyLead("sun.set", 30)
        val fired = Intent(SkyAlarmScheduler.ACTION_FIRE)
            .putExtra(SkyAlarmScheduler.EXTRA_JOB_ID, "sun.rises.in.the.west")
            .putExtra(SkyAlarmScheduler.EXTRA_OCCURRENCE, 1_800_000_000L)
        SkyAlarmReceiver().handle(context, fired)
        assertNotNull("nothing was re-armed", shadow.peekNextScheduledAlarm())
    }

    @Test
    fun `the plan names the job and the occurrence it announces`() = runBlocking {
        setUpStores()
        skyStore.setNotifyLead("sun.set", 30)
        val plan = requireNotNull(SkyAlarmScheduler.plan(context))
        assertEquals("sun.set", plan.jobId)
        assertTrue(plan.occurrenceAt.isAfter(plan.fireAt))
    }
}
