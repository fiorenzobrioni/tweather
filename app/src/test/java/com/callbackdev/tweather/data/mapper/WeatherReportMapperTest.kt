package com.callbackdev.tweather.data.mapper

import com.callbackdev.tweather.data.remote.dto.AirQualityCurrentDto
import com.callbackdev.tweather.data.remote.dto.CurrentDto
import com.callbackdev.tweather.data.remote.dto.DailyDto
import com.callbackdev.tweather.data.remote.dto.ForecastResponseDto
import com.callbackdev.tweather.data.remote.dto.HourlyDto
import com.callbackdev.tweather.domain.model.CacheStatus
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.domain.model.PollenLevel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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

    private val fetchedAt = Instant.parse("2026-08-13T18:23:00Z")

    /** 48 hourly slots starting at local midnight; current time 14:23 → window starts at 14:00. */
    private fun forecast(
        currentTime: String = "2026-08-13T14:23",
        hourlyCount: Int = 48,
        dailyCount: Int = 8
    ): ForecastResponseDto {
        val midnight = LocalDateTime.parse("2026-08-13T00:00")
        return ForecastResponseDto(
            latitude = 40.71,
            longitude = -74.01,
            timezone = "America/New_York",
            current = CurrentDto(
                time = currentTime,
                temperatureC = 22.4,
                humidityPct = 65,
                apparentTemperatureC = 24.1,
                dewPointC = 15.6,
                isDay = 1,
                precipitationMm = 0.5,
                weatherCode = 2,
                pressureMslHpa = 1013.2,
                windSpeedKph = 12.5,
                windDirectionDeg = 310,
                windGustsKph = 18.3,
                visibilityM = 16090.0,
                cloudCoverPct = 60,
                uvIndex = 5.4
            ),
            hourly = HourlyDto(
                time = List(hourlyCount) { midnight.plusHours(it.toLong()).toString() },
                temperatureC = List(hourlyCount) { 10.0 + it },
                weatherCode = List(hourlyCount) { if (it == 14) 61 else 0 },
                precipitationProbabilityPct = List(hourlyCount) { if (it == 14) 40 else null },
                isDay = List(hourlyCount) { if (it % 24 in 6..19) 1 else 0 },
                visibilityM = List(hourlyCount) { 20_000.0 },
                cloudCoverPct = List(hourlyCount) { 0 }
            ),
            daily = DailyDto(
                time = List(dailyCount) { LocalDate.parse("2026-08-13").plusDays(it.toLong()).toString() },
                weatherCode = List(dailyCount) { 3 },
                temperatureMaxC = List(dailyCount) { 28.0 + it },
                temperatureMinC = List(dailyCount) { 18.0 + it },
                sunrise = List(dailyCount) { "2026-08-13T06:07" },
                sunset = List(dailyCount) { "2026-08-13T19:52" },
                daylightDurationSec = List(dailyCount) { 49_500.0 },
                precipitationProbabilityMaxPct = List(dailyCount) { if (it == 0) 55 else null },
                uvIndexMax = List(dailyCount) { 6.0 }
            )
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
    fun `current conditions convert units and derive labels`() {
        val current = map().current
        assertEquals("Partly Cloudy ⛅", current.condition.label)
        assertEquals(22.4, current.tempC, 0.0)
        assertEquals(24.1, current.feelsLikeC, 0.0)
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
    fun `hourly window starts at current hour and spans 25 entries`() {
        // 25, not 24: slot 0 is the current hour (current_conditions' rain chance,
        // the engines' anchor), the views drop it and still show a full day.
        val hourly = map().hourly
        assertEquals(25, hourly.size)
        assertEquals(LocalDateTime.parse("2026-08-13T14:00"), hourly.first().time)
        assertEquals(LocalDateTime.parse("2026-08-14T14:00"), hourly.last().time)
        assertEquals(24.0, hourly.first().tempC, 0.0)        // 10.0 + index 14
        assertEquals("Light Rain 🌧️", hourly.first().condition.label)
        assertEquals(40, hourly.first().precipChancePct)
        assertEquals(0, hourly[1].precipChancePct)           // null probability → 0
        // Night slot (2026-08-14T03:00, index 27) uses the night emoji for clear sky.
        val night = hourly.first { it.time == LocalDateTime.parse("2026-08-14T03:00") }
        assertEquals("🌙", night.condition.emoji)
    }

    @Test
    fun `hourly window is clipped when the API returns fewer slots`() {
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
        // Derived from the day's hours, not from daily.weather_code (3): it rains at hour
        // 14, and rain outranks any sky code.
        assertEquals("Light Rain 🌧️", daily.first().condition.label)
        assertEquals(55, daily.first().precipPct)
        assertEquals(0, daily[1].precipPct)                  // null max probability → 0
        // uv_index_max was fetched and parsed all along but never mapped, so the
        // README's "Today" section fell back to the instant reading (Aug 2026 fix)
        assertEquals(6, daily.first().uvIndexMax)
        assertEquals("High ☀️", daily.first().uvDescription)
    }

    /**
     * One local day of hourly codes (index = hour of 2026-08-13), daylight 06:00-19:00
     * like the default fixture, and the code `daily.weather_code` would have carried.
     */
    private fun dayOf(codes: List<Int>, providerCode: Int): ForecastResponseDto {
        val base = forecast()
        val midnight = LocalDateTime.parse("2026-08-13T00:00")
        return base.copy(
            hourly = base.hourly.copy(
                time = List(24) { midnight.plusHours(it.toLong()).toString() },
                temperatureC = List(24) { 20.0 },
                weatherCode = codes,
                precipitationProbabilityPct = List(24) { null },
                isDay = List(24) { if (it in 6..19) 1 else 0 },
                // Kept coherent with the codes, or the fog repair would rewrite them: the
                // aggregation is what these tests are about.
                visibilityM = codes.map { if (it in setOf(45, 48)) 200.0 else 20_000.0 },
                cloudCoverPct = codes.map { if (it >= 45) 100 else it * 30 }
            ),
            daily = base.daily.copy(weatherCode = List(8) { providerCode })
        )
    }

    private fun statusOfFirstDay(codes: List<Int>, providerCode: Int) =
        map(forecast = dayOf(codes, providerCode)).daily.first().condition.label

    /** The default fixture with the CURRENT hour (index 14, `hourly.first()`) overridden. */
    private fun hourAt(code: Int, visibilityM: Double?, cloudPct: Int): ForecastResponseDto {
        val base = forecast()
        val n = base.hourly.time.size
        return base.copy(
            hourly = base.hourly.copy(
                weatherCode = List(n) { if (it == 14) code else 0 },
                visibilityM = List(n) { if (it == 14) visibilityM else 20_000.0 },
                cloudCoverPct = List(n) { if (it == 14) cloudPct else 0 }
            )
        )
    }

    private fun statusOfCurrentHour(code: Int, visibilityM: Double?, cloudPct: Int) =
        map(forecast = hourAt(code, visibilityM, cloudPct)).hourly.first().condition.label

    @Test
    fun `fog reported with kilometres of visibility falls back on the cloud cover`() {
        // Cavenago, 22 Aug 2026, 01:00: weather_code 45 with 9.76 km of visibility.
        assertEquals("Partly Cloudy ⛅", statusOfCurrentHour(45, 9760.0, 59))
    }

    @Test
    fun `dense fog the provider called overcast reads as fog`() {
        // Same city, 07:00: 160 m of visibility served as code 3. The dangerous direction.
        assertEquals("Foggy 🌫️", statusOfCurrentHour(3, 160.0, 100))
    }

    @Test
    fun `fog that really is fog is left alone`() {
        assertEquals("Foggy 🌫️", statusOfCurrentHour(45, 40.0, 100))
    }

    @Test
    fun `precipitation is never rewritten by the fog repair`() {
        // Rain is not derived from visibility, and it can rain in fog.
        assertEquals("Light Rain 🌧️", statusOfCurrentHour(61, 40.0, 100))
    }

    @Test
    fun `a missing visibility leaves the provider code untouched`() {
        assertEquals("Foggy 🌫️", statusOfCurrentHour(45, null, 10))
    }

    @Test
    fun `current conditions get the same repair as the hours`() {
        val base = forecast()
        val report = map(
            forecast = base.copy(
                current = base.current.copy(weatherCode = 45, visibilityM = 9760.0, cloudCoverPct = 59)
            )
        )
        // The JSON printed "Foggy" right above a 9.76 km visibility of its own.
        assertEquals("Partly Cloudy ⛅", report.current.condition.label)
        assertEquals(9.76, report.current.visibilityKm, 1e-9)
    }

    @Test
    fun `night fog does not label a clear day`() {
        // The reported case: Open-Meteo's daily code is max() over 24 hours, so the fog
        // hours of a Po Valley night outrank 14 hours of sun. Only the daylight votes.
        val codes = List(24) { if (it in 6..19) 0 else 45 }
        assertEquals("Clear ☀️", statusOfFirstDay(codes, providerCode = 45))
    }

    @Test
    fun `rain in daylight outranks the sky`() {
        val codes = List(24) { if (it == 15) 61 else 0 }
        assertEquals("Light Rain 🌧️", statusOfFirstDay(codes, providerCode = 61))
    }

    @Test
    fun `rain that only falls at night still labels the day`() {
        // Only the sky is the daylight's. Scoping the rain to it too silently turned 8
        // nocturnal thunderstorms into "Overcast" across the 8 cities measured — the rule
        // may remove a distortion, never a warning.
        val codes = List(24) { if (it in 6..19) 3 else 95 }
        assertEquals("Thunderstorm ⛈️", statusOfFirstDay(codes, providerCode = 95))
    }

    @Test
    fun `a day that really is foggy still reads foggy`() {
        // No special case for 45: it wins on its own when it is the day's usual sky.
        val codes = List(24) { if (it in 6..15) 45 else 3 }
        assertEquals("Foggy 🌫️", statusOfFirstDay(codes, providerCode = 45))
    }

    @Test
    fun `a tie between two skies goes to the heavier one`() {
        val codes = List(24) { if (it in 6..12) 1 else 3 }
        assertEquals("Overcast ☁️", statusOfFirstDay(codes, providerCode = 3))
    }

    @Test
    fun `days the hourly run does not reach keep the provider code`() {
        // 48 hourly slots cover two days; the rest of the week falls back on daily.
        val daily = map().daily
        assertEquals("Clear ☀️", daily[1].condition.label)     // derived: clear all day
        assertEquals("Overcast ☁️", daily[2].condition.label)  // provider's code 3
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
