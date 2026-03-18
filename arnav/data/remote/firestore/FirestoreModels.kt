package com.campus.arnav.data.remote.firestore

import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.BuildingType
import com.campus.arnav.data.model.CampusLocation

/**
 * Firestore document model for buildings.
 * Matches the Firestore schema the Admin app writes to.
 */
data class FirestoreBuilding(
    val id: String = "",
    val name: String = "",
    val shortName: String = "",
    val description: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val type: String = "ACADEMIC",
    val isAccessible: Boolean = true,
    val imageUrl: String? = null,
    val entrances: List<FirestoreEntrance> = emptyList()
) {
    /**
     * Convert Firestore document → domain Building model
     */
    fun toBuilding(): Building {
        return Building(
            id = id,
            name = name,
            shortName = shortName,
            description = description,
            location = CampusLocation(
                id = id,
                latitude = latitude,
                longitude = longitude,
                altitude = altitude
            ),
            type = BuildingType.fromString(type),
            isAccessible = isAccessible,
            imageUrl = imageUrl,
            entrances = entrances.map { entrance ->
                CampusLocation(
                    id = entrance.id,
                    latitude = entrance.latitude,
                    longitude = entrance.longitude
                )
            }
        )
    }
}

data class FirestoreEntrance(
    val id: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

/**
 * Firestore document model for markers
 */
data class FirestoreMarker(
    val id: String = "",
    val buildingId: String = "",
    val categoryId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val title: String = "",
    val description: String = "",
    val iconType: String = ""
)

/**
 * Firestore document model for categories
 */
data class FirestoreCategory(
    val id: String = "",
    val name: String = "",
    val icon: String = "",
    val color: String = "",
    val order: Int = 0
)

/**
 * Firestore document model for geofences
 */

data class FirestoreGeofence(
    val id: String = "",
    val buildingId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radius: Double = 50.0,
    val triggerOnEnter: Boolean = true,
    val triggerOnExit: Boolean = true,
    val enterMessage: String = "",
    val exitMessage: String = "",
    val isActive: Boolean = true,
    val polygonPoints: List<Map<String, Double>>? = null
)

data class FirestoreRoad(
    val id: String = "",
    val name: String = "",
    val roadNodes: List<Map<String, Double>> = emptyList()
)