package com.callbackdev.tweather.domain.sky

import java.time.Instant
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The arithmetic under [AstronomyEngine] (Fase 16b): positions of the sun and the
 * moon, in degrees, from an [Instant] and nothing else.
 *
 * Source: Jean Meeus, *Astronomical Algorithms*, 2nd ed. — chapter 25 for the sun
 * (the "low accuracy" solar coordinates, good to ~0.01°), chapters 22 and 47 for
 * nutation and the lunar series, 48 for the illuminated fraction. The lunar series is
 * truncated to the terms above 0.001° in longitude and latitude, which keeps the
 * moon's longitude inside ~0.02°: the moon's elongation moves 0.0085°/min, so that is
 * under three minutes on a quarter instant and about ten seconds on a moonrise.
 *
 * **Everything here is geocentric and in degrees**, and every function is pure and
 * frame-free: no clock, no zone, no Android. The zone appears exactly once in the
 * module, in [AstronomyEngine], where an instant is turned into a local time to be
 * shown to somebody.
 *
 * **Two time scales, and they are not interchangeable.** The position series are
 * series in Terrestrial Time; sidereal time — and therefore every hour angle — is a
 * function of Universal Time. ΔT between them is ~75 s in 2026 ([deltaTSeconds]).
 * Feeding UT to a position series is a small error (the moon moves 0.015° in 75 s)
 * and feeding TT to sidereal time is a large one (the sky turns 0.3° in 75 s), so
 * this file keeps them apart: [centuriesTT] for positions, the plain Julian day for
 * [greenwichSiderealTime].
 */
internal object AstronomyMath {

    const val DEG = Math.PI / 180.0

    /** Julian Day (UT) of [instant]; the epoch of every series below. */
    fun julianDay(instant: Instant): Double =
        instant.toEpochMilli() / 86_400_000.0 + 2_440_587.5

    fun instantOf(julianDay: Double): Instant =
        Instant.ofEpochMilli(Math.round((julianDay - 2_440_587.5) * 86_400_000.0))

    /** Julian centuries from J2000.0. */
    fun centuries(julianDay: Double): Double = (julianDay - 2_451_545.0) / 36_525.0

    /**
     * Julian centuries of Terrestrial Time from J2000.0 — the argument every position
     * series below actually wants.
     */
    fun centuriesTT(instant: Instant): Double {
        val jd = julianDay(instant)
        return centuries(jd + deltaTSeconds(jd) / 86_400.0)
    }

    /**
     * ΔT = TT − UT, seconds. Espenak & Meeus's polynomial for 2005–2050, extended by
     * holding its ends: it reads ~75 s for 2026 against a measured ~69 s, and the
     * six-second difference is three orders of magnitude below anything this module
     * prints. It exists so the TWO TIME SCALES stay distinguishable, not because the
     * app needs ΔT to the second — a future year is unknowable anyway, since ΔT
     * depends on how fast the earth happens to be turning.
     */
    fun deltaTSeconds(julianDay: Double): Double {
        val year = 2000.0 + (julianDay - 2_451_545.0) / 365.25
        val t = year.coerceIn(2005.0, 2050.0) - 2000.0
        return 62.92 + 0.32217 * t + 0.005589 * t * t
    }

    /** Reduces to `[0, 360)`. */
    fun norm360(degrees: Double): Double {
        val r = degrees % 360.0
        return if (r < 0) r + 360.0 else r
    }

    /** Reduces to `(-180, 180]` — the form a difference of two angles wants. */
    fun norm180(degrees: Double): Double {
        val r = norm360(degrees)
        return if (r > 180.0) r - 360.0 else r
    }

    private fun sinDeg(d: Double) = sin(d * DEG)
    private fun cosDeg(d: Double) = cos(d * DEG)

    // ---------------------------------------------------------------- the sun

    /** Apparent geocentric ecliptic longitude of the sun, degrees (Meeus 25). */
    fun sunApparentLongitude(t: Double): Double {
        val sun = sunElements(t)
        // Nutation in longitude and aberration, folded into the "apparent" value.
        val omega = 125.04 - 1934.136 * t
        return norm360(sun.trueLongitude - 0.00569 - 0.00478 * sinDeg(omega))
    }

    /**
     * Mean longitude plus the equation of the centre (Meeus 25): the sun's true
     * longitude, which is every solar angle in this file.
     */
    private fun sunElements(t: Double): SunElements {
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t          // mean longitude
        val m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t           // mean anomaly
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sinDeg(m) +
            (0.019993 - 0.000101 * t) * sinDeg(2 * m) +
            0.000289 * sinDeg(3 * m)
        return SunElements(trueLongitude = l0 + c)
    }

    private data class SunElements(val trueLongitude: Double)

    /**
     * The earth's distance from the sun in AU, from the VSOP87D radius series
     * (Meeus appendix III, truncated), good to about 10⁻⁷ AU.
     *
     * A two-body Kepler distance — the companion of the low-accuracy longitude above —
     * is good to 10⁻⁵ AU, which is ample for the size of the sun's disk and hopeless
     * for the one question that needs the DERIVATIVE of the distance: when the earth
     * is closest. The orbit is so flat at perihelion that 10⁻⁵ AU of error moves the
     * instant by hours (measured: up to five, against the published instants of
     * sixteen years), so the series is here and the two-body form is not.
     *
     * Like every VSOP87 solution for "the earth" this is the earth–MOON BARYCENTRE,
     * which is also what an almanac means by the earth at perihelion.
     */
    fun earthRadiusVectorAu(t: Double): Double {
        val tau = t / 10.0                              // VSOP87 counts millennia
        var r = 0.0
        var power = 1.0
        for (series in EarthRadiusSeries) {
            var sum = 0.0
            for ((amplitude, phase, frequency) in series) {
                sum += amplitude * cos(phase + frequency * tau)
            }
            r += sum * power
            power *= tau
        }
        return r / 1e8
    }

    /** Apparent semidiameter of the solar disk, degrees: 959.63" at one AU. */
    fun sunSemidiameterDeg(t: Double): Double = (959.63 / 3600.0) / earthRadiusVectorAu(t)

    /** Equatorial horizontal parallax of the sun, degrees: 8.794" at one AU. */
    fun sunParallaxDeg(t: Double): Double = (8.794 / 3600.0) / earthRadiusVectorAu(t)

    /**
     * Apparent semidiameter of the moon, degrees, from its horizontal parallax — the
     * ratio is the moon's own radius over the earth's (Meeus 55.4).
     */
    fun moonSemidiameterDeg(parallaxDeg: Double): Double = 0.2725 * parallaxDeg

    /** Mean obliquity corrected for nutation, degrees (Meeus 22). */
    fun obliquity(t: Double): Double {
        val e0 = 23.0 + 26.0 / 60.0 + 21.448 / 3600.0 -
            (46.8150 * t + 0.00059 * t * t - 0.001813 * t * t * t) / 3600.0
        val omega = 125.04 - 1934.136 * t
        return e0 + 0.00256 * cosDeg(omega)
    }

    /** Apparent equatorial coordinates of the sun: right ascension and declination. */
    fun sunEquatorial(t: Double): Equatorial {
        val lambda = sunApparentLongitude(t)
        val eps = obliquity(t)
        return Equatorial(
            rightAscension = norm360(
                atan2(cosDeg(eps) * sinDeg(lambda), cosDeg(lambda)) / DEG
            ),
            declination = asin(sinDeg(eps) * sinDeg(lambda)) / DEG
        )
    }

    // --------------------------------------------------------------- the moon

    /**
     * Apparent geocentric ecliptic coordinates of the moon and its horizontal
     * parallax, degrees (Meeus 47, truncated — see the file KDoc).
     */
    fun moonEcliptic(t: Double): MoonEcliptic {
        val lp = norm360(218.3164477 + 481267.88123421 * t - 0.0015786 * t * t)   // L'
        val d = norm360(297.8501921 + 445267.1114034 * t - 0.0018819 * t * t)     // D
        val m = norm360(357.5291092 + 35999.0502909 * t - 0.0001536 * t * t)      // M
        val mp = norm360(134.9633964 + 477198.8675055 * t + 0.0087414 * t * t)    // M'
        val f = norm360(93.2720950 + 483202.0175233 * t - 0.0036539 * t * t)      // F
        // Eccentricity correction applied to terms carrying the sun's anomaly M.
        val e = 1.0 - 0.002516 * t - 0.0000074 * t * t

        var sumL = 0.0
        for ((cd, cm, cmp, cf, coeff) in LongitudeTerms) {
            val arg = cd * d + cm * m + cmp * mp + cf * f
            val ecc = when (abs(cm)) { 1.0 -> e; 2.0 -> e * e; else -> 1.0 }
            sumL += coeff * ecc * sinDeg(arg)
        }
        var sumB = 0.0
        for ((cd, cm, cmp, cf, coeff) in LatitudeTerms) {
            val arg = cd * d + cm * m + cmp * mp + cf * f
            val ecc = when (abs(cm)) { 1.0 -> e; 2.0 -> e * e; else -> 1.0 }
            sumB += coeff * ecc * sinDeg(arg)
        }
        var sumR = 0.0
        for ((cd, cm, cmp, cf, coeff) in DistanceTerms) {
            val arg = cd * d + cm * m + cmp * mp + cf * f
            val ecc = when (abs(cm)) { 1.0 -> e; 2.0 -> e * e; else -> 1.0 }
            sumR += coeff * ecc * cosDeg(arg)
        }

        val distanceKm = 385_000.56 + sumR / 1000.0
        return MoonEcliptic(
            longitude = norm360(lp + sumL / 1_000_000.0),
            latitude = sumB / 1_000_000.0,
            distanceKm = distanceKm,
            parallax = asin(6378.14 / distanceKm) / DEG
        )
    }

    fun moonEquatorial(t: Double): Equatorial = moonEquatorial(moonEcliptic(t), obliquity(t))

    fun moonEquatorial(moon: MoonEcliptic, eps: Double): Equatorial {
        val sl = sinDeg(moon.longitude)
        val cl = cosDeg(moon.longitude)
        val sb = sinDeg(moon.latitude)
        val cb = cosDeg(moon.latitude)
        val se = sinDeg(eps)
        val ce = cosDeg(eps)
        return Equatorial(
            rightAscension = norm360(atan2(sl * ce - (sb / cb) * se, cl) / DEG),
            declination = asin(sb * ce + cb * se * sl) / DEG
        )
    }

    /**
     * Illuminated fraction of the moon's disk, 0..1 (Meeus 48), and the elongation
     * that says which side of the cycle it is on. Geocentric: the difference between
     * this and what an observer sees is far below the precision of an emoji.
     */
    fun moonIllumination(t: Double): MoonIllumination {
        val moon = moonEcliptic(t)
        val sunLongitude = sunApparentLongitude(t)
        val elongation = norm360(moon.longitude - sunLongitude)
        // Meeus 48.2/48.3: the phase angle i seen from the moon, then the fraction.
        // cos ψ from ecliptic coordinates directly — the moon's latitude is the only
        // reason ψ is not just the longitude difference.
        val cosPsi = cosDeg(moon.latitude) * cosDeg(elongation)
        val psi = Math.acos(cosPsi.coerceIn(-1.0, 1.0)) / DEG
        val i = atan2(
            SUN_DISTANCE_KM * sinDeg(psi),
            moon.distanceKm - SUN_DISTANCE_KM * cosDeg(psi)
        ) / DEG
        return MoonIllumination(
            fraction = (1 + cosDeg(i)) / 2,
            elongation = elongation
        )
    }

    /** Mean earth–sun distance; the illuminated fraction is insensitive to its wobble. */
    private const val SUN_DISTANCE_KM = 149_598_000.0

    // -------------------------------------------------------------- the earth

    /** Apparent sidereal time at Greenwich, degrees (Meeus 12). */
    fun greenwichSiderealTime(julianDay: Double): Double {
        val t = centuries(julianDay)
        val mean = 280.46061837 + 360.98564736629 * (julianDay - 2_451_545.0) +
            0.000387933 * t * t - t * t * t / 38_710_000.0
        // Nutation in longitude × cos(obliquity): under 1.2 s of time, kept because
        // dropping it is the kind of shortcut that shows up as a systematic bias.
        val omega = 125.04 - 1934.136 * t
        val deltaPsi = -0.00478 * sinDeg(omega)
        return norm360(mean + deltaPsi * cosDeg(obliquity(t)))
    }

    /**
     * Equatorial coordinates of the point of the ECLIPTIC at longitude [longitude],
     * latitude zero — the band the planets and the zodiacal light live on.
     */
    fun eclipticPoint(longitude: Double, eps: Double): Equatorial = Equatorial(
        rightAscension = norm360(
            atan2(cosDeg(eps) * sinDeg(longitude), cosDeg(longitude)) / DEG
        ),
        declination = asin(sinDeg(eps) * sinDeg(longitude)) / DEG
    )

    /** Angular distance between two directions on the sphere, degrees. */
    fun separation(a: Equatorial, b: Equatorial): Double {
        val d1 = a.declination * DEG
        val d2 = b.declination * DEG
        val dRa = (a.rightAscension - b.rightAscension) * DEG
        // atan2 of the cross and dot products rather than acos of the dot: acos loses
        // its precision exactly where an eclipse lives, a few arcminutes from zero.
        val x = cos(d1) * sin(d2) - sin(d1) * cos(d2) * cos(dRa)
        val y = cos(d2) * sin(dRa)
        val z = sin(d1) * sin(d2) + cos(d1) * cos(d2) * cos(dRa)
        return atan2(Math.hypot(x, y), z) / DEG
    }

    /** The point opposite the sun: the axis of the earth's shadow. */
    fun antisolar(sun: Equatorial): Equatorial =
        Equatorial(norm360(sun.rightAscension + 180.0), -sun.declination)

    /**
     * Where a body is seen from a point ON the earth rather than from its centre
     * (Meeus 40), and how far away it is from there.
     *
     * Done as a vector subtraction instead of Meeus' Δα/Δδ series: the observer's
     * offset is one vector in earth radii, and subtracting it gives the topocentric
     * DISTANCE in the same step — which is what the apparent size of the moon during
     * a solar eclipse is made of, and what the series form throws away.
     */
    fun topocentric(
        equatorial: Equatorial,
        parallaxDeg: Double,
        julianDay: Double,
        lat: Double,
        lon: Double
    ): Topocentric {
        // Distance in earth radii, from the parallax that defines it.
        val r = 1.0 / sin(parallaxDeg * DEG)
        val x = r * cosDeg(equatorial.declination) * cosDeg(equatorial.rightAscension)
        val y = r * cosDeg(equatorial.declination) * sinDeg(equatorial.rightAscension)
        val z = r * sinDeg(equatorial.declination)
        // The observer, on the reference ellipsoid at sea level (Meeus 11): the
        // flattening moves ρ by up to 0.3 %, which is 20 km — a fifth of the moon's
        // own radius, and worth keeping in an eclipse.
        val u = atan2(0.99664719 * sinDeg(lat), cosDeg(lat))
        val rhoSin = 0.99664719 * sin(u)
        val rhoCos = cos(u)
        val theta = norm360(greenwichSiderealTime(julianDay) + lon)
        val ox = rhoCos * cosDeg(theta)
        val oy = rhoCos * sinDeg(theta)
        val oz = rhoSin
        val dx = x - ox
        val dy = y - oy
        val dz = z - oz
        val distance = Math.sqrt(dx * dx + dy * dy + dz * dz)
        return Topocentric(
            equatorial = Equatorial(
                rightAscension = norm360(atan2(dy, dx) / DEG),
                declination = asin(dz / distance) / DEG
            ),
            distanceEarthRadii = distance
        )
    }

    data class Topocentric(val equatorial: Equatorial, val distanceEarthRadii: Double)

    /**
     * Azimuth of a body, degrees clockwise from NORTH — the compass bearing you turn
     * to. Meeus 13.5 measures it from the south, which is an astronomer's convention
     * and not a compass's, so the half-turn is added here rather than at every call.
     */
    fun azimuth(equatorial: Equatorial, julianDay: Double, lat: Double, lon: Double): Double {
        val h = norm360(greenwichSiderealTime(julianDay) + lon - equatorial.rightAscension)
        val south = atan2(
            sinDeg(h),
            cosDeg(h) * sinDeg(lat) - Math.tan(equatorial.declination * DEG) * cosDeg(lat)
        ) / DEG
        return norm360(south + 180.0)
    }

    /** Altitude of a body above the horizon, degrees, ignoring refraction. */
    fun altitude(equatorial: Equatorial, julianDay: Double, lat: Double, lon: Double): Double {
        val hourAngle = greenwichSiderealTime(julianDay) + lon - equatorial.rightAscension
        val sinAlt = sinDeg(lat) * sinDeg(equatorial.declination) +
            cosDeg(lat) * cosDeg(equatorial.declination) * cosDeg(hourAngle)
        return asin(sinAlt.coerceIn(-1.0, 1.0)) / DEG
    }

    /**
     * Julian Day (TT) of a solstice or equinox (Meeus 27), for `k` = 0 March equinox,
     * 1 June solstice, 2 September equinox, 3 December solstice.
     *
     * A dedicated algorithm rather than a root-find on [sunApparentLongitude], and the
     * reason is measured, not stylistic: the low-accuracy solar series is good to
     * ~0.01°, the sun covers 0.01° in about fifteen minutes, and an equinox is
     * rendered as an `HH:mm`. Root-finding it produced an instant eight minutes from
     * the published one — inside the model's own error bar, and outside anything the
     * file should print. This series is fitted to the answer instead of to the
     * position, and lands within a minute.
     */
    fun seasonJulianDay(year: Int, k: Int): Double {
        val y = (year - 2000) / 1000.0
        val (a, b, c, d, e) = SeasonPolynomials[k]
        val jde0 = a + b * y + c * y * y + d * y * y * y + e * y * y * y * y
        val t = centuries(jde0)
        val w = 35999.373 * t - 2.47
        val lambda = 1 + 0.0334 * cosDeg(w) + 0.0007 * cosDeg(2 * w)
        val s = SeasonTerms.sumOf { (amplitude, phase, frequency) ->
            amplitude * cosDeg(phase + frequency * t)
        }
        return jde0 + (0.00001 * s) / lambda
    }

    data class Equatorial(val rightAscension: Double, val declination: Double)

    data class MoonEcliptic(
        val longitude: Double,
        val latitude: Double,
        val distanceKm: Double,
        /** Equatorial horizontal parallax, degrees — the moon's rise threshold uses it. */
        val parallax: Double
    )

    data class MoonIllumination(
        /** 0 at new moon, 1 at full. */
        val fraction: Double,
        /** Moon longitude − sun longitude, `[0, 360)`: 0 new, 90 first quarter, 180 full. */
        val elongation: Double
    )

    /** Mean-season polynomial coefficients, Meeus table 27.A/27.B (years 1000–3000). */
    private data class SeasonPolynomial(
        val a: Double, val b: Double, val c: Double, val d: Double, val e: Double
    )

    private val SeasonPolynomials = listOf(
        SeasonPolynomial(2451623.80984, 365242.37404, 0.05169, -0.00411, -0.00057),
        SeasonPolynomial(2451716.56767, 365241.62603, 0.00325, 0.00888, -0.00030),
        SeasonPolynomial(2451810.21715, 365242.01767, -0.11575, 0.00337, 0.00078),
        SeasonPolynomial(2451900.05952, 365242.74049, -0.06223, -0.00823, 0.00032)
    )

    /** Periodic corrections, Meeus table 27.C: amplitude, phase, frequency. */
    private val SeasonTerms = listOf(
        Triple(485.0, 324.96, 1934.136), Triple(203.0, 337.23, 32964.467),
        Triple(199.0, 342.08, 20.186), Triple(182.0, 27.85, 445267.112),
        Triple(156.0, 73.14, 45036.886), Triple(136.0, 171.52, 22518.443),
        Triple(77.0, 222.54, 65928.934), Triple(74.0, 296.72, 3034.906),
        Triple(70.0, 243.58, 9037.513), Triple(58.0, 119.81, 33718.147),
        Triple(52.0, 297.17, 150.678), Triple(50.0, 21.02, 2281.226),
        Triple(45.0, 247.54, 29929.562), Triple(44.0, 325.15, 31555.956),
        Triple(29.0, 60.93, 4443.417), Triple(18.0, 155.12, 67555.328),
        Triple(17.0, 288.79, 4562.452), Triple(16.0, 198.04, 62894.029),
        Triple(14.0, 199.76, 31436.921), Triple(12.0, 95.39, 14577.848),
        Triple(12.0, 287.11, 31931.756), Triple(12.0, 320.81, 34777.259),
        Triple(9.0, 227.73, 1222.114), Triple(8.0, 15.45, 16859.074)
    )

    /**
     * VSOP87D, earth, radius vector: `amplitude (1e-8 AU), phase (rad), frequency
     * (rad/millennium)`, one list per power of τ. Truncated to the terms that move
     * the answer by more than a metre of a metre — the tail below 1e-8 AU is far
     * under the accuracy of anything this module prints.
     */
    private val EarthRadiusSeries: List<List<Triple<Double, Double, Double>>> = listOf(
        listOf(
            Triple(100_013_989.0, 0.0, 0.0),
            Triple(1_670_700.0, 3.0984635, 6283.07585),
            Triple(13_956.0, 3.05525, 12566.1517),
            Triple(3_084.0, 5.1985, 77713.7715),
            Triple(1_628.0, 1.1739, 5753.3849),
            Triple(1_576.0, 2.8469, 7860.4194),
            Triple(925.0, 5.453, 11506.770),
            Triple(542.0, 4.564, 3930.210),
            Triple(472.0, 3.661, 5884.927),
            Triple(346.0, 0.964, 5507.553),
            Triple(329.0, 5.900, 5223.694),
            Triple(307.0, 0.299, 5573.143),
            Triple(243.0, 4.273, 11790.629),
            Triple(212.0, 5.847, 1577.344),
            Triple(186.0, 5.022, 10977.079),
            Triple(175.0, 3.012, 18849.228),
            Triple(110.0, 5.055, 5486.778),
            Triple(98.0, 0.89, 6069.78),
            Triple(86.0, 5.69, 15720.84),
            Triple(86.0, 1.27, 161000.69),
            Triple(65.0, 0.27, 17260.15),
            Triple(63.0, 0.92, 529.69),
            Triple(57.0, 2.01, 83996.85),
            Triple(56.0, 5.24, 71430.70),
            Triple(49.0, 3.25, 2544.31),
            Triple(47.0, 2.58, 775.52),
            Triple(45.0, 5.54, 9437.76),
            Triple(43.0, 6.01, 6275.96),
            Triple(39.0, 5.36, 4694.00),
            Triple(38.0, 2.39, 8827.39),
            Triple(37.0, 0.83, 19651.05),
            Triple(37.0, 4.90, 12139.55),
            Triple(36.0, 1.67, 12036.46),
            Triple(35.0, 1.84, 2942.46),
            Triple(33.0, 0.24, 7084.90),
            Triple(32.0, 0.18, 5088.63),
            Triple(32.0, 1.78, 398.15),
            Triple(28.0, 1.21, 6286.60),
            Triple(28.0, 1.90, 6279.55),
            Triple(26.0, 4.59, 10447.39)
        ),
        listOf(
            Triple(103_019.0, 1.10749, 6283.07585),
            Triple(1_721.0, 1.0644, 12566.1517),
            Triple(702.0, 3.142, 0.0),
            Triple(32.0, 1.02, 18849.23),
            Triple(31.0, 2.84, 5507.55),
            Triple(25.0, 1.32, 5223.69),
            Triple(18.0, 1.42, 1577.34),
            Triple(10.0, 5.91, 10977.08),
            Triple(9.0, 1.42, 6275.96),
            Triple(9.0, 0.27, 5486.78)
        ),
        listOf(
            Triple(4_359.0, 5.7846, 6283.0758),
            Triple(124.0, 5.579, 12566.152),
            Triple(12.0, 3.14, 0.0),
            Triple(9.0, 3.63, 77713.77),
            Triple(6.0, 1.87, 5573.14),
            Triple(3.0, 5.47, 18849.23)
        ),
        listOf(
            Triple(145.0, 4.273, 6283.076),
            Triple(7.0, 3.92, 12566.15)
        ),
        listOf(
            Triple(4.0, 2.56, 6283.08)
        )
    )

    /** `D, M, M', F, coefficient` — coefficients in 1e-6 degrees (Meeus table 47.A). */
    private data class Term(
        val d: Double, val m: Double, val mp: Double, val f: Double, val coeff: Double
    )

    // Table 47.A, longitude column, terms ≥ 1000 (0.001°) plus the leading few below
    // it — the tail contributes under 0.005° in total.
    private val LongitudeTerms = listOf(
        Term(0.0, 0.0, 1.0, 0.0, 6288774.0),
        Term(2.0, 0.0, -1.0, 0.0, 1274027.0),
        Term(2.0, 0.0, 0.0, 0.0, 658314.0),
        Term(0.0, 0.0, 2.0, 0.0, 213618.0),
        Term(0.0, 1.0, 0.0, 0.0, -185116.0),
        Term(0.0, 0.0, 0.0, 2.0, -114332.0),
        Term(2.0, 0.0, -2.0, 0.0, 58793.0),
        Term(2.0, -1.0, -1.0, 0.0, 57066.0),
        Term(2.0, 0.0, 1.0, 0.0, 53322.0),
        Term(2.0, -1.0, 0.0, 0.0, 45758.0),
        Term(0.0, 1.0, -1.0, 0.0, -40923.0),
        Term(1.0, 0.0, 0.0, 0.0, -34720.0),
        Term(0.0, 1.0, 1.0, 0.0, -30383.0),
        Term(2.0, 0.0, 0.0, -2.0, 15327.0),
        Term(0.0, 0.0, 1.0, 2.0, -12528.0),
        Term(0.0, 0.0, 1.0, -2.0, 10980.0),
        Term(4.0, 0.0, -1.0, 0.0, 10675.0),
        Term(0.0, 0.0, 3.0, 0.0, 10034.0),
        Term(4.0, 0.0, -2.0, 0.0, 8548.0),
        Term(2.0, 1.0, -1.0, 0.0, -7888.0),
        Term(2.0, 1.0, 0.0, 0.0, -6766.0),
        Term(1.0, 0.0, -1.0, 0.0, -5163.0),
        Term(1.0, 1.0, 0.0, 0.0, 4987.0),
        Term(2.0, -1.0, 1.0, 0.0, 4036.0),
        Term(2.0, 0.0, 2.0, 0.0, 3994.0),
        Term(4.0, 0.0, 0.0, 0.0, 3861.0),
        Term(2.0, 0.0, -3.0, 0.0, 3665.0),
        Term(0.0, 1.0, -2.0, 0.0, -2689.0),
        Term(2.0, 0.0, -1.0, 2.0, -2602.0),
        Term(2.0, -1.0, -2.0, 0.0, 2390.0),
        Term(1.0, 0.0, 1.0, 0.0, -2348.0),
        Term(2.0, -2.0, 0.0, 0.0, 2236.0),
        Term(0.0, 1.0, 2.0, 0.0, -2120.0),
        Term(0.0, 2.0, 0.0, 0.0, -2069.0),
        Term(2.0, -2.0, -1.0, 0.0, 2048.0),
        Term(2.0, 0.0, 1.0, -2.0, -1773.0),
        Term(2.0, 0.0, 0.0, 2.0, -1595.0),
        Term(4.0, -1.0, -1.0, 0.0, 1215.0),
        Term(0.0, 0.0, 2.0, 2.0, -1110.0),
        Term(3.0, 0.0, -1.0, 0.0, -892.0),
        Term(2.0, 1.0, 1.0, 0.0, -810.0),
        Term(4.0, -1.0, -2.0, 0.0, 759.0),
        Term(0.0, 2.0, -1.0, 0.0, -713.0),
        Term(2.0, 2.0, -1.0, 0.0, -700.0),
        Term(2.0, 1.0, -2.0, 0.0, 691.0),
        Term(2.0, -1.0, 0.0, -2.0, 596.0),
        Term(4.0, 0.0, 1.0, 0.0, 549.0),
        Term(0.0, 0.0, 4.0, 0.0, 537.0),
        Term(4.0, -1.0, 0.0, 0.0, 520.0),
        Term(1.0, 0.0, -2.0, 0.0, -487.0),
        Term(2.0, 1.0, 0.0, -2.0, -399.0),
        Term(0.0, 0.0, 2.0, -2.0, -381.0),
        Term(1.0, 1.0, 1.0, 0.0, 351.0),
        Term(3.0, 0.0, -2.0, 0.0, -340.0),
        Term(4.0, 0.0, -3.0, 0.0, 330.0),
        Term(2.0, -1.0, 2.0, 0.0, 327.0),
        Term(0.0, 2.0, 1.0, 0.0, -323.0),
        Term(1.0, 1.0, -1.0, 0.0, 299.0),
        Term(2.0, 0.0, 3.0, 0.0, 294.0)
    )

    // Table 47.B, latitude column, same truncation rule.
    private val LatitudeTerms = listOf(
        Term(0.0, 0.0, 0.0, 1.0, 5128122.0),
        Term(0.0, 0.0, 1.0, 1.0, 280602.0),
        Term(0.0, 0.0, 1.0, -1.0, 277693.0),
        Term(2.0, 0.0, 0.0, -1.0, 173237.0),
        Term(2.0, 0.0, -1.0, 1.0, 55413.0),
        Term(2.0, 0.0, -1.0, -1.0, 46271.0),
        Term(2.0, 0.0, 0.0, 1.0, 32573.0),
        Term(0.0, 0.0, 2.0, 1.0, 17198.0),
        Term(2.0, 0.0, 1.0, -1.0, 9266.0),
        Term(0.0, 0.0, 2.0, -1.0, 8822.0),
        Term(2.0, -1.0, 0.0, -1.0, 8216.0),
        Term(2.0, 0.0, -2.0, -1.0, 4324.0),
        Term(2.0, 0.0, 1.0, 1.0, 4200.0),
        Term(2.0, 1.0, 0.0, -1.0, -3359.0),
        Term(2.0, -1.0, -1.0, 1.0, 2463.0),
        Term(2.0, -1.0, 0.0, 1.0, 2211.0),
        Term(2.0, -1.0, -1.0, -1.0, 2065.0),
        Term(0.0, 1.0, -1.0, -1.0, -1870.0),
        Term(4.0, 0.0, -1.0, -1.0, 1828.0),
        Term(0.0, 1.0, 0.0, 1.0, -1794.0),
        Term(0.0, 0.0, 0.0, 3.0, -1749.0),
        Term(0.0, 1.0, -1.0, 1.0, -1565.0),
        Term(1.0, 0.0, 0.0, 1.0, -1491.0),
        Term(0.0, 1.0, 1.0, 1.0, -1475.0),
        Term(0.0, 1.0, 1.0, -1.0, -1410.0),
        Term(0.0, 1.0, 0.0, -1.0, -1344.0),
        Term(1.0, 0.0, 0.0, -1.0, -1335.0),
        Term(0.0, 0.0, 3.0, 1.0, 1107.0),
        Term(4.0, 0.0, 0.0, -1.0, 1021.0),
        Term(4.0, 0.0, -1.0, 1.0, 833.0),
        Term(0.0, 0.0, 1.0, -3.0, 777.0),
        Term(4.0, 0.0, -2.0, 1.0, 671.0),
        Term(2.0, 0.0, 0.0, -3.0, 607.0),
        Term(2.0, 0.0, 2.0, -1.0, 596.0),
        Term(2.0, -1.0, 1.0, -1.0, 491.0),
        Term(2.0, 0.0, -2.0, 1.0, -451.0),
        Term(0.0, 0.0, 3.0, -1.0, 439.0),
        Term(2.0, 0.0, 2.0, 1.0, 422.0),
        Term(2.0, 0.0, -3.0, -1.0, 421.0),
        Term(2.0, 1.0, -1.0, 1.0, -366.0),
        Term(2.0, 1.0, 0.0, 1.0, -351.0),
        Term(4.0, 0.0, 0.0, 1.0, 331.0),
        Term(2.0, -1.0, 1.0, 1.0, 315.0),
        Term(2.0, -2.0, 0.0, -1.0, 302.0)
    )

    // Table 47.A, distance column (coefficients in 0.001 km).
    private val DistanceTerms = listOf(
        Term(0.0, 0.0, 1.0, 0.0, -20905355.0),
        Term(2.0, 0.0, -1.0, 0.0, -3699111.0),
        Term(2.0, 0.0, 0.0, 0.0, -2955968.0),
        Term(0.0, 0.0, 2.0, 0.0, -569925.0),
        Term(0.0, 1.0, 0.0, 0.0, 48888.0),
        Term(0.0, 0.0, 0.0, 2.0, -3149.0),
        Term(2.0, 0.0, -2.0, 0.0, 246158.0),
        Term(2.0, -1.0, -1.0, 0.0, -152138.0),
        Term(2.0, 0.0, 1.0, 0.0, -170733.0),
        Term(2.0, -1.0, 0.0, 0.0, -204586.0),
        Term(0.0, 1.0, -1.0, 0.0, -129620.0),
        Term(1.0, 0.0, 0.0, 0.0, 108743.0),
        Term(0.0, 1.0, 1.0, 0.0, 104755.0),
        Term(2.0, 0.0, 0.0, -2.0, 10321.0),
        Term(0.0, 0.0, 1.0, -2.0, 79661.0),
        Term(4.0, 0.0, -1.0, 0.0, -34782.0),
        Term(0.0, 0.0, 3.0, 0.0, -23210.0),
        Term(4.0, 0.0, -2.0, 0.0, -21636.0),
        Term(2.0, 1.0, -1.0, 0.0, 24208.0),
        Term(2.0, 1.0, 0.0, 0.0, 30824.0),
        Term(1.0, 0.0, -1.0, 0.0, -8379.0),
        Term(1.0, 1.0, 0.0, 0.0, -16675.0),
        Term(2.0, -1.0, 1.0, 0.0, -12831.0),
        Term(2.0, 0.0, 2.0, 0.0, -10445.0),
        Term(4.0, 0.0, 0.0, 0.0, -11650.0),
        Term(2.0, 0.0, -3.0, 0.0, 14403.0),
        Term(0.0, 1.0, -2.0, 0.0, -7003.0),
        Term(2.0, -1.0, -2.0, 0.0, 10056.0),
        Term(1.0, 0.0, 1.0, 0.0, 6322.0),
        Term(2.0, -2.0, 0.0, 0.0, -9884.0),
        Term(0.0, 2.0, 0.0, 0.0, 5751.0),
        Term(2.0, -2.0, -1.0, 0.0, -4950.0),
        Term(2.0, 0.0, 1.0, -2.0, 4130.0),
        Term(4.0, -1.0, -1.0, 0.0, -3958.0),
        Term(3.0, 0.0, -1.0, 0.0, 3258.0),
        Term(2.0, 1.0, 1.0, 0.0, 2616.0),
        Term(4.0, -1.0, -2.0, 0.0, -1897.0),
        Term(0.0, 2.0, -1.0, 0.0, -2117.0),
        Term(2.0, 2.0, -1.0, 0.0, 2354.0),
        Term(4.0, 0.0, 1.0, 0.0, -1423.0),
        Term(0.0, 0.0, 4.0, 0.0, -1117.0),
        Term(4.0, -1.0, 0.0, 0.0, -1571.0),
        Term(1.0, 0.0, -2.0, 0.0, -1739.0),
        Term(0.0, 0.0, 2.0, -2.0, -4421.0),
        Term(2.0, 0.0, -1.0, -2.0, 1165.0),
        Term(2.0, 0.0, -2.0, 2.0, 8752.0)
    )

}
