package com.callbackdev.tweather.ui

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.tweather.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The register rule as a test (`PLANNING.md` Fase 18), read off the resources
 * themselves rather than off any one screen.
 *
 * The per-screen tests check that a line is drawn; this checks the thing the rule
 * is actually about — that what moved is prose and what stayed is code — and it
 * checks it for **every** note at once, which is the only way to notice the day
 * somebody adds one more and writes it in English.
 */
@RunWith(RobolectricTestRunner::class)
class RegisterRuleTest {

    private val resources: Resources
        get() = ApplicationProvider.getApplicationContext<Context>().resources

    /**
     * Every `note_*` string the app prints in the comment channel, taken by
     * **reflection** over `R.string`.
     *
     * This list used to be written out by hand, and by the time tsteps' Fase 20
     * came to copy it, eleven of the sixty-one notes had been added without ever
     * being added here: the guard was green and watching fifty of them. A list
     * that has to be remembered is a list that gets forgotten, so now there is
     * nothing to keep in sync — a note written tomorrow is guarded the moment it
     * exists.
     */
    private val notes: List<Pair<String, Int>> = R.string::class.java.declaredFields
        .filter { it.name.startsWith("note_") }
        .map { it.name to it.getInt(null) }
        .sortedBy { it.first }

    @Test
    fun `the sweep found the notes at all`() {
        // A reflective list that silently came back empty would make every other
        // test in this class pass by vacuum.
        assertTrue("suspiciously few notes: ${notes.size}", notes.size >= 55)
    }

    /**
     * The marker is the file's syntax and is added by the renderer, never by the
     * resource. A note that carried its own `//` would be a line that cannot be
     * reused by a file whose comment channel is `#`, which is half of them.
     */
    @Test
    fun `no note carries its own comment marker`() {
        notes.forEach { (name, id) ->
            val text = resources.getString(id)
            assertTrue("$name carries its own marker: '$text'",
                !text.startsWith("//") && !text.startsWith("#"))
        }
    }

    /**
     * And none of them carries a level either: `ERROR:` and `WARN:` are tokens of
     * the channel, the renderer puts them there, and a translated `ERRORE:` would
     * be the one word on the line that a reader looking for a log level cannot find.
     */
    @Test
    fun `no note carries its own level`() {
        notes.forEach { (name, id) ->
            val text = resources.getString(id)
            assertTrue("$name carries a level: '$text'",
                !text.startsWith("ERROR") && !text.startsWith("WARN"))
        }
    }

    /**
     * The one note that reads the same in both languages, and why.
     *
     * `current_location.json in cities.json` is two file names and the preposition
     * between them, and that preposition is the same word in Italian. It is a
     * resource rather than a literal so that a future rewording is translatable
     * without moving it, but there is nothing in it to translate today — and
     * inventing a difference (`dentro cities.json`) to satisfy a test would be
     * writing worse Italian to make a green tick.
     */
    private val identicalOnPurpose: Set<String> = setOf("note_gps_pinned")

    /**
     * Every other note is actually translated. A rule kept sixty times out of
     * sixty-one does not read as a decision, it reads as a job somebody abandoned
     * halfway — which is the failure mode Fase 18 wrote itself against.
     */
    @Test
    @Config(qualifiers = "it")
    fun `every note says something different in Italian`() {
        val english = ApplicationProvider.getApplicationContext<Context>()
            .createConfigurationContext(
                android.content.res.Configuration(resources.configuration).apply {
                    setLocale(java.util.Locale.ENGLISH)
                }
            ).resources
        val unchanged = notes.filter { (name, id) ->
            name !in identicalOnPurpose && resources.getString(id) == english.getString(id)
        }.map { it.first }
        assertEquals("these notes were never translated: $unchanged", emptyList<String>(), unchanged)
    }

    /** The allowlist must not rot into a list of names nobody looked at. */
    @Test
    fun `every allowlisted name still exists`() {
        val known = notes.map { it.first }.toSet()
        identicalOnPurpose.forEach {
            assertTrue("'$it' is allowlisted but is no longer a note", it in known)
        }
    }

    /**
     * The tokens survive the translation. A file name, a key or a command inside a
     * localized sentence is still the thing the reader has to type or look for, so
     * it comes through both languages unchanged.
     */
    @Test
    @Config(qualifiers = "it")
    fun `the tokens inside a translated sentence survive it`() {
        assertTrue(resources.getString(R.string.note_hint_search).contains("cities.json"))
        assertTrue(resources.getString(R.string.note_fetching).contains("weather_data.json"))
        assertTrue(resources.getString(R.string.note_first_commit).contains("weather_data.json"))
        assertTrue(resources.getString(R.string.note_rules_builtin).contains("settings.config"))
        assertTrue(resources.getString(R.string.note_rules_off).contains("\"user_rules\""))
        assertTrue(resources.getString(R.string.note_sky_in_editor).contains("sky.crontab"))
        assertTrue(resources.getString(R.string.note_gps_pinned).contains("current_location.json"))
        // `commit` is a git noun: git keeps it in every language it ships.
        assertTrue(resources.getString(R.string.note_no_commits).contains("commit"))
    }
}
