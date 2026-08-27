package com.callbackdev.tweather.ui.sky

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.tweather.domain.sky.SkyJobCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [SkyJobNames] is the dictionary between `sky.crontab`'s ids and the README's prose,
 * and the only thing keeping `golden_hour.pm` out of a document written in sentences.
 * A missing entry falls back to the id — which IS the bug — so the map is required to
 * be total over the catalog here, in both languages, rather than discovered to have a
 * hole by whoever adds the thirty-third job.
 */
@RunWith(RobolectricTestRunner::class)
class SkyJobNamesTest {

    private val resources: Resources
        get() = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun `every job in the catalog has a name that is not its id`() {
        SkyJobCatalog.all.forEach { job ->
            val name = SkyJobNames.name(resources, job.id)
            assertFalse("${job.id} falls back to its own id", name == job.id)
            assertTrue("${job.id} has no name", name.isNotBlank())
            // A dot in a name is an id having got through some other door.
            assertFalse("$name reads like an id", name.contains('.'))
        }
    }

    @Test
    @Config(qualifiers = "it")
    fun `the italian side of the dictionary is complete too`() {
        SkyJobCatalog.all.forEach { job ->
            val name = SkyJobNames.name(resources, job.id)
            assertFalse("${job.id} falls back to its own id in Italian", name == job.id)
        }
        assertEquals("L'ora d'oro della sera", SkyJobNames.name(resources, "golden_hour.pm"))
        assertEquals("Il picco delle Perseidi", SkyJobNames.name(resources, "meteor.perseids.peak"))
    }

    @Test
    fun `a label leads with the job's emoji, the way a Status line does`() {
        assertEquals("🌇 The evening golden hour", SkyJobNames.label(resources, "golden_hour.pm"))
        assertEquals("🌌 The dark sky window", SkyJobNames.label(resources, "darkness.window"))
        assertEquals("🌠 The peak of the Geminids", SkyJobNames.label(resources, "meteor.geminids.peak"))
        SkyJobCatalog.all.forEach { job ->
            val name = SkyJobNames.name(resources, job.id)
            val label = SkyJobNames.label(resources, job.id)
            assertTrue("${job.id} has no emoji in front of its name", label.endsWith(" $name"))
            assertTrue("${job.id} has an empty emoji", label.length > name.length + 1)
        }
    }
}
