package com.campus.arnav.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Building(
    val id: String,
    val name: String,
    val shortName: String,
    val description: String,
    val location: CampusLocation,
    val type: BuildingType,
    val floors: List<Floor> = emptyList(),
    val entrances: List<CampusLocation> = emptyList(),
    val imageUrl: String? = null,
    val isAccessible: Boolean = true
) : Parcelable

enum class BuildingType {
    ACADEMIC,
    LIBRARY,
    CAFETERIA,
    SPORTS,
    ADMINISTRATIVE,
    LANDMARK;

    companion object {
        fun fromString(value: String): BuildingType {
            return try {
                valueOf(value.uppercase())
            } catch (e: Exception) {
                // Custom categories default to LANDMARK on the map
                LANDMARK
            }
        }
    }
}

@Parcelize
data class Floor(
    val number: Int,
    val name: String,
    val rooms: List<Room> = emptyList()
) : Parcelable

@Parcelize
data class Room(
    val id: String,
    val name: String,
    val number: String,
    val buildingId: String,
    val floorNumber: Int,
    val location: CampusLocation,
    val type: RoomType
) : Parcelable

enum class RoomType {
    CLASSROOM,
    OFFICE,
    LAB,
    RESTROOM,
    STAIRS,
    ENTRANCE
}

enum class BuildingCategory {
    ACADEMIC, OFFICE, CAFETERIA, LIBRARY, GYM, CLINIC, LAB, DORM, PARKING, OTHER
}