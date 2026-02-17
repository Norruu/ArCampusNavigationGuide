package com.campus.arnav.domain.pathfinding

import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.data.model.Direction
import com.campus.arnav.data.model.NavigationStep
import com.campus.arnav.data.model.Route
import com.campus.arnav.data.model.Waypoint
import com.campus.arnav.data.model.WaypointType
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

// --- FIXED: Defined Data Classes Here ---
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
        options: RouteOptions = RouteOptions()
    ): RouteResult {
        val graph = campusGraph ?: return RouteResult.Error("Graph not initialized")

        val startNode = graph.findNearestNode(start)
        val endNode = graph.findNearestNode(end)

        if (startNode == null || endNode == null) {
            return RouteResult.NoRouteFound("Locations not near campus paths")
        }

        val pathNodes = runAStar(graph, startNode, endNode)
            ?: return RouteResult.NoRouteFound("No path found")

        val route = convertPathToRoute(pathNodes, start, end)
        return RouteResult.Success(route)
    }

    // --- INTERNAL A* ALGORITHM ---
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

            if (visited.contains(current.id)) continue
            visited.add(current.id)

            graph.adjacencyList[current.id]?.forEach { edge ->
                if (edge.isAccessible) {
                    val tentativeG = gScore.getValue(current.id) + edge.distance
                    val neighbor = graph.nodes[edge.toNodeId]

                    if (neighbor != null && tentativeG < gScore.getValue(neighbor.id)) {
                        cameFrom[neighbor.id] = current
                        gScore[neighbor.id] = tentativeG
                        val fScore = tentativeG + calculateHeuristic(neighbor.location, targetNode.location)
                        openSet.add(NodeWrapper(neighbor, fScore))
                    }
                }
            }
        }
        return null
    }

    private fun reconstructPath(cameFrom: Map<String, CampusGraph.GraphNode>, current: CampusGraph.GraphNode): List<CampusGraph.GraphNode> {
        val path = mutableListOf(current)
        var curr = current
        while (cameFrom.containsKey(curr.id)) {
            curr = cameFrom[curr.id]!!
            path.add(0, curr)
        }
        return path
    }

    private fun convertPathToRoute(pathNodes: List<CampusGraph.GraphNode>, origin: CampusLocation, destination: CampusLocation): Route {
        val waypoints = mutableListOf<Waypoint>()
        val steps = mutableListOf<NavigationStep>()
        var totalDist = 0.0

        waypoints.add(Waypoint(origin, WaypointType.START, "Start"))

        for (i in 0 until pathNodes.size - 1) {
            val curr = pathNodes[i]
            val next = pathNodes[i+1]
            val dist = calculateHeuristic(curr.location, next.location)
            totalDist += dist

            steps.add(NavigationStep(
                instruction = "Go to ${next.id}",
                distance = dist,
                direction = Direction.FORWARD,
                startLocation = curr.location,
                endLocation = next.location,
                isIndoor = false
            ))
            waypoints.add(Waypoint(next.location, WaypointType.CONTINUE_STRAIGHT, ""))
        }

        waypoints.add(Waypoint(destination, WaypointType.CONTINUE_STRAIGHT, "Arrived"))

        return Route(
            id = "route_${System.currentTimeMillis()}",
            origin = origin,
            destination = destination,
            waypoints = waypoints,
            totalDistance = totalDist,
            estimatedTime = (totalDist / 1.4).toLong(),
            steps = steps
        )
    }

    fun estimateWalkingTime(start: CampusLocation, end: CampusLocation, speed: Double = 1.4): Long? {
        val result = findRoute(start, end)
        return if (result is RouteResult.Success) {
            (result.route.totalDistance / speed).toLong()
        } else {
            null
        }
    }

    fun findAlternativeRoutes(start: CampusLocation, end: CampusLocation, max: Int): List<Route> {
        val result = findRoute(start, end)
        return if (result is RouteResult.Success) listOf(result.route) else emptyList()
    }

    private fun calculateHeuristic(p1: CampusLocation, p2: CampusLocation): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private data class NodeWrapper(val node: CampusGraph.GraphNode, val fScore: Double) : Comparable<NodeWrapper> {
        override fun compareTo(other: NodeWrapper) = fScore.compareTo(other.fScore)
    }
}