package com.campus.arnav.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.BuildingType
import com.campus.arnav.data.model.CampusLocation
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
    val imageUrl: String?,
    val entrancesJson: String = "[]"  // NEW: stores entrances as JSON string
) {
    fun toBuilding(): Building {
        // Parse entrances from JSON
        val entrances: List<CampusLocation> = try {
            val type = object : TypeToken<List<EntranceJson>>() {}.type
            val parsed: List<EntranceJson> = Gson().fromJson(entrancesJson, type)
            parsed.map {
                CampusLocation(
                    id = it.id,
                    latitude = it.latitude,
                    longitude = it.longitude
                )
            }
        } catch (e: Exception) {
            emptyList()
        }

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
            type = try {
                BuildingType.valueOf(type)
            } catch (e: Exception) {
                BuildingType.ACADEMIC
            },
            isAccessible = isAccessible,
            imageUrl = imageUrl,
            entrances = entrances
        )
    }

    companion object {
        fun fromBuilding(building: Building): BuildingEntity {
            // Serialize entrances to JSON
            val entrancesJson = Gson().toJson(
                building.entrances.map {
                    EntranceJson(it.id, it.latitude, it.longitude)
                }
            )

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
                imageUrl = building.imageUrl,
                entrancesJson = entrancesJson
            )
        }
    }
}

/**
 * Simple JSON-friendly class for entrance serialization
 */
data class EntranceJson(
    val id: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)