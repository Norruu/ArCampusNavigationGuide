package com.campus.arnav.domain.pathfinding

import com.campus.arnav.data.model.*
import com.campus.arnav.ui.map.components.CampusPaths
import com.campus.arnav.util.LocationUtils
import com.campus.arnav.util.LocationUtils.toGeoPoint
import com.campus.arnav.util.LocationUtils.toCampusLocation
import org.osmdroid.util.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Integrated Campus Pathfinding System
 *
 * Bridges the CampusPaths overlay (visual roads) with PathfindingEngine (A*)
 * to produce routes that follow actual campus roads.
 *
 * Edge-snapping lives exclusively in [PathfindingEngine.findRoute].
 * This class does not duplicate that logic.
 */
@Singleton
class CampusPathfinding @Inject constructor(
    private val pathfindingEngine: PathfindingEngine
) {

    private data class NodeInfo(
        val nodeId: String,
        val location: GeoPoint,
        val pathId: String
    )

    // ── Initialisation ────────────────────────────────────────────────────────

    suspend fun initializeFromCampusPaths() {
        val graph = buildGraphFromCampusPaths()
        pathfindingEngine.initializeWithGraph(graph)
        android.util.Log.d("CampusPathfinding", "Graph initialised with ${CampusPaths.campusPaths.size} paths")
    }

    private fun buildGraphFromCampusPaths(): CampusGraph {
        val builder   = CampusGraphBuilder()
        val allNodes  = mutableListOf<NodeInfo>()

        // STEP 1: Create nodes from every path point
        CampusPaths.campusPaths.forEach { path ->
            path.points.forEachIndexed { index, geoPoint ->
                val nodeId = "${path.id}_point_$index"
                val location = CampusLocation(
                    id        = nodeId,
                    latitude  = geoPoint.latitude,
                    longitude = geoPoint.longitude,
                    altitude  = geoPoint.altitude
                )
                builder.addPathNode(nodeId, location)
                allNodes.add(NodeInfo(nodeId, geoPoint, path.id))
            }
        }

        android.util.Log.d("CampusPathfinding", "Created ${allNodes.size} nodes")

        // STEP 2: Connect consecutive points within each path
        CampusPaths.campusPaths.forEach { path ->
            val nodeIds = path.points.indices.map { "${path.id}_point_$it" }
            val accessible = when (path.type) {
                CampusPaths.PathType.MAIN_ROAD -> true
                CampusPaths.PathType.WALKWAY   -> true
            }
            for (i in 0 until nodeIds.lastIndex) {
                builder.connectPath(nodeIds[i], nodeIds[i + 1], accessible)
            }
        }

        // STEP 3: Connect paths that share nearby points (intersections)
        connectIntersections(builder, allNodes)

        return builder.build().also {
            android.util.Log.d("CampusPathfinding", "Graph built successfully")
        }
    }

    /**
     * Connect nodes from *different* paths that are within [thresholdMetres] of
     * each other — these represent road intersections.
     */
    private fun connectIntersections(
        builder: CampusGraphBuilder,
        allNodes: List<NodeInfo>,
        thresholdMetres: Double = 15.0
    ) {
        var count = 0
        for (i in allNodes.indices) {
            val n1 = allNodes[i]
            for (j in i + 1 until allNodes.size) {
                val n2 = allNodes[j]
                if (n1.pathId == n2.pathId) continue
                if (LocationUtils.haversineDistance(n1.location, n2.location) < thresholdMetres) {
                    builder.connectPath(n1.nodeId, n2.nodeId, isAccessible = true)
                    count++
                    android.util.Log.d(
                        "CampusPathfinding",
                        "Intersection: ${n1.pathId} ↔ ${n2.pathId}"
                    )
                }
            }
        }
        android.util.Log.d("CampusPathfinding", "Created $count intersection connections")
    }

    // ── Public Routing API ────────────────────────────────────────────────────

    suspend fun findRoute(
        start: GeoPoint,
        end: GeoPoint,
        markerDestination: GeoPoint = end,   // building centre for the visual end connector
        options: RouteOptions = RouteOptions()
    ): RouteResult {
        android.util.Log.d(
            "CampusPathfinding",
            "Finding route (${start.latitude}, ${start.longitude}) → (${end.latitude}, ${end.longitude})"
        )
        val result = pathfindingEngine.findRoute(
            start.toCampusLocation("start"),
            end.toCampusLocation("end"),
            markerDestination.toCampusLocation("marker"),
            options
        )
        when (result) {
            is RouteResult.Success      -> android.util.Log.d("CampusPathfinding", "Route found: ${result.route.waypoints.size} waypoints")
            is RouteResult.NoRouteFound -> android.util.Log.w("CampusPathfinding", "No route: ${result.message}")
            is RouteResult.Error        -> android.util.Log.e("CampusPathfinding", "Error: ${result.message}")
        }
        return result
    }

    suspend fun findRouteWithPath(
        start: GeoPoint,
        end: GeoPoint,
        options: RouteOptions = RouteOptions()
    ): Pair<RouteResult, List<GeoPoint>?> {
        // markerDestination defaults to end when not provided separately
        val result = findRoute(start, end, markerDestination = end, options = options)
        val points = (result as? RouteResult.Success)?.route?.toGeoPoints()
        return Pair(result, points)
    }

    suspend fun getDirections(
        start: GeoPoint,
        end: GeoPoint,
        options: RouteOptions = RouteOptions()
    ): List<NavigationStep>? =
        (findRoute(start, end, markerDestination = end, options = options) as? RouteResult.Success)?.route?.steps

    suspend fun estimateWalkingTime(
        start: GeoPoint,
        end: GeoPoint,
        walkingSpeed: Double = 1.4
    ): Long? = pathfindingEngine.estimateWalkingTime(
        start.toCampusLocation("start"),
        end.toCampusLocation("end"),
        walkingSpeed
    )

    suspend fun hasRoute(start: GeoPoint, end: GeoPoint): Boolean =
        findRoute(start, end) is RouteResult.Success

    suspend fun getAlternativeRoutes(
        start: GeoPoint,
        end: GeoPoint,
        maxAlternatives: Int = 3
    ): List<Route> = pathfindingEngine.findAlternativeRoutes(
        start.toCampusLocation("start"),
        end.toCampusLocation("end"),
        maxAlternatives
    )

    // ── Visual / Map Helpers ──────────────────────────────────────────────────

    fun getRoutePathPoints(route: Route): List<GeoPoint> = route.toGeoPoints()

    fun findNearestPath(location: GeoPoint): CampusPaths.Path? {
        var nearest: CampusPaths.Path? = null
        var minDist = Double.MAX_VALUE
        CampusPaths.campusPaths.forEach { path ->
            path.points.forEach { point ->
                val d = LocationUtils.haversineDistance(location, point)
                if (d < minDist) { minDist = d; nearest = path }
            }
        }
        return nearest
    }

    fun getPathTypeAtLocation(location: GeoPoint): CampusPaths.PathType? =
        findNearestPath(location)?.type

    /**
     * Snaps [location] to the nearest point on any road segment (not just nodes).
     * Used for UI visual helpers only — routing uses the engine's internal snap.
     *
     * Fixed: was previously passing pts[i] instead of [location] as the point
     * to project, causing it to always return the segment start node.
     */
    fun snapToNearestSegmentPoint(location: GeoPoint, maxDistance: Double = 80.0): GeoPoint? {
        var best: GeoPoint? = null
        var bestDist = Double.MAX_VALUE
        CampusPaths.campusPaths.forEach { path ->
            val pts = path.points
            for (i in 0 until pts.lastIndex) {
                // FIX: project 'location' (not pts[i]) onto the segment
                val proj = LocationUtils.projectPointOnSegment(
                    location.toCampusLocation("snap"),
                    pts[i].toCampusLocation("a"),
                    pts[i + 1].toCampusLocation("b")
                ).toGeoPoint()
                val d = LocationUtils.haversineDistance(location, proj)
                if (d < bestDist && d <= maxDistance) { bestDist = d; best = proj }
            }
        }
        return best
    }

    /** Legacy node-only snap — kept for backward compatibility. */
    fun snapToPath(location: GeoPoint, maxDistance: Double = 50.0): GeoPoint? {
        var nearest: GeoPoint? = null
        var minDist = Double.MAX_VALUE
        CampusPaths.campusPaths.forEach { path ->
            path.points.forEach { point ->
                val d = LocationUtils.haversineDistance(location, point)
                if (d < minDist && d <= maxDistance) { minDist = d; nearest = point }
            }
        }
        return nearest
    }
}

/** Extension: convert a [Route]'s waypoints to a [GeoPoint] list for map overlays. */
fun Route.toGeoPoints(): List<GeoPoint> =
    waypoints.map { GeoPoint(it.location.latitude, it.location.longitude, it.location.altitude) }