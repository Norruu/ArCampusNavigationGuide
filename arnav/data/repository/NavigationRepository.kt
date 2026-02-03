package com.campus.arnav.data.repository

import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.data.model.Direction
import com.campus.arnav.data.model.NavigationStep
import com.campus.arnav.data.model.Route
import com.campus.arnav.data.model.Waypoint
import com.campus.arnav.data.model.WaypointType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class NavigationRepository @Inject constructor() {

    /**
     * Calculate route between two locations
     */
    suspend fun calculateRoute(
        start: CampusLocation,
        end: CampusLocation
    ): Route? = withContext(Dispatchers.Default) {
        try {
            // Calculate direct distance
            val distance = calculateDistance(start, end)

            // Estimate walking time (1.4 m/s average walking speed)
            val estimatedTime = (distance / 1.4).toLong()

            // Generate simple waypoints (in a real app, this would use pathfinding)
            val waypoints = generateWaypoints(start, end)

            // Generate navigation steps
            val steps = generateSteps(waypoints)

            Route(
                id = UUID.randomUUID().toString(),
                origin = start,
                destination = end,
                waypoints = waypoints,
                totalDistance = distance,
                estimatedTime = estimatedTime,
                steps = steps
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generate waypoints between start and end
     */
    private fun generateWaypoints(start: CampusLocation, end: CampusLocation): List<Waypoint> {
        val waypoints = mutableListOf<Waypoint>()

        // Start waypoint
        waypoints.add(
            Waypoint(
                location = start,
                type = WaypointType.START,
                instruction = "Start navigation"
            )
        )

        // Calculate intermediate points (simple linear interpolation)
        val distance = calculateDistance(start, end)
        val numIntermediatePoints = (distance / 50).toInt().coerceIn(1, 10) // One point every 50m, max 10

        for (i in 1 until numIntermediatePoints) {
            val fraction = i.toDouble() / numIntermediatePoints
            val lat = start.latitude + (end.latitude - start.latitude) * fraction
            val lon = start.longitude + (end.longitude - start.longitude) * fraction

            waypoints.add(
                Waypoint(
                    location = CampusLocation(
                        id = "waypoint_$i",
                        latitude = lat,
                        longitude = lon
                    ),
                    type = WaypointType.CONTINUE_STRAIGHT,
                    instruction = "Continue straight"
                )
            )
        }

        // End waypoint
        waypoints.add(
            Waypoint(
                location = end,
                type = WaypointType.END,
                instruction = "You have arrived"
            )
        )

        return waypoints
    }

    /**
     * Generate navigation steps from waypoints
     */
    private fun generateSteps(waypoints: List<Waypoint>): List<NavigationStep> {
        val steps = mutableListOf<NavigationStep>()

        for (i in 0 until waypoints.size - 1) {
            val current = waypoints[i]
            val next = waypoints[i + 1]
            val distance = calculateDistance(current.location, next.location)

            val direction = when (next.type) {
                WaypointType.TURN_LEFT -> Direction.LEFT
                WaypointType.TURN_RIGHT -> Direction.RIGHT
                WaypointType.END -> Direction.ARRIVE
                else -> Direction.FORWARD
            }

            val instruction = when (next.type) {
                WaypointType.START -> "Start walking"
                WaypointType.END -> "You have arrived at your destination"
                WaypointType.TURN_LEFT -> "Turn left"
                WaypointType.TURN_RIGHT -> "Turn right"
                else -> "Continue straight for ${distance.toInt()} meters"
            }

            steps.add(
                NavigationStep(
                    instruction = instruction,
                    distance = distance,
                    direction = direction,
                    startLocation = current.location,
                    endLocation = next.location,
                    isIndoor = false
                )
            )
        }

        return steps
    }

    /**
     * Calculate distance between two locations using Haversine formula
     */
    private fun calculateDistance(from: CampusLocation, to: CampusLocation): Double {
        val earthRadius = 6371000.0 // meters

        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = Math.toRadians(to.latitude - from.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)

        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(deltaLon / 2) * sin(deltaLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }
}