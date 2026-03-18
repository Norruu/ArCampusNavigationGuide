package com.campus.arnav.data.repository

import android.util.Log
import com.campus.arnav.data.CampusDataProvider
import com.campus.arnav.data.local.dao.BuildingDao
import com.campus.arnav.data.local.entity.BuildingEntity
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.data.remote.firestore.FirestoreDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import com.campus.arnav.data.remote.firestore.FirestoreRoad
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CampusRepository @Inject constructor(
    private val buildingDao: BuildingDao,
    private val firestoreDataSource: FirestoreDataSource
) {
    companion object {
        private const val TAG = "CampusRepository"
    }

    private var buildingsCache: List<Building>? = null
    private var pathNodesCache: List<PathNode>? = null
    private var connectionsCache: List<PathConnection>? = null

    /**
     * Get all buildings using 3-tier fallback:
     * 1. In-memory cache (fastest)
     * 2. Room database (offline cache, synced from Firestore)
     * 3. CampusDataProvider (hardcoded fallback — your existing 40+ buildings)
     */
    suspend fun getAllBuildings(): List<Building> = withContext(Dispatchers.IO) {
        // 1. Return cache if available
        buildingsCache?.let { return@withContext it }

        // 2. Try Room database (populated by FirestoreSyncManager)
        val dbBuildings = buildingDao.getAllBuildings().map { it.toBuilding() }
        if (dbBuildings.isNotEmpty()) {
            Log.d(TAG, "Loaded ${dbBuildings.size} buildings from Room (synced from Firestore)")
            buildingsCache = dbBuildings
            return@withContext dbBuildings
        }

        // 3. Try one-shot Firestore fetch (first launch, before sync kicks in)
        try {
            val firestoreBuildings = firestoreDataSource.getBuildings()
            if (firestoreBuildings.isNotEmpty()) {
                val buildings = firestoreBuildings.map { it.toBuilding() }
                Log.d(TAG, "Loaded ${buildings.size} buildings directly from Firestore")

                // Save to Room for offline use
                val entities = buildings.map { BuildingEntity.fromBuilding(it) }
                buildingDao.deleteAllBuildings()
                buildingDao.insertBuildings(entities)

                buildingsCache = buildings
                return@withContext buildings
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore fetch failed, falling back to hardcoded data", e)
        }

        // 4. Final fallback — hardcoded CampusDataProvider (your existing data)
        Log.d(TAG, "Using hardcoded CampusDataProvider as fallback")
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
            it.name.contains(query, ignoreCase = true) ||
                    it.shortName.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
        }
    }

    // ==================== DYNAMIC PATHFINDING GRAPH ====================

    suspend fun getPathNodes(): List<PathNode> = withContext(Dispatchers.IO) {
        pathNodesCache?.let { return@withContext it }
        // Fallback to empty if sync hasn't happened yet
        emptyList()
    }

    suspend fun getPathConnections(): List<PathConnection> = withContext(Dispatchers.IO) {
        connectionsCache?.let { return@withContext it }
        // Fallback to empty if sync hasn't happened yet
        emptyList()
    }

    /**
     * Converts drawn FirestoreRoad polylines into a routing graph.
     * Call this whenever roads are fetched from Firebase!
     */
    fun updateRoadGraph(roads: List<FirestoreRoad>) {
        val nodes = mutableMapOf<String, PathNode>()
        val connections = mutableListOf<PathConnection>()

        roads.forEach { road ->
            val points = road.roadNodes
            for (i in points.indices) {
                val lat = points[i]["lat"] ?: 0.0
                val lng = points[i]["lng"] ?: 0.0

                // Create a unique ID based on the coordinate.
                // This automatically links intersecting roads if you tap the exact same spot!
                val nodeId = "node_${lat}_${lng}"

                if (!nodes.containsKey(nodeId)) {
                    nodes[nodeId] = PathNode(nodeId, CampusLocation(nodeId, lat, lng))
                }

                // Connect this node to the previous node on the same road
                if (i > 0) {
                    val prevLat = points[i - 1]["lat"] ?: 0.0
                    val prevLng = points[i - 1]["lng"] ?: 0.0
                    val prevNodeId = "node_${prevLat}_${prevLng}"

                    // Add a two-way connection so users can walk both directions
                    connections.add(PathConnection(prevNodeId, nodeId, true))
                    connections.add(PathConnection(nodeId, prevNodeId, true))
                }
            }
        }

        pathNodesCache = nodes.values.toList()
        connectionsCache = connections.distinct()
        Log.d(TAG, "Generated Routing Graph: ${pathNodesCache?.size} nodes, ${connectionsCache?.size} connections")
    }

    fun clearCache() {
        buildingsCache = null
        pathNodesCache = null
        connectionsCache = null
    }

    // ==================== REAL-TIME FLOWS ====================
    fun getRoadsFlow(): Flow<List<FirestoreRoad>> {
        return firestoreDataSource.getRoadsFlow()
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