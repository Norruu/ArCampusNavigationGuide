package com.campus.arnav.domain.graph

import android.util.Log
import com.campus.arnav.data.remote.firestore.FirestoreDataSource
import com.campus.arnav.data.remote.firestore.FirestoreRoad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AdminRoadSyncAdapter
 * ────────────────────
 * Bridges Firestore real-time road updates with the [UnifiedGraphManager].
 *
 * Flow
 * ────
 * Firestore `roads` collection
 *   → [FirestoreDataSource.getRoadsFlow]
 *   → convert [FirestoreRoad] → [AdminRoad]
 *   → [UnifiedGraphManager.addAdminRoad] / [UnifiedGraphManager.removeAdminRoad]
 *
 * The graph is always kept in sync with what an admin saved in Firestore.
 * Changes are applied incrementally:
 * • New road   → addAdminRoad()
 * • Road gone  → removeAdminRoad()
 *
 * Call [startSync] once (e.g. from FirestoreSyncManager or MainActivity).
 * It is safe to call again – it cancels the previous job first.
 */
@Singleton
class AdminRoadSyncAdapter @Inject constructor(
    private val firestoreDataSource: FirestoreDataSource,
    private val graphManager: UnifiedGraphManager
) {
    companion object {
        private const val TAG = "AdminRoadSync"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Snapshot of road ids seen in the previous Firestore emission. */
    private var previousRoadIds = emptySet<String>()

    fun startSync() {
        scope.launch {
            firestoreDataSource.getRoadsFlow()
                .catch { e -> Log.e(TAG, "Road flow error", e) }
                .collect { firestoreRoads ->
                    applyRoadDiff(firestoreRoads)
                }
        }
        Log.i(TAG, "Admin road sync started")
    }

    // ── Diff algorithm ────────────────────────────────────────────────────────

    /**
     * Compute a minimal diff between the previous and current road sets:
     * - Roads no longer present → remove from graph.
     * - Roads with the same id but updated nodes → re-add (replace).
     * - New roads → add.
     */
    private fun applyRoadDiff(current: List<FirestoreRoad>) {
        val currentIds = current.map { it.id }.toSet()

        // Remove roads that disappeared
        val removed = previousRoadIds - currentIds
        removed.forEach { id ->
            graphManager.removeAdminRoad(id)
            Log.d(TAG, "Removed admin road: $id")
        }

        // Add / update roads
        current.forEach { road ->
            val adminRoad = road.toAdminRoad() ?: return@forEach
            graphManager.addAdminRoad(adminRoad)
            Log.d(TAG, "Added/updated admin road: ${road.id} (${road.roadNodes.size} nodes)")
        }

        previousRoadIds = currentIds
    }

    // ── Conversion ────────────────────────────────────────────────────────────

    /**
     * Convert a [FirestoreRoad] (raw Firestore document) into an [AdminRoad]
     * suitable for graph ingestion.
     *
     * Returns null if the road has fewer than 2 valid waypoints.
     */
    private fun FirestoreRoad.toAdminRoad(): AdminRoad? {
        val geoPoints = roadNodes.mapNotNull { node ->
            val lat = node["lat"] ?: return@mapNotNull null
            val lng = node["lng"] ?: return@mapNotNull null
            GeoPoint(lat, lng)
        }
        if (geoPoints.size < 2) return null

        // Infer road type from Firestore `type` field (optional)
        // Default to WALKWAY for admin-drawn roads
        return AdminRoad(
            id       = id,
            name     = name.ifBlank { "Admin Road $id" },
            roadNodes = geoPoints,
            roadType  = RoadType.WALKWAY
        )
    }
}