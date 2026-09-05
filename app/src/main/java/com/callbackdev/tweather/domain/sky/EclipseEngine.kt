package com.callbackdev.tweather.domain.sky

import com.callbackdev.tweather.domain.model.Coordinates
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** What the moon does inside the earth's shadow. */
enum class LunarEclipseKind { PENUMBRAL, PARTIAL, TOTAL }

/**
 * One lunar eclipse, computed for the earth as a whole — a lunar eclipse looks the
 * same from every place that can see the moon at all, which is why the local part of
 * it is only ever "is the moon up", handled by [EclipseEngine.visibleWindow].
 *
 * [penumbral] is the whole event, faint ends included; [umbral] is the part where the
 * moon is inside the true shadow and something is actually visible to a passer-by;
 * [totality] is the red hour. Magnitudes are fractions of the moon's DIAMETER: an
 * umbral magnitude of 1.15 means the shadow overlaps the disk by 1.15 of its width,
 * so the eclipse is total with room to spare.
 */
data class LunarEclipse(
    val kind: LunarEclipseKind,
    val greatest: Instant,
    val penumbral: ClosedRange<Instant>,
    val umbral: ClosedRange<Instant>?,
    val totality: ClosedRange<Instant>?,
    val umbralMagnitude: Double,
    val penumbralMagnitude: Double
)

/** What the moon does to the sun, **as seen from one place**. */
enum class SolarEclipseKind { PARTIAL, ANNULAR, TOTAL }

/**
 * One solar eclipse as it happens at ONE place. Unlike a lunar eclipse there is no
 * such thing as "the" solar eclipse: the same event is total along a line a hundred
 * kilometres wide and a shallow bite two thousand kilometres away, so every field
 * here is topocentric and belongs to the coordinates it was asked about.
 *
 * [magnitude] is the fraction of the sun's diameter covered, [obscuration] the
 * fraction of its area — the second is the one that matches what the light does, and
 * it is much the smaller of the two (0.93 of the diameter is 0.86 of the area).
 */
data class SolarEclipse(
    val kind: SolarEclipseKind,
    val greatest: Instant,
    val contacts: ClosedRange<Instant>,
    val magnitude: Double,
    val obscuration: Double
)

/**
 * Eclipses, by geometry rather than by table (Fase 19).
 *
 * Meeus gives eclipses their own chapter of coefficients (54), fitted so that the
 * answer falls out of the lunation number with no positions computed at all. This
 * module already owns positions — a sun, a moon, a parallax, a topocentric transform
 * — so it takes the other road: put the shadow where the geometry says it is and ask
 * how close the moon gets to it. One model for the whole module, which is the same
 * reason [AstronomyEngine] has one altitude primitive and not eleven formulas, and it
 * means an eclipse cannot disagree with the moonrise printed above it.
 *
 * **Accuracy, measured against the NASA five-millennium catalogues** (`EclipseTest`):
 * greatest-eclipse instants land within a couple of minutes of the published ones and
 * magnitudes within a few hundredths. That is the truncated lunar series of
 * [AstronomyMath] showing through, and it is why the app renders contact times to the
 * minute and never claims a second.
 *
 * **What is deliberately not here:** the path of a total solar eclipse across the
 * earth. That needs Besselian elements and a proper ellipsoid, it is a map rather
 * than a time, and a weather app that drew it would be pretending to be an atlas.
 * The local circumstances are what a person here can act on.
 */
object EclipseEngine {

    /** How far ahead the searches look before giving up and saying so. */
    val LUNAR_HORIZON: Duration = Duration.ofDays(3 * 366L)
    val SOLAR_HORIZON: Duration = Duration.ofDays(6 * 366L)

    /**
     * The next lunar eclipse whose penumbral phase has not ended at [after], or null
     * when none falls inside [within].
     *
     * Walks full moons: an eclipse of the moon can only happen at one, and the walk
     * is the same series the phase rows are printed from.
     */
    fun nextLunar(after: Instant, within: Duration = LUNAR_HORIZON): LunarEclipse? {
        val limit = after.plus(within)
        // Back up half a day so an eclipse already in progress is still found.
        var at = after.minus(Duration.ofHours(12))
        while (at.isBefore(limit)) {
            val full = AstronomyEngine.nextMoonQuarter(at, MoonQuarterKind.FULL_MOON).at
            lunarAt(full)?.let { if (it.penumbral.endInclusive.isAfter(after)) return it }
            at = full
        }
        return null
    }

    /**
     * The next solar eclipse visible from [coords] — any bite out of the sun counts,
     * however small — or null when none falls inside [within].
     *
     * Two filters, cheap before dear: a new moon whose geocentric distance from the
     * sun is more than the moon's own parallax plus both disks cannot be an eclipse
     * anywhere on earth, so only the handful that survive that get the topocentric
     * treatment.
     */
    fun nextSolar(
        after: Instant,
        coords: Coordinates,
        within: Duration = SOLAR_HORIZON
    ): SolarEclipse? {
        val limit = after.plus(within)
        var at = after.minus(Duration.ofHours(6))
        while (at.isBefore(limit)) {
            val new = AstronomyEngine.nextMoonQuarter(at, MoonQuarterKind.NEW_MOON).at
            if (possibleSolarEclipse(new)) {
                solarAt(new, coords)?.let {
                    if (it.contacts.endInclusive.isAfter(after)) return it
                }
            }
            at = new
        }
        return null
    }

    /**
     * The part of a lunar eclipse [coords] can actually watch: the moon has to be
     * above the horizon. Null when it sets before the shadow arrives or rises after
     * it has gone — the same eclipse, on the other side of the planet.
     *
     * Measured on the UMBRAL phase when there is one. The penumbral ends of an
     * eclipse are invisible to the naked eye, and letting them decide whether a row
     * appears would promise a sight that is not there.
     */
    fun visibleWindow(eclipse: LunarEclipse, coords: Coordinates): ClosedRange<Instant>? {
        val range = eclipse.umbral ?: eclipse.penumbral
        return AstronomyEngine.moonAbove(range.start, range.endInclusive, coords)
    }

    /** A lunar eclipse and the part of it one place gets to see. */
    data class LocalLunarEclipse(
        val eclipse: LunarEclipse,
        val window: ClosedRange<Instant>
    )

    /**
     * The next lunar eclipse **visible from [coords]** — skipping the ones that
     * happen while the moon is down here, which is roughly half of them.
     */
    fun nextLunarFrom(
        after: Instant,
        coords: Coordinates,
        within: Duration = LUNAR_HORIZON
    ): LocalLunarEclipse? {
        var at = after
        val limit = after.plus(within)
        while (at.isBefore(limit)) {
            val eclipse = nextLunar(at, Duration.between(at, limit)) ?: return null
            visibleWindow(eclipse, coords)?.let { return LocalLunarEclipse(eclipse, it) }
            at = eclipse.penumbral.endInclusive
        }
        return null
    }

    // ------------------------------------------------------------- the moon

    /**
     * The eclipse at a given full moon, or null when the moon misses the shadow —
     * which it does at ten full moons out of twelve, the reason eclipses come in
     * seasons rather than monthly.
     */
    private fun lunarAt(full: Instant): LunarEclipse? {
        val greatest = closestApproach(full, ::shadowSeparation)
        val geometry = shadowGeometry(greatest)
        if (geometry.separation > geometry.penumbraRadius + geometry.moonRadius) return null

        val penumbralMagnitude = (geometry.penumbraRadius + geometry.moonRadius -
            geometry.separation) / (2 * geometry.moonRadius)
        val umbralMagnitude = (geometry.umbraRadius + geometry.moonRadius -
            geometry.separation) / (2 * geometry.moonRadius)
        val kind = when {
            umbralMagnitude >= 1.0 -> LunarEclipseKind.TOTAL
            umbralMagnitude > 0.0 -> LunarEclipseKind.PARTIAL
            else -> LunarEclipseKind.PENUMBRAL
        }
        val penumbral = contactWindow(greatest) { g -> g.penumbraRadius + g.moonRadius }
            ?: return null
        return LunarEclipse(
            kind = kind,
            greatest = greatest,
            penumbral = penumbral,
            umbral = if (umbralMagnitude > 0) {
                contactWindow(greatest) { g -> g.umbraRadius + g.moonRadius }
            } else {
                null
            },
            totality = if (umbralMagnitude >= 1.0) {
                contactWindow(greatest) { g -> g.umbraRadius - g.moonRadius }
            } else {
                null
            },
            umbralMagnitude = umbralMagnitude,
            penumbralMagnitude = penumbralMagnitude
        )
    }

    /** Distance from the moon's centre to the axis of the earth's shadow, degrees. */
    private fun shadowSeparation(at: Instant): Double = shadowGeometry(at).separation

    private class ShadowGeometry(
        val separation: Double,
        val umbraRadius: Double,
        val penumbraRadius: Double,
        val moonRadius: Double
    )

    /**
     * The shadow at one instant, in degrees on the sky at the moon's distance.
     *
     * The umbra and penumbra radii are the classical ones — the moon's parallax plus
     * the sun's, minus or plus the sun's semidiameter — enlarged by the 1/50 that
     * every almanac since Chauvenet adds for the earth's atmosphere. Without that
     * enlargement every umbral magnitude here would come out about 0.02 low, which is
     * the difference between a total eclipse and a nearly total one.
     */
    private fun shadowGeometry(at: Instant): ShadowGeometry {
        val t = AstronomyMath.centuriesTT(at)
        val moon = AstronomyMath.moonEcliptic(t)
        val eps = AstronomyMath.obliquity(t)
        val moonEq = AstronomyMath.moonEquatorial(moon, eps)
        val sunEq = AstronomyMath.sunEquatorial(t)
        val sunParallax = AstronomyMath.sunParallaxDeg(t)
        val sunRadius = AstronomyMath.sunSemidiameterDeg(t)
        return ShadowGeometry(
            separation = AstronomyMath.separation(moonEq, AstronomyMath.antisolar(sunEq)),
            umbraRadius = SHADOW_ENLARGEMENT * (moon.parallax + sunParallax - sunRadius),
            penumbraRadius = SHADOW_ENLARGEMENT * (moon.parallax + sunParallax + sunRadius),
            moonRadius = AstronomyMath.moonSemidiameterDeg(moon.parallax)
        )
    }

    /**
     * The window around [greatest] inside which the separation is under the radius
     * [threshold] asks for. Null when the moon never gets that deep — a partial
     * eclipse has no totality, and the caller reads the null as exactly that.
     */
    private fun contactWindow(
        greatest: Instant,
        threshold: (ShadowGeometry) -> Double
    ): ClosedRange<Instant>? {
        fun inside(at: Instant): Double {
            val g = shadowGeometry(at)
            return threshold(g) - g.separation
        }
        if (inside(greatest) <= 0) return null
        val start = edge(greatest, forward = false, ::inside) ?: return null
        val end = edge(greatest, forward = true, ::inside) ?: return null
        return start..end
    }

    // ------------------------------------------------------------- the sun

    /**
     * Could this new moon be an eclipse for ANYBODY? True when the moon passes within
     * its own parallax plus the two disks of the sun, which is the widest an observer
     * anywhere on the earth's surface can be displaced from the geocentric line.
     */
    private fun possibleSolarEclipse(newMoon: Instant): Boolean {
        val closest = closestApproach(newMoon) { geocentricSunMoonSeparation(it) }
        val t = AstronomyMath.centuriesTT(closest)
        val moon = AstronomyMath.moonEcliptic(t)
        val reach = moon.parallax + AstronomyMath.moonSemidiameterDeg(moon.parallax) +
            AstronomyMath.sunSemidiameterDeg(t)
        return geocentricSunMoonSeparation(closest) < reach
    }

    private fun geocentricSunMoonSeparation(at: Instant): Double {
        val t = AstronomyMath.centuriesTT(at)
        val eps = AstronomyMath.obliquity(t)
        return AstronomyMath.separation(
            AstronomyMath.moonEquatorial(AstronomyMath.moonEcliptic(t), eps),
            AstronomyMath.sunEquatorial(t)
        )
    }

    private class Discs(val separation: Double, val sunRadius: Double, val moonRadius: Double) {
        /** Fraction of the sun's DIAMETER covered; ≤ 0 when the disks do not touch. */
        val magnitude: Double get() = (sunRadius + moonRadius - separation) / (2 * sunRadius)
    }

    /**
     * The two disks as [coords] sees them. Parallax is the whole story here: the moon
     * is displaced by up to a degree — four of its own diameters — between the earth's
     * centre and a point on its surface, which is why a solar eclipse is a local event
     * and a lunar one is not.
     */
    private fun discs(at: Instant, coords: Coordinates): Discs {
        val t = AstronomyMath.centuriesTT(at)
        val jd = AstronomyMath.julianDay(at)
        val eps = AstronomyMath.obliquity(t)
        val moon = AstronomyMath.moonEcliptic(t)
        val moonTopo = AstronomyMath.topocentric(
            AstronomyMath.moonEquatorial(moon, eps), moon.parallax, jd, coords.lat, coords.lon
        )
        val sunTopo = AstronomyMath.topocentric(
            AstronomyMath.sunEquatorial(t), AstronomyMath.sunParallaxDeg(t), jd,
            coords.lat, coords.lon
        )
        // The moon's apparent size follows its topocentric distance: overhead it is
        // one earth radius nearer than it is to the earth's centre, which is 1.7 % of
        // its diameter and the difference between an annular eclipse and a total one.
        val moonRadius = Math.toDegrees(
            Math.asin(MOON_RADII_PER_EARTH_RADIUS / moonTopo.distanceEarthRadii)
        )
        // The sun's own parallax is only 8.8", but it is what turns its distance in
        // earth radii into a number the same subtraction can use.
        val sunDistanceEarthRadii = 1.0 / Math.sin(AstronomyMath.sunParallaxDeg(t) * AstronomyMath.DEG)
        val sunRadius = AstronomyMath.sunSemidiameterDeg(t) *
            sunDistanceEarthRadii / sunTopo.distanceEarthRadii
        return Discs(
            separation = AstronomyMath.separation(moonTopo.equatorial, sunTopo.equatorial),
            sunRadius = sunRadius,
            moonRadius = moonRadius
        )
    }

    /**
     * The eclipse as [coords] sees it, clipped to the part where the sun is actually
     * up — and re-measured inside that part.
     *
     * The clipping is not cosmetic. On 12 August 2026 the sun sets over Milan with
     * the eclipse still running: the geometric last contact is at 19:11 and nobody
     * there will see it. Worse, if greatest eclipse itself fell below the horizon,
     * reporting its magnitude would be describing something the reader cannot look
     * at — so the maximum is taken over the VISIBLE window, which is the honest
     * answer to "how much of the sun will I see covered".
     */
    private fun solarAt(newMoon: Instant, coords: Coordinates): SolarEclipse? {
        val geometric = closestApproach(newMoon) { discs(it, coords).separation }
        if (discs(geometric, coords).magnitude <= 0) return null
        fun inside(at: Instant): Double {
            val g = discs(at, coords)
            return g.sunRadius + g.moonRadius - g.separation
        }
        val first = edge(geometric, forward = false, ::inside) ?: return null
        val last = edge(geometric, forward = true, ::inside) ?: return null
        val daylight = AstronomyEngine.sunAbove(first, last, coords) ?: return null
        val greatest = if (geometric in daylight) {
            geometric
        } else {
            // The sun rose or set through the eclipse: the best moment available is
            // then the edge of the visible window nearest the geometric maximum.
            if (geometric.isBefore(daylight.start)) daylight.start else daylight.endInclusive
        }
        val d = discs(greatest, coords)
        val central = d.separation < abs(d.moonRadius - d.sunRadius)
        return SolarEclipse(
            kind = when {
                central && d.moonRadius >= d.sunRadius -> SolarEclipseKind.TOTAL
                central -> SolarEclipseKind.ANNULAR
                else -> SolarEclipseKind.PARTIAL
            },
            greatest = greatest,
            contacts = daylight,
            magnitude = min(d.magnitude, 1.0),
            obscuration = obscuration(d)
        )
    }

    /**
     * Fraction of the sun's AREA the moon covers: the area of the lens the two disks
     * make, over the sun's own. The number the light in the street follows — a 0.93
     * magnitude eclipse still leaves 8 % of the sun shining, and the afternoon barely
     * notices.
     */
    private fun obscuration(d: Discs): Double {
        val r1 = d.sunRadius
        val r2 = d.moonRadius
        val s = d.separation
        if (s >= r1 + r2) return 0.0
        if (s <= abs(r1 - r2)) return min(1.0, (r2 * r2) / (r1 * r1))
        val a1 = r1 * r1 * acos(((s * s + r1 * r1 - r2 * r2) / (2 * s * r1)).coerceIn(-1.0, 1.0))
        val a2 = r2 * r2 * acos(((s * s + r2 * r2 - r1 * r1) / (2 * s * r2)).coerceIn(-1.0, 1.0))
        val triangle = 0.5 * sqrt(
            max(
                0.0,
                (-s + r1 + r2) * (s + r1 - r2) * (s - r1 + r2) * (s + r1 + r2)
            )
        )
        return (a1 + a2 - triangle) / (Math.PI * r1 * r1)
    }

    // ------------------------------------------------------------ internals

    /**
     * The instant of closest approach within ±6 hours of [around]: a coarse walk to
     * the smallest sample, then a ternary search of the cells either side.
     *
     * Six hours is the bracket because both events here are anchored to a syzygy — a
     * full or new moon — and the geometry's minimum is never more than a few hours
     * from one.
     */
    private fun closestApproach(around: Instant, value: (Instant) -> Double): Instant {
        var best = around.minus(SEARCH_SPAN)
        var bestValue = Double.POSITIVE_INFINITY
        var at = best
        val end = around.plus(SEARCH_SPAN)
        while (at <= end) {
            val v = value(at)
            if (v < bestValue) {
                bestValue = v
                best = at
            }
            at = at.plus(COARSE_STEP)
        }
        var low = best.minus(COARSE_STEP)
        var high = best.plus(COARSE_STEP)
        repeat(40) {
            val third = Duration.between(low, high).dividedBy(3)
            val a = low.plus(third)
            val b = high.minus(third)
            if (value(a) < value(b)) high = b else low = a
        }
        return low.plus(Duration.between(low, high).dividedBy(2))
    }

    /**
     * The instant either side of [greatest] where [inside] changes sign — a contact.
     * Bisected to the second on a function that is monotonic there by construction:
     * the separation grows away from the closest approach.
     */
    private fun edge(
        greatest: Instant,
        forward: Boolean,
        inside: (Instant) -> Double
    ): Instant? {
        val step = if (forward) FINE_STEP else FINE_STEP.negated()
        var previous = greatest
        var at = greatest.plus(step)
        var guard = 0
        while (guard++ < MAX_FINE_STEPS) {
            if (inside(at) <= 0) {
                var lo = previous
                var hi = at
                repeat(30) {
                    val mid = lo.plus(Duration.between(lo, hi).dividedBy(2))
                    if (inside(mid) > 0) lo = mid else hi = mid
                }
                return lo.plus(Duration.between(lo, hi).dividedBy(2))
            }
            previous = at
            at = at.plus(step)
        }
        return null
    }

    /** Chauvenet's 1/50: the earth's atmosphere makes its shadow bigger than its body. */
    private const val SHADOW_ENLARGEMENT = 1.0 + 1.0 / 85.0

    /** The moon's radius over the earth's — the ratio behind its apparent size. */
    private const val MOON_RADII_PER_EARTH_RADIUS = 0.2725

    private val SEARCH_SPAN: Duration = Duration.ofHours(6)
    private val COARSE_STEP: Duration = Duration.ofMinutes(20)
    private val FINE_STEP: Duration = Duration.ofMinutes(5)

    /** Six hours of five-minute steps: longer than any eclipse's half-duration. */
    private const val MAX_FINE_STEPS = 72
}
