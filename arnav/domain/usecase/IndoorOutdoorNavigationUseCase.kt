package com.campus.arnav.domain.usecase

import com.campus.arnav.data.model.*
import com.campus.arnav.data.repository.CampusRepository
import javax.inject.Inject

/**
 * Handles transitions between indoor and outdoor navigation
 */
class IndoorOutdoorNavigationUseCase @Inject constructor(
    private val campusRepository: CampusRepository
) {

    /**
     * Determine if user is currently indoors based on location and building data
     */
    suspend fun isUserIndoors(location: CampusLocation): IndoorStatus {
        val buildings = campusRepository.getAllBuildings()

        for (building in buildings) {
            if (isLocationInsideBuilding(location, building)) {
                return IndoorStatus.Indoor(
                    buildingId = building.id,
                    buildingName = building.name,
                    estimatedFloor = estimateFloor(location, building)
                )
            }
        }

        return IndoorStatus.Outdoor
    }

    /**
     * Check if a location is within a building's bounds
     */
    private fun isLocationInsideBuilding(
        location: CampusLocation,
        building: Building
    ): Boolean {
        // Simple proximity check (in a real app, you'd use building polygons)
        val distance = calculateDistance(location, building.location)
        val buildingRadius = 30.0 // Approximate building radius in meters

        return distance < buildingRadius
    }

    /**
     * Estimate which floor the user is on based on altitude
     */
    private fun estimateFloor(location: CampusLocation, building: Building): Int {
        // Ground floor altitude (would be stored with building data)
        val groundFloorAltitude = building.location.altitude
        val floorHeight = 3.5 // Average floor height in meters

        val altitudeDiff = location.altitude - groundFloorAltitude

        return when {
            altitudeDiff < 0 -> -1 // Basement
            altitudeDiff < floorHeight -> 0 // Ground floor
            else -> (altitudeDiff / floorHeight).toInt()
        }
    }

    /**
     * Get navigation instructions for entering a building
     */
    fun getEnterBuildingInstructions(building: Building): List<String> {
        return listOf(
            "Head to the main entrance of ${building.name}",
            "Enter through the front doors",
            "GPS signal may be limited indoors"
        )
    }

    /**
     * Get navigation instructions for exiting a building
     */
    fun getExitBuildingInstructions(building: Building): List<String> {
        return listOf(
            "Head to the nearest exit",
            "Exit ${building.name}",
            "Continue to outdoor navigation"
        )
    }

    /**
     * Calculate appropriate navigation mode based on route
     */
    fun determineNavigationMode(route: Route): NavigationMode {
        val hasIndoorSegments = route.steps.any { it.isIndoor }
        val hasOutdoorSegments = route.steps.any { !it.isIndoor }

        return when {
            hasIndoorSegments && hasOutdoorSegments -> NavigationMode.Mixed
            hasIndoorSegments -> NavigationMode.IndoorOnly
            else -> NavigationMode.OutdoorOnly
        }
    }

    private fun calculateDistance(from: CampusLocation, to: CampusLocation): Double {
        val earthRadius = 6371000.0
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = Math.toRadians(to.latitude - from.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)

        val a = kotlin.math.sin(deltaLat / 2).let { it * it } +
                kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
                kotlin.math.sin(deltaLon / 2).let { it * it }
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return earthRadius * c
    }
}

// Status classes
sealed class IndoorStatus {
    object Outdoor : IndoorStatus()

    data class Indoor(
        val buildingId: String,
        val buildingName: String,
        val estimatedFloor: Int
    ) : IndoorStatus()
}

enum class NavigationMode {
    OutdoorOnly,
    IndoorOnly,
    Mixed
}