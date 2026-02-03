package com.campus.arnav.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.data.model.Route
import com.campus.arnav.data.model.Waypoint

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey
    val id: String,
    val originLatitude: Double,
    val originLongitude: Double,
    val originAltitude: Double,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    val destinationAltitude: Double,
    val totalDistance: Double,
    val estimatedTime: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
) {
    fun toRoute(waypoints: List<Waypoint>, steps: List<com.campus.arnav.data.model.NavigationStep>): Route {
        return Route(
            id = id,
            origin = CampusLocation(
                id = "origin_$id",
                latitude = originLatitude,
                longitude = originLongitude,
                altitude = originAltitude
            ),
            destination = CampusLocation(
                id = "destination_$id",
                latitude = destinationLatitude,
                longitude = destinationLongitude,
                altitude = destinationAltitude
            ),
            waypoints = waypoints,
            totalDistance = totalDistance,
            estimatedTime = estimatedTime,
            steps = steps
        )
    }

    companion object {
        fun fromRoute(route: Route): RouteEntity {
            return RouteEntity(
                id = route.id,
                originLatitude = route.origin.latitude,
                originLongitude = route.origin.longitude,
                originAltitude = route.origin.altitude,
                destinationLatitude = route.destination.latitude,
                destinationLongitude = route.destination.longitude,
                destinationAltitude = route.destination.altitude,
                totalDistance = route.totalDistance,
                estimatedTime = route.estimatedTime
            )
        }
    }
}