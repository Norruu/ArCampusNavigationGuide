package com.campus.arnav.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.BuildingType
import com.campus.arnav.data.model.CampusLocation

@Entity(tableName = "buildings")
data class BuildingEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val shortName: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val type: String,
    val isAccessible: Boolean,
    val imageUrl: String?
) {
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
            type = BuildingType.valueOf(type),
            isAccessible = isAccessible
        )
    }

    companion object {
        fun fromBuilding(building: Building): BuildingEntity {
            return BuildingEntity(
                id = building.id,
                name = building.name,
                shortName = building.shortName,
                description = building.description,
                latitude = building.location.latitude,
                longitude = building.location.longitude,
                altitude = building.location.altitude,
                type = building.type.name,
                isAccessible = building.isAccessible,
                imageUrl = building.imageUrl
            )
        }
    }
}