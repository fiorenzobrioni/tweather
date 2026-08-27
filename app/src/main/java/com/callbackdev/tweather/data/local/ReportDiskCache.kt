package com.callbackdev.tweather.data.local

import com.callbackdev.tweather.data.remote.dto.AirQualityCurrentDto
import com.callbackdev.tweather.data.remote.OpenMeteoForecastApi
import com.callbackdev.tweather.data.remote.dto.ForecastResponseDto
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Last successful fetch per city, persisted as the raw API DTOs plus fetch
 * metadata. The repository's in-memory TTL cache dies with the process; this copy
 * survives it, so a cold start inside the TTL re-maps the disk entry instead of
 * re-spending the two HTTP GETs a worker (or a previous process) already paid for.
 * Not Room: the history table stores flattened diff snapshots, not enough to
 * rebuild a [com.callbackdev.tweather.domain.model.WeatherReport]. Best-effort by
 * design — any I/O or parse failure just falls through to the network.
 *
 * Since Fase 17 it is also **what the editor falls back to when a fetch fails**, and
 * that changed what "dead weight" means here: an entry past the TTL is no longer
 * useless, it is the only forecast an offline phone has. See [prune].
 */
class ReportDiskCache(private val dir: File, private val json: Json) {

    @Serializable
    data class Entry(
        val fetchedAtEpochMs: Long,
        val responseTimeMs: Long,
        val forecast: ForecastResponseDto,
        val airQuality: AirQualityCurrentDto?
    )

    suspend fun read(cacheKey: String): Entry? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(dir, fileName(cacheKey))
            if (file.isFile) json.decodeFromString<Entry>(file.readText()) else null
        }.getOrNull()
    }

    suspend fun write(cacheKey: String, entry: Entry) {
        withContext(Dispatchers.IO) {
            runCatching {
                dir.mkdirs()
                File(dir, fileName(cacheKey)).writeText(json.encodeToString(entry))
                prune()
            }
        }
    }

    /**
     * Two cutoffs since Fase 17, and the age one moved a long way out.
     *
     * It used to be four hours — "twice the largest TTL, so it can never be read back
     * as a hit". True while a hit was the only thing an entry could ever be; now an
     * entry is also the document the editor shows when it cannot refresh, and the
     * response holds a **week** of forecast, so it stays useful for as long as that
     * forecast still reaches the present ([com.callbackdev.tweather.domain.WeatherRecency]
     * decides that, from the data rather than from a constant). Pruning at four hours
     * would have quietly capped the offline fallback at four hours.
     *
     * The count cutoff is what the age one used to do: the GPS pseudo-city mints a
     * cacheKey per ~1.1 km cell, so a week of commuting would otherwise leave a file
     * behind for every place the user has been. Newest kept, oldest deleted.
     */
    private fun prune() {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.forEachIndexed { index, file ->
            if (index >= MAX_ENTRIES || file.lastModified() < cutoff) file.delete()
        }
    }

    /** cacheKey is `lat:lon` in hundredths — ':' is not filesystem-safe everywhere. */
    private fun fileName(cacheKey: String) = cacheKey.replace(':', '_') + ".json"

    companion object {
        /**
         * The forecast horizon: past it the response says nothing about any hour that
         * has not already happened, so the file is finally dead weight for real.
         */
        private const val MAX_AGE_MS = OpenMeteoForecastApi.FORECAST_DAYS * 24 * 60 * 60 * 1000L

        /**
         * Roughly a saved-city list plus a handful of GPS cells. An evicted entry
         * costs one fetch when there is a network and the offline fallback for that
         * one city when there is not — which is why the cap is on the count and not
         * on the age the fallback needs.
         */
        private const val MAX_ENTRIES = 16
    }
}
