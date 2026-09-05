package com.callbackdev.tweather.ui.sky

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.tweather.domain.sky.SkyJobCatalog
import com.callbackdev.tweather.domain.sky.SkyJobKind
import com.callbackdev.tweather.domain.sky.SkyJobShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The manual (Fase 23). The catalog is a fixed list in code and the pages are a map
 * beside it, which is exactly the shape that goes quietly out of date: the guard that
 * matters is totality, asserted here rather than discovered by the reader who taps
 * `[man]` on the fifty-second job and gets a crash.
 */
@RunWith(RobolectricTestRunner::class)
class SkyManPagesTest {

    private val resources: Resources
        get() = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun `every job in the catalog has a page`() {
        SkyJobCatalog.all.forEach { job ->
            assertTrue("${job.id} has no manual page", SkyManPages.hasPage(job.id))
            val body = resources.getString(SkyManPages.pageOf(job.id))
            assertTrue("${job.id}'s page is empty", body.isNotBlank())
        }
    }

    /**
     * The point of a page rather than a tooltip is that there is room to explain, so
     * this is the placeholder guard: two paragraphs, a body with something in it, and
     * no paragraph that is a fragment. It deliberately does NOT ask each paragraph to
     * be long — several pages open with a one-sentence definition and then explain
     * underneath, which is how a manual is supposed to read, and a floor high enough
     * to forbid that would be a test with an opinion about prose rhythm.
     */
    @Test
    fun `every page is two real paragraphs and not a placeholder`() {
        SkyJobCatalog.all.forEach { job ->
            val body = resources.getString(SkyManPages.pageOf(job.id))
            val paragraphs = body.split("\n\n").filter { it.isNotBlank() }
            assertEquals("${job.id} is not two paragraphs", 2, paragraphs.size)
            // 300, not "as long as the longest": the shortest real pages run just
            // over 320 characters, and a floor set at the current minimum turns any
            // future edit that tightens a sentence into a failing test.
            assertTrue("${job.id}'s page is a stub: $body", body.length > 300)
            paragraphs.forEach { paragraph ->
                assertTrue(
                    "${job.id} has a fragment for a paragraph: $paragraph",
                    paragraph.trim().length > 50
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "it")
    fun `every page is actually translated`() {
        val english = ApplicationProvider.getApplicationContext<Context>()
        SkyJobCatalog.all.forEach { job ->
            val italian = resources.getString(SkyManPages.pageOf(job.id))
            assertTrue("${job.id} has no Italian page", italian.isNotBlank())
            // The English is fetched through a separate configuration below; here it
            // is enough that the Italian is not a copy of it.
            assertNotEquals(
                "${job.id} reads the same in both languages",
                englishPage(english, job.id),
                italian
            )
        }
    }

    private fun englishPage(context: Context, jobId: String): String {
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(java.util.Locale.ENGLISH)
        return context.createConfigurationContext(config).resources
            .getString(SkyManPages.pageOf(jobId))
    }

    /** A page that points nowhere is a dead end; one that points at itself is a bug. */
    @Test
    fun `see also only ever points at real pages, never at itself`() {
        SkyJobCatalog.all.forEach { job ->
            val targets = SkyManPages.seeAlso(job.id)
            assertEquals("${job.id} lists a page twice", targets.distinct(), targets)
            targets.forEach { target ->
                assertNotEquals("${job.id} points at itself", job.id, target)
                assertTrue(
                    "${job.id} points at $target, which is not in the catalog",
                    SkyJobCatalog.byId(target) != null
                )
            }
        }
    }

    /**
     * Symmetry, with the one exception the map documents: every shower points at
     * `darkness.window` and it does not point back, because a page whose SEE ALSO
     * listed thirteen showers is a page nobody finishes.
     */
    @Test
    fun `see also is mutual except for the showers' shared link`() {
        val showers = com.callbackdev.tweather.domain.sky.MeteorShowerTable.all
            .map { com.callbackdev.tweather.domain.sky.MeteorShowerTable.jobId(it) }
            .toSet()
        SkyJobCatalog.all.forEach { job ->
            SkyManPages.seeAlso(job.id).forEach { target ->
                val oneWay = job.id in showers &&
                    target == SkyJobCatalog.DarknessWindow.id
                if (!oneWay) {
                    assertTrue(
                        "${job.id} points at $target and $target does not point back",
                        job.id in SkyManPages.seeAlso(target)
                    )
                }
            }
        }
    }

    /** Every shower's page names the darkness window, since that is what decides it. */
    @Test
    fun `every shower hands the reader to the darkness window`() {
        com.callbackdev.tweather.domain.sky.MeteorShowerTable.all.forEach { shower ->
            val id = com.callbackdev.tweather.domain.sky.MeteorShowerTable.jobId(shower)
            assertTrue(
                "$id does not point at the darkness window",
                SkyJobCatalog.DarknessWindow.id in SkyManPages.seeAlso(id)
            )
        }
    }

    /**
     * WHEN is read off the job and never written down twice — this is the test that
     * says so, by checking the sentences track the fields rather than a hand-kept
     * list. A job whose kind changed and whose page still claimed the old cadence is
     * exactly the drift the generation exists to prevent.
     */
    @Test
    fun `the WHEN section is the job's own definition, said in words`() {
        val daily = resources.getString(com.callbackdev.tweather.R.string.man_when_daily)
        val annual = resources.getString(com.callbackdev.tweather.R.string.man_when_annual)
        val polling = resources.getString(com.callbackdev.tweather.R.string.man_when_polling)
        val range = resources.getString(com.callbackdev.tweather.R.string.man_when_range)
        val geometry = resources.getString(com.callbackdev.tweather.R.string.man_when_geometry)
        val darkness = resources.getString(com.callbackdev.tweather.R.string.man_when_darkness)

        SkyJobCatalog.all.forEach { job ->
            val lines = SkyManPages.whenLines(resources, job)
            val cadence = when (job.kind) {
                SkyJobKind.DAILY -> daily
                SkyJobKind.ANNUAL -> annual
                SkyJobKind.POLLING -> polling
            }
            assertEquals("${job.id} says the wrong cadence", cadence, lines.first())
            assertEquals(
                "${job.id} says the wrong shape",
                job.shape == SkyJobShape.RANGE,
                lines.contains(range)
            )
            assertEquals(
                "${job.id} is wrong about being a sight",
                !job.observable,
                lines.contains(geometry)
            )
            assertEquals(
                "${job.id} is wrong about needing a dark sky",
                job.needsDarkness,
                lines.contains(darkness)
            )
        }
    }

    /** A moment of geometry has no verdict, so it must never claim the clouds decide. */
    @Test
    fun `a job the clouds cannot spoil never says they can`() {
        val visibility =
            resources.getString(com.callbackdev.tweather.R.string.man_when_visibility)
        SkyJobCatalog.all.filter { !it.observable }.forEach { job ->
            assertFalse(
                "${job.id} is not observable but blames the clouds",
                SkyManPages.whenLines(resources, job).contains(visibility)
            )
        }
    }

    @Test
    fun `the header is the id shouted, with its manual section`() {
        assertEquals("GOLDEN_HOUR.PM(7)", SkyManPages.header(SkyJobCatalog.GoldenPm.id))
    }
}
