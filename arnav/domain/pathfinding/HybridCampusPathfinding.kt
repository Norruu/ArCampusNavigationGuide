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

    // Coordinates taken from your MapFragment setup
    private val campusBounds = object {
        val north = 9.857365352521331
        val south = 9.843818207123961
        val east = 122.89307299341952
        val west = 122.88305672555643

        fun isInside(lat: Double, lon: Double): Boolean {
            return lat in south..north && lon in west..east
        }
    }

    suspend fun findRoute(
        start: GeoPoint,
        end: GeoPoint,
        options: RouteOptions = RouteOptions()
    ): RouteResult = withContext(Dispatchers.IO) {

        val startInside = campusBounds.isInside(start.latitude, start.longitude)
        val endInside = campusBounds.isInside(end.latitude, end.longitude)

        Log.d("HybridNav", "Context: StartInside=$startInside, EndInside=$endInside")

        return@withContext when {
            // CASE 1: PURE CAMPUS (Room to Room)
            startInside && endInside -> {
                findPureCampusRoute(start, end, options)
            }

            // CASE 2: INBOUND (City -> Campus)
            !startInside && endInside -> {
                findInboundRoute(start, end, options)
            }

            // CASE 3: OUTBOUND (Campus -> City)
            startInside && !endInside -> {
                findOutboundRoute(start, end, options)
            }

            // CASE 4: PURE OSM (City to City)
            else -> {
                findPureOSMRoute(start, end)
            }
        }
    }

    // --- STRATEGIES ---

    private suspend fun findPureCampusRoute(start: GeoPoint, end: GeoPoint, options: RouteOptions): RouteResult {
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
        // 1. Dynamic Gateway: Find the campus node closest to the user's external position
        // This acts as the "Entrance Gate"
        val gatewayNode = pathfindingEngine.findNearestNode(geoPointToCampusLocation(start, "temp"))
            ?: return RouteResult.Error("Could not find campus entry point")

        val gatewayGeo = GeoPoint(gatewayNode.location.latitude, gatewayNode.location.longitude)

        // 2. Leg 1: Outside -> Gateway (OSM)
        val leg1 = findPureOSMRoute(start, gatewayGeo)

        // 3. Leg 2: Gateway -> Inside Dest (A*)
        val leg2 = pathfindingEngine.findRoute(gatewayNode.location, geoPointToCampusLocation(end, "end"), options)

        // 4. Stitch
        return stitchRoutes(leg1, leg2)
    }

    private suspend fun findOutboundRoute(start: GeoPoint, end: GeoPoint, options: RouteOptions): RouteResult {
        // 1. Dynamic Gateway: Find the campus node closest to the external destination
        // This acts as the "Exit Gate"
        val gatewayNode = pathfindingEngine.findNearestNode(geoPointToCampusLocation(end, "temp"))
            ?: return RouteResult.Error("Could not find campus exit point")

        val gatewayGeo = GeoPoint(gatewayNode.location.latitude, gatewayNode.location.longitude)

        // 2. Leg 1: Inside -> Gateway (A*)
        val leg1 = pathfindingEngine.findRoute(geoPointToCampusLocation(start, "start"), gatewayNode.location, options)

        // 3. Leg 2: Gateway -> Outside Dest (OSM)
        val leg2 = findPureOSMRoute(gatewayGeo, end)

        return stitchRoutes(leg1, leg2)
    }

    // --- STITCHING LOGIC ---

    private fun stitchRoutes(result1: RouteResult, result2: RouteResult): RouteResult {
        if (result1 !is RouteResult.Success) return result1
        if (result2 !is RouteResult.Success) return result2

        val r1 = result1.route
        val r2 = result2.route

        val mergedWaypoints = r1.waypoints.toMutableList()
        val mergedSteps = r1.steps.toMutableList()

        // Smooth connection: if end of R1 is close to start of R2, avoid duplicate point
        if (mergedWaypoints.isNotEmpty() && r2.waypoints.isNotEmpty()) {
            val lastP = mergedWaypoints.last().location
            val firstP = r2.waypoints.first().location
            val dist = calculateDistance(lastP, firstP)

            if (dist < 15.0) {
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

    // --- HELPERS ---

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