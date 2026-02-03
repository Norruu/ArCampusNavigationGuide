package com.campus.arnav.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.data.model.Direction           // <-- Make sure this import exists
import com.campus.arnav.data.model.NavigationStep      // <-- Make sure this import exists

@Entity(
    tableName = "navigation_steps",
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
data class NavigationStepEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routeId: String,
    val orderIndex: Int,
    val instruction: String,
    val distance: Double,
    val direction: String,
    val startLatitude: Double,
    val startLongitude: Double,
    val startAltitude: Double,
    val endLatitude: Double,
    val endLongitude: Double,
    val endAltitude: Double,
    val isIndoor: Boolean
) {
    fun toNavigationStep(): NavigationStep {
        return NavigationStep(
            instruction = instruction,
            distance = distance,
            direction = Direction.valueOf(direction),
            startLocation = CampusLocation(
                id = "step_start_$id",
                latitude = startLatitude,
                longitude = startLongitude,
                altitude = startAltitude
            ),
            endLocation = CampusLocation(
                id = "step_end_$id",
                latitude = endLatitude,
                longitude = endLongitude,
                altitude = endAltitude
            ),
            isIndoor = isIndoor
        )
    }

    companion object {
        fun fromNavigationStep(
            step: NavigationStep,
            routeId: String,
            orderIndex: Int
        ): NavigationStepEntity {
            return NavigationStepEntity(
                routeId = routeId,
                orderIndex = orderIndex,
                instruction = step.instruction,
                distance = step.distance,
                direction = step.direction.name,
                startLatitude = step.startLocation.latitude,
                startLongitude = step.startLocation.longitude,
                startAltitude = step.startLocation.altitude,
                endLatitude = step.endLocation.latitude,
                endLongitude = step.endLocation.longitude,
                endAltitude = step.endLocation.altitude,
                isIndoor = step.isIndoor
            )
        }
    }
}