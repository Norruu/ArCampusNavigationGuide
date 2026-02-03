package com.campus.arnav.domain.pathfinding

import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.CampusLocation

/**
 * Helper class to build the campus navigation graph
 *
 * Usage:
 * val graph = CampusGraphBuilder()
 *     .addBuilding(libraryBuilding)
 *     .addPathNode("path_1", location1)
 *     .addPathNode("path_2", location2)
 *     .connectPath("path_1", "path_2")
 *     .connectBuildingEntrance("library", "path_1")
 *     .build()
 */
class CampusGraphBuilder {

    private val graph = CampusGraph()
    private val buildings = mutableMapOf<String, Building>()

    /**
     * Add a building with its entrances
     */
    fun addBuilding(building: Building): CampusGraphBuilder {
        buildings[building.id] = building

        // Add main building location as destination node
        graph.addNode(CampusGraph.GraphNode(
            id = building.id,
            location = building.location,
            type = CampusGraph.NodeType.DESTINATION,
            buildingId = building.id
        ))

        // Add entrance nodes
        building.entrances.forEachIndexed { index, entrance ->
            val entranceId = "${building.id}_entrance_$index"
            graph.addNode(CampusGraph.GraphNode(
                id = entranceId,
                location = entrance,
                type = CampusGraph.NodeType.ENTRANCE,
                buildingId = building.id
            ))

            // Connect entrance to building
            graph.addEdge(entranceId, building.id, isIndoor = true)
        }

        return this
    }

    /**
     * Add an outdoor path node (intersection, corner, etc.)
     */
    fun addPathNode(
        id: String,
        location: CampusLocation
    ): CampusGraphBuilder {
        graph.addNode(CampusGraph.GraphNode(
            id = id,
            location = location,
            type = CampusGraph.NodeType.OUTDOOR_PATH
        ))
        return this
    }

    /**
     * Add an indoor junction node
     */
    fun addIndoorJunction(
        id: String,
        location: CampusLocation,
        buildingId: String,
        floor: Int
    ): CampusGraphBuilder {
        graph.addNode(CampusGraph.GraphNode(
            id = id,
            location = location,
            type = CampusGraph.NodeType.INDOOR_JUNCTION,
            buildingId = buildingId,
            floor = floor
        ))
        return this
    }

    /**
     * Add stairs node
     */
    fun addStairs(
        id: String,
        location: CampusLocation,
        buildingId: String,
        floors: List<Int>
    ): CampusGraphBuilder {
        floors.forEach { floor ->
            val stairsId = "${id}_floor_$floor"
            val floorLocation = location.copy(
                id = stairsId,
                altitude = floor * 3.0 // Assume 3m per floor
            )

            graph.addNode(CampusGraph.GraphNode(
                id = stairsId,
                location = floorLocation,
                type = CampusGraph.NodeType.STAIRS,
                buildingId = buildingId,
                floor = floor
            ))
        }

        // Connect stairs between floors
        for (i in 0 until floors.lastIndex) {
            graph.addEdge(
                "${id}_floor_${floors[i]}",
                "${id}_floor_${floors[i + 1]}",
                isIndoor = true,
                isAccessible = false // Stairs are not accessible
            )
        }

        return this
    }

    /**
     * Add elevator node
     */
    fun addElevator(
        id: String,
        location: CampusLocation,
        buildingId: String,
        floors: List<Int>
    ): CampusGraphBuilder {
        floors.forEach { floor ->
            val elevatorId = "${id}_floor_$floor"
            val floorLocation = location.copy(
                id = elevatorId,
                altitude = floor * 3.0
            )

            graph.addNode(CampusGraph.GraphNode(
                id = elevatorId,
                location = floorLocation,
                type = CampusGraph.NodeType.ELEVATOR,
                buildingId = buildingId,
                floor = floor
            ))
        }

        // Connect elevator between all floors (bidirectional)
        for (i in floors.indices) {
            for (j in i + 1..floors.lastIndex) {
                graph.addEdge(
                    "${id}_floor_${floors[i]}",
                    "${id}_floor_${floors[j]}",
                    isIndoor = true,
                    isAccessible = true // Elevators are accessible
                )
            }
        }

        return this
    }

    /**
     * Connect two path nodes (outdoor)
     */
    fun connectPath(
        fromId: String,
        toId: String,
        isAccessible: Boolean = true
    ): CampusGraphBuilder {
        graph.addEdge(fromId, toId, isIndoor = false, isAccessible = isAccessible)
        return this
    }

    /**
     * Connect indoor nodes
     */
    fun connectIndoor(
        fromId: String,
        toId: String,
        isAccessible: Boolean = true
    ): CampusGraphBuilder {
        graph.addEdge(fromId, toId, isIndoor = true, isAccessible = isAccessible)
        return this
    }

    /**
     * Connect a building entrance to a path node
     */
    fun connectBuildingToPath(
        buildingId: String,
        entranceIndex: Int,
        pathNodeId: String
    ): CampusGraphBuilder {
        val entranceId = "${buildingId}_entrance_$entranceIndex"
        graph.addEdge(entranceId, pathNodeId, isIndoor = false)
        return this
    }

    /**
     * Build the final graph
     */
    fun build(): CampusGraph = graph

    /**
     * Get the current graph (for inspection)
     */
    fun getGraph(): CampusGraph = graph
}