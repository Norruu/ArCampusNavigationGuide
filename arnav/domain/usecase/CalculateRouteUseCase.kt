package com.campus.arnav.domain.usecase

import com.campus.arnav.data.CampusDataProvider
import com.campus.arnav.data.local.dao.BuildingDao
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.domain.pathfinding.PathConnection
import com.campus.arnav.domain.pathfinding.PathNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CampusRepository @Inject constructor(
    private val buildingDao: BuildingDao
) {

    // Cache
    private var buildingsCache: List<Building>? = null
    private var pathNodesCache: List<PathNode>? = null
    private var connectionsCache: List<PathConnection>? = null

    /**
     * Get all campus buildings
     */
    suspend fun getAllBuildings(): List<Building> = withContext(Dispatchers.IO) {
        buildingsCache?.let { return@withContext it }

        // Try to load from database
        val dbBuildings = buildingDao.getAllBuildings().map { it.toBuilding() }

        if (dbBuildings.isNotEmpty()) {
            buildingsCache = dbBuildings
            return@withContext dbBuildings
        }

        // Fall back to sample data
        val sampleBuildings = CampusDataProvider.getSampleBuildings()
        buildingsCache = sampleBuildings
        sampleBuildings
    }

    /**
     * Get building by ID
     */
    suspend fun getBuildingById(id: String): Building? = withContext(Dispatchers.IO) {
        getAllBuildings().find { it.id == id }
    }

    /**
     * Search buildings
     */
    suspend fun searchBuildings(query: String): List<Building> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        getAllBuildings().filter { building ->
            building.name.contains(query, ignoreCase = true) ||
                    building.shortName.contains(query, ignoreCase = true) ||
                    building.description.contains(query, ignoreCase = true)
        }
    }

    /**
     * Get path nodes for navigation graph
     */
    suspend fun getPathNodes(): List<PathNode> = withContext(Dispatchers.IO) {
        pathNodesCache?.let { return@withContext it }

        val nodes = generatePathNodes()
        pathNodesCache = nodes
        nodes
    }

    /**
     * Get path connections for navigation graph
     */
    suspend fun getPathConnections(): List<PathConnection> = withContext(Dispatchers.IO) {
        connectionsCache?.let { return@withContext it }

        val connections = generatePathConnections()
        connectionsCache = connections
        connections
    }

    /**
     * Generate path nodes (would typically come from a backend)
     */
    private fun generatePathNodes(): List<PathNode> {
        return listOf(
            PathNode("path_1", CampusLocation("p1", 37.4275, -122.1695)),
            PathNode("path_2", CampusLocation("p2", 37.4275, -122.1702)),
            PathNode("path_3", CampusLocation("p3", 37.4270, -122.1695)),
            PathNode("path_4", CampusLocation("p4", 37.4268, -122.1700))
        )
    }

    private fun generatePathConnections(): List<PathConnection> {
        return listOf(
            PathConnection("path_1", "path_2", true),
            PathConnection("path_1", "path_3", true),
            PathConnection("path_2", "path_4", true),
            PathConnection("path_3", "path_4", true),
            PathConnection("library_entrance_0", "path_2", true),
            PathConnection("science_hall_entrance_0", "path_3", true),
            PathConnection("student_center_entrance_0", "path_4", true)
        )
    }

    /**
     * Clear cache
     */
    fun clearCache() {
        buildingsCache = null
        pathNodesCache = null
        connectionsCache = null
    }
}

// REMOVED: PathNode and PathConnection classes from here
// They are now in domain/pathfinding/PathfindingEngine.kt