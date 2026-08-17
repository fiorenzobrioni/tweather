package com.callbackdev.tweather.data

import com.callbackdev.tweather.data.local.ReportDiskCache
import com.callbackdev.tweather.data.local.WeatherHistoryDao
import com.callbackdev.tweather.data.local.WeatherHistoryEntry
import com.callbackdev.tweather.data.local.WeatherSnapshots
import com.callbackdev.tweather.data.mapper.WeatherReportMapper
import com.callbackdev.tweather.data.remote.OpenMeteoAirQualityApi
import com.callbackdev.tweather.data.remote.OpenMeteoForecastApi
import com.callbackdev.tweather.data.remote.OpenMeteoGeocodingApi
import com.callbackdev.tweather.data.remote.dto.GeoResultDto
import com.callbackdev.tweather.domain.WeatherException
import com.callbackdev.tweather.domain.model.CacheStatus
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.WeatherReport
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Single entry point of the data layer. In-memory cache of the last report per city
 * (TTL-based HIT/MISS, surfaced in `system_info`) backed by [ReportDiskCache] so a
 * process death doesn't cost a re-fetch inside the TTL, history persisted to Room
 * as "commits" for the Logs screen, errors normalized to [WeatherException].
 */
class WeatherRepository(
    private val forecastApi: OpenMeteoForecastApi,
    private val airQualityApi: OpenMeteoAirQualityApi,
    private val geocodingApi: OpenMeteoGeocodingApi,
    private val historyDao: WeatherHistoryDao,
    private val diskCache: ReportDiskCache? = null,
    private val json: Json = Json,
    private val clock: Clock = Clock.systemUTC(),
    private val cacheTtl: Duration = Duration.ofMinutes(15),
    /**
     * Fired after every history commit — the single choke point where new data
     * lands, whichever caller fetched it (FAB, cold start, background worker).
     * Wired to the home-widget re-render in [ServiceLocator]; a cache HIT rightly
     * skips it, as nothing changed.
     */
    private val onHistoryCommitted: suspend () -> Unit = {}
) {

    private data class CacheEntry(val report: WeatherReport, val fetchedAt: Instant)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    suspend fun searchCities(query: String): List<City> = wrapErrors {
        val results = geocodingApi.search(query).results
        if (results.isEmpty()) throw WeatherException.CityNotFound(query)
        results.map { it.toCity() }
    }

    /**
     * Report for [city]: fresh cache entry unless expired or [forceRefresh] (the
     * FAB). Cache hits keep the original `last_sync` and flip `cache_status` to HIT.
     * [ttl] lets the caller apply the user's `update_frequency_min` setting.
     */
    suspend fun getWeather(
        city: City,
        forceRefresh: Boolean = false,
        ttl: Duration = cacheTtl
    ): WeatherReport {
        val now = clock.instant()
        if (!forceRefresh) {
            cache[city.cacheKey]
                ?.takeIf { Duration.between(it.fetchedAt, now) < ttl }
                ?.let {
                    return it.report.copy(
                        systemInfo = it.report.systemInfo.copy(cacheStatus = CacheStatus.HIT)
                    )
                }
            // Process death wipes the map above: an unexpired disk entry (last fetch
            // by any process — app or worker) re-maps as a HIT instead of re-spending
            // two GETs. No history commit: its fetch already committed one.
            diskCache?.read(city.cacheKey)
                ?.takeIf { Duration.between(Instant.ofEpochMilli(it.fetchedAtEpochMs), now) < ttl }
                ?.let { entry ->
                    val fetchedAt = Instant.ofEpochMilli(entry.fetchedAtEpochMs)
                    runCatching {
                        WeatherReportMapper.map(
                            city = city,
                            forecast = entry.forecast,
                            airQuality = entry.airQuality,
                            fetchedAt = fetchedAt,
                            responseTimeMs = entry.responseTimeMs,
                            cacheStatus = CacheStatus.MISS
                        )
                    }.getOrNull()?.let { report ->
                        cache[city.cacheKey] = CacheEntry(report, fetchedAt)
                        return report.copy(
                            systemInfo = report.systemInfo.copy(cacheStatus = CacheStatus.HIT)
                        )
                    }
                }
        }
        return fetch(city, now)
    }

    fun observeHistory(limit: Int = HISTORY_RETENTION) = historyDao.observeLatest(limit)

    suspend fun historyFor(city: City, limit: Int = HISTORY_RETENTION) =
        historyDao.historyFor(city.cacheKey, limit)

    private suspend fun fetch(city: City, now: Instant): WeatherReport = wrapErrors {
        val startNanos = System.nanoTime()
        val (forecast, air) = coroutineScope {
            val forecastDeferred = async {
                forecastApi.forecast(city.coordinates.lat, city.coordinates.lon)
            }
            // Air quality is best-effort: its failure must not sink the whole report
            val airDeferred = async {
                runCatching { airQualityApi.current(city.coordinates.lat, city.coordinates.lon) }
                    .getOrNull()
            }
            forecastDeferred.await() to airDeferred.await()
        }
        val responseTimeMs = (System.nanoTime() - startNanos) / 1_000_000
        val report = WeatherReportMapper.map(
            city = city,
            forecast = forecast,
            airQuality = air?.current,
            fetchedAt = now,
            responseTimeMs = responseTimeMs,
            cacheStatus = CacheStatus.MISS
        )
        cache[city.cacheKey] = CacheEntry(report, now)
        diskCache?.write(
            city.cacheKey,
            ReportDiskCache.Entry(
                fetchedAtEpochMs = now.toEpochMilli(),
                responseTimeMs = responseTimeMs,
                forecast = forecast,
                airQuality = air?.current
            )
        )
        recordHistory(city, report)
        report
    }

    private suspend fun recordHistory(city: City, report: WeatherReport) {
        val snapshot = json.encodeToString(WeatherSnapshots.flatten(report))
        val timestamp = report.systemInfo.lastSync.epochSecond
        historyDao.insert(
            WeatherHistoryEntry(
                cityKey = city.cacheKey,
                cityLabel = city.label,
                hash = WeatherSnapshots.commitHash(city.cacheKey, timestamp.toString(), snapshot),
                author = HISTORY_AUTHOR,
                timestampEpochSeconds = timestamp,
                snapshotJson = snapshot,
                forecastJson = json.encodeToString(WeatherSnapshots.flattenForecast(report))
            )
        )
        historyDao.prune(HISTORY_RETENTION)
        // A failing observer must never sink a successful fetch — but a cancelled
        // caller still has to unwind, and runCatching would eat the cancellation
        // too, letting a superseded load publish its stale report.
        try {
            onHistoryCommitted()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // report already cached and committed; at worst the widget misses a repaint
        }
    }

    private inline fun <T> wrapErrors(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: WeatherException) {
        throw e
    } catch (e: HttpException) {
        throw WeatherException.ApiError(e.code(), e)
    } catch (e: IOException) {
        throw WeatherException.NoNetwork(e)
    } catch (e: Exception) {
        throw WeatherException.Unknown(e)
    }

    companion object {
        const val HISTORY_AUTHOR = "sys@tweather.app"
        const val HISTORY_RETENTION = 100
    }
}

private fun GeoResultDto.toCity() = City(
    id = id,
    name = name,
    region = admin1,
    country = country ?: countryCode,
    coordinates = Coordinates(latitude, longitude),
    timezone = timezone
)
