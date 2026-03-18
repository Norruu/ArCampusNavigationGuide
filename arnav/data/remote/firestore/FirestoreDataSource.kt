package com.campus.arnav.data.remote.firestore

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "FirestoreDataSource"
    }

    // ==================== ONE-SHOT FETCH (for CampusRepository fallback) ====================

    suspend fun getBuildings(): List<FirestoreBuilding> {
        return try {
            val snapshot = firestore.collection("buildings").get().await()
            snapshot.documents.mapNotNull { doc ->
                try {
                    FirestoreBuilding(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        shortName = doc.getString("shortName") ?: "",
                        description = doc.getString("description") ?: "",
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        altitude = doc.getDouble("altitude") ?: 0.0,
                        type = doc.getString("type") ?: "ACADEMIC",
                        isAccessible = doc.getBoolean("isAccessible") ?: true,
                        imageUrl = doc.getString("imageUrl")
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing building: ${doc.id}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching buildings one-shot", e)
            emptyList()
        }
    }

    // ==================== REAL-TIME FLOWS ====================

    fun getBuildingsFlow(): Flow<List<FirestoreBuilding>> = callbackFlow {
        val listener = firestore.collection("buildings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Buildings listener error", error)
                    return@addSnapshotListener
                }
                val buildings = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        FirestoreBuilding(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            shortName = doc.getString("shortName") ?: "",
                            description = doc.getString("description") ?: "",
                            latitude = doc.getDouble("latitude") ?: 0.0,
                            longitude = doc.getDouble("longitude") ?: 0.0,
                            altitude = doc.getDouble("altitude") ?: 0.0,
                            type = doc.getString("type") ?: "ACADEMIC",
                            isAccessible = doc.getBoolean("isAccessible") ?: true,
                            imageUrl = doc.getString("imageUrl")
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing building: ${doc.id}", e)
                        null
                    }
                } ?: emptyList()

                trySend(buildings)
                Log.d(TAG, "Buildings updated: ${buildings.size}")
            }
        awaitClose { listener.remove() }
    }

    fun getMarkersFlow(): Flow<List<FirestoreMarker>> = callbackFlow {
        val listener = firestore.collection("markers")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Markers listener error", error)
                    return@addSnapshotListener
                }
                val markers = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        FirestoreMarker(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            description = doc.getString("description") ?: "",
                            buildingId = doc.getString("buildingId") ?: "",
                            latitude = doc.getDouble("latitude") ?: 0.0,
                            longitude = doc.getDouble("longitude") ?: 0.0,
                            iconType = doc.getString("iconType") ?: "",
                            categoryId = doc.getString("categoryId") ?: ""
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing marker: ${doc.id}", e)
                        null
                    }
                } ?: emptyList()

                trySend(markers)
                Log.d(TAG, "Markers updated: ${markers.size}")
            }
        awaitClose { listener.remove() }
    }

    fun getCategoriesFlow(): Flow<List<FirestoreCategory>> = callbackFlow {
        val listener = firestore.collection("categories")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Categories listener error", error)
                    return@addSnapshotListener
                }
                val categories = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        FirestoreCategory(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            icon = doc.getString("icon") ?: "",
                            color = doc.getString("color") ?: "",
                            order = doc.getLong("order")?.toInt() ?: 0
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing category: ${doc.id}", e)
                        null
                    }
                } ?: emptyList()

                trySend(categories)
                Log.d(TAG, "Categories updated: ${categories.size}")
            }
        awaitClose { listener.remove() }
    }

    fun getGeofencesFlow(): Flow<List<FirestoreGeofence>> = callbackFlow {
        val listener = firestore.collection("geofences")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Geofences listener error", error)
                    return@addSnapshotListener
                }
                val geofences = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        FirestoreGeofence(
                            id = doc.id,
                            buildingId = doc.getString("buildingId") ?: "",
                            latitude = doc.getDouble("latitude") ?: 0.0,
                            longitude = doc.getDouble("longitude") ?: 0.0,
                            radius = doc.getDouble("radius") ?: 50.0,
                            enterMessage = doc.getString("enterMessage") ?: "",
                            exitMessage = doc.getString("exitMessage") ?: "",
                            isActive = doc.getBoolean("isActive") ?: true,
                            triggerOnEnter = doc.getBoolean("triggerOnEnter") ?: true,
                            triggerOnExit = doc.getBoolean("triggerOnExit") ?: true,

                            polygonPoints = doc.get("polygonPoints") as? List<Map<String, Double>>
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing geofence: ${doc.id}", e)
                        null
                    }
                } ?: emptyList()

                trySend(geofences)
                Log.d(TAG, "Geofences updated: ${geofences.size}")
            }
        awaitClose { listener.remove() }
    }

    fun getRoadsFlow(): Flow<List<FirestoreRoad>> = callbackFlow {
        val listener = firestore.collection("roads")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Roads listener error", error)
                    return@addSnapshotListener
                }

                val roads = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        FirestoreRoad(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            roadNodes = doc.get("roadNodes") as? List<Map<String, Double>> ?: emptyList()
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing road: ${doc.id}", e)
                        null
                    }
                } ?: emptyList()

                trySend(roads)
                Log.d(TAG, "Roads updated: ${roads.size}")
            }
        awaitClose { listener.remove() }
    }
}