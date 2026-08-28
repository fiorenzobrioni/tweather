package com.callbackdev.tweather.ui.sky

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.tweather.domain.sky.SkyVerdictEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The guard on the one duplication Fase 18 had to accept.
 *
 * `SkyDocumentBuilder` is a pure value and its tests are plain JVM tests, so the
 * document could not take a `Resources`: its sentences arrive as [SkyNotes], with
 * [SkyNotes.EN] as the fallback a caller with no resources gets. That means the
 * English lives twice — once in Kotlin, once in `values/strings.xml` — and two
 * copies of a sentence drift the day somebody edits one of them.
 *
 * So they are tied together here, word for word. Edit either side alone and the
 * suite goes red, which is the only reason the duplication is allowed to exist.
 */
@RunWith(RobolectricTestRunner::class)
class SkyNotesTest {

    private val resources: Resources
        get() = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun `the English in Kotlin is the English in the resources, word for word`() {
        val fromResources = skyNotes(resources)
        val en = SkyNotes.EN

        assertEquals(en.times, fromResources.times)
        assertEquals(en.footer, fromResources.footer)
        assertEquals(en.polarDay, fromResources.polarDay)
        assertEquals(en.polarNight, fromResources.polarNight)
        assertEquals(en.moonAbsent, fromResources.moonAbsent)
        assertEquals(en.noDarkness, fromResources.noDarkness)
        assertEquals(en.beyondHorizon, fromResources.beyondHorizon)
        assertEquals(en.noFetchYet, fromResources.noFetchYet)
        assertEquals(en.staleData, fromResources.staleData)
        assertEquals(en.noCoverage, fromResources.noCoverage)
        assertEquals(en.moonless, fromResources.moonless)
        assertEquals(en.moonAllNight, fromResources.moonAllNight)
        // The three that take an argument, checked through it.
        assertEquals(en.dstForward("Oct 25"), fromResources.dstForward("Oct 25"))
        assertEquals(en.dstBack("Oct 25"), fromResources.dstBack("Oct 25"))
        assertEquals(en.moonlessFrom("23:41"), fromResources.moonlessFrom("23:41"))
    }

    /**
     * The footer states the thresholds the verdicts were actually built from
     * (`VISION_SKY.md` §7: they must not be invisible). A resource with the numbers
     * typed into it instead of formatted in would satisfy the test above and still
     * lie the day a constant moves, so the numbers are checked against the engine.
     */
    @Test
    fun `the footer quotes the engine's own thresholds`() {
        val footer = skyNotes(resources).footer.first()
        assertEquals(true, footer.contains("${SkyVerdictEngine.CLOUD_PASS_PCT}%"))
        assertEquals(true, footer.contains("${SkyVerdictEngine.CLOUD_FAIL_PCT}%"))
        assertEquals(true, footer.contains("${SkyVerdictEngine.PRECIP_FAIL_PCT}%"))
    }

    /**
     * And in Italian the sentences move while the numbers and the tokens do not.
     * `pass` and `fail` are verdict words, so they read the same in both files and
     * in both languages.
     */
    @Test
    @Config(qualifiers = "it")
    fun `in Italian the sentences move and the verdict words do not`() {
        val notes = skyNotes(resources)
        assertEquals("gli orari sono calcolati per occorrenza, non fissi; vedi ogni riga", notes.times)
        assertEquals("giorno polare: qui il sole resta sopra l'orizzonte", notes.polarDay)
        assertEquals("nessun fetch ancora", notes.noFetchYet)
        assertEquals("senza luna dalle 23:41", notes.moonlessFrom("23:41"))
        val footer = notes.footer.first()
        assertEquals(true, footer.contains("pass ≤ ${SkyVerdictEngine.CLOUD_PASS_PCT}%"))
        assertEquals(true, footer.contains("fail sopra"))
    }

    /** A moon phase is a value, and values have localized since Fase 6b. */
    @Test
    @Config(qualifiers = "it")
    fun `a moon phase reads the same word here as it does in weather_data json`() {
        assertEquals("Luna piena", skyNotes(resources).moonPhase("Full Moon"))
    }
}
