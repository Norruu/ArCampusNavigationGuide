package com.campus.arnav.domain.graph

import android.util.Log
import com.campus.arnav.data.model.*
import com.campus.arnav.util.LocationUtils
import org.osmdroid.util.GeoPoint
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * UnifiedPathfinder
 * ─────────────────
 * A* pathfinder that works on a [UnifiedGraphSnapshot] produced by
 * [UnifiedGraphManager].  It treats hardcoded and admin roads identically:
 * both are just nodes and edges in the same graph.
 *
 * Key features
 * ────────────
 * • Edge-snapping: GPS position is projected onto the nearest road segment
 *   before routing begins, so the user is never "off-graph".
 * • Temporary nodes are injected for start/end, then cleaned up after each call.
 * • Configurable via [UnifiedRouteOptions].
 * • Returns a sealed [UnifiedRouteResult] (Success / NoRoute / Error).
 */
@Singleton
class UnifiedPathfinder @Inject constructor(
    private val graphManager: UnifiedGraphManager
) {

    companion object {
        private const val TAG          = "UnifiedPathfinder"
        private const val MAX_SNAP_M   = 50.0   // metres – max distance to snap onto road
        private const val TEMP_START   = "__temp_start__"
        private const val TEMP_END     = "__temp_end__"
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Find the shortest walking route from [startGeo] to [endGeo].
     *
     * This call is safe to run on a background thread (no UI operations).
     * It uses a fresh graph snapshot and performs no mutations to [graphManager].
     */
    fun findRoute(
        startGeo: GeoPoint,
        endGeo:   GeoPoint,
        options:  UnifiedRouteOptions = UnifiedRouteOptions()
    ): UnifiedRouteResult {

        // Work on a mutable copy of the snapshot so we can inject temp nodes
        val snap  = graphManager.snapshot()
        val nodes = snap.nodes.toMutableMap()
        val adj   = snap.adjacency.mapValues { it.value.toMutableList() }.toMutableMap()

        val startLoc = startGeo.toCL("gps_start")
        val endLoc   = endGeo.toCL("gps_end")

        // ── Snap start & end to road ──────────────────────────────────────────
        val startNodeId = snapOrNearest(startLoc, nodes, adj, TEMP_START)
            ?: return UnifiedRouteResult.NoRoute("Start location not near any road")

        val endNodeId = snapOrNearest(endLoc, nodes, adj, TEMP_END)
            ?: return UnifiedRouteResult.NoRoute("Destination not near any road")

        if (startNodeId == endNodeId) {
            return buildDirectRoute(startLoc, endLoc, options)
        }

        // ── A* ────────────────────────────────────────────────────────────────
        val pathNodes = runAStar(startNodeId, endNodeId, nodes, adj, options)
            ?: return UnifiedRouteResult.NoRoute("No path between these locations")

        // ── Convert path to Route domain model ────────────────────────────────
        val route = convertToRoute(pathNodes, startLoc, endLoc, nodes, options)
        Log.d(TAG, "Route found: ${route.waypoints.size} waypoints, ${route.totalDistance.toInt()} m")
        return UnifiedRouteResult.Success(route)
    }

    // ── Snapping ──────────────────────────────────────────────────────────────

    /**
     * Projects [location] onto the nearest road edge (within [MAX_SNAP_M]).
     * Injects a temporary node onto that edge and returns its id.
     * Falls back to nearest existing node if no edge is close enough.
     */
    private fun snapOrNearest(
        location: CampusLocation,
        nodes: MutableMap<String, UnifiedNode>,
        adj: MutableMap<String, MutableList<UnifiedEdge>>,
        tempId: String
    ): String? {
        var bestDist = Double.MAX_VALUE
        var bestProj: CampusLocation? = null
        var bestAId  = ""
        var bestBId  = ""

        val visited = mutableSetOf<String>()

        for ((fromId, edges) in adj) {
            val fromNode = nodes[fromId] ?: continue
            for (edge in edges) {
                val key = "${minOf(fromId, edge.toId)}|${maxOf(fromId, edge.toId)}"
                if (!visited.add(key)) continue
                val toNode = nodes[edge.toId] ?: continue

                val proj = LocationUtils.projectPointOnSegment(location, fromNode.location, toNode.location)
                val d    = haversine(location, proj)

                if (d < bestDist) {
                    bestDist = d
                    bestProj = proj
                    bestAId  = fromId
                    bestBId  = edge.toId
                }
            }
        }

        if (bestProj != null && bestDist <= MAX_SNAP_M) {
            // Inject temp node onto the edge
            val tempNode = UnifiedNode(
                id       = tempId,
                location = bestProj!!.copy(id = tempId),
                source   = NodeSource.SYNTHETIC
            )
            nodes[tempId] = tempNode
            adj[tempId]   = mutableListOf()

            val nodeA = nodes[bestAId]!!
            val nodeB = nodes[bestBId]!!
            val dA    = haversine(tempNode.location, nodeA.location)
            val dB    = haversine(tempNode.location, nodeB.location)

            adj[tempId]!!.add(UnifiedEdge(tempId, bestAId, dA))
            adj[tempId]!!.add(UnifiedEdge(tempId, bestBId, dB))
            adj.getOrPut(bestAId) { mutableListOf() }.add(UnifiedEdge(bestAId, tempId, dA))
            adj.getOrPut(bestBId) { mutableListOf() }.add(UnifiedEdge(bestBId, tempId, dB))

            return tempId
        }

        // Fallback: nearest existing node
        return nodes.values.minByOrNull { haversine(location, it.location) }?.id
    }

    // ── A* core ───────────────────────────────────────────────────────────────

    private data class AStarNode(
        val id: String,
        val fScore: Double
    ) : Comparable<AStarNode> {
        override fun compareTo(other: AStarNode) = fScore.compareTo(other.fScore)
    }

    private fun runAStar(
        startId: String,
        goalId:  String,
        nodes: Map<String, UnifiedNode>,
        adj:   Map<String, List<UnifiedEdge>>,
        options: UnifiedRouteOptions
    ): List<UnifiedNode>? {
        val openSet   = PriorityQueue<AStarNode>()
        val cameFrom  = mutableMapOf<String, String>()
        val gScore    = mutableMapOf<String, Double>().withDefault { Double.MAX_VALUE }
        val closed    = mutableSetOf<String>()

        val goal = nodes[goalId] ?: return null

        gScore[startId] = 0.0
        openSet.add(AStarNode(startId, heuristic(nodes[startId]!!, goal)))

        while (openSet.isNotEmpty()) {
            val current = openSet.poll()!!
            if (current.id == goalId) return reconstructPath(cameFrom, current.id, nodes)
            if (!closed.add(current.id)) continue

            for (edge in adj[current.id] ?: emptyList()) {
                if (edge.toId in closed) continue
                if (!options.accessible || edge.isAccessible) {
                    val tentG = gScore.getValue(current.id) + edgeCost(edge, options)
                    if (tentG < gScore.getValue(edge.toId)) {
                        cameFrom[edge.toId] = current.id
                        gScore[edge.toId]   = tentG
                        val neighbor = nodes[edge.toId] ?: continue
                        openSet.add(AStarNode(edge.toId, tentG + heuristic(neighbor, goal)))
                    }
                }
            }
        }
        return null   // No path found
    }

    private fun heuristic(from: UnifiedNode, to: UnifiedNode) =
        haversine(from.location, to.location)

    private fun edgeCost(edge: UnifiedEdge, options: UnifiedRouteOptions): Double {
        var cost = edge.distance
        if (edge.roadType == RoadType.WALKWAY && !options.preferWalkways) cost *= 1.2
        if (edge.roadType == RoadType.MAIN_ROAD && options.preferWalkways) cost *= 1.1
        return cost
    }

    private fun reconstructPath(
        cameFrom: Map<String, String>,
        goalId: String,
        nodes: Map<String, UnifiedNode>
    ): List<UnifiedNode> {
        val path = mutableListOf<String>(goalId)
        var curr = goalId
        while (cameFrom.containsKey(curr)) {
            curr = cameFrom[curr]!!
            path.add(0, curr)
        }
        return path.mapNotNull { nodes[it] }
    }

    // ── Route construction ────────────────────────────────────────────────────

    private fun convertToRoute(
        pathNodes: List<UnifiedNode>,
        rawOrigin: CampusLocation,
        rawDest:   CampusLocation,
        nodes: Map<String, UnifiedNode>,
        options: UnifiedRouteOptions
    ): Route {
        val waypoints = mutableListOf<Waypoint>()
        val steps     = mutableListOf<NavigationStep>()
        var totalDist = 0.0

        // Start connector: GPS dot → first road node
        waypoints.add(Waypoint(rawOrigin, WaypointType.START, "Start navigation"))
        val firstRoadNode = pathNodes.first()
        val distToFirst   = haversine(rawOrigin, firstRoadNode.location)
        if (distToFirst > 1.0) {
            totalDist += distToFirst
            steps.add(NavigationStep(
                instruction   = "Head to the nearest path",
                distance      = distToFirst,
                direction     = Direction.FORWARD,
                startLocation = rawOrigin,
                endLocation   = firstRoadNode.location
            ))
        }

        // Main path
        for (i in 0 until pathNodes.lastIndex) {
            val curr = pathNodes[i]
            val next = pathNodes[i + 1]
            val dist = haversine(curr.location, next.location)
            totalDist += dist

            val direction = if (i == 0) Direction.FORWARD
            else bearingToDirection(
                LocationUtils.bearing(pathNodes[i - 1].location, curr.location),
                LocationUtils.bearing(curr.location, next.location)
            )

            steps.add(NavigationStep(
                instruction   = directionInstruction(direction, dist),
                distance      = dist,
                direction     = direction,
                startLocation = curr.location,
                endLocation   = next.location
            ))
            waypoints.add(Waypoint(next.location, WaypointType.CONTINUE_STRAIGHT))
        }

        // End connector: last road node → destination marker
        val lastRoadNode = pathNodes.last()
        val distToMarker = haversine(lastRoadNode.location, rawDest)
        totalDist += distToMarker
        steps.add(NavigationStep(
            instruction   = "Arrive at destination",
            distance      = distToMarker,
            direction     = Direction.ARRIVE,
            startLocation = lastRoadNode.location,
            endLocation   = rawDest
        ))
        // Road-snap point (last solid line point)
        waypoints.add(Waypoint(lastRoadNode.location, WaypointType.CONTINUE_STRAIGHT, "Road snap"))
        // Destination marker point (dashed connector target)
        waypoints.add(Waypoint(rawDest, WaypointType.END, "You have arrived"))

        val speed = options.walkingSpeedMps
        return Route(
            id            = "unified_${System.currentTimeMillis()}",
            origin        = rawOrigin,
            destination   = rawDest,
            waypoints     = waypoints,
            steps         = steps,
            totalDistance = totalDist,
            estimatedTime = (totalDist / speed).toLong()
        )
    }

    private fun buildDirectRoute(
        start: CampusLocation, end: CampusLocation, options: UnifiedRouteOptions
    ): UnifiedRouteResult.Success {
        val dist = haversine(start, end)
        return UnifiedRouteResult.Success(Route(
            id            = "direct_${System.currentTimeMillis()}",
            origin        = start,
            destination   = end,
            waypoints     = listOf(
                Waypoint(start, WaypointType.START),
                Waypoint(end,   WaypointType.END, "You have arrived")
            ),
            steps         = listOf(NavigationStep(
                instruction   = "Walk directly to destination",
                distance      = dist,
                direction     = Direction.FORWARD,
                startLocation = start,
                endLocation   = end
            )),
            totalDistance = dist,
            estimatedTime = (dist / options.walkingSpeedMps).toLong()
        ))
    }

    // ── Direction helpers ─────────────────────────────────────────────────────

    private fun bearingToDirection(prevBearing: Double, nextBearing: Double): Direction {
        var diff = nextBearing - prevBearing
        while (diff < -180) diff += 360
        while (diff >  180) diff -= 360
        return when {
            diff in -20.0..20.0    -> Direction.FORWARD
            diff in -45.0..-20.0   -> Direction.SLIGHT_LEFT
            diff in -135.0..-45.0  -> Direction.LEFT
            diff < -135.0          -> Direction.U_TURN
            diff in  20.0..45.0    -> Direction.SLIGHT_RIGHT
            diff in  45.0..135.0   -> Direction.RIGHT
            else                   -> Direction.SHARP_RIGHT
        }
    }

    private fun directionInstruction(direction: Direction, dist: Double): String {
        val d = if (dist < 1000) "${dist.toInt()} m" else String.format("%.1f km", dist / 1000)
        return when (direction) {
            Direction.FORWARD      -> "Continue straight for $d"
            Direction.SLIGHT_LEFT  -> "Keep left for $d"
            Direction.LEFT         -> "Turn left and walk $d"
            Direction.SHARP_LEFT   -> "Turn sharp left for $d"
            Direction.SLIGHT_RIGHT -> "Keep right for $d"
            Direction.RIGHT        -> "Turn right and walk $d"
            Direction.SHARP_RIGHT  -> "Turn sharp right for $d"
            Direction.U_TURN       -> "Make a U-turn"
            Direction.ARRIVE       -> "Arrive at destination"
        }
    }

    // ── Extension helpers ─────────────────────────────────────────────────────

    private fun GeoPoint.toCL(id: String) = CampusLocation(id, latitude, longitude, altitude)
}

// ─────────────────────────────────────────────────────────────────────────────
// OPTIONS  &  RESULT
// ─────────────────────────────────────────────────────────────────────────────

data class UnifiedRouteOptions(
    val walkingSpeedMps: Double = 1.4,   // ~5 km/h
    val accessible:      Boolean = false,
    val preferWalkways:  Boolean = false
)

sealed class UnifiedRouteResult {
    data class Success(val route: Route) : UnifiedRouteResult()
    data class NoRoute(val reason: String) : UnifiedRouteResult()
    data class Error(val message: String) : UnifiedRouteResult()
}