package com.campus.arnav.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.data.model.Waypoint
import com.campus.arnav.data.model.WaypointType  // <-- Make sure this import exists

@Entity(
    tableName = "waypoints",
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routeId")]
)
data class WaypointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routeId: String,
    val orderIndex: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val type: String,
    val instruction: String?
) {
    fun toWaypoint(): Waypoint {
        return Waypoint(
            location = CampusLocation(
                id = "waypoint_$id",
                latitude = latitude,
                longitude = longitude,
                altitude = altitude
            ),
            type = WaypointType.valueOf(type),
            instruction = instruction
        )
    }

    companion object {
        fun fromWaypoint(
            waypoint: Waypoint,
            routeId: String,
            orderIndex: Int
        ): WaypointEntity {
            return WaypointEntity(
                routeId = routeId,
                orderIndex = orderIndex,
                latitude = waypoint.location.latitude,
                longitude = waypoint.location.longitude,
                altitude = waypoint.location.altitude,
                type = waypoint.type.name,
                instruction = waypoint.instruction
            )
        }
    }
}