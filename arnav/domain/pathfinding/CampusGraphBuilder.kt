package com.campus.arnav.domain.pathfinding

import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.CampusLocation

class CampusGraphBuilder {
    private val graph = CampusGraph()

    fun addBuilding(building: Building): CampusGraphBuilder {
        // Add building center
        graph.addNode(CampusGraph.GraphNode(
            id = building.id,
            location = building.location,
            type = CampusGraph.NodeType.BUILDING
        ))

        // Add entrances
        building.entrances.forEachIndexed { index, entrance ->
            val entranceId = "${building.id}_entrance_$index"
            graph.addNode(CampusGraph.GraphNode(
                id = entranceId,
                location = entrance,
                type = CampusGraph.NodeType.PATH
            ))
            // Connect entrance to building center
            val dist = calculateDistance(building.location, entrance)
            graph.addEdge(building.id, entranceId, dist)
        }
        return this
    }

    /**
     * Add a generic path node
     */
    fun addPathNode(id: String, location: CampusLocation): CampusGraphBuilder {
        graph.addNode(CampusGraph.GraphNode(
            id = id,
            location = location,
            type = CampusGraph.NodeType.PATH
        ))
        return this
    }

    /**
     * Add a GATE (Entrance to Campus)
     * This is critical for the Hybrid Routing System
     */
    fun addGate(id: String, location: CampusLocation): CampusGraphBuilder {
        graph.addNode(CampusGraph.GraphNode(
            id = id,
            location = location,
            type = CampusGraph.NodeType.ENTRANCE // Mark as ENTRANCE
        ))
        return this
    }

    fun connectPath(id1: String, id2: String, isAccessible: Boolean = true): CampusGraphBuilder {
        val node1 = graph.nodes[id1]
        val node2 = graph.nodes[id2]

        if (node1 != null && node2 != null) {
            val dist = calculateDistance(node1.location, node2.location)
            graph.addEdge(id1, id2, dist, isAccessible)
        }
        return this
    }

    fun connectBuildingToPath(buildingId: String, entranceIndex: Int, pathId: String): CampusGraphBuilder {
        val entranceId = "${buildingId}_entrance_$entranceIndex"
        return connectPath(entranceId, pathId)
    }

    fun build(): CampusGraph {
        return graph
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