package com.campus.arnav.ui.map.components

import org.osmdroid.util.GeoPoint
import kotlin.math.sqrt

object CampusPaths {

    data class Path(
        val id: String,
        val name: String,
        val points: List<GeoPoint>,
        val type: PathType,
        val bidirectional: Boolean = true
    )

    enum class PathType {
        MAIN_ROAD,
        WALKWAY
    }

    // DEFINING CAMPUS PATHS - Update these coordinates to match your actual campus
    val campusPaths = listOf(

        // Main entrance to Admin Building End Hallway
        Path(
            id = "mg-ehw_ad",
            name = "maingate-ehw_admin",
            points = listOf(
                GeoPoint(9.847753, 122.887348),   // Main gate
                GeoPoint(9.852341, 122.890256)    // End Hallway Admin Building
            ),
            type = PathType.MAIN_ROAD
        ),

        Path(
            id = "BG-to-Gym",
            name = "Backofgym-Gym",
            points = listOf(
                GeoPoint(9.8535565134402, 122.88714843882991),   // Back of Gym
                GeoPoint(9.85313539824331, 122.88773534398655)    // Gym road
            ),
            type = PathType.MAIN_ROAD
        ),

        // Between Admin Building and Gym Intersection
        Path(
            id = "between-mg-ehw_ad",
            name = "between-maingate-ehw_admin",
            points = listOf(
                GeoPoint(9.852680, 122.889757),  // Between End Hallway Admin Building
                GeoPoint(9.853487375231582, 122.89014312114),  // Front of Museum
            ),
            type = PathType.MAIN_ROAD
        ),

        // Admin Building to Gym Intersection
        Path(
            id = "ehw_admin-Igym",
            name = "admin-igym",
            points = listOf(
                GeoPoint(9.852341, 122.890256),   // End Hallway Admin Building
                GeoPoint(9.853662, 122.888087)    // Gym Intersection
            ),
            type = PathType.MAIN_ROAD
        ),

        // Gym Intersection to Engineering Building (Lukay)
        Path(
            id = "Igym-lukay_coe",
            name = "igym-lukay_coe",
            points = listOf(
                GeoPoint(9.853662, 122.888087),  // Gym Intersection
                GeoPoint(9.852121, 122.887101),  // Bankal Drom
                GeoPoint(9.850483, 122.885598)   // Lukay Engineering Building
            ),
            type = PathType.MAIN_ROAD
        ),

        // Gym Intersection to IT Building
        Path(
            id = "Igym-IT",
            name = "igym-IT",
            points = listOf(
                GeoPoint(9.853662, 122.888087),  // Gym Intersection
                GeoPoint(9.854445, 122.888603)   // IT Building
            ),
            type = PathType.MAIN_ROAD
        ),

        // Lukay Engineering Building to COE Building
        Path(
            id = "lukay_coe-HW",
            name = "lukay_coe-COE",
            points = listOf(
                GeoPoint(9.850483, 122.885598),   // Lukay Engineering Building
                GeoPoint(9.848964238285093, 122.88812607495888)    // Halllway
            ),
            type = PathType.MAIN_ROAD
        ),

        // Lukay to Building H (Walkway)
        Path(
            id = "lukay-to-bh",
            name = "lukay-to-bh",
            points = listOf(
                GeoPoint(9.852204, 122.886163),
                GeoPoint(9.852121, 122.886243)
            ),
            type = PathType.WALKWAY
        ),

        //Marzland to pool
        Path(
            id = "Marzland-to-pool",
            name = "Marzland-to-pool",
            points = listOf(
                GeoPoint(9.855337262336496, 122.88695889997297),
                GeoPoint(9.8529774944415, 122.89099593664268)
            ),
            type = PathType.WALKWAY
        ),

        Path(
            id = "Cafeteria-to-Caf",
            name = "Cafeteria-to-Caf",
            points = listOf(
                GeoPoint(9.853232820539615, 122.89054821330097),
                GeoPoint(9.854420740778087, 122.89122761982317),
                GeoPoint(9.854581577074951, 122.89077331332565)
            ),
            type = PathType.WALKWAY
        )
    )





    /**
     * Find path between two locations using campus paths
     */
    fun findPath(start: GeoPoint, end: GeoPoint): List<GeoPoint>? {
        // Find paths near start and end
        val startPaths = findNearbyPaths(start, 300.0)  // 300m search radius
        val endPaths = findNearbyPaths(end, 300.0)

        android.util.Log.d("CampusPaths", "Start near ${startPaths.size} paths, End near ${endPaths.size} paths")

        if (startPaths.isEmpty() || endPaths.isEmpty()) {
            android.util.Log.d("CampusPaths", "No paths found nearby - will use straight line")
            return null
        }

        // Build route through paths
        val route = buildRoute(start, end, startPaths, endPaths)

        if (route != null && route.size >= 2) {
            android.util.Log.d("CampusPaths", "Route built with ${route.size} points")
            return route
        }

        android.util.Log.d("CampusPaths", "Could not build route through paths")
        return null
    }

    /**
     * Find all paths within radius of a point
     */
    private fun findNearbyPaths(point: GeoPoint, radiusMeters: Double): List<PathWithDistance> {
        val nearbyPaths = mutableListOf<PathWithDistance>()

        campusPaths.forEach { path ->
            // Find closest point on this path
            val closestPoint = path.points.minByOrNull { pathPoint ->
                calculateDistance(point, pathPoint)
            }

            closestPoint?.let { closest ->
                val distance = calculateDistance(point, closest)
                if (distance <= radiusMeters) {
                    val pointIndex = path.points.indexOf(closest)
                    nearbyPaths.add(PathWithDistance(path, closest, pointIndex, distance))
                }
            }
        }

        return nearbyPaths.sortedBy { it.distance }
    }

    /**
     * Build route connecting multiple paths
     */
    private fun buildRoute(
        start: GeoPoint,
        end: GeoPoint,
        startPaths: List<PathWithDistance>,
        endPaths: List<PathWithDistance>
    ): List<GeoPoint>? {

        // If same path contains both start and end, use it directly
        val commonPath = startPaths.find { sp ->
            endPaths.any { ep -> ep.path.id == sp.path.id }
        }

        if (commonPath != null) {
            return buildSinglePathRoute(start, end, commonPath, endPaths)
        }

        // Otherwise, connect multiple paths
        return buildMultiPathRoute(start, end, startPaths, endPaths)
    }

    /**
     * Build route along a single path
     */
    private fun buildSinglePathRoute(
        start: GeoPoint,
        end: GeoPoint,
        startPathInfo: PathWithDistance,
        endPaths: List<PathWithDistance>
    ): List<GeoPoint> {

        val path = startPathInfo.path
        val endPathInfo = endPaths.find { it.path.id == path.id } ?: return emptyList()

        val startIdx = startPathInfo.pointIndex
        val endIdx = endPathInfo.pointIndex

        val route = mutableListOf<GeoPoint>()
        route.add(start)

        // Add path points between start and end
        if (startIdx <= endIdx) {
            // Forward direction
            route.addAll(path.points.subList(startIdx, endIdx + 1))
        } else {
            // Reverse direction (if bidirectional)
            if (path.bidirectional) {
                route.addAll(path.points.subList(endIdx, startIdx + 1).reversed())
            } else {
                // Can't go backwards on one-way path
                return emptyList()
            }
        }

        route.add(end)
        return route
    }

    /**
     * Build route connecting multiple paths
     */
    private fun buildMultiPathRoute(
        start: GeoPoint,
        end: GeoPoint,
        startPaths: List<PathWithDistance>,
        endPaths: List<PathWithDistance>
    ): List<GeoPoint>? {

        // Simple approach: Use closest start path and closest end path
        val startPath = startPaths.firstOrNull() ?: return null
        val endPath = endPaths.firstOrNull() ?: return null

        val route = mutableListOf<GeoPoint>()

        // Start point to first path
        route.add(start)

        // Walk along start path to its end
        val startIdx = startPath.pointIndex
        if (startIdx < startPath.path.points.size - 1) {
            route.addAll(startPath.path.points.subList(startIdx, startPath.path.points.size))
        }

        // Connect to end path
        val startPathEnd = startPath.path.points.last()
        val endPathStart = endPath.closestPoint

        // Add connection point if paths don't connect directly
        if (calculateDistance(startPathEnd, endPathStart) > 10.0) {
            route.add(endPathStart)
        }

        // Walk along end path to destination
        val endIdx = endPath.pointIndex
        if (endIdx > 0) {
            route.addAll(endPath.path.points.subList(0, endIdx + 1))
        }

        // End point
        route.add(end)

        // Remove duplicates
        return route.distinct()
    }

    /**
     * Get all connection points between paths
     */
    fun getPathConnections(): List<PathConnection> {
        val connections = mutableListOf<PathConnection>()

        for (i in campusPaths.indices) {
            for (j in i + 1 until campusPaths.size) {
                val path1 = campusPaths[i]
                val path2 = campusPaths[j]

                // Check if paths connect (end points close together)
                val path1End = path1.points.last()
                val path2Start = path2.points.first()
                val path2End = path2.points.last()
                val path1Start = path1.points.first()

                // Check various connection possibilities
                if (calculateDistance(path1End, path2Start) < 150.0) {
                    connections.add(PathConnection(path1.id, path2.id, path1End, "end-to-start"))
                }
                if (calculateDistance(path1End, path2End) < 150.0) {
                    connections.add(PathConnection(path1.id, path2.id, path1End, "end-to-end"))
                }
                if (calculateDistance(path1Start, path2Start) < 150.0) {
                    connections.add(PathConnection(path1.id, path2.id, path1Start, "start-to-start"))
                }
            }
        }

        return connections
    }

    /**
     * Calculate distance between two points (Haversine formula)
     */
    private fun calculateDistance(p1: GeoPoint, p2: GeoPoint): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)

        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(p1.latitude)) *
                kotlin.math.cos(Math.toRadians(p2.latitude)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)

        val c = 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    data class PathWithDistance(
        val path: Path,
        val closestPoint: GeoPoint,
        val pointIndex: Int,
        val distance: Double
    )

    data class PathConnection(
        val path1Id: String,
        val path2Id: String,
        val connectionPoint: GeoPoint,
        val connectionType: String
    )
}