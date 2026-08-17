package com.callbackdev.tweather.ui.explorer

import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unique fake filenames for the Explorer tree and widget.config: an editor can't
 * show two files with the same name in one folder, so homonym cities must not
 * produce identical `.json` entries.
 */
class ExplorerFileNamesTest {

    private fun city(id: Long, name: String, region: String? = null, country: String? = null) =
        City(id, name, region, country, Coordinates(0.0, 0.0), null)

    @Test
    fun `unique names keep the plain slug`() {
        val names = fileNames(listOf(city(1, "Milan", "Lombardy"), city(2, "New York", "New York")))
        assertEquals("milan.json", names.getValue(1))
        assertEquals("new_york.json", names.getValue(2))
    }

    @Test
    fun `homonyms qualify themselves with their region`() {
        val names = fileNames(
            listOf(city(1, "Springfield", "Illinois"), city(2, "Springfield", "Missouri"))
        )
        assertEquals("springfield_illinois.json", names.getValue(1))
        assertEquals("springfield_missouri.json", names.getValue(2))
    }

    @Test
    fun `homonyms in the same region fall back to the id`() {
        val names = fileNames(
            listOf(city(1, "Springfield", "Illinois"), city(2, "Springfield", "Illinois"))
        )
        assertEquals("springfield_1.json", names.getValue(1))
        assertEquals("springfield_2.json", names.getValue(2))
    }

    @Test
    fun `a homonym without a region qualifies with its country`() {
        val names = fileNames(
            listOf(city(1, "Springfield", null, "USA"), city(2, "Springfield", "Missouri"))
        )
        assertEquals("springfield_usa.json", names.getValue(1))
        assertEquals("springfield_missouri.json", names.getValue(2))
    }
}
