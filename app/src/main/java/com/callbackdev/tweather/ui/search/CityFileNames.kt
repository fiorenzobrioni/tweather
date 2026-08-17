package com.callbackdev.tweather.ui.search

import com.callbackdev.tweather.domain.model.City

/** `New York` → `new_york`, the fake filename shown in cities.json (and in widget.config). */
internal fun City.fileSlug(): String = slugOf(name)

private fun slugOf(raw: String): String =
    raw.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "city" }

/**
 * One unique fake filename per saved city. An editor can't show two files with the
 * same name in one folder, so homonyms qualify themselves with their region/country
 * (`springfield_illinois.json` vs `springfield_missouri.json`) and the id breaks any
 * tie even that can't (same name, same region — ids never collide).
 */
internal fun fileNames(cities: List<City>): Map<Long, String> {
    val byBase = cities.groupBy { it.fileSlug() }
    val byQualified = cities.groupBy { it.qualifiedSlug() }
    return cities.associate { city ->
        val slug = when {
            byBase.getValue(city.fileSlug()).size == 1 -> city.fileSlug()
            byQualified.getValue(city.qualifiedSlug()).size == 1 -> city.qualifiedSlug()
            else -> "${city.fileSlug()}_${city.id}"
        }
        city.id to "$slug.json"
    }
}

private fun City.qualifiedSlug(): String =
    (region ?: country)?.let { "${fileSlug()}_${slugOf(it)}" } ?: fileSlug()
