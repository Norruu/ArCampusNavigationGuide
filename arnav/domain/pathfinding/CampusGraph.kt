package com.campus.arnav.domain.pathfinding

import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.data.model.WaypointType
import kotlin.math.*

/**
 * Graph representation of campus walkways for pathfinding
 */
class CampusGraph {

    private val nodes = mutableMapOf<String, GraphNode>()
    private val edges = mutableMapOf<String, MutableList<GraphEdge>>()

    data class GraphNode(
        val id: String,
        val location: CampusLocation,
        val type: NodeType,
        val buildingId: String? = null,
        val floor: Int? = null
    )

    enum class NodeType {
        OUTDOOR_PATH,
        ENTRANCE,
        INDOOR_JUNCTION,
        STAIRS,
        ELEVATOR,
        DESTINATION
    }

    data class GraphEdge(
        val fromId: String,
        val toId: String,
        val distance: Double,
        val isIndoor: Boolean,
        val isAccessible: Boolean = true,
        val elevationChange: Double = 0.0
    )

    fun addNode(node: GraphNode) {
        nodes[node.id] = node
        if (!edges.containsKey(node.id)) {
            edges[node.id] = mutableListOf()
        }
    }

    fun addEdge(fromId: String, toId: String, isIndoor: Boolean = false, isAccessible: Boolean = true) {
        val from = nodes[fromId] ?: return
        val to = nodes[toId] ?: return

        val distance = calculateDistance(from.location, to.location)
        val elevationChange = to.location.altitude - from.location.altitude

        // Add bidirectional edge
        edges.getOrPut(fromId) { mutableListOf() }.add(
            GraphEdge(fromId, toId, distance, isIndoor, isAccessible, elevationChange)
        )
        edges.getOrPut(toId) { mutableListOf() }.add(
            GraphEdge(toId, fromId, distance, isIndoor, isAccessible, -elevationChange)
        )
    }

    fun getNode(id: String): GraphNode? = nodes[id]

    fun getNeighbors(nodeId: String): List<GraphEdge> = edges[nodeId] ?: emptyList()

    fun findNearestNode(location: CampusLocation, type: NodeType? = null): GraphNode? {
        return nodes.values
            .filter { type == null || it.type == type }
            .minByOrNull { calculateDistance(it.location, location) }
    }

    fun getAllNodes(): Collection<GraphNode> = nodes.values

    private fun calculateDistance(from: CampusLocation, to: CampusLocation): Double {
        // Haversine formula for distance calculation
        val earthRadius = 6371000.0 // meters

        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = Math.toRadians(to.latitude - from.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)

        val a = sin(deltaLat / 2).pow(2) +
                cos(lat1) * cos(lat2) * sin(deltaLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }
}