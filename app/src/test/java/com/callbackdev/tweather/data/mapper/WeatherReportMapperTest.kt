package com.callbackdev.tweather.data.mapper

import com.callbackdev.tweather.data.remote.dto.AirQualityCurrentDto
import com.callbackdev.tweather.data.remote.dto.ForecastResponseDto
import com.callbackdev.tweather.domain.model.CacheStatus
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.PollenLevel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherReportMapperTest {

    private val city = City(
        id = 5128581,
        name = "New York",
        region = "New York",
        country = "United States",
        coordinates = Coordinates(40.7128, -74.0060),
        timezone = "America/New_York"
    )

    // 18:23 UTC is 14:23 in America/New_York (EDT, UTC-4) in August.
    private val fetchedAt = Instant.parse("2026-08-13T18:23:00Z")

    /** 48 hourly slots starting at local midnight; fetchedAt is 14:23 → window starts at 14:00. */
    private fun forecast(hourlyCount: Int = 48, dailyCount: Int = 8): ForecastResponseDto {
        val midnight = LocalDateTime.parse("2026-08-13T00:00")
        val hourlyTimes = List(hourlyCount) { midnight.plusHours(it.toLong()).toString() }
        val dailyTimes = List(dailyCount) { LocalDate.parse("2026-08-13").plusDays(it.toLong()).toString() }

        return ForecastResponseDto(
            latitude = 40.71,
            longitude = -74.01,
            timezone = "America/New_York",
            hourly = buildJsonObject {
                putStrings("time", hourlyTimes)
                putDoubles("temperature_2m_best_match", List(hourlyCount) { 10.0 + it })
                putInts("relative_humidity_2m_best_match", List(hourlyCount) { 65 })
                putDoubles("apparent_temperature_best_match", List(hourlyCount) { 12.0 + it })
                putDoubles("dew_point_2m_best_match", List(hourlyCount) { 5.0 + it })
                putInts("is_day_best_match", List(hourlyCount) { if (it % 24 in 6..19) 1 else 0 })
                putDoubles("precipitation_best_match", List(hourlyCount) { if (it == 14) 0.5 else 0.0 })
                putInts("weather_code_best_match", List(hourlyCount) { if (it == 14) 61 else 0 })
                putDoubles("pressure_msl_best_match", List(hourlyCount) { 1013.2 })
                putDoubles("wind_speed_10m_best_match", List(hourlyCount) { 12.5 })
                putInts("wind_direction_10m_best_match", List(hourlyCount) { 310 })
                putDoubles("wind_gusts_10m_best_match", List(hourlyCount) { 18.3 })
                putDoubles("visibility_best_match", List(hourlyCount) { 16090.0 })
                putDoubles("uv_index_best_match", List(hourlyCount) { 5.4 })
                putNullableInts(
                    "precipitation_probability_best_match",
                    List(hourlyCount) { if (it == 14) 40 else null }
                )
            },
            daily = buildJsonObject {
                putStrings("time", dailyTimes)
                putInts("weather_code_best_match", List(dailyCount) { 3 })
                putDoubles("temperature_2m_max_best_match", List(dailyCount) { 28.0 + it })
                putDoubles("temperature_2m_min_best_match", List(dailyCount) { 18.0 + it })
                putStrings("sunrise_best_match", List(dailyCount) { "2026-08-13T06:07" })
                putStrings("sunset_best_match", List(dailyCount) { "2026-08-13T19:52" })
                putDoubles("daylight_duration_best_match", List(dailyCount) { 49_500.0 })
                putNullableInts(
                    "precipitation_probability_max_best_match",
                    List(dailyCount) { if (it == 0) 55 else null }
                )
                putDoubles("uv_index_max_best_match", List(dailyCount) { 6.0 })
            }
        )
    }

    private fun map(
        forecast: ForecastResponseDto = forecast(),
        airQuality: AirQualityCurrentDto? = null
    ) = WeatherReportMapper.map(
        city = city,
        forecast = forecast,
        airQuality = airQuality,
        fetchedAt = fetchedAt,
        responseTimeMs = 245,
        cacheStatus = CacheStatus.MISS
    )

    @Test
    fun `location comes from city and forecast coordinates`() {
        val location = map().location
        assertEquals("New York", location.city)
        assertEquals("New York", location.region)
        assertEquals("United States", location.country)
        // Coordinates are the ones echoed by the forecast API, not the city's.
        assertEquals(40.71, location.coordinates.lat, 0.0)
        assertEquals(-74.01, location.coordinates.lon, 0.0)
        assertEquals("America/New_York", location.timezone)
        assertEquals(LocalDateTime.parse("2026-08-13T14:23"), location.localTime)
    }

    @Test
    fun `current conditions read from the hourly slot nearest to fetchedAt`() {
        val current = map().current
        // fetchedAt 14:23 local → hour 14 → temperature_2m = 10.0 + 14.
        assertEquals("Light Rain 🌧️", current.condition.label)
        assertEquals(24.0, current.tempC, 0.0)
        assertEquals(26.0, current.feelsLikeC, 0.0)
        assertEquals(65, current.humidityPct)
        assertEquals(16.09, current.visibilityKm, 1e-9)      // meters → km
        assertEquals(5, current.uvIndex)                     // 5.4 rounds down
        assertEquals("Moderate ☀️", current.uvDescription)
        assertEquals("NW", current.wind.directionCompass)    // 310°
        assertEquals(310, current.wind.degree)
        // Precip chance read from the hourly slot matching the current hour (14:00 → index 14).
        assertEquals(40, current.precipitation.chancePct)
        assertEquals(0.5, current.precipitation.lastHourMm, 0.0)
    }

    @Test
    fun `hourly window starts at current hour and spans 24 entries`() {
        val hourly = map().hourly
        assertEquals(24, hourly.size)
        assertEquals(LocalDateTime.parse("2026-08-13T14:00"), hourly.first().time)
        assertEquals(LocalDateTime.parse("2026-08-14T13:00"), hourly.last().time)
        assertEquals(24.0, hourly.first().tempC, 0.0)        // 10.0 + index 14
        assertEquals("Light Rain 🌧️", hourly.first().condition.label)
        assertEquals(40, hourly.first().precipChancePct)
        assertEquals(0, hourly[1].precipChancePct)           // null probability → 0
        // Night slot (2026-08-14T03:00, index 27) uses the night emoji for clear sky.
        val night = hourly.first { it.time == LocalDateTime.parse("2026-08-14T03:00") }
        assertEquals("🌙", night.condition.emoji)
    }

    @Test
    fun `hourly window is clipped when fewer than 24 slots remain`() {
        // 20 slots total, current hour at index 14 → only 6 remain.
        val report = map(forecast = forecast(hourlyCount = 20))
        assertEquals(6, report.hourly.size)
    }

    @Test
    fun `daily takes at most 7 days`() {
        val daily = map().daily
        assertEquals(7, daily.size)
        assertEquals(LocalDate.parse("2026-08-13"), daily.first().date)
        assertEquals(28.0, daily.first().highC, 0.0)
        assertEquals(18.0, daily.first().lowC, 0.0)
        assertEquals("Overcast ☁️", daily.first().condition.label)
        assertEquals(55, daily.first().precipPct)
        assertEquals(0, daily[1].precipPct)                  // null max probability → 0
    }

    @Test
    fun `astronomical maps sunrise sunset and daylight duration`() {
        val astro = map().astronomical
        assertEquals(LocalTime.of(6, 7), astro.sunrise)
        assertEquals(LocalTime.of(19, 52), astro.sunset)
        assertEquals(Duration.ofSeconds(49_500), astro.daylightDuration)
    }

    @Test
    fun `system info carries source sync time and cache status`() {
        val info = map().systemInfo
        assertEquals("Open-Meteo API", info.source)
        assertEquals(fetchedAt, info.lastSync)
        assertEquals(CacheStatus.MISS, info.cacheStatus)
        assertEquals(245, info.responseTimeMs)
    }

    @Test
    fun `local high-res model temperature wins over best_match when present`() {
        // Same shape as the default fixture, but the current hour (index 14) also
        // carries an italia_meteo_arpae_icon_2i reading that disagrees with best_match.
        val base = forecast()
        val hourlyWithLocalModel = JsonObject(
            base.hourly.toMutableMap().apply {
                val bestMatch = (this["temperature_2m_best_match"] as JsonArray)
                val local = bestMatch.mapIndexed { idx, _ ->
                    if (idx == 14) JsonPrimitive(99.9) else JsonNull
                }
                put("temperature_2m_italia_meteo_arpae_icon_2i", JsonArray(local))
            }
        )
        val report = map(forecast = base.copy(hourly = hourlyWithLocalModel))
        assertEquals(99.9, report.current.tempC, 0.0)         // local model wins at index 14
        assertEquals(25.0, report.hourly[1].tempC, 0.0)       // null local value → best_match (10+15)
    }

    @Test
    fun `weather_code always reads from best_match even when a local model disagrees`() {
        // Reproduces the live Milan case: italia_meteo_arpae_icon_2i said "overcast"
        // (3) while best_match said "light rain" (61, the fixture's value at index 14)
        // and every other source agreed with best_match. weather_code must never take
        // the local model's read, unlike continuous fields such as temperature.
        val base = forecast()
        val hourlyWithLocalModel = JsonObject(
            base.hourly.toMutableMap().apply {
                val bestMatch = (this["weather_code_best_match"] as JsonArray)
                val local = bestMatch.mapIndexed { idx, _ ->
                    if (idx == 14) JsonPrimitive(3) else JsonNull
                }
                put("weather_code_italia_meteo_arpae_icon_2i", JsonArray(local))
            }
        )
        val report = map(forecast = base.copy(hourly = hourlyWithLocalModel))
        assertEquals("Light Rain 🌧️", report.current.condition.label)
        assertEquals("Light Rain 🌧️", report.hourly.first().condition.label)
    }

    @Test
    fun `missing air quality response leaves both sections null`() {
        val report = map(airQuality = null)
        assertNull(report.airQuality)
        assertNull(report.pollen)
    }

    @Test
    fun `air quality without us aqi maps to null`() {
        val report = map(airQuality = AirQualityCurrentDto(time = "2026-08-13T14:00"))
        assertNull(report.airQuality)
    }

    @Test
    fun `air quality maps pollutants and converts co to mg`() {
        val report = map(
            airQuality = AirQualityCurrentDto(
                time = "2026-08-13T14:00",
                usAqi = 42,
                pm25 = 12.5, pm10 = 20.1, ozone = 45.2, no2 = 15.3, so2 = 2.1,
                co = 300.0 // µg/m³ from the API
            )
        )
        val aq = report.airQuality!!
        assertEquals(42, aq.aqiIndex)
        assertEquals("Good ⚪", aq.status)
        assertEquals(12.5, aq.pollutants.pm25, 0.0)
        assertEquals(0.3, aq.pollutants.coMg, 1e-9)          // µg → mg
        assertEquals(0.0, aq.pollutants.no2 - 15.3, 1e-9)
    }

    @Test
    fun `missing pollutants default to zero`() {
        val report = map(
            airQuality = AirQualityCurrentDto(time = "2026-08-13T14:00", usAqi = 66)
        )
        val pollutants = report.airQuality!!.pollutants
        assertEquals(0.0, pollutants.pm25, 0.0)
        assertEquals(0.0, pollutants.coMg, 0.0)
    }

    @Test
    fun `pollen tree level is the max of birch alder olive`() {
        val report = map(
            airQuality = AirQualityCurrentDto(
                time = "2026-08-13T14:00",
                usAqi = 42,
                grassPollen = 0.4,     // NONE
                birchPollen = 5.0,     // LOW…
                alderPollen = 120.0,   // …but alder is HIGH → tree = HIGH
                olivePollen = 0.0,
                ragweedPollen = 35.0,  // MODERATE
                mugwortPollen = 2.0
            )
        )
        val pollen = report.pollen!!
        assertEquals(PollenLevel.NONE, pollen.grass)
        assertEquals(PollenLevel.HIGH, pollen.tree)
        assertEquals(PollenLevel.MODERATE, pollen.weed)
    }

    @Test
    fun `pollen report is null outside pollen coverage`() {
        // US location: air quality present, every pollen field absent.
        val report = map(
            airQuality = AirQualityCurrentDto(time = "2026-08-13T14:00", usAqi = 42)
        )
        assertNull(report.pollen)
    }
}

private fun JsonObject.toMutableMap() = LinkedHashMap(this)

private fun kotlinx.serialization.json.JsonObjectBuilder.putStrings(key: String, values: List<String>) =
    put(key, JsonArray(values.map { JsonPrimitive(it) }))

private fun kotlinx.serialization.json.JsonObjectBuilder.putDoubles(key: String, values: List<Double>) =
    put(key, JsonArray(values.map { JsonPrimitive(it) }))

private fun kotlinx.serialization.json.JsonObjectBuilder.putInts(key: String, values: List<Int>) =
    put(key, JsonArray(values.map { JsonPrimitive(it) }))

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableInts(key: String, values: List<Int?>) =
    put(key, JsonArray(values.map { it?.let { v -> JsonPrimitive(v) } ?: JsonNull }))
