package com.callbackdev.tweather.data.mapper

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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Hourly slots carried in the domain: the CURRENT hour first — slot 0 feeds
 * `current_conditions`' rain chance and anchors AlertEngine/rules — plus the full
 * day both tabs read from the hour after it (Fase 11f: the views drop slot 0, it
 * only repeats the current section; the JSON still shows 24 rows, `+1h..+24h`).
 */
private const val HOURLY_WINDOW = 25
private const val DAILY_WINDOW = 7

/**
 * Below 51 the WMO scale carries only sky states and fog; from 51 up every code is a
 * precipitation of some kind (drizzle, rain, snow, showers, thunderstorm).
 */
private const val FIRST_PRECIP_CODE = 51

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
                condition = WeatherCodes.condition(current.weatherCode, isDay),
                tempC = current.temperatureC,
                feelsLikeC = current.apparentTemperatureC,
                humidityPct = current.humidityPct,
                dewPointC = current.dewPointC,
                visibilityKm = current.visibilityM / 1000.0,
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
                        .getOrNull(currentHourIndex) ?: 0
                )
            ),
            airQuality = airQuality?.toAirQuality(),
            pollen = airQuality?.toPollenReport(),
            astronomical = mapAstronomical(forecast, fetchedAt),
            hourly = mapHourly(forecast, hourlyTimes, currentHourIndex),
            daily = mapDaily(forecast, hourlyTimes),
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
        fromIndex: Int
    ): List<HourlyForecast> {
        val hourly = forecast.hourly
        return (fromIndex until (fromIndex + HOURLY_WINDOW).coerceAtMost(times.size))
            .map { i ->
                HourlyForecast(
                    time = times[i],
                    tempC = hourly.temperatureC[i],
                    condition = WeatherCodes.condition(
                        hourly.weatherCode[i],
                        isDay = hourly.isDay[i] == 1
                    ),
                    precipChancePct = hourly.precipitationProbabilityPct.getOrNull(i) ?: 0
                )
            }
    }

    private fun mapDaily(
        forecast: ForecastResponseDto,
        hourlyTimes: List<LocalDateTime>
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
                    dailyCode(forecast.hourly, hoursByDate[day].orEmpty(), daily.weatherCode[i]),
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
     * Rain first, over the WHOLE day: any precipitation code (≥ 51) outranks every sky code,
     * as it does for the provider. Scoping this half to the daylight too was the first cut
     * and it dropped the precipitation from 17 days out of 56, 8 of them turning a night
     * thunderstorm into `Overcast` — this rule may remove a distortion, never a warning.
     * `max()` within the precipitation family keeps Open-Meteo's own ordering, where 80
     * (slight showers) outranks 65 (heavy rain): imprecise about intensity, never wrong
     * about whether it rains.
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
    private fun dailyCode(hourly: HourlyDto, hours: List<Int>, fallback: Int): Int {
        if (hours.isEmpty()) return fallback
        hours.map { hourly.weatherCode[it] }
            .filter { it >= FIRST_PRECIP_CODE }
            .maxOrNull()
            ?.let { return it }
        val daylight = hours.filter { hourly.isDay[it] == 1 }.ifEmpty { hours }
        return daylight.map { hourly.weatherCode[it] }
            .groupingBy { it }.eachCount()
            .maxWithOrNull(compareBy({ it.value }, { it.key }))?.key ?: fallback
    }

    private fun mapAstronomical(forecast: ForecastResponseDto, fetchedAt: Instant): Astronomical {
        val daily = forecast.daily
        return Astronomical(
            sunrise = LocalDateTime.parse(daily.sunrise.first()).toLocalTime(),
            sunset = LocalDateTime.parse(daily.sunset.first()).toLocalTime(),
            moonPhase = MoonPhase.at(fetchedAt),
            daylightDuration = Duration.ofSeconds(daily.daylightDurationSec.first().toLong())
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
