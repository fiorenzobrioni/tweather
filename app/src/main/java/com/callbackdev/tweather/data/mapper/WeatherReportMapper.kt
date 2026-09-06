package com.callbackdev.tweather.data.mapper

import com.callbackdev.tweather.data.remote.OpenMeteoForecastApi
import com.callbackdev.tweather.data.remote.dto.AirQualityCurrentDto
import com.callbackdev.tweather.data.remote.dto.ForecastResponseDto
import com.callbackdev.tweather.data.remote.dto.HourlyDto
import com.callbackdev.tweather.domain.WeatherCodes
import com.callbackdev.tweather.domain.model.AirQuality
import com.callbackdev.tweather.domain.model.Astronomical
import com.callbackdev.tweather.domain.model.CacheStatus
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.CurrentConditions
import com.callbackdev.tweather.domain.model.DailyForecast
import com.callbackdev.tweather.domain.model.HourlyForecast
import com.callbackdev.tweather.domain.model.Location
import com.callbackdev.tweather.domain.model.MoonPhase
import com.callbackdev.tweather.domain.model.PollenReport
import com.callbackdev.tweather.domain.model.Pollutants
import com.callbackdev.tweather.domain.model.Precipitation
import com.callbackdev.tweather.domain.model.SystemInfo
import com.callbackdev.tweather.domain.model.WeatherReport
import com.callbackdev.tweather.domain.model.Wind
import com.callbackdev.tweather.domain.sky.AstronomyEngine
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Hourly slots carried in the domain: the CURRENT hour first — slot 0 feeds
 * `current_conditions`' rain chance and anchors AlertEngine/rules (Fase 11f: the
 * views drop it, it only repeats the current section) — and then every remaining
 * hour the response carries.
 *
 * `FORECAST_DAYS × 24` since Fase 16a, 25 before it. The old number was one day, and
 * the other 143 hours of the response were deserialized into [HourlyDto], written to
 * `ReportDiskCache` as part of the raw DTO, and then dropped on the floor by this
 * function. So a sky verdict three days out is not a new capability that needs a new
 * request: it is data the app has been paying for and discarding. Widening costs no
 * network, no disk and no parsing — only the mapped objects, which is why the count
 * is bounded here rather than left as "whatever arrived".
 *
 * The realized count is never the full 168: the window opens at the current hour, so
 * it runs from ~168 at midnight down to ~145 at 23:00, and [mapHourly] clips it to
 * what the response actually holds.
 *
 * **The views do not follow this window.** `weather_data.json` caps its table at 24
 * rows and the README at 14; AlertEngine and RuleVariables bound themselves by time
 * (`next_6h`, `next_12h`, `now.plusHours(…)`) rather than by position. Anything new
 * reading `report.hourly` has to decide its own horizon — the list is a week now.
 */
private const val HOURLY_WINDOW = OpenMeteoForecastApi.FORECAST_DAYS * 24

/** Days of daily forecast carried — the whole response, like [HOURLY_WINDOW]. */
private const val DAILY_WINDOW = OpenMeteoForecastApi.FORECAST_DAYS

/**
 * Below 51 the WMO scale carries only sky states and fog; from 51 up every code is a
 * precipitation of some kind (drizzle, rain, snow, showers, thunderstorm).
 */
private const val FIRST_PRECIP_CODE = 51

/** WMO 45; Open-Meteo derives 48 in its enum but never emits it. */
private const val WMO_FOG = 45
private val FogCodes = setOf(45, 48)

/** Open-Meteo's own fog threshold, `WeatherCode.swift:99`: `visibility <= 1000 → fog`. */
private const val FOG_VISIBILITY_M = 1000.0

/**
 * WMO codes that declare an intensity or a hazard of their own: freezing anything,
 * the heavy grades, violent showers, every thunderstorm. These claim the day
 * unconditionally in [WeatherReportMapper.dailyCode] — the materiality gate below is
 * allowed to drop a label, never a warning.
 */
private val HazardCodes = setOf(56, 57, 65, 66, 67, 75, 82, 86, 95, 96, 99)

/**
 * How much has to fall before precipitation may label the whole day (Fase 26), and
 * the escape hatch for the day where little falls but it falls all afternoon.
 *
 * `dailyCode` used to let ANY hour with a code ≥ 51 claim the day. Measured 6 Sep
 * 2026 on 23 cities across five continents, 161 city-days: 45% of days came back
 * "wet", and 47% of those were wet only from drizzle codes — ten of them with under a
 * millimetre in twenty-four hours, five with a peak probability below 30%. The worst
 * was Singapore, one hour, 0.1 mm, **1% probability**, and the whole day printed
 * `Drizzle 🌦️` in the week table and in the morning summary; Milan the same with
 * `Rain Showers` for **0.0 mm**. That is the fog defect again in another column: one
 * unrepresentative hour labelling a day.
 *
 * One millimetre is the Met Office's own "wet day", and three hours is what keeps a
 * long soft drizzle — which really does look like a drizzly day — from being dropped
 * by an accumulation rule. On the same 161 days the pair moves 5 days out of 73, and
 * all five are the ones above: one or two hours, 0.0–0.4 mm. The weakest day it keeps
 * is six hours and 0.6 mm, which is a day you take a jacket for.
 */
private const val WET_DAY_MM = 1.0
private const val WET_DAY_HOURS = 3

object WeatherReportMapper {

    const val SOURCE = "Open-Meteo API"

    fun map(
        city: City,
        forecast: ForecastResponseDto,
        airQuality: AirQualityCurrentDto?,
        fetchedAt: Instant,
        responseTimeMs: Long,
        cacheStatus: CacheStatus
    ): WeatherReport {
        val current = forecast.current
        val localTime = LocalDateTime.parse(current.time)
        val isDay = current.isDay == 1

        val hourlyTimes = forecast.hourly.time.map(LocalDateTime::parse)
        val hourlyCodes = forecast.hourly.repairedCodes()
        val currentHour = localTime.truncatedTo(ChronoUnit.HOURS)
        val currentHourIndex = hourlyTimes.indexOfFirst { !it.isBefore(currentHour) }
            .coerceAtLeast(0)

        return WeatherReport(
            location = Location(
                city = city.name,
                region = city.region,
                country = city.country,
                coordinates = Coordinates(forecast.latitude, forecast.longitude),
                timezone = forecast.timezone,
                localTime = localTime
            ),
            current = CurrentConditions(
                condition = WeatherCodes.condition(
                    repairFog(
                        code = current.weatherCode,
                        visibilityM = current.visibilityM,
                        cloudCoverPct = current.cloudCoverPct,
                        // An observation has no run to belong to — see repairFog.
                        fogPersists = true
                    ),
                    isDay
                ),
                tempC = current.temperatureC,
                feelsLikeC = current.apparentTemperatureC,
                humidityPct = current.humidityPct,
                dewPointC = current.dewPointC,
                visibilityKm = current.visibilityM?.div(1000.0),
                pressureMb = current.pressureMslHpa,
                uvIndex = current.uvIndex.roundToInt(),
                uvDescription = WeatherCodes.uvDescription(current.uvIndex.roundToInt()),
                wind = Wind(
                    speedKph = current.windSpeedKph,
                    directionCompass = WeatherCodes.windCompass(current.windDirectionDeg),
                    degree = current.windDirectionDeg,
                    gustKph = current.windGustsKph
                ),
                precipitation = Precipitation(
                    lastHourMm = current.precipitationMm,
                    chancePct = forecast.hourly.precipitationProbabilityPct
                        .getOrNull(currentHourIndex)
                )
            ),
            airQuality = airQuality?.toAirQuality(),
            pollen = airQuality?.toPollenReport(),
            astronomical = mapAstronomical(city, forecast, localTime, fetchedAt),
            hourly = mapHourly(forecast, hourlyTimes, hourlyCodes, currentHourIndex),
            daily = mapDaily(forecast, hourlyTimes, hourlyCodes),
            systemInfo = SystemInfo(
                source = SOURCE,
                lastSync = fetchedAt,
                cacheStatus = cacheStatus,
                responseTimeMs = responseTimeMs
            )
        )
    }

    private fun mapHourly(
        forecast: ForecastResponseDto,
        times: List<LocalDateTime>,
        codes: List<Int>,
        fromIndex: Int
    ): List<HourlyForecast> {
        val hourly = forecast.hourly
        return (fromIndex until (fromIndex + HOURLY_WINDOW).coerceAtMost(times.size))
            .map { i ->
                HourlyForecast(
                    time = times[i],
                    tempC = hourly.temperatureC[i],
                    condition = WeatherCodes.condition(
                        codes[i],
                        isDay = hourly.isDay[i] == 1
                    ),
                    precipChancePct = hourly.precipitationProbabilityPct.getOrNull(i),
                    // Read like its siblings: the parallel arrays are the same length
                    // in any response that deserialized, and `repairedCodes()` has
                    // already indexed this very column over all of them.
                    cloudCoverPct = hourly.cloudCoverPct[i]
                )
            }
    }

    private fun mapDaily(
        forecast: ForecastResponseDto,
        hourlyTimes: List<LocalDateTime>,
        hourlyCodes: List<Int>
    ): List<DailyForecast> {
        val daily = forecast.daily
        val hoursByDate = hourlyTimes.indices.groupBy { hourlyTimes[it].toLocalDate() }
        return daily.time.take(DAILY_WINDOW).mapIndexed { i, date ->
            val day = LocalDate.parse(date)
            val uvMax = daily.uvIndexMax.getOrNull(i)?.roundToInt() ?: 0
            DailyForecast(
                date = day,
                highC = daily.temperatureMaxC[i],
                lowC = daily.temperatureMinC[i],
                condition = WeatherCodes.condition(
                    dailyCode(
                        hourlyCodes,
                        forecast.hourly.isDay,
                        forecast.hourly.precipitationMm,
                        hoursByDate[day].orEmpty(),
                        daily.weatherCode[i]
                    ),
                    isDay = true
                ),
                precipPct = daily.precipitationProbabilityMaxPct.getOrNull(i) ?: 0,
                uvIndexMax = uvMax,
                uvDescription = WeatherCodes.uvDescription(uvMax)
            )
        }
    }

    /**
     * The day's weather code, derived from the day's own hourly codes instead of read off
     * `daily.weather_code` — which is `max()` over all 24 of them ("the most severe weather
     * condition on a given day"), the one summary guaranteed to pick the least
     * representative hour there is. A single 3am fog code relabelled a whole clear August
     * day `Foggy 🌫️` in Prossimi giorni, in `daily_forecast` and in the morning summary
     * notification; the same `max()` prints a thunderstorm over a week for one nocturnal
     * CAPE spike. Measured 22 Aug 2026 on 8 Po Valley cities — see Fase 13b in PLANNING.md
     * for the numbers behind every choice below.
     *
     * Rain first, over the WHOLE day — but only rain that is REALLY there (Fase 26).
     * Any precipitation code used to outrank every sky code, which handed the day to a
     * single hour of 0.1 mm at 1% probability; now the light codes have to clear
     * [WET_DAY_MM] over the day or run for [WET_DAY_HOURS] of it, and [HazardCodes]
     * clear nothing because a warning is never dropped. Scoping the rain half to the
     * daylight too was the first cut, back in 13b, and it dropped the precipitation
     * from 17 days out of 56, 8 of them turning a night thunderstorm into `Overcast`:
     * this rule may remove a distortion, never a warning, and that is exactly why the
     * hazard clause comes first.
     * `max()` within the precipitation family keeps Open-Meteo's own ordering, where 80
     * (slight showers) outranks 65 (heavy rain): imprecise about intensity, never wrong
     * about whether it rains. Measured 6 Sep 2026 over 3 024 hours, the codes that make
     * that inversion possible (82, 66, 67, 99) were not emitted once, so it stays.
     *
     * The sky, with no rain to report, is the daylight's: the row answers "how will the day
     * look", so a single closed hour at 4am neither darkens nor fogs a sunny day. Apple
     * (`daytimeForecast`/`overnightForecast`) and Google (`daytimeForecast` 07-19) split the
     * day for the same reason. Most frequent daylight code wins, ties to the heavier one —
     * which needs no case for fog: on a really foggy day fog is the most frequent code.
     *
     * Dates the hourly run does not reach keep the provider's code: it ends with
     * `forecast_days` and the daily one can outrun it. Aggregate better, never blank a row.
     */
    private fun dailyCode(
        codes: List<Int>,
        isDay: List<Int>,
        precipMm: List<Double>,
        hours: List<Int>,
        fallback: Int
    ): Int {
        if (hours.isEmpty()) return fallback
        val wet = hours.filter { codes[it] >= FIRST_PRECIP_CODE }
        // An empty `precipMm` is a cache entry written before the field was requested:
        // the amount clause simply cannot speak, and the hour count answers alone.
        val claimsTheDay = wet.any { codes[it] in HazardCodes } ||
            wet.size >= WET_DAY_HOURS ||
            wet.sumOf { precipMm.getOrElse(it) { 0.0 } } >= WET_DAY_MM
        if (claimsTheDay) wet.map { codes[it] }.maxOrNull()?.let { return it }
        val daylight = hours.filter { isDay[it] == 1 }.ifEmpty { hours }
        return daylight.map { codes[it] }
            .groupingBy { it }.eachCount()
            .maxWithOrNull(compareBy({ it.value }, { it.key }))?.key ?: fallback
    }

    /**
     * The hourly codes with [repairFog] applied, plus the one thing a series can say
     * that a single hour cannot: **fog is not one hour long** (Fase 26).
     *
     * Writing fog into an hour whose neighbours are both clear-aired nearly doubled
     * the fog↔non-fog transitions of the week — measured 6 Sep 2026 over 23 cities and
     * 3 864 hours: the provider's own series turns 10 times, the repaired one 18.
     * Every one of those extra turns is a row that changes character for an hour and
     * a line in `history.diff` that says nothing happened twice. Requiring one
     * neighbour below the threshold takes it to 14 and costs two rewrites out of 42.
     *
     * The requirement is deliberately **one-sided**. Removing a fog code the
     * provider's own visibility contradicts stays a per-hour decision: there is no
     * "run" argument for keeping a value the data disagrees with, and 68% of the fog
     * codes served are contradicted (median 4 km, worst 16.3 km). It is only the
     * INVENTING direction that has to be patient.
     */
    private fun HourlyDto.repairedCodes(): List<Int> {
        val lowVisibility = weatherCode.indices.map {
            visibilityM.getOrNull(it)?.let { v -> v <= FOG_VISIBILITY_M } == true
        }
        return weatherCode.indices.map { i ->
            repairFog(
                code = weatherCode[i],
                visibilityM = visibilityM.getOrNull(i),
                cloudCoverPct = cloudCoverPct[i],
                fogPersists = lowVisibility[i] &&
                    (lowVisibility.getOrNull(i - 1) == true || lowVisibility.getOrNull(i + 1) == true)
            )
        }
    }

    /**
     * `weather_code` with its fog checked against the same hour's visibility — the only
     * rewriting the app does to a provider code, and it applies Open-Meteo's own rule
     * rather than any meteorology of ours: `WeatherCode.swift:99` derives fog from
     * `visibility <= 1000` once precipitation is ruled out, and falls back on the cloud
     * cover otherwise. The served code is categorical and interpolated differently from the
     * continuous fields, so the two drift apart and the code loses: measured 22 Aug 2026 on
     * 8 Po Valley cities, `45` arrives with 10 km of visibility (01:00 and 03:00 at
     * Cavenago) while 160 m of dense fog is served as `3`, overcast. Both directions are
     * wrong and the second is the dangerous one.
     *
     * Deliberately surgical: only the fog verdict is revisited. Re-deriving the sky from
     * `cloud_cover` too — the obvious next step — moved 308 of those 1344 hours, 23%, which
     * is no longer repairing a defect but replacing the provider's classification wholesale.
     * The fog-only rule moves 15 hours, 1.1%, every one of them with its reason legible in
     * the visibility. Precipitation (>= 51) is never touched: it is not derived from
     * visibility, and thunderstorms need CAPE fields the app does not fetch.
     *
     * A null visibility (never seen in 20 cities across 5 continents, but the field is
     * model-dependent) leaves the code exactly as the provider sent it.
     *
     * [fogPersists] is the series' veto on inventing fog, and it is false for the
     * `current` block on purpose: that block is an observation of NOW, and if the
     * visibility is 300 metres right now then it is foggy right now — there is no run
     * to require, because there is no series. Persistence is a property of a forecast,
     * not of a measurement. Callers with a series pass [HourlyDto.repairedCodes]'
     * answer; the one caller without one passes `true` and gets the old rule.
     */
    private fun repairFog(
        code: Int,
        visibilityM: Double?,
        cloudCoverPct: Int,
        fogPersists: Boolean
    ): Int {
        if (code >= FIRST_PRECIP_CODE || visibilityM == null) return code
        val foggy = visibilityM <= FOG_VISIBILITY_M
        return when {
            code in FogCodes && !foggy -> skyCode(cloudCoverPct)
            code !in FogCodes && foggy && fogPersists -> WMO_FOG
            else -> code
        }
    }

    /** Open-Meteo's cloud cover buckets, `WeatherCode.swift:103`. */
    private fun skyCode(cloudCoverPct: Int): Int = when {
        cloudCoverPct < 20 -> 0
        cloudCoverPct < 50 -> 1
        cloudCoverPct < 80 -> 2
        else -> 3
    }

    /**
     * Sun and moon from [AstronomyEngine], not from the provider's daily block
     * (Fase 16e).
     *
     * The provider's values are still fetched — `daily.sunrise` feeds nothing now,
     * but it costs nothing and the contract test compares the two. The engine wins
     * for the reason `VISION_SKY.md` §9.2 gives: the same figure appears in the JSON
     * tab, in the README and on a `sky.crontab` line that is computed anyway, and a
     * document showing 06:31 in one tab and 06:32 in another because one of them
     * waited for the network is exactly what "one engine is the source of truth" was
     * written against. It also means these times are right offline and past the
     * seven-day horizon, which the provider's cannot be.
     *
     * Nulls are real answers here: above the Arctic circle in June there is no
     * sunrise, and the old code could only put some other time in its place.
     */
    private fun mapAstronomical(
        city: City,
        forecast: ForecastResponseDto,
        localTime: LocalDateTime,
        fetchedAt: Instant
    ): Astronomical {
        val zone = runCatching { ZoneId.of(forecast.timezone) }.getOrDefault(ZoneId.systemDefault())
        val day = AstronomyEngine.solarDay(localTime.toLocalDate(), zone, city.coordinates)
        // Truncated to the minute, and not for tidiness. Every surface renders these
        // as `HH:mm`, but `WeatherSnapshots.flatten` writes `sunrise.toString()` into
        // the history — so a value carrying seconds would put a fresh
        // `astronomical.sunrise` line in `history.diff` on EVERY fetch, since
        // the engine's answer moves by a fraction of a second between two of them.
        // The provider's values were minute-precise and nothing noticed until they
        // stopped being the source.
        fun clock(at: Instant?) =
            at?.atZone(zone)?.toLocalTime()?.truncatedTo(ChronoUnit.MINUTES)
        return Astronomical(
            sunrise = clock(day.sunrise),
            sunset = clock(day.sunset),
            moonPhase = MoonPhase.at(fetchedAt),
            // Derived from this engine's own two ends rather than read off
            // `daily.daylight_duration`: three numbers that must agree are better as
            // two numbers and a subtraction.
            daylightDuration = day.daylight
        )
    }

    private fun AirQualityCurrentDto.toAirQuality(): AirQuality? {
        val aqi = usAqi ?: return null
        return AirQuality(
            aqiIndex = aqi,
            status = WeatherCodes.usAqiStatus(aqi),
            pollutants = Pollutants(
                pm25 = pm25 ?: 0.0,
                pm10 = pm10 ?: 0.0,
                o3 = ozone ?: 0.0,
                no2 = no2 ?: 0.0,
                so2 = so2 ?: 0.0,
                coMg = (co ?: 0.0) / 1000.0 // API returns µg/m³, sample shows mg/m³
            )
        )
    }

    private fun AirQualityCurrentDto.toPollenReport(): PollenReport? {
        val tree = listOfNotNull(birchPollen, alderPollen, olivePollen).maxOrNull()
        val weed = listOfNotNull(ragweedPollen, mugwortPollen).maxOrNull()
        return PollenReport(
            grass = WeatherCodes.pollenLevel(grassPollen) ?: return null,
            tree = WeatherCodes.pollenLevel(tree) ?: return null,
            weed = WeatherCodes.pollenLevel(weed) ?: return null
        )
    }
}
