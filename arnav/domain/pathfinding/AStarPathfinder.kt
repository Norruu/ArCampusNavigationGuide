package com.campus.arnav.domain.pathfinding

import com.campus.arnav.data.model.*
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow  // <-- This import makes .pow() work on Double
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A* Pathfinding Algorithm optimized for campus-scale walking navigation
 */
class AStarPathfinder(private val graph: CampusGraph) {

    data class PathNode(
        val nodeId: String,
        val gScore: Double,
        val fScore: Double,
        val parent: PathNode?
    ) : Comparable<PathNode> {
        override fun compareTo(other: PathNode): Int = fScore.compareTo(other.fScore)
    }

    data class PathfindingConfig(
        val preferAccessible: Boolean = false,
        val preferOutdoor: Boolean = true,
        val avoidStairs: Boolean = false,
        val maxWalkingDistance: Double = Double.MAX_VALUE,
        val walkingSpeed: Double = 1.4
    )

    fun findPath(
        start: CampusLocation,
        end: CampusLocation,
        config: PathfindingConfig = PathfindingConfig()
    ): Route? {
        val startNode = graph.findNearestNode(start) ?: return null
        val endNode = graph.findNearestNode(end) ?: return null

        if (startNode.id == endNode.id) {
            return createDirectRoute(start, end)
        }

        val openSet = PriorityQueue<PathNode>()
        val closedSet = mutableSetOf<String>()
        val gScores = mutableMapOf<String, Double>()

        val startPathNode = PathNode(
            nodeId = startNode.id,
            gScore = 0.0,
            fScore = heuristic(startNode, endNode),
            parent = null
        )

        openSet.add(startPathNode)
        gScores[startNode.id] = 0.0

        var iterations = 0
        val maxIterations = 10000

        while (openSet.isNotEmpty() && iterations < maxIterations) {
            iterations++

            val current = openSet.poll()

            if (current.nodeId == endNode.id) {
                return reconstructRoute(current, start, end, config)
            }

            if (current.nodeId in closedSet) continue
            closedSet.add(current.nodeId)

            for (edge in graph.getNeighbors(current.nodeId)) {
                if (edge.toId in closedSet) continue

                if (!isEdgeAllowed(edge, config)) continue

                val edgeCost = calculateEdgeCost(edge, config)
                val tentativeGScore = current.gScore + edgeCost

                if (tentativeGScore < (gScores[edge.toId] ?: Double.MAX_VALUE)) {
                    gScores[edge.toId] = tentativeGScore

                    val neighborNode = graph.getNode(edge.toId) ?: continue
                    val fScore = tentativeGScore + heuristic(neighborNode, endNode)

                    openSet.add(PathNode(
                        nodeId = edge.toId,
                        gScore = tentativeGScore,
                        fScore = fScore,
                        parent = current
                    ))
                }
            }
        }

        return null
    }

    private fun isEdgeAllowed(edge: CampusGraph.GraphEdge, config: PathfindingConfig): Boolean {
        if (config.preferAccessible && !edge.isAccessible) {
            return false
        }

        if (config.avoidStairs && abs(edge.elevationChange) > 0.5) {
            val fromNode = graph.getNode(edge.fromId)
            val toNode = graph.getNode(edge.toId)

            if (fromNode?.type == CampusGraph.NodeType.ELEVATOR ||
                toNode?.type == CampusGraph.NodeType.ELEVATOR) {
                return true
            }
            return false
        }

        return true
    }

    private fun heuristic(from: CampusGraph.GraphNode, to: CampusGraph.GraphNode): Double {
        return haversineDistance(from.location, to.location)
    }

    private fun calculateEdgeCost(
        edge: CampusGraph.GraphEdge,
        config: PathfindingConfig
    ): Double {
        var cost = edge.distance

        if (config.preferOutdoor && edge.isIndoor) {
            cost *= 1.3
        }

        if (!config.preferOutdoor && !edge.isIndoor) {
            cost *= 1.2
        }

        if (edge.elevationChange != 0.0) {
            val elevationPenalty = abs(edge.elevationChange) * 2.0
            cost += elevationPenalty

            if (config.preferAccessible && abs(edge.elevationChange) > 0.3) {
                cost *= 1.5
            }
        }

        if (config.preferAccessible && !edge.isAccessible) {
            cost *= 3.0
        }

        return cost
    }

    private fun reconstructRoute(
        finalNode: PathNode,
        start: CampusLocation,
        end: CampusLocation,
        config: PathfindingConfig
    ): Route {
        val pathNodes = mutableListOf<CampusGraph.GraphNode>()
        var current: PathNode? = finalNode

        while (current != null) {
            graph.getNode(current.nodeId)?.let { pathNodes.add(0, it) }
            current = current.parent
        }

        val waypoints = generateWaypoints(pathNodes)
        val steps = generateNavigationSteps(pathNodes, waypoints)
        val totalDistance = calculateTotalDistance(pathNodes)
        val estimatedTime = (totalDistance / config.walkingSpeed).toLong()

        return Route(
            id = generateRouteId(),
            origin = start,
            destination = end,
            waypoints = waypoints,
            totalDistance = totalDistance,
            estimatedTime = estimatedTime,
            steps = steps
        )
    }

    private fun generateWaypoints(pathNodes: List<CampusGraph.GraphNode>): List<Waypoint> {
        val waypoints = mutableListOf<Waypoint>()

        pathNodes.forEachIndexed { index, node ->
            val waypointType = when {
                index == 0 -> WaypointType.START
                index == pathNodes.lastIndex -> WaypointType.END
                else -> determineWaypointType(pathNodes, index)
            }

            val instruction = generateWaypointInstruction(waypointType, pathNodes, index)

            waypoints.add(Waypoint(
                location = node.location,
                type = waypointType,
                instruction = instruction
            ))
        }

        return waypoints
    }

    private fun determineWaypointType(
        pathNodes: List<CampusGraph.GraphNode>,
        index: Int
    ): WaypointType {
        if (index == 0 || index >= pathNodes.lastIndex) {
            return if (index == 0) WaypointType.START else WaypointType.END
        }

        val prevNode = pathNodes[index - 1]
        val currentNode = pathNodes[index]
        val nextNode = pathNodes[index + 1]

        if (prevNode.buildingId != currentNode.buildingId && currentNode.buildingId != null) {
            return WaypointType.ENTER_BUILDING
        }
        if (currentNode.buildingId != null && currentNode.buildingId != nextNode.buildingId) {
            return WaypointType.EXIT_BUILDING
        }

        if (currentNode.type == CampusGraph.NodeType.STAIRS) {
            val elevationChange = nextNode.location.altitude - currentNode.location.altitude
            return if (elevationChange > 0) WaypointType.STAIRS_UP else WaypointType.STAIRS_DOWN
        }
        if (currentNode.type == CampusGraph.NodeType.ELEVATOR) {
            return WaypointType.ELEVATOR
        }

        val turnAngle = calculateTurnAngle(
            prevNode.location,
            currentNode.location,
            nextNode.location
        )

        return when {
            turnAngle in -20.0..20.0 -> WaypointType.CONTINUE_STRAIGHT
            turnAngle > 20.0 -> WaypointType.TURN_RIGHT
            turnAngle < -20.0 -> WaypointType.TURN_LEFT
            else -> WaypointType.CONTINUE_STRAIGHT
        }
    }

    private fun calculateTurnAngle(
        prev: CampusLocation,
        current: CampusLocation,
        next: CampusLocation
    ): Double {
        val bearing1 = calculateBearing(prev, current)
        val bearing2 = calculateBearing(current, next)

        var turn = bearing2 - bearing1

        while (turn > 180) turn -= 360
        while (turn < -180) turn += 360

        return turn
    }

    private fun calculateBearing(from: CampusLocation, to: CampusLocation): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)

        val x = sin(deltaLon) * cos(lat2)
        val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)

        val bearing = Math.toDegrees(atan2(x, y))
        return (bearing + 360) % 360
    }

    private fun generateWaypointInstruction(
        type: WaypointType,
        pathNodes: List<CampusGraph.GraphNode>,
        index: Int
    ): String {
        val currentNode = pathNodes[index]
        val nextNode = pathNodes.getOrNull(index + 1)

        val distanceToNext = nextNode?.let {
            haversineDistance(currentNode.location, it.location).toInt()
        } ?: 0

        return when (type) {
            WaypointType.START -> "Start navigation"
            WaypointType.END -> "You have arrived at your destination"
            WaypointType.TURN_LEFT -> "Turn left and walk $distanceToNext meters"
            WaypointType.TURN_RIGHT -> "Turn right and walk $distanceToNext meters"
            WaypointType.CONTINUE_STRAIGHT -> "Continue straight for $distanceToNext meters"
            WaypointType.ENTER_BUILDING -> "Enter the building"
            WaypointType.EXIT_BUILDING -> "Exit the building"
            WaypointType.STAIRS_UP -> "Take the stairs up"
            WaypointType.STAIRS_DOWN -> "Take the stairs down"
            WaypointType.ELEVATOR -> "Take the elevator"
            WaypointType.LANDMARK -> "Pass by ${currentNode.id}"
        }
    }

    private fun generateNavigationSteps(
        pathNodes: List<CampusGraph.GraphNode>,
        waypoints: List<Waypoint>
    ): List<NavigationStep> {
        val steps = mutableListOf<NavigationStep>()

        for (i in 0 until pathNodes.lastIndex) {
            val currentNode = pathNodes[i]
            val nextNode = pathNodes[i + 1]
            val waypoint = waypoints[i]

            val distance = haversineDistance(currentNode.location, nextNode.location)
            val direction = waypointTypeToDirection(waypoint.type)

            val isIndoor = currentNode.buildingId != null &&
                    currentNode.buildingId == nextNode.buildingId

            steps.add(NavigationStep(
                instruction = waypoint.instruction ?: "Continue",
                distance = distance,
                direction = direction,
                startLocation = currentNode.location,
                endLocation = nextNode.location,
                isIndoor = isIndoor
            ))
        }

        if (pathNodes.isNotEmpty()) {
            val lastNode = pathNodes.last()
            steps.add(NavigationStep(
                instruction = "You have arrived",
                distance = 0.0,
                direction = Direction.ARRIVE,
                startLocation = lastNode.location,
                endLocation = lastNode.location,
                isIndoor = lastNode.buildingId != null
            ))
        }

        return steps
    }

    private fun waypointTypeToDirection(type: WaypointType): Direction {
        return when (type) {
            WaypointType.START -> Direction.FORWARD
            WaypointType.END -> Direction.ARRIVE
            WaypointType.TURN_LEFT -> Direction.LEFT
            WaypointType.TURN_RIGHT -> Direction.RIGHT
            WaypointType.CONTINUE_STRAIGHT -> Direction.FORWARD
            WaypointType.ENTER_BUILDING -> Direction.FORWARD
            WaypointType.EXIT_BUILDING -> Direction.FORWARD
            WaypointType.STAIRS_UP -> Direction.FORWARD
            WaypointType.STAIRS_DOWN -> Direction.FORWARD
            WaypointType.ELEVATOR -> Direction.FORWARD
            WaypointType.LANDMARK -> Direction.FORWARD
        }
    }

    private fun calculateTotalDistance(pathNodes: List<CampusGraph.GraphNode>): Double {
        var total = 0.0
        for (i in 0 until pathNodes.lastIndex) {
            total += haversineDistance(pathNodes[i].location, pathNodes[i + 1].location)
        }
        return total
    }

    /**
     * Haversine formula for distance between two coordinates
     */
    private fun haversineDistance(from: CampusLocation, to: CampusLocation): Double {
        val earthRadius = 6371000.0 // meters

        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = Math.toRadians(to.latitude - from.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)

        // Using .pow(2.0) from kotlin.math
        val a = sin(deltaLat / 2).pow(2.0) +
                cos(lat1) * cos(lat2) * sin(deltaLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    private fun createDirectRoute(start: CampusLocation, end: CampusLocation): Route {
        val distance = haversineDistance(start, end)

        return Route(
            id = generateRouteId(),
            origin = start,
            destination = end,
            waypoints = listOf(
                Waypoint(start, WaypointType.START, "Start"),
                Waypoint(end, WaypointType.END, "You have arrived")
            ),
            totalDistance = distance,
            estimatedTime = (distance / 1.4).toLong(),
            steps = listOf(
                NavigationStep(
                    instruction = "Walk to your destination",
                    distance = distance,
                    direction = Direction.FORWARD,
                    startLocation = start,
                    endLocation = end,
                    isIndoor = false
                )
            )
        )
    }

    private fun generateRouteId(): String {
        return "route_${System.currentTimeMillis()}_${(0..999).random()}"
    }

    // NOTE: Removed the extension function that was causing the error
    // private fun Double.pow(n: Int): Double = kotlin.math.pow(this, n.toDouble())
}