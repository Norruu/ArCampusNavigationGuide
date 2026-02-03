package com.campus.arnav.data.repository

import com.campus.arnav.data.model.*
import com.campus.arnav.data.remote.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteRouteRepository @Inject constructor(
    private val apiService: RoutingApiService
) {

    /**
     * Get route from remote server
     */
    suspend fun getRemoteRoute(
        start: CampusLocation,
        end: CampusLocation,
        preferAccessible: Boolean = false
    ): Route? = withContext(Dispatchers.IO) {
        try {
            val request = CampusRouteRequest(
                originLat = start.latitude,
                originLon = start.longitude,
                destinationLat = end.latitude,
                destinationLon = end.longitude,
                preferAccessible = preferAccessible
            )

            val response = apiService.getCampusRoute(request)

            if (response.isSuccessful) {
                response.body()?.toRoute(start, end)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get route using OSRM format
     */
    suspend fun getOSRMRoute(
        start: CampusLocation,
        end: CampusLocation
    ): Route? = withContext(Dispatchers.IO) {
        try {
            // OSRM uses lon,lat format
            val coordinates = "${start.longitude},${start.latitude};${end.longitude},${end.latitude}"

            val response = apiService.getRoute(coordinates)

            if (response.isSuccessful && response.body()?.code == "Ok") {
                response.body()?.toRoute(start, end)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Sync buildings from server
     */
    suspend fun syncBuildings(): List<Building>? = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getBuildings()

            if (response.isSuccessful) {
                response.body()?.map { it.toBuilding() }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Check if local data needs update
     */
    suspend fun needsUpdate(localVersion: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getCampusDataVersion()

            if (response.isSuccessful) {
                response.body()?.version != localVersion
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}

// ============== EXTENSION FUNCTIONS ==============

/**
 * Convert API response to domain model
 */
private fun CampusRouteResponse.toRoute(start: CampusLocation, end: CampusLocation): Route {
    return Route(
        id = id,
        origin = start,
        destination = end,
        waypoints = waypoints.map { it.toWaypoint() },
        totalDistance = distance,
        estimatedTime = duration,
        steps = steps.map { it.toNavigationStep() }
    )
}

private fun WaypointResponse.toWaypoint(): Waypoint {
    return Waypoint(
        location = CampusLocation(
            id = "",
            latitude = lat,
            longitude = lon
        ),
        type = try {
            WaypointType.valueOf(type.uppercase())
        } catch (e: Exception) {
            WaypointType.CONTINUE_STRAIGHT
        },
        instruction = instruction
    )
}

private fun StepResponse.toNavigationStep(): NavigationStep {
    return NavigationStep(
        instruction = instruction,
        distance = distance,
        direction = try {
            Direction.valueOf(direction.uppercase())
        } catch (e: Exception) {
            Direction.FORWARD
        },
        startLocation = CampusLocation("", startLat, startLon),
        endLocation = CampusLocation("", endLat, endLon),
        isIndoor = isIndoor
    )
}

private fun OSRMRouteResponse.toRoute(start: CampusLocation, end: CampusLocation): Route? {
    val route = routes.firstOrNull() ?: return null

    val waypoints = mutableListOf<Waypoint>()
    val steps = mutableListOf<NavigationStep>()

    // Add start waypoint
    waypoints.add(Waypoint(start, WaypointType.START, "Start navigation"))

    // Process OSRM steps
    route.legs.forEach { leg ->
        leg.steps.forEach { osrmStep ->
            val stepStart = CampusLocation(
                id = "",
                latitude = osrmStep.maneuver.location[1],
                longitude = osrmStep.maneuver.location[0]
            )

            val coords = osrmStep.geometry.coordinates
            val stepEnd = if (coords.isNotEmpty()) {
                CampusLocation(
                    id = "",
                    latitude = coords.last()[1],
                    longitude = coords.last()[0]
                )
            } else {
                stepStart
            }

            val direction = osrmManeuverToDirection(osrmStep.maneuver)
            val waypointType = directionToWaypointType(direction)

            waypoints.add(Waypoint(
                location = stepStart,
                type = waypointType,
                instruction = osrmStep.maneuver.instruction ?: generateInstruction(direction, osrmStep.distance)
            ))

            steps.add(NavigationStep(
                instruction = osrmStep.maneuver.instruction ?: generateInstruction(direction, osrmStep.distance),
                distance = osrmStep.distance,
                direction = direction,
                startLocation = stepStart,
                endLocation = stepEnd,
                isIndoor = false
            ))
        }
    }

    // Add end waypoint
    waypoints.add(Waypoint(end, WaypointType.END, "You have arrived"))

    return Route(
        id = "osrm_${System.currentTimeMillis()}",
        origin = start,
        destination = end,
        waypoints = waypoints,
        totalDistance = route.distance,
        estimatedTime = route.duration.toLong(),
        steps = steps
    )
}

private fun osrmManeuverToDirection(maneuver: OSRMManeuver): Direction {
    return when (maneuver.type) {
        "arrive" -> Direction.ARRIVE
        "turn" -> when (maneuver.modifier) {
            "left" -> Direction.LEFT
            "right" -> Direction.RIGHT
            "slight left" -> Direction.SLIGHT_LEFT
            "slight right" -> Direction.SLIGHT_RIGHT
            "sharp left" -> Direction.SHARP_LEFT
            "sharp right" -> Direction.SHARP_RIGHT
            "uturn" -> Direction.U_TURN
            else -> Direction.FORWARD
        }
        else -> Direction.FORWARD
    }
}

private fun directionToWaypointType(direction: Direction): WaypointType {
    return when (direction) {
        Direction.LEFT, Direction.SLIGHT_LEFT, Direction.SHARP_LEFT -> WaypointType.TURN_LEFT
        Direction.RIGHT, Direction.SLIGHT_RIGHT, Direction.SHARP_RIGHT -> WaypointType.TURN_RIGHT
        Direction.ARRIVE -> WaypointType.END
        Direction.U_TURN -> WaypointType.TURN_LEFT
        else -> WaypointType.CONTINUE_STRAIGHT
    }
}

private fun generateInstruction(direction: Direction, distance: Double): String {
    val distanceStr = if (distance < 1000) {
        "${distance.toInt()} m"
    } else {
        String.format("%.1f km", distance / 1000)
    }

    return when (direction) {
        Direction.FORWARD -> "Continue straight for $distanceStr"
        Direction.LEFT -> "Turn left and walk $distanceStr"
        Direction.RIGHT -> "Turn right and walk $distanceStr"
        Direction.SLIGHT_LEFT -> "Turn slightly left"
        Direction.SLIGHT_RIGHT -> "Turn slightly right"
        Direction.SHARP_LEFT -> "Turn sharp left"
        Direction.SHARP_RIGHT -> "Turn sharp right"
        Direction.U_TURN -> "Make a U-turn"
        Direction.ARRIVE -> "You have arrived"
    }
}

private fun BuildingResponse.toBuilding(): Building {
    return Building(
        id = id,
        name = name,
        shortName = shortName,
        description = description,
        location = CampusLocation(
            id = id,
            latitude = lat,
            longitude = lon,
            altitude = altitude ?: 0.0
        ),
        type = try {
            BuildingType.valueOf(type.uppercase())
        } catch (e: Exception) {
            BuildingType.ACADEMIC
        },
        isAccessible = isAccessible,
        imageUrl = imageUrl,
        entrances = entrances?.map {
            CampusLocation(
                id = "${id}_entrance",
                latitude = it.lat,
                longitude = it.lon
            )
        } ?: emptyList()
    )
}