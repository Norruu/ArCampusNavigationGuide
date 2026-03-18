package com.campus.arnav.util

import android.util.Log
import com.campus.arnav.data.CampusDataProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * One-time utility to seed CampusDataProvider buildings into Firestore.
 * Run this ONCE, then delete or disable it.
 */
object FirestoreSeeder {

    private const val TAG = "FirestoreSeeder"

    suspend fun seedBuildings() = withContext(Dispatchers.IO) {
        val firestore = FirebaseFirestore.getInstance()
        val buildings = CampusDataProvider.getSampleBuildings()

        Log.d(TAG, "Starting seed: ${buildings.size} buildings")

        buildings.forEach { building ->
            try {
                val data = hashMapOf(
                    "name" to building.name,
                    "shortName" to building.shortName,
                    "description" to building.description,
                    "latitude" to building.location.latitude,
                    "longitude" to building.location.longitude,
                    "altitude" to building.location.altitude,
                    "type" to building.type.name,
                    "isAccessible" to building.isAccessible,
                    "imageUrl" to (building.imageUrl ?: ""),
                    "entrances" to building.entrances.map { entrance ->
                        hashMapOf(
                            "id" to entrance.id,
                            "latitude" to entrance.latitude,
                            "longitude" to entrance.longitude
                        )
                    }
                )

                // Use building.id as the document ID
                firestore.collection("buildings")
                    .document(building.id)
                    .set(data)
                    .await()

                Log.d(TAG, "✅ Seeded: ${building.name}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to seed: ${building.name}", e)
            }
        }

        Log.d(TAG, "🎉 Seed complete! ${buildings.size} buildings uploaded to Firestore")
    }
}