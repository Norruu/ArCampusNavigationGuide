package com.campus.arnav.domain.pathfinding

import android.content.Context
import android.util.Log
import com.campus.arnav.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.util.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class HybridCampusPathfinding @Inject constructor(
    private val pathfindingEngine: PathfindingEngine,
    @ApplicationContext private val context: Context
) {

    private val roadManager: RoadManager by lazy {
        OSRMRoadManager(context, "CampusARNav").apply {
            setMean(OSRMRoadManager.MEAN_BY_FOOT)
        }
    }

    // --- CAMPUS BOUNDING BOX ---
    // User is considered "Inside" if they are within these coordinates
    private val CAMPUS_NORTH = 9.857365352521331
    private val CAMPUS_SOUTH = 9.843818207123961
    private val CAMPUS_EAST = 122.89307299341952
    private val CAMPUS_WEST = 122.88305672555643

    suspend fun findRoute(
        start: GeoPoint,
        end: GeoPoint,
        options: RouteOptions = RouteOptions()
    ): RouteResult = withContext(Dispatchers.IO) {

        // 1. Check if Start/End are physically inside the campus grounds
        val startInside = isInsideCampus(start)
        val endInside = isInsideCampus(end)

        Log.d("HybridNav", "Context: StartInside=$startInside, EndInside=$endInside")

        // --- FIXED: ADDED FALLBACK LOGIC ---
        return@withContext when {
            // CASE A: INTERNAL NAVIGATION (Room to Room / Building to Building)
            startInside && endInside -> {
                Log.d("HybridNav", "Strategy: Pure Internal")
                val internalResult = findPureCampusRoute(start, end, options)

                // If internal graph fails, fallback to OSM
                if (internalResult is RouteResult.Success) {
                    internalResult
                } else {
                    Log.w("HybridNav", "Internal route failed, falling back to OSM")
                    findPureOSMRoute(start, end)
                }
            }

            // CASE B: INBOUND (City -> Campus Building)
            // Start is outside, End is inside
            !startInside && endInside -> {
                Log.d("HybridNav", "Strategy: Inbound (OSM -> Gate -> Internal)")
                findInboundRoute(start, end, options)
            }

            // CASE C: OUTBOUND (Campus Building -> City)
            // Start is inside, End is outside
            startInside && !endInside -> {
                Log.d("HybridNav", "Strategy: Outbound (Internal -> Gate -> OSM)")
                findOutboundRoute(start, end, options)
            }

            // CASE D: PURE CITY (Navigating entirely outside)
            else -> {
                Log.d("HybridNav", "Strategy: Pure OSM")
                findPureOSMRoute(start, end)
            }
        }
    }

    // --- HELPER: BOUNDING BOX CHECK ---

    private fun isInsideCampus(point: GeoPoint): Boolean {
        return point.latitude <= CAMPUS_NORTH &&
                point.latitude >= CAMPUS_SOUTH &&
                point.longitude <= CAMPUS_EAST &&
                point.longitude >= CAMPUS_WEST
    }

    // --- STRATEGIES ---

    private suspend fun findPureCampusRoute(start: GeoPoint, end: GeoPoint, options: RouteOptions): RouteResult {
        // Since we are inside the box, we find the nearest known nodes
        return pathfindingEngine.findRoute(
            geoPointToCampusLocation(start, "start"),
            geoPointToCampusLocation(end, "end"),
            options
        )
    }

    private suspend fun findPureOSMRoute(start: GeoPoint, end: GeoPoint): RouteResult {
        return try {
            val road = roadManager.getRoad(arrayListOf(start, end))
            if (road.mStatus == Road.STATUS_OK) {
                convertOSMRoadToRouteResult(road, start, end)
            } else {
                RouteResult.NoRouteFound("OSM Path failed")
            }
        } catch (e: Exception) {
            RouteResult.Error("OSM Error: ${e.message}")
        }
    }

    private suspend fun findInboundRoute(start: GeoPoint, end: GeoPoint, options: RouteOptions): RouteResult {
        // 1. Find the Nearest GATE to the user
        // We use the Gateway logic to ensure valid entry
        val gatewayNode = pathfindingEngine.findNearestNode(
            location = geoPointToCampusLocation(start, "temp"),
            type = CampusGraph.NodeType.ENTRANCE
        ) ?: pathfindingEngine.findNearestNode(geoPointToCampusLocation(start, "temp")) // Fallback

        if (gatewayNode == null) return RouteResult.Error("No campus entrance found")

        val gatewayGeo = GeoPoint(gatewayNode.location.latitude, gatewayNode.location.longitude)

        // 2. Leg 1: Drive/Walk to the Gate (OSM)
        val leg1 = findPureOSMRoute(start, gatewayGeo)

        // 3. Leg 2: Walk inside to the building (Internal Graph)
        val leg2 = pathfindingEngine.findRoute(gatewayNode.location, geoPointToCampusLocation(end, "end"), options)

        // 4. Stitch them together (With Fallback)
        val stitched = stitchRoutes(leg1, leg2)
        if (stitched is RouteResult.Success) return stitched

        // Fallback: If stitching failed, just use pure OSM
        return findPureOSMRoute(start, end)
    }

    private suspend fun findOutboundRoute(start: GeoPoint, end: GeoPoint, options: RouteOptions): RouteResult {
        // 1. Find the Nearest GATE to the destination (Exit point)
        val gatewayNode = pathfindingEngine.findNearestNode(
            location = geoPointToCampusLocation(end, "temp"),
            type = CampusGraph.NodeType.ENTRANCE
        ) ?: pathfindingEngine.findNearestNode(geoPointToCampusLocation(end, "temp"))

        if (gatewayNode == null) return RouteResult.Error("No campus exit found")

        val gatewayGeo = GeoPoint(gatewayNode.location.latitude, gatewayNode.location.longitude)

        // 2. Leg 1: Walk to the Gate (Internal Graph)
        val leg1 = pathfindingEngine.findRoute(geoPointToCampusLocation(start, "start"), gatewayNode.location, options)

        // 3. Leg 2: Drive/Walk to destination (OSM)
        val leg2 = findPureOSMRoute(gatewayGeo, end)

        // 4. Stitch them together (With Fallback)
        val stitched = stitchRoutes(leg1, leg2)
        if (stitched is RouteResult.Success) return stitched

        // Fallback: If stitching failed, just use pure OSM
        return findPureOSMRoute(start, end)
    }

    // --- STITCHING LOGIC ---

    private fun stitchRoutes(result1: RouteResult, result2: RouteResult): RouteResult {
        if (result1 !is RouteResult.Success) return result1
        if (result2 !is RouteResult.Success) return result2

        val r1 = result1.route
        val r2 = result2.route

        val mergedWaypoints = r1.waypoints.toMutableList()
        val mergedSteps = r1.steps.toMutableList()

        // Smooth connection
        if (mergedWaypoints.isNotEmpty() && r2.waypoints.isNotEmpty()) {
            val lastP = mergedWaypoints.last().location
            val firstP = r2.waypoints.first().location
            val dist = calculateDistance(lastP, firstP)

            // If the stitch point is very close (< 20m), merge them
            if (dist < 20.0) {
                mergedWaypoints.addAll(r2.waypoints.drop(1))
            } else {
                mergedWaypoints.addAll(r2.waypoints)
            }
        } else {
            mergedWaypoints.addAll(r2.waypoints)
        }

        mergedSteps.addAll(r2.steps)

        val stitchedRoute = Route(
            id = "hybrid_${System.currentTimeMillis()}",
            origin = r1.origin,
            destination = r2.destination,
            waypoints = mergedWaypoints,
            totalDistance = r1.totalDistance + r2.totalDistance,
            estimatedTime = r1.estimatedTime + r2.estimatedTime,
            steps = mergedSteps
        )

        return RouteResult.Success(stitchedRoute)
    }

    private fun convertOSMRoadToRouteResult(road: Road, start: GeoPoint, end: GeoPoint): RouteResult.Success {
        val waypoints = road.mNodes.mapIndexed { index, node ->
            Waypoint(
                location = CampusLocation("osm_$index", node.mLocation.latitude, node.mLocation.longitude, 0.0),
                type = if (index == 0) WaypointType.START else WaypointType.CONTINUE_STRAIGHT,
                instruction = node.mInstructions ?: ""
            )
        }

        val steps = road.mNodes.map { node ->
            NavigationStep(
                instruction = node.mInstructions ?: "Continue",
                distance = node.mLength * 1000,
                direction = Direction.FORWARD,
                startLocation = CampusLocation("s", node.mLocation.latitude, node.mLocation.longitude, 0.0),
                endLocation = CampusLocation("e", node.mLocation.latitude, node.mLocation.longitude, 0.0),
                isIndoor = false
            )
        }

        val route = Route(
            id = "osm_${System.currentTimeMillis()}",
            origin = geoPointToCampusLocation(start, "start"),
            destination = geoPointToCampusLocation(end, "end"),
            waypoints = waypoints,
            totalDistance = road.mLength * 1000,
            estimatedTime = road.mDuration.toLong(),
            steps = steps
        )
        return RouteResult.Success(route)
    }

    private fun geoPointToCampusLocation(geo: GeoPoint, id: String): CampusLocation {
        return CampusLocation(id, geo.latitude, geo.longitude, geo.altitude)
    }

    private fun calculateDistance(p1: CampusLocation, p2: CampusLocation): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}