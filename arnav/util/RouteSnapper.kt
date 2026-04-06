package com.campus.arnav.util

import android.location.Location
import org.osmdroid.util.GeoPoint
import kotlin.math.*

/**
 * RouteSnapper
 * ─────────────
 * Mirrors exactly what MapViewModel.trimRouteFromCurrentPosition() does on
 * the 2D map, but for the AR layer:
 *
 *   MapViewModel result:   [userGeo,  routePoint[nearestIdx], ..., destination]
 *   RouteSnapper result:   [userGeo,  projectedPoint,         ..., destination]
 *
 * The difference is that MapViewModel snaps to the nearest POINT (vertex),
 * while RouteSnapper snaps to the nearest SEGMENT (perpendicular projection).
 * This is more accurate — the projected point lies exactly on the path even
 * when the user is between two waypoints.
 *
 * What this fixes
 * ────────────────
 *  • The user's effective position is always on the road, so bearingToNode
 *    is computed from the path — not from wherever GPS drifted to off-road.
 *  • The AR arrow aims along the road segment, not diagonally across grass.
 *  • trimPolylineToUser in ARNavigationActivity trims correctly because the
 *    route points now start from the snapped position on the path.
 *
 * Usage
 * ──────
 *   val snapper = RouteSnapper()
 *   snapper.setRoute(routeGeoPoints)           // once, when route is loaded
 *   val result  = snapper.snap(rawGpsLocation)
 *   // result.snappedLocation  → use for all AR bearing math
 *   // result.trimmedPoints    → replace routeGeoPoints for polyline display
 */
class RouteSnapper {

    companion object {
        /**
         * If the raw GPS is farther than this from the route the user is
         * considered off-route and the raw GPS is returned unchanged.
         * 25 m covers normal GPS drift without snapping to the wrong road.
         */
        const val MAX_SNAP_M = 25.0
    }

    data class SnapResult(
        /** Snapped lat/lon — always on the path (or raw GPS if off-route). */
        val snappedLocation: Location,
        /**
         * Rebuilt route point list, same as MapViewModel.trimRouteFromCurrentPosition:
         *   [snappedGeoPoint, nextRoutePoint, ..., destination]
         * Use this to replace routeGeoPoints so the polyline and node arrows
         * both start from the correct on-path position.
         */
        val trimmedPoints: List<GeoPoint>
    )

    private val routePoints = mutableListOf<GeoPoint>()

    // Flat-earth anchor for Cartesian projection
    private var anchorLat = 0.0
    private var anchorLon = 0.0

    // ── Public API ────────────────────────────────────────────────────────────

    fun setRoute(points: List<GeoPoint>) {
        routePoints.clear()
        routePoints.addAll(points)
        if (points.isNotEmpty()) {
            anchorLat = points[0].latitude
            anchorLon = points[0].longitude
        }
    }

    /**
     * Project [raw] GPS onto the nearest route segment and rebuild the
     * trimmed point list exactly as MapViewModel does, but using the
     * perpendicular projection point instead of the nearest vertex.
     */
    fun snap(raw: Location): SnapResult {
        // No route or single point — return raw unchanged with original points
        if (routePoints.size < 2) {
            return SnapResult(raw, routePoints.toList())
        }

        val px = metersEast(raw.latitude,  raw.longitude)
        val py = metersNorth(raw.latitude)

        var bestDist      = Double.MAX_VALUE
        var bestSegIdx    = 0          // index of segment start point
        var bestT         = 0.0        // projection parameter [0,1] on that segment
        var bestLat       = raw.latitude
        var bestLon       = raw.longitude

        for (i in 0 until routePoints.size - 1) {
            val a = routePoints[i]
            val b = routePoints[i + 1]

            val ax = metersEast(a.latitude,  a.longitude)
            val ay = metersNorth(a.latitude)
            val bx = metersEast(b.latitude,  b.longitude)
            val by = metersNorth(b.latitude)

            val abx = bx - ax
            val aby = by - ay
            val ab2 = abx * abx + aby * aby
            if (ab2 == 0.0) continue

            // Scalar projection of A→P onto A→B, clamped to [0,1]
            val t = ((px - ax) * abx + (py - ay) * aby) / ab2
            val tc = t.coerceIn(0.0, 1.0)

            // Closest point on this segment
            val cx = ax + tc * abx
            val cy = ay + tc * aby

            val dx = px - cx
            val dy = py - cy
            val dist = sqrt(dx * dx + dy * dy)

            if (dist < bestDist) {
                bestDist   = dist
                bestSegIdx = i
                bestT      = tc
                bestLat    = anchorLat + cy / METERS_PER_DEG_LAT
                bestLon    = anchorLon + cx / metersPerDegLon(anchorLat)
            }
        }

        // Off-route: return raw GPS + full remaining points
        if (bestDist > MAX_SNAP_M) {
            android.util.Log.d("RouteSnapper",
                "Off-route %.1fm — raw GPS returned".format(bestDist))
            return SnapResult(raw, routePoints.toList())
        }

        android.util.Log.d("RouteSnapper",
            "Snapped %.2fm from path, seg=%d t=%.2f".format(bestDist, bestSegIdx, bestT))

        // Build snapped Location (keeps accuracy/time/provider from original)
        val snapped = Location(raw).apply {
            latitude  = bestLat
            longitude = bestLon
        }

        // ── Rebuild trimmed point list — mirrors MapViewModel exactly ─────────
        //
        // MapViewModel:
        //   add(userGeo)
        //   addAll(points.subList(nearestIdx.coerceAtLeast(1), points.size))
        //
        // We do the same but insert the PROJECTED point on the segment instead
        // of jumping to the nearest vertex.  This gives a smooth cut-in:
        //
        //   [snappedGeoPoint, routePoints[bestSegIdx+1], ..., destination]
        //
        // If t==1.0 the projection landed on point B (end of segment), so we
        // start from bestSegIdx+1 directly — same result either way.
        val snappedGeo = GeoPoint(bestLat, bestLon)
        val nextIdx    = (bestSegIdx + 1).coerceAtMost(routePoints.size - 1)

        val trimmed = buildList {
            add(snappedGeo)                                      // user on path
            addAll(routePoints.subList(nextIdx, routePoints.size)) // rest of route
        }

        return SnapResult(snapped, trimmed)
    }

    // ── Flat-earth helpers (< 0.1 % error over campus distances) ─────────────

    private val METERS_PER_DEG_LAT = 111_320.0

    private fun metersPerDegLon(lat: Double) =
        111_320.0 * cos(Math.toRadians(lat))

    private fun metersNorth(lat: Double) =
        (lat - anchorLat) * METERS_PER_DEG_LAT

    private fun metersEast(lat: Double, lon: Double) =
        (lon - anchorLon) * metersPerDegLon(lat)
}