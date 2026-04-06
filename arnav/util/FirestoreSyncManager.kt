package com.campus.arnav.util

import android.util.Log
import com.campus.arnav.data.local.dao.BuildingDao
import com.campus.arnav.data.local.entity.BuildingEntity
import com.campus.arnav.data.remote.firestore.*
import com.campus.arnav.data.repository.CampusRepository
import com.campus.arnav.domain.graph.AdminRoadSyncAdapter
import com.campus.arnav.domain.graph.UnifiedGraphManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FirestoreSyncManager — fixed v2
 *
 * Key fix: the unified graph sequence is now strictly ordered:
 *
 *   1. unifiedGraphManager.buildFromHardcodedPaths()   ← base graph
 *   2. adminRoadSyncAdapter.startSync()                ← layers admin roads on top
 *
 * Both steps run in the same coroutine so step 2 is guaranteed to start only
 * after step 1 completes.  Previously they were in separate coroutines which
 * meant admin roads could arrive before the base graph existed.
 *
 * The isBuilt guard inside UnifiedGraphManager means MapViewModel calling
 * buildFromHardcodedPaths() a second time is now harmless.
 */
@Singleton
class FirestoreSyncManager @Inject constructor(
    private val firestoreDataSource: FirestoreDataSource,
    private val buildingDao: BuildingDao,
    private val campusRepository: CampusRepository,
    private val adminRoadSyncAdapter: AdminRoadSyncAdapter,
    private val unifiedGraphManager: UnifiedGraphManager
) {
    companion object { private const val TAG = "FirestoreSyncManager" }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isSyncing = false

    var cachedGeofences:  List<FirestoreGeofence>  = emptyList(); private set
    var cachedMarkers:    List<FirestoreMarker>     = emptyList(); private set
    var cachedCategories: List<FirestoreCategory>   = emptyList(); private set
    var cachedRoads:      List<FirestoreRoad>       = emptyList(); private set

    fun startSync() {
        if (isSyncing) return
        isSyncing = true
        Log.d(TAG, "Starting Firestore sync…")

        // Buildings
        scope.launch {
            firestoreDataSource.getBuildingsFlow()
                .catch { e -> Log.e(TAG, "Buildings error", e) }
                .collect { list ->
                    if (list.isNotEmpty()) {
                        val entities = list.map { BuildingEntity.fromBuilding(it.toBuilding()) }
                        buildingDao.deleteAllBuildings()
                        buildingDao.insertBuildings(entities)
                        campusRepository.clearCache()
                        Log.d(TAG, "Buildings synced: ${entities.size}")
                    }
                }
        }

        // Markers
        scope.launch {
            firestoreDataSource.getMarkersFlow()
                .catch { e -> Log.e(TAG, "Markers error", e) }
                .collect { cachedMarkers = it }
        }

        // Categories
        scope.launch {
            firestoreDataSource.getCategoriesFlow()
                .catch { e -> Log.e(TAG, "Categories error", e) }
                .collect { cachedCategories = it.sortedBy { c -> c.order } }
        }

        // Geofences
        scope.launch {
            firestoreDataSource.getGeofencesFlow()
                .catch { e -> Log.e(TAG, "Geofences error", e) }
                .collect { cachedGeofences = it.filter { g -> g.isActive } }
        }

        // Roads → polyline cache (for map rendering)
        scope.launch {
            firestoreDataSource.getRoadsFlow()
                .catch { e -> Log.e(TAG, "Roads error", e) }
                .collect { roads ->
                    cachedRoads = roads
                    campusRepository.updateRoadGraph(roads)   // legacy graph kept for compat
                }
        }

        // ── Unified graph — ORDERED: base first, then admin roads on top ──────
        //
        // Both calls are in the SAME coroutine with sequential execution so
        // adminRoadSyncAdapter.startSync() is guaranteed to run only after
        // buildFromHardcodedPaths() has finished.
        //
        // The isBuilt guard in UnifiedGraphManager means if MapViewModel also
        // calls buildFromHardcodedPaths() concurrently, the second call is a
        // safe no-op and does not wipe the admin roads.
        scope.launch(Dispatchers.Default) {
            unifiedGraphManager.buildFromHardcodedPaths()   // step 1 — hardcoded base
            Log.i(TAG, "Unified base graph ready")
            adminRoadSyncAdapter.startSync()                // step 2 — admin roads layer
        }
    }
}