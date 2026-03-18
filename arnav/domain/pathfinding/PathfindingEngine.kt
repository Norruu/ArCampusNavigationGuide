package com.campus.arnav.domain.pathfinding

import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.data.model.Direction
import com.campus.arnav.data.model.NavigationStep
import com.campus.arnav.data.model.Route
import com.campus.arnav.data.model.Waypoint
import com.campus.arnav.data.model.WaypointType
import com.campus.arnav.util.LocationUtils
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

data class RouteOptions(
    val accessible: Boolean = false,
    val preferOutdoor: Boolean = true,
    val avoidStairs: Boolean = false,
    val walkingSpeed: Double = 1.4
)

sealed class RouteResult {
    data class Success(val route: Route) : RouteResult()
    data class Error(val message: String) : RouteResult()
    data class NoRouteFound(val message: String) : RouteResult()
}

@Singleton
class PathfindingEngine @Inject constructor() {

    private var campusGraph: CampusGraph? = null

    fun initializeWithGraph(graph: CampusGraph) {
        this.campusGraph = graph
    }

    fun findNearestNode(location: CampusLocation, type: CampusGraph.NodeType? = null): CampusGraph.GraphNode? {
        return campusGraph?.findNearestNode(location, type)
    }

    fun findRoute(
        start: CampusLocation,
        end: CampusLocation,
        markerDestination: CampusLocation = end,   // visual marker position (building centre)
        options: RouteOptions = RouteOptions()
    ): RouteResult {
        val graph = campusGraph ?: return RouteResult.Error("Graph not initialized")

        // ── Start: edge-snap the user's GPS position onto the nearest road ────
        val startSnapResult = snapToNearestEdge(start, graph)
        val tempStartId = "temp_start"

        val actualStartNode: CampusGraph.GraphNode = if (startSnapResult != null) {
            val (snappedLoc, nodeAId, nodeBId) = startSnapResult
            val nodeA = graph.nodes[nodeAId]!!
            val nodeB = graph.nodes[nodeBId]!!
            val tempNode = CampusGraph.GraphNode(tempStartId, snappedLoc, CampusGraph.NodeType.PATH)
            graph.addTemporaryStartNode(
                tempNode, nodeAId, nodeBId,
                LocationUtils.haversineDistance(snappedLoc, nodeA.location),
                LocationUtils.haversineDistance(snappedLoc, nodeB.location)
            )
            tempNode
        } else {
            graph.findNearestNode(start)
                ?: return RouteResult.NoRouteFound("Start location not near campus paths")
        }
        val endSnapResult = snapToNearestEdge(end, graph)
        val tempEndId = "temp_end"

        // ── End: edge-snap the entrance onto the nearest road ─────────────────
        // 'end' is the building entrance — used as the A* target so routing
        // picks the road segment nearest to the entrance.
        // 'markerDestination' is the building's visual pin position — used only
        // for the final dotted connector so it always points to the marker.

        val actualEndNode: CampusGraph.GraphNode = if (endSnapResult != null) {
            val (snappedLoc, nodeAId, nodeBId) = endSnapResult
            val nodeA = graph.nodes[nodeAId]!!
            val nodeB = graph.nodes[nodeBId]!!
            val tempNode = CampusGraph.GraphNode(tempEndId, snappedLoc, CampusGraph.NodeType.PATH)
            graph.addTemporaryStartNode(
                tempNode, nodeAId, nodeBId,
                LocationUtils.haversineDistance(snappedLoc, nodeA.location),
                LocationUtils.haversineDistance(snappedLoc, nodeB.location)
            )
            tempNode
        } else {
            graph.findNearestNode(end)
                ?: return RouteResult.NoRouteFound("Destination not near campus paths")
        }

        // ── A* + guaranteed cleanup for both temp nodes ───────────────────────
        return try {
            val pathNodes = runAStar(graph, actualStartNode, actualEndNode)
                ?: return RouteResult.NoRouteFound("No path found between these locations")

            // convertPathToRoute uses markerDestination (building pin) for the
            // final visual connector, not 'end' (entrance used for routing).
            val route = convertPathToRoute(pathNodes, start, markerDestination, options.walkingSpeed)
            RouteResult.Success(route)
        } finally {
            if (startSnapResult != null) graph.removeTemporaryNode(tempStartId)
            if (endSnapResult   != null) graph.removeTemporaryNode(tempEndId)
        }
    }

    /**
     * Finds the nearest road segment to [userLocation] and returns the projected
     * point on that segment together with the IDs of the two bordering nodes.
     *
     * Delegates all projection math to [LocationUtils.projectPointOnSegment].
     * The threshold is 50 m — well beyond any reasonable campus GPS inaccuracy
     * while still refusing to snap to a road 200 m away.
     */
    private fun snapToNearestEdge(
        userLocation: CampusLocation,
        graph: CampusGraph,
        maxDistanceMetres: Double = 50.0
    ): Triple<CampusLocation, String, String>? {
        var minDistance = Double.MAX_VALUE
        var bestProj: CampusLocation? = null
        var bestNodeA = ""
        var bestNodeB = ""

        val visitedEdges = mutableSetOf<String>()

        for ((nodeAId, edges) in graph.adjacencyList) {
            val nodeA = graph.nodes[nodeAId] ?: continue

            for (edge in edges) {
                val nodeBId = edge.toNodeId
                val edgeKey = if (nodeAId < nodeBId) "$nodeAId-$nodeBId" else "$nodeBId-$nodeAId"
                if (!visitedEdges.add(edgeKey)) continue

                val nodeB = graph.nodes[nodeBId] ?: continue

                val proj = LocationUtils.projectPointOnSegment(
                    userLocation, nodeA.location, nodeB.location
                )
                val dist = LocationUtils.haversineDistance(userLocation, proj)

                if (dist < minDistance) {
                    minDistance = dist
                    bestProj = proj
                    bestNodeA = nodeAId
                    bestNodeB = nodeBId
                }
            }
        }

        return if (bestProj != null && minDistance <= maxDistanceMetres)
            Triple(bestProj, bestNodeA, bestNodeB)
        else
            null
    }

    private fun runAStar(
        graph: CampusGraph,
        startNode: CampusGraph.GraphNode,
        targetNode: CampusGraph.GraphNode
    ): List<CampusGraph.GraphNode>? {
        val openSet = PriorityQueue<NodeWrapper>()
        val cameFrom = mutableMapOf<String, CampusGraph.GraphNode>()
        val gScore = mutableMapOf<String, Double>().withDefault { Double.MAX_VALUE }

        gScore[startNode.id] = 0.0
        openSet.add(NodeWrapper(startNode, 0.0))

        val visited = mutableSetOf<String>()

        while (openSet.isNotEmpty()) {
            val current = openSet.poll()?.node ?: break

            if (current.id == targetNode.id) {
                return reconstructPath(cameFrom, current)
            }

            if (!visited.add(current.id)) continue

            graph.adjacencyList[current.id]?.forEach { edge ->
                if (edge.isAccessible) {
                    val tentativeG = gScore.getValue(current.id) + edge.distance
                    val neighbor = graph.nodes[edge.toNodeId]

                    if (neighbor != null && tentativeG < gScore.getValue(neighbor.id)) {
                        cameFrom[neighbor.id] = current
                        gScore[neighbor.id] = tentativeG
                        val h = LocationUtils.haversineDistance(neighbor.location, targetNode.location)
                        openSet.add(NodeWrapper(neighbor, tentativeG + h))
                    }
                }
            }
        }
        return null
    }

    private fun reconstructPath(
        cameFrom: Map<String, CampusGraph.GraphNode>,
        current: CampusGraph.GraphNode
    ): List<CampusGraph.GraphNode> {
        val path = mutableListOf(current)
        var curr = current
        while (cameFrom.containsKey(curr.id)) {
            curr = cameFrom[curr.id]!!
            path.add(0, curr)
        }
        return path
    }

    private fun convertPathToRoute(
        pathNodes: List<CampusGraph.GraphNode>,
        rawOrigin: CampusLocation,
        rawDestination: CampusLocation,
        speedMPS: Double // 🆕 Add this parameter
    ): Route {
        val waypoints = mutableListOf<Waypoint>()
        val steps = mutableListOf<NavigationStep>()
        var totalDist = 0.0

        // ── Start connector: GPS dot → road snap point ────────────────────────
        waypoints.add(Waypoint(rawOrigin, WaypointType.START, "Start"))

        val startSnapNode = pathNodes.first()
        val distToStartSnap = LocationUtils.haversineDistance(rawOrigin, startSnapNode.location)
        if (distToStartSnap > 1.0) {
            totalDist += distToStartSnap
            steps.add(NavigationStep(
                instruction   = "Head to the path",
                distance      = distToStartSnap,
                direction     = Direction.FORWARD,
                startLocation = rawOrigin,
                endLocation   = startSnapNode.location,
                isIndoor      = false
            ))
            waypoints.add(Waypoint(startSnapNode.location, WaypointType.CONTINUE_STRAIGHT, ""))
        }

        // ── Road network: follow A* path ──────────────────────────────────────
        for (i in 0 until pathNodes.size - 1) {
            val curr = pathNodes[i]
            val next = pathNodes[i + 1]
            val dist = LocationUtils.haversineDistance(curr.location, next.location)
            totalDist += dist

            val direction = if (i == 0) {
                Direction.FORWARD
            } else {
                val prev = pathNodes[i - 1]
                bearingToDirection(
                    LocationUtils.bearing(prev.location, curr.location),
                    LocationUtils.bearing(curr.location, next.location)
                )
            }

            steps.add(NavigationStep(
                instruction   = directionToInstruction(direction),
                distance      = dist,
                direction     = direction,
                startLocation = curr.location,
                endLocation   = next.location,
                isIndoor      = false
            ))
            waypoints.add(Waypoint(next.location, WaypointType.CONTINUE_STRAIGHT, ""))
        }

        // ── End connector: road snap point → building marker ─────────────────
        // pathNodes.last() is temp_end — the edge-snapped point on the road.
        // rawDestination is the marker pin — used only by DestinationConnectorOverlay.
        // We add rawDestination as the final waypoint so the connector overlay has
        // both endpoints, but the solid polyline must NOT include this last point
        // (MapViewModel strips it via dropLast(1) before passing to routePolyline).
        val endSnapNode = pathNodes.last()
        val distToMarker = LocationUtils.haversineDistance(endSnapNode.location, rawDestination)
        totalDist += distToMarker
        steps.add(NavigationStep(
            instruction   = "Head to the destination",
            distance      = distToMarker,
            direction     = Direction.ARRIVE,
            startLocation = endSnapNode.location,
            endLocation   = rawDestination,
            isIndoor      = false
        ))
        // endSnapNode waypoint is the LAST solid-line point (index lastIndex - 1)
        // rawDestination waypoint is the marker-only point  (index lastIndex)
        waypoints.add(Waypoint(endSnapNode.location, WaypointType.CONTINUE_STRAIGHT, "Road snap"))
        waypoints.add(Waypoint(rawDestination, WaypointType.END, "Arrived"))

        return Route(
            id            = "route_${System.currentTimeMillis()}",
            origin        = rawOrigin,
            destination   = rawDestination,
            waypoints     = waypoints,
            totalDistance = totalDist,
            estimatedTime = (totalDist / speedMPS).toLong(),
            steps         = steps
        )
    }

    fun estimateWalkingTime(start: CampusLocation, end: CampusLocation, speed: Double = 1.4): Long? {
        val result = findRoute(start, end)
        return if (result is RouteResult.Success) (result.route.totalDistance / speed).toLong() else null
    }

    fun findAlternativeRoutes(start: CampusLocation, end: CampusLocation, max: Int): List<Route> {
        val result = findRoute(start, end)
        return if (result is RouteResult.Success) listOf(result.route) else emptyList()
    }

    private fun bearingToDirection(prevBearing: Double, nextBearing: Double): Direction {
        var diff = nextBearing - prevBearing
        while (diff < -180) diff += 360
        while (diff >  180) diff -= 360
        return when {
            diff in -20.0..20.0      -> Direction.FORWARD
            diff in -45.0..-20.0    -> Direction.SLIGHT_LEFT
            diff in -135.0..-45.0   -> Direction.LEFT
            diff < -135.0           -> Direction.U_TURN
            diff in  20.0..45.0     -> Direction.SLIGHT_RIGHT
            diff in  45.0..135.0    -> Direction.RIGHT
            else                    -> Direction.SHARP_RIGHT
        }
    }

    private fun directionToInstruction(direction: Direction): String = when (direction) {
        Direction.FORWARD       -> "Go straight"
        Direction.SLIGHT_LEFT   -> "Slight left"
        Direction.LEFT          -> "Turn left"
        Direction.SHARP_LEFT    -> "Sharp left"
        Direction.SLIGHT_RIGHT  -> "Slight right"
        Direction.RIGHT         -> "Turn right"
        Direction.SHARP_RIGHT   -> "Sharp right"
        Direction.U_TURN        -> "Make a U-turn"
        Direction.ARRIVE        -> "Arrive at destination"
    }

    private data class NodeWrapper(val node: CampusGraph.GraphNode, val fScore: Double) :
        Comparable<NodeWrapper> {
        override fun compareTo(other: NodeWrapper) = fScore.compareTo(other.fScore)
    }
}