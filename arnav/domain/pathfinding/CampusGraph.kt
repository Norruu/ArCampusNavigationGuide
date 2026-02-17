package com.campus.arnav.domain.pathfinding

import com.campus.arnav.data.model.CampusLocation

class CampusGraph {
    val nodes = mutableMapOf<String, GraphNode>()
    val adjacencyList = mutableMapOf<String, MutableList<Edge>>()

    data class GraphNode(
        val id: String,
        val location: CampusLocation,
        val type: NodeType = NodeType.PATH // Default to PATH
    )

    data class Edge(
        val toNodeId: String,
        val distance: Double,
        val isAccessible: Boolean = true
    )

    enum class NodeType {
        PATH,
        ENTRANCE, // <--- CRITICAL: Used to find Gates
        BUILDING
    }

    fun addNode(node: GraphNode) {
        nodes[node.id] = node
        adjacencyList[node.id] = mutableListOf()
    }

    fun addEdge(fromId: String, toId: String, distance: Double, isAccessible: Boolean = true) {
        adjacencyList[fromId]?.add(Edge(toId, distance, isAccessible))
        adjacencyList[toId]?.add(Edge(fromId, distance, isAccessible)) // Bidirectional
    }

    /**
     * Find nearest node, optionally filtering by type (e.g., Find nearest GATE)
     */
    fun findNearestNode(location: CampusLocation, type: NodeType? = null): GraphNode? {
        return nodes.values
            .filter { type == null || it.type == type } // Filter by type if requested
            .minByOrNull { node ->
                calculateDistance(location, node.location)
            }
    }

    private fun calculateDistance(p1: CampusLocation, p2: CampusLocation): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(p1.latitude)) * Math.cos(Math.toRadians(p2.latitude)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}