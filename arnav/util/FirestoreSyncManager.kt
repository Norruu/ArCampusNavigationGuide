package com.campus.arnav.util

import android.util.Log
import com.campus.arnav.data.local.dao.BuildingDao
import com.campus.arnav.data.local.entity.BuildingEntity
import com.campus.arnav.data.remote.firestore.*
import com.campus.arnav.data.repository.CampusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreSyncManager @Inject constructor(
    private val firestoreDataSource: FirestoreDataSource,
    private val buildingDao: BuildingDao,
    private val campusRepository: CampusRepository
) {
    companion object {
        private const val TAG = "FirestoreSyncManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isSyncing = false

    var cachedGeofences: List<FirestoreGeofence> = emptyList()
        private set

    var cachedMarkers: List<FirestoreMarker> = emptyList()
        private set

    var cachedCategories: List<FirestoreCategory> = emptyList()
        private set

    var cachedRoads: List<FirestoreRoad> = emptyList()
        private set

    fun startSync() {
        if (isSyncing) return
        isSyncing = true

        Log.d(TAG, "Starting Firestore sync for all collections...")

        scope.launch {
            firestoreDataSource.getBuildingsFlow()
                .catch { e -> Log.e(TAG, "Error in buildings sync", e) }
                .collect { firestoreBuildings ->
                    Log.d(TAG, "Syncing ${firestoreBuildings.size} buildings from Firestore → Room")
                    if (firestoreBuildings.isNotEmpty()) {
                        val buildings = firestoreBuildings.map { it.toBuilding() }
                        val entities = buildings.map { BuildingEntity.fromBuilding(it) }
                        buildingDao.deleteAllBuildings()
                        buildingDao.insertBuildings(entities)
                        campusRepository.clearCache()
                        Log.d(TAG, "Buildings sync complete: ${entities.size}")
                    }
                }
        }

        scope.launch {
            firestoreDataSource.getMarkersFlow()
                .catch { e -> Log.e(TAG, "Error in markers sync", e) }
                .collect { markers ->
                    cachedMarkers = markers
                    Log.d(TAG, "Markers sync complete: ${cachedMarkers.size}")
                }
        }

        scope.launch {
            firestoreDataSource.getCategoriesFlow()
                .catch { e -> Log.e(TAG, "Error in categories sync", e) }
                .collect { categories ->
                    cachedCategories = categories.sortedBy { it.order }
                    Log.d(TAG, "Categories sync complete: ${cachedCategories.size}")
                }
        }

        scope.launch {
            firestoreDataSource.getGeofencesFlow()
                .catch { e -> Log.e(TAG, "Error in geofences sync", e) }
                .collect { geofences ->
                    cachedGeofences = geofences.filter { it.isActive }
                    Log.d(TAG, "Geofences sync complete: ${cachedGeofences.size} active")
                }
        }

        scope.launch {
            firestoreDataSource.getRoadsFlow()
                .catch { e -> Log.e(TAG, "Error in roads sync", e) }
                .collect { roads ->
                    cachedRoads = roads

                    // Trigger the Graph conversion immediately!
                    campusRepository.updateRoadGraph(roads)

                    Log.d(TAG, "Roads sync complete: ${cachedRoads.size}")
                }
        }
    }
}