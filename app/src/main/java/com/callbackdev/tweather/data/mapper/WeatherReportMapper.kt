package com.callbackdev.tweather.data.mapper

import com.callbackdev.tweather.data.remote.dto.AirQualityCurrentDto
import com.callbackdev.tweather.data.remote.dto.ForecastResponseDto
import com.callbackdev.tweather.data.remote.dto.bestMatchInts
import com.callbackdev.tweather.data.remote.dto.mergedDoubles
import com.callbackdev.tweather.data.remote.dto.mergedInts
import com.callbackdev.tweather.data.remote.dto.mergedNullableInts
import com.callbackdev.tweather.data.remote.dto.mergedStrings
import com.callbackdev.tweather.data.remote.dto.timeSeries
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
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private const val HOURLY_WINDOW = 24
private const val DAILY_WINDOW = 7

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
        // The API's own `current` block can't be split per model (see ForecastDto.kt),
        // so "now" is the hourly slot nearest to fetchedAt in the location's timezone.
        val localTime = LocalDateTime.ofInstant(fetchedAt, ZoneId.of(forecast.timezone))
        val currentHour = localTime.truncatedTo(ChronoUnit.HOURS)

        val hourly = forecast.hourly
        val hourlyTimes = hourly.timeSeries().map(LocalDateTime::parse)
        val size = hourlyTimes.size
        val currentHourIndex = hourlyTimes.indexOfFirst { !it.isBefore(currentHour) }
            .coerceAtLeast(0)

        val temperatureC = hourly.mergedDoubles("temperature_2m", size)
        val humidityPct = hourly.mergedInts("relative_humidity_2m", size)
        val apparentTemperatureC = hourly.mergedDoubles("apparent_temperature", size)
        val dewPointC = hourly.mergedDoubles("dew_point_2m", size)
        val isDay = hourly.mergedInts("is_day", size)
        val precipitationMm = hourly.mergedDoubles("precipitation", size)
        val weatherCode = hourly.bestMatchInts("weather_code", size)
        val pressureMslHpa = hourly.mergedDoubles("pressure_msl", size)
        val windSpeedKph = hourly.mergedDoubles("wind_speed_10m", size)
        val windDirectionDeg = hourly.mergedInts("wind_direction_10m", size)
        val windGustsKph = hourly.mergedDoubles("wind_gusts_10m", size)
        val visibilityM = hourly.mergedDoubles("visibility", size)
        val uvIndex = hourly.mergedDoubles("uv_index", size)
        val precipitationProbabilityPct = hourly.mergedNullableInts("precipitation_probability", size)

        val i = currentHourIndex
        val uvIndexNow = uvIndex.getOrElse(i) { 0.0 }.roundToInt()

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
                    weatherCode.getOrElse(i) { 0 },
                    isDay = isDay.getOrElse(i) { 1 } == 1
                ),
                tempC = temperatureC.getOrElse(i) { 0.0 },
                feelsLikeC = apparentTemperatureC.getOrElse(i) { 0.0 },
                humidityPct = humidityPct.getOrElse(i) { 0 },
                dewPointC = dewPointC.getOrElse(i) { 0.0 },
                visibilityKm = visibilityM.getOrElse(i) { 0.0 } / 1000.0,
                pressureMb = pressureMslHpa.getOrElse(i) { 0.0 },
                uvIndex = uvIndexNow,
                uvDescription = WeatherCodes.uvDescription(uvIndexNow),
                wind = Wind(
                    speedKph = windSpeedKph.getOrElse(i) { 0.0 },
                    directionCompass = WeatherCodes.windCompass(windDirectionDeg.getOrElse(i) { 0 }),
                    degree = windDirectionDeg.getOrElse(i) { 0 },
                    gustKph = windGustsKph.getOrElse(i) { 0.0 }
                ),
                precipitation = Precipitation(
                    lastHourMm = precipitationMm.getOrElse(i) { 0.0 },
                    chancePct = precipitationProbabilityPct.getOrNull(i) ?: 0
                )
            ),
            airQuality = airQuality?.toAirQuality(),
            pollen = airQuality?.toPollenReport(),
            astronomical = mapAstronomical(forecast, fetchedAt),
            hourly = mapHourly(
                times = hourlyTimes,
                fromIndex = currentHourIndex,
                temperatureC = temperatureC,
                weatherCode = weatherCode,
                isDay = isDay,
                precipitationProbabilityPct = precipitationProbabilityPct
            ),
            daily = mapDaily(forecast),
            systemInfo = SystemInfo(
                source = SOURCE,
                lastSync = fetchedAt,
                cacheStatus = cacheStatus,
                responseTimeMs = responseTimeMs
            )
        )
    }

    private fun mapHourly(
        times: List<LocalDateTime>,
        fromIndex: Int,
        temperatureC: List<Double>,
        weatherCode: List<Int>,
        isDay: List<Int>,
        precipitationProbabilityPct: List<Int?>
    ): List<HourlyForecast> =
        (fromIndex until (fromIndex + HOURLY_WINDOW).coerceAtMost(times.size)).map { idx ->
            HourlyForecast(
                time = times[idx],
                tempC = temperatureC[idx],
                condition = WeatherCodes.condition(weatherCode[idx], isDay = isDay[idx] == 1),
                precipChancePct = precipitationProbabilityPct.getOrNull(idx) ?: 0
            )
        }

    private fun mapDaily(forecast: ForecastResponseDto): List<DailyForecast> {
        val daily = forecast.daily
        val times = daily.timeSeries()
        val size = times.size
        val weatherCode = daily.bestMatchInts("weather_code", size)
        val temperatureMaxC = daily.mergedDoubles("temperature_2m_max", size)
        val temperatureMinC = daily.mergedDoubles("temperature_2m_min", size)
        val precipitationProbabilityMaxPct = daily.mergedNullableInts("precipitation_probability_max", size)
        return times.take(DAILY_WINDOW).mapIndexed { idx, date ->
            DailyForecast(
                date = LocalDate.parse(date),
                highC = temperatureMaxC[idx],
                lowC = temperatureMinC[idx],
                condition = WeatherCodes.condition(weatherCode[idx], isDay = true),
                precipPct = precipitationProbabilityMaxPct.getOrNull(idx) ?: 0
            )
        }
    }

    private fun mapAstronomical(forecast: ForecastResponseDto, fetchedAt: Instant): Astronomical {
        val daily = forecast.daily
        val size = daily.timeSeries().size
        val sunrise = daily.mergedStrings("sunrise", size)
        val sunset = daily.mergedStrings("sunset", size)
        val daylightDurationSec = daily.mergedDoubles("daylight_duration", size)
        return Astronomical(
            sunrise = LocalDateTime.parse(sunrise.first()).toLocalTime(),
            sunset = LocalDateTime.parse(sunset.first()).toLocalTime(),
            moonPhase = MoonPhase.at(fetchedAt),
            daylightDuration = Duration.ofSeconds(daylightDurationSec.first().toLong())
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
