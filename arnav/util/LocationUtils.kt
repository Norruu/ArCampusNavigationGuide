package com.campus.arnav.util

import com.campus.arnav.data.model.CampusLocation
import org.osmdroid.util.GeoPoint
import kotlin.math.*

/**
 * Single source of truth for all geographic math across the app.
 *
 * Every distance, projection, and bearing calculation flows through here.
 * No other file should re-implement these formulas.
 */
object LocationUtils {

    private const val EARTH_RADIUS = 6_371_000.0 // metres

    // ── Distance ─────────────────────────────────────────────────────────────

    fun haversineDistance(p1: CampusLocation, p2: CampusLocation): Double =
        haversine(p1.latitude, p1.longitude, p2.latitude, p2.longitude)

    fun haversineDistance(p1: GeoPoint, p2: GeoPoint): Double =
        haversine(p1.latitude, p1.longitude, p2.latitude, p2.longitude)

    /** Mixed-type overload — avoids manual conversion at every call site. */
    fun haversineDistance(p1: CampusLocation, p2: GeoPoint): Double =
        haversine(p1.latitude, p1.longitude, p2.latitude, p2.longitude)

    fun haversineDistance(p1: GeoPoint, p2: CampusLocation): Double =
        haversine(p1.latitude, p1.longitude, p2.latitude, p2.longitude)

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat2 - lat1).toRadians()
        val dLon = (lon2 - lon1).toRadians()
        val a = sin(dLat / 2).pow(2) +
                cos(lat1.toRadians()) * cos(lat2.toRadians()) * sin(dLon / 2).pow(2)
        return EARTH_RADIUS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    // ── Projection ────────────────────────────────────────────────────────────

    /**
     * Projects point [p] onto segment [a]→[b] using the flat-earth (equirectangular)
     * approximation.  Accurate to < 0.1 m at campus scale (< 1 km).
     *
     * This is the canonical implementation.  All other classes must call this;
     * do not copy-paste the math elsewhere.
     *
     * @return The closest point on the segment, never outside [a, b].
     */
    fun projectPointOnSegment(p: CampusLocation, a: CampusLocation, b: CampusLocation): CampusLocation {
        val (projLat, projLon) = projectLatLon(
            p.latitude, p.longitude,
            a.latitude, a.longitude,
            b.latitude, b.longitude
        )
        return CampusLocation("temp_snap", projLat, projLon, p.altitude)
    }

    /** GeoPoint variant — used by CampusPathfinding visual helpers. */
    fun projectPointOnSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): GeoPoint {
        val (projLat, projLon) = projectLatLon(
            p.latitude, p.longitude,
            a.latitude, a.longitude,
            b.latitude, b.longitude
        )
        return GeoPoint(projLat, projLon)
    }

    /**
     * Core projection math.  Returns (projectedLat, projectedLon).
     * Uses a cosLat-scaled flat-earth frame centred on point A.
     */
    private fun projectLatLon(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double
    ): Pair<Double, Double> {
        val cosLat = cos(aLat.toRadians())

        // Local metre coordinates (A is origin)
        val px = (pLon - aLon) * cosLat
        val py =  pLat - aLat
        val bx = (bLon - aLon) * cosLat
        val by =  bLat - aLat

        val lenSq = bx * bx + by * by
        if (lenSq == 0.0) return Pair(aLat, aLon)   // degenerate segment

        val t = ((px * bx + py * by) / lenSq).coerceIn(0.0, 1.0)

        return Pair(aLat + t * (bLat - aLat), aLon + t * (bLon - aLon))
    }

    // ── Bearing ───────────────────────────────────────────────────────────────

    fun bearing(start: CampusLocation, end: CampusLocation): Double {
        val lat1 = start.latitude.toRadians()
        val lat2 = end.latitude.toRadians()
        val dLon = (end.longitude - start.longitude).toRadians()
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return Math.toDegrees(atan2(y, x))
    }

    // ── Conversions ───────────────────────────────────────────────────────────

    fun CampusLocation.toGeoPoint() = GeoPoint(latitude, longitude, altitude)
    fun GeoPoint.toCampusLocation(id: String = "converted") =
        CampusLocation(id, latitude, longitude, altitude)

    private fun Double.toRadians() = Math.toRadians(this)
}