package com.callbackdev.tweather.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.tweather.R
import com.callbackdev.tweather.data.TemperatureUnit
import com.callbackdev.tweather.data.WindSpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The second half of the guard on `WidgetContentBuilder`'s English defaults.
 *
 * The builder is a pure value, so its two empty-state sentences arrive as strings
 * with an English fallback written into the signature — a copy of what
 * `values/strings.xml` says. Two copies of a sentence drift the day somebody edits
 * one, so this ties them: change either alone and the suite goes red.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetNotesTest {

    @Test
    fun `the defaults in the signature are what the resources say`() {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        val default = WidgetContentBuilder.build(
            snapshot = null,
            timestampEpochSeconds = null,
            temperature = TemperatureUnit.CELSIUS,
            windSpeed = WindSpeedUnit.KMH,
            tier = WidgetTier.Terminal(4)
        )
        assertEquals(
            "# " + resources.getString(R.string.note_widget_no_data_yet),
            default.bodyLines.single().tokens.single().text
        )
        assertEquals(
            "# " + resources.getString(R.string.note_widget_no_data),
            default.smallLocation?.tokens?.single()?.text
        )
    }
}
