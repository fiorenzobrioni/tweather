package com.callbackdev.tweather.data

import android.location.Address
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The two decisions that turned a device position into a place name, and got the
 * place wrong (device, 5 set 2026).
 *
 * The reader was in Cavenago di Brianza and the header said "Provincia di Monza e
 * della Brianza"; standing in Segrate it said "Milano". Neither is a geocoder
 * failure — the geocoder was asked the wrong question, and its answer was read the
 * wrong way round.
 */
@RunWith(RobolectricTestRunner::class)
class LocationProviderTest {

    private fun address(
        locality: String? = null,
        subLocality: String? = null,
        subAdminArea: String? = null,
        adminArea: String? = null,
        countryName: String? = null
    ) = Address(Locale.ITALY).apply {
        this.locality = locality
        this.subLocality = subLocality
        this.subAdminArea = subAdminArea
        this.adminArea = adminArea
        this.countryName = countryName
    }

    // --- the name -----------------------------------------------------------------

    @Test
    fun `a province does not stand in for a town another rung knows`() {
        val place = geocodedPlace(
            listOf(
                // What a point in the fields comes back as: region, province, nothing
                // between them. This rung used to be the only one read.
                address(
                    subAdminArea = "Provincia di Monza e della Brianza",
                    adminArea = "Lombardia",
                    countryName = "Italia"
                ),
                address(locality = "Cavenago di Brianza", adminArea = "Lombardia")
            )
        )
        assertEquals("Cavenago di Brianza", place.name)
        assertEquals("Lombardia", place.region)
        assertEquals("Italia", place.country)
    }

    @Test
    fun `a quarter is somewhere you can be and a province is not`() {
        val place = geocodedPlace(
            listOf(
                address(
                    subLocality = "Redecesio",
                    subAdminArea = "Citta metropolitana di Milano"
                )
            )
        )
        assertEquals("Redecesio", place.name)
    }

    @Test
    fun `the province is still the answer when nothing on the ladder knows a town`() {
        val place = geocodedPlace(
            listOf(address(subAdminArea = "Provincia di Monza e della Brianza"))
        )
        assertEquals("Provincia di Monza e della Brianza", place.name)
    }

    @Test
    fun `a blank field is not an answer`() {
        val place = geocodedPlace(
            listOf(
                address(locality = "   ", subLocality = ""),
                address(locality = "Segrate")
            )
        )
        assertEquals("Segrate", place.name)
    }

    @Test
    fun `nothing geocoded leaves every field null`() {
        val place = geocodedPlace(emptyList())
        assertNull(place.name)
        assertNull(place.region)
        assertNull(place.country)
    }

    @Test
    fun `region and country are taken from whichever rung carries them`() {
        val place = geocodedPlace(
            listOf(
                address(locality = "Segrate"),
                address(adminArea = "Lombardia", countryName = "Italia")
            )
        )
        assertEquals("Segrate", place.name)
        assertEquals("Lombardia", place.region)
        assertEquals("Italia", place.country)
    }

    // --- which known position wins ------------------------------------------------

    private fun seconds(n: Long) = n * 1_000_000_000L

    @Test
    fun `a fresher cell fix does not beat a good one from two minutes ago`() {
        val cell = expectedErrorMeters(accuracyMeters = 5_000f, ageNanos = seconds(10))
        val fused = expectedErrorMeters(accuracyMeters = 2_000f, ageNanos = seconds(120))
        assertTrue("$fused should be better than $cell", fused < cell)
    }

    @Test
    fun `age still decides between two positions of the same quality`() {
        val old = expectedErrorMeters(accuracyMeters = 2_000f, ageNanos = seconds(3_600))
        val recent = expectedErrorMeters(accuracyMeters = 2_000f, ageNanos = seconds(60))
        assertTrue(recent < old)
    }

    @Test
    fun `a yesterday fix loses the last resort to a worse one from an hour ago`() {
        val yesterday = expectedErrorMeters(accuracyMeters = 2_000f, ageNanos = seconds(20 * 3_600))
        val hourAgo = expectedErrorMeters(accuracyMeters = 5_000f, ageNanos = seconds(3_600))
        assertTrue(hourAgo < yesterday)
    }

    @Test
    fun `a position that will not say how good it is loses to any that does`() {
        val silent = expectedErrorMeters(accuracyMeters = null, ageNanos = 0L)
        val stated = expectedErrorMeters(accuracyMeters = 5_000f, ageNanos = seconds(60))
        assertTrue(stated < silent)
    }

    @Test
    fun `a clock that moved under us cannot make a position better than perfect`() {
        assertEquals(
            2_000.0,
            expectedErrorMeters(accuracyMeters = 2_000f, ageNanos = -seconds(60)),
            0.001
        )
    }
}
