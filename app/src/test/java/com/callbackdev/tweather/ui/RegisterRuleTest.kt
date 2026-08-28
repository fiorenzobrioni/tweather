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
 * somebody adds a forty-eighth and writes it in English.
 */
@RunWith(RobolectricTestRunner::class)
class RegisterRuleTest {

    private val resources: Resources
        get() = ApplicationProvider.getApplicationContext<Context>().resources

    /** Every `note_*` string the app prints in the comment channel. */
    private val notes: List<Int> = listOf(
        R.string.note_no_location, R.string.note_hint_search, R.string.note_gps_acquiring,
        R.string.note_fetching, R.string.note_hint_retry, R.string.note_tap_add_city,
        R.string.note_clear_history, R.string.note_click_toggle, R.string.note_full_json,
        R.string.note_sky_in_editor, R.string.note_notify_invisible,
        R.string.note_restore_defaults, R.string.note_alerts_disabled,
        R.string.note_gps_tap_enable, R.string.note_gps_pinned,
        R.string.note_err_notif_missing, R.string.note_err_denied,
        R.string.note_err_gps_denied, R.string.note_err_gps_revoked,
        R.string.note_rules_builtin, R.string.note_rules_off, R.string.note_rules_none,
        R.string.note_rules_run, R.string.note_rules_evaluating, R.string.note_no_commits,
        R.string.note_first_commit, R.string.note_no_revisions, R.string.note_revisions_land,
        R.string.note_no_runs, R.string.note_first_run, R.string.note_sky_no_jobs,
        R.string.note_sky_all_added, R.string.note_sky_run, R.string.note_sky_times,
        R.string.note_sky_light, R.string.note_sky_opinion, R.string.note_sky_polar_day,
        R.string.note_sky_polar_night, R.string.note_sky_moon_absent,
        R.string.note_sky_no_darkness, R.string.note_sky_beyond_horizon,
        R.string.note_sky_no_data, R.string.note_sky_stale_data, R.string.note_sky_no_coverage,
        R.string.note_sky_moonless, R.string.note_sky_moon_all_night,
        R.string.note_widget_no_data_yet, R.string.note_widget_no_data,
        R.string.note_widget_tap_pin, R.string.note_widget_follows
    )

    /**
     * The marker is the file's syntax and is added by the renderer, never by the
     * resource. A note that carried its own `//` would be a line that cannot be
     * reused by a file whose comment channel is `#`, which is half of them.
     */
    @Test
    fun `no note carries its own comment marker`() {
        notes.forEach { id ->
            val text = resources.getString(id)
            assertTrue("'$text' carries its own marker", !text.startsWith("//") && !text.startsWith("#"))
        }
    }

    /**
     * And none of them carries a level either: `ERROR:` and `WARN:` are tokens of
     * the channel, the renderer puts them there, and a translated `ERRORE:` would
     * be the one word on the line that a reader looking for a log level cannot find.
     */
    @Test
    fun `no note carries its own level`() {
        notes.forEach { id ->
            val text = resources.getString(id)
            assertTrue("'$text' carries a level", !text.startsWith("ERROR") && !text.startsWith("WARN"))
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
    private val identicalOnPurpose: Set<Int> = setOf(R.string.note_gps_pinned)

    /**
     * Every other note is actually translated. A rule kept forty-nine times out of
     * fifty does not read as a decision, it reads as a job somebody abandoned
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
        val unchanged = notes.filter { id ->
            id !in identicalOnPurpose && resources.getString(id) == english.getString(id)
        }.map { english.getString(it) }
        assertEquals("these notes were never translated: $unchanged", emptyList<String>(), unchanged)
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
