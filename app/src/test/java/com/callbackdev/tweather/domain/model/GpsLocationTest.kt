package com.callbackdev.tweather.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpsLocationTest {

    @Test
    fun `gpsLabel formats hemispheres and two decimals`() {
        assertEquals("45.46N 9.19E", Coordinates(45.46, 9.19).gpsLabel)
        assertEquals("33.87S 151.21E", Coordinates(-33.87, 151.21).gpsLabel)
        assertEquals("40.71N 74.01W", Coordinates(40.71, -74.006).gpsLabel)
        assertEquals("0.00N 0.00E", Coordinates(0.0, 0.0).gpsLabel)
    }

    @Test
    fun `toGpsCity uses reverse geocoded name when present`() {
        val city = GeoFix(Coordinates(45.46, 9.19), "Milano", "Lombardia", "Italy").toGpsCity()
        assertEquals(GpsCityId, city.id)
        assertEquals("Milano", city.name)
        assertEquals("Lombardia", city.region)
        assertEquals("Italy", city.country)
        assertNull(city.timezone)
    }

    @Test
    fun `toGpsCity falls back to coordinate label without geocoding`() {
        val city = GeoFix(Coordinates(45.46, 9.19), null, null, null).toGpsCity()
        assertEquals("45.46N 9.19E", city.name)
        assertEquals("45.46N 9.19E", city.label)
    }

    @Test
    fun `two-decimal coordinates map exactly onto cacheKey`() {
        val a = GeoFix(Coordinates(45.46, 9.19), null, null, null).toGpsCity()
        val b = GeoFix(Coordinates(45.46, 9.19), "Milano", null, null).toGpsCity()
        assertEquals(a.cacheKey, b.cacheKey)
        assertEquals("4546:919", a.cacheKey)
    }
}
