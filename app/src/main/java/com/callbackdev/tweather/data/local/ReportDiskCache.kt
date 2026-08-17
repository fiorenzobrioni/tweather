package com.callbackdev.tweather.data.local

import com.callbackdev.tweather.data.remote.dto.AirQualityCurrentDto
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
            }
        }
    }

    /** cacheKey is `lat:lon` in hundredths — ':' is not filesystem-safe everywhere. */
    private fun fileName(cacheKey: String) = cacheKey.replace(':', '_') + ".json"
}
