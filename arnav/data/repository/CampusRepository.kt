package com.campus.arnav.data.repository

import com.campus.arnav.data.CampusDataProvider
import com.campus.arnav.data.local.dao.BuildingDao
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.CampusLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CampusRepository @Inject constructor(
    private val buildingDao: BuildingDao
) {

    private var buildingsCache: List<Building>? = null
    private var pathNodesCache: List<PathNode>? = null
    private var connectionsCache: List<PathConnection>? = null

    suspend fun getAllBuildings(): List<Building> = withContext(Dispatchers.IO) {
        buildingsCache?.let { return@withContext it }
        val dbBuildings = buildingDao.getAllBuildings().map { it.toBuilding() }
        if (dbBuildings.isNotEmpty()) {
            buildingsCache = dbBuildings
            return@withContext dbBuildings
        }
        val sampleBuildings = CampusDataProvider.getSampleBuildings()
        buildingsCache = sampleBuildings
        sampleBuildings
    }

    suspend fun getBuildingById(id: String): Building? = withContext(Dispatchers.IO) {
        getAllBuildings().find { it.id == id }
    }

    suspend fun searchBuildings(query: String): List<Building> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        getAllBuildings().filter {
            it.name.contains(query, ignoreCase = true) || it.shortName.contains(query, ignoreCase = true)
        }
    }

    suspend fun getPathNodes(): List<PathNode> = withContext(Dispatchers.IO) {
        pathNodesCache?.let { return@withContext it }
        val nodes = generatePathNodes()
        pathNodesCache = nodes
        nodes
    }

    suspend fun getPathConnections(): List<PathConnection> = withContext(Dispatchers.IO) {
        connectionsCache?.let { return@withContext it }
        val connections = generatePathConnections()
        connectionsCache = connections
        connections
    }

    private fun generatePathNodes(): List<PathNode> {
        return listOf(PathNode("p1", CampusLocation("p1", 0.0, 0.0)))
    }

    private fun generatePathConnections(): List<PathConnection> {
        return listOf(PathConnection("p1", "p2", true))
    }

    fun clearCache() {
        buildingsCache = null
        pathNodesCache = null
        connectionsCache = null
    }
}

// --- DATA CLASSES ---
data class PathNode(
    val id: String,
    val location: CampusLocation
)

data class PathConnection(
    val fromId: String,
    val toId: String,
    val isAccessible: Boolean = true
)