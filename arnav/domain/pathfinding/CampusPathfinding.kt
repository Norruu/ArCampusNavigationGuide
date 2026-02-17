package com.campus.arnav.domain.pathfinding

import com.campus.arnav.data.model.*
import com.campus.arnav.ui.map.components.CampusPaths
import org.osmdroid.util.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Integrated Campus Pathfinding System
 *
 * This class bridges the CampusPaths overlay (visual roads on map) with the
 * PathfindingEngine (A* algorithm) to create a complete navigation system
 * that follows actual campus roads.
 */
@Singleton
class CampusPathfinding @Inject constructor(
    private val pathfindingEngine: PathfindingEngine
) {

    private data class NodeInfo(
        val nodeId: String,
        val location: GeoPoint,
        val pathId: String,
        val pointIndex: Int
    )

    /**
     * Initialize pathfinding using CampusPaths data
     */
    suspend fun initializeFromCampusPaths() {
        val graph = buildGraphFromCampusPaths()
        pathfindingEngine.initializeWithGraph(graph)
        android.util.Log.d("CampusPathfinding", "Graph initialized with ${CampusPaths.campusPaths.size} paths")
    }

    /**
     * Build a navigation graph from CampusPaths with proper intersection connections
     */
    private fun buildGraphFromCampusPaths(): CampusGraph {
        val builder = CampusGraphBuilder()
        val allNodes = mutableListOf<NodeInfo>()

        android.util.Log.d("CampusPathfinding", "Building graph from ${CampusPaths.campusPaths.size} paths")

        // STEP 1: Create all nodes from all paths
        CampusPaths.campusPaths.forEach { path ->
            path.points.forEachIndexed { index, geoPoint ->
                val nodeId = "${path.id}_point_$index"

                val location = CampusLocation(
                    id = nodeId,
                    latitude = geoPoint.latitude,
                    longitude = geoPoint.longitude,
                    altitude = geoPoint.altitude
                )

                // Add node to graph
                builder.addPathNode(nodeId, location)

                // Track node info for intersection detection
                allNodes.add(NodeInfo(nodeId, geoPoint, path.id, index))
            }
        }

        android.util.Log.d("CampusPathfinding", "Created ${allNodes.size} nodes")

        // STEP 2: Connect consecutive points within each path
        CampusPaths.campusPaths.forEach { path ->
            val nodeIds = path.points.indices.map { "${path.id}_point_$it" }

            for (i in 0 until nodeIds.lastIndex) {
                val isAccessible = when (path.type) {
                    CampusPaths.PathType.MAIN_ROAD -> true
                    CampusPaths.PathType.WALKWAY -> true
                }
                builder.connectPath(nodeIds[i], nodeIds[i + 1], isAccessible)
            }
        }

        // STEP 3: Connect paths at intersection points
        connectIntersections(builder, allNodes)

        val graph = builder.build()
        android.util.Log.d("CampusPathfinding", "Graph built successfully")

        return graph
    }

    /**
     * Find and connect paths that share nearby points (intersections)
     */
    private fun connectIntersections(builder: CampusGraphBuilder, allNodes: List<NodeInfo>) {
        val connectionThreshold = 15.0 // meters - points within this distance are considered connected
        var connectionsCount = 0

        // For each node, find other nodes from different paths that are very close
        for (i in allNodes.indices) {
            val node1 = allNodes[i]

            for (j in i + 1 until allNodes.size) {
                val node2 = allNodes[j]

                // Skip if same path
                if (node1.pathId == node2.pathId) continue

                // Calculate distance between nodes
                val distance = calculateDistance(node1.location, node2.location)

                // If nodes are very close, they represent an intersection
                if (distance < connectionThreshold) {
                    builder.connectPath(node1.nodeId, node2.nodeId, isAccessible = true)
                    connectionsCount++

                    android.util.Log.d(
                        "CampusPathfinding",
                        "Connected ${node1.pathId} to ${node2.pathId} (distance: ${distance.toInt()}m)"
                    )
                }
            }
        }

        android.util.Log.d("CampusPathfinding", "Created $connectionsCount intersection connections")
    }

    /**
     * Find route between two GeoPoints using campus paths
     */
    suspend fun findRoute(
        start: GeoPoint,
        end: GeoPoint,
        options: RouteOptions = RouteOptions()
    ): RouteResult {
        android.util.Log.d("CampusPathfinding", "Finding route from (${start.latitude}, ${start.longitude}) to (${end.latitude}, ${end.longitude})")

        val startLocation = geoPointToCampusLocation(start, "start")
        val endLocation = geoPointToCampusLocation(end, "end")

        val result = pathfindingEngine.findRoute(startLocation, endLocation, options)

        when (result) {
            is RouteResult.Success -> {
                android.util.Log.d("CampusPathfinding", "Route found with ${result.route.waypoints.size} waypoints")
            }
            is RouteResult.NoRouteFound -> {
                android.util.Log.w("CampusPathfinding", "No route found: ${result.message}")
            }
            is RouteResult.Error -> {
                android.util.Log.e("CampusPathfinding", "Route error: ${result.message}")
            }
        }

        return result
    }

    /**
     * Find route with route overlay visualization
     */
    suspend fun findRouteWithPath(
        start: GeoPoint,
        end: GeoPoint,
        options: RouteOptions = RouteOptions()
    ): Pair<RouteResult, List<GeoPoint>?> {
        val result = findRoute(start, end, options)

        val pathPoints = when (result) {
            is RouteResult.Success -> {
                // Convert route waypoints to GeoPoints for overlay
                val points = result.route.waypoints.map { waypoint ->
                    GeoPoint(
                        waypoint.location.latitude,
                        waypoint.location.longitude,
                        waypoint.location.altitude
                    )
                }
                android.util.Log.d("CampusPathfinding", "Generated ${points.size} path points for visualization")
                points
            }
            else -> null
        }

        return Pair(result, pathPoints)
    }

    /**
     * Find nearest campus path to a location
     */
    fun findNearestPath(location: GeoPoint): CampusPaths.Path? {
        var nearestPath: CampusPaths.Path? = null
        var minDistance = Double.MAX_VALUE

        CampusPaths.campusPaths.forEach { path ->
            path.points.forEach { point ->
                val distance = calculateDistance(location, point)
                if (distance < minDistance) {
                    minDistance = distance
                    nearestPath = path
                }
            }
        }

        return nearestPath
    }

    /**
     * Get walking directions between two points
     */
    suspend fun getDirections(
        start: GeoPoint,
        end: GeoPoint,
        options: RouteOptions = RouteOptions()
    ): List<NavigationStep>? {
        val result = findRoute(start, end, options)
        return when (result) {
            is RouteResult.Success -> result.route.steps
            else -> null
        }
    }

    /**
     * Calculate estimated walking time
     */
    suspend fun estimateWalkingTime(
        start: GeoPoint,
        end: GeoPoint,
        walkingSpeed: Double = 1.4 // m/s (about 5 km/h)
    ): Long? {
        val startLocation = geoPointToCampusLocation(start, "start")
        val endLocation = geoPointToCampusLocation(end, "end")

        return pathfindingEngine.estimateWalkingTime(startLocation, endLocation, walkingSpeed)
    }

    /**
     * Check if route exists between two points
     */
    suspend fun hasRoute(start: GeoPoint, end: GeoPoint): Boolean {
        val result = findRoute(start, end)
        return result is RouteResult.Success
    }

    /**
     * Get multiple alternative routes
     */
    suspend fun getAlternativeRoutes(
        start: GeoPoint,
        end: GeoPoint,
        maxAlternatives: Int = 3
    ): List<Route> {
        val startLocation = geoPointToCampusLocation(start, "start")
        val endLocation = geoPointToCampusLocation(end, "end")

        return pathfindingEngine.findAlternativeRoutes(startLocation, endLocation, maxAlternatives)
    }

    /**
     * Get the actual path points for a route (for drawing on map)
     */
    fun getRoutePathPoints(route: Route): List<GeoPoint> {
        return route.waypoints.map { waypoint ->
            GeoPoint(
                waypoint.location.latitude,
                waypoint.location.longitude,
                waypoint.location.altitude
            )
        }
    }

    /**
     * Snap a point to the nearest path
     */
    fun snapToPath(location: GeoPoint, maxDistance: Double = 50.0): GeoPoint? {
        var nearestPoint: GeoPoint? = null
        var minDistance = Double.MAX_VALUE

        CampusPaths.campusPaths.forEach { path ->
            path.points.forEach { point ->
                val distance = calculateDistance(location, point)
                if (distance < minDistance && distance <= maxDistance) {
                    minDistance = distance
                    nearestPoint = point
                }
            }
        }

        return nearestPoint
    }

    /**
     * Find the path type at a location
     */
    fun getPathTypeAtLocation(location: GeoPoint): CampusPaths.PathType? {
        val nearestPath = findNearestPath(location)
        return nearestPath?.type
    }

    // ============== HELPER METHODS ==============

    private fun geoPointToCampusLocation(geoPoint: GeoPoint, id: String): CampusLocation {
        return CampusLocation(
            id = id,
            latitude = geoPoint.latitude,
            longitude = geoPoint.longitude,
            altitude = geoPoint.altitude
        )
    }

    private fun calculateDistance(p1: GeoPoint, p2: GeoPoint): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)

        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(p1.latitude)) *
                kotlin.math.cos(Math.toRadians(p2.latitude)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)

        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return earthRadius * c
    }
}

/**
 * Extension function to convert Route to GeoPoints for map overlay
 */
fun Route.toGeoPoints(): List<GeoPoint> {
    return this.waypoints.map { waypoint ->
        GeoPoint(
            waypoint.location.latitude,
            waypoint.location.longitude,
            waypoint.location.altitude
        )
    }
}