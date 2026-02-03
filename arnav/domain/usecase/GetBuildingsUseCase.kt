package com.campus.arnav.domain.usecase

import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.BuildingType
import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.data.repository.CampusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case for getting and filtering campus buildings
 *
 * Responsibilities:
 * - Fetch all buildings
 * - Search buildings by name
 * - Filter buildings by type
 * - Find nearby buildings
 * - Sort buildings by distance
 */
class GetBuildingsUseCase @Inject constructor(
    private val campusRepository: CampusRepository
) {

    /**
     * Get all campus buildings
     */
    suspend fun execute(): List<Building> = withContext(Dispatchers.IO) {
        campusRepository.getAllBuildings()
    }

    /**
     * Get building by ID
     */
    suspend fun getById(buildingId: String): Building? = withContext(Dispatchers.IO) {
        campusRepository.getBuildingById(buildingId)
    }

    /**
     * Search buildings by name or description
     */
    suspend fun search(query: String): List<Building> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext emptyList()
        }

        campusRepository.searchBuildings(query)
    }

    /**
     * Get buildings by type
     */
    suspend fun getByType(type: BuildingType): List<Building> = withContext(Dispatchers.IO) {
        campusRepository.getAllBuildings().filter { it.type == type }
    }

    /**
     * Get buildings by multiple types
     */
    suspend fun getByTypes(types: List<BuildingType>): List<Building> = withContext(Dispatchers.IO) {
        campusRepository.getAllBuildings().filter { it.type in types }
    }

    /**
     * Get nearby buildings sorted by distance
     */
    suspend fun getNearby(
        userLocation: CampusLocation,
        maxDistance: Double = 500.0, // meters
        limit: Int = 10
    ): List<BuildingWithDistance> = withContext(Dispatchers.IO) {
        campusRepository.getAllBuildings()
            .map { building ->
                val distance = calculateDistance(userLocation, building.location)
                BuildingWithDistance(building, distance)
            }
            .filter { it.distance <= maxDistance }
            .sortedBy { it.distance }
            .take(limit)
    }

    /**
     * Get all buildings sorted by distance from user
     */
    suspend fun getAllSortedByDistance(
        userLocation: CampusLocation
    ): List<BuildingWithDistance> = withContext(Dispatchers.IO) {
        campusRepository.getAllBuildings()
            .map { building ->
                val distance = calculateDistance(userLocation, building.location)
                BuildingWithDistance(building, distance)
            }
            .sortedBy { it.distance }
    }

    /**
     * Get nearest building of a specific type
     */
    suspend fun getNearestOfType(
        userLocation: CampusLocation,
        type: BuildingType
    ): BuildingWithDistance? = withContext(Dispatchers.IO) {
        campusRepository.getAllBuildings()
            .filter { it.type == type }
            .map { building ->
                val distance = calculateDistance(userLocation, building.location)
                BuildingWithDistance(building, distance)
            }
            .minByOrNull { it.distance }
    }

    /**
     * Find nearest building (any type)
     */
    suspend fun getNearest(
        userLocation: CampusLocation
    ): BuildingWithDistance? = withContext(Dispatchers.IO) {
        campusRepository.getAllBuildings()
            .map { building ->
                val distance = calculateDistance(userLocation, building.location)
                BuildingWithDistance(building, distance)
            }
            .minByOrNull { it.distance }
    }

    /**
     * Get accessible buildings only
     */
    suspend fun getAccessibleBuildings(): List<Building> = withContext(Dispatchers.IO) {
        campusRepository.getAllBuildings().filter { it.isAccessible }
    }

    /**
     * Get buildings grouped by type
     */
    suspend fun getGroupedByType(): Map<BuildingType, List<Building>> = withContext(Dispatchers.IO) {
        campusRepository.getAllBuildings().groupBy { it.type }
    }

    /**
     * Check if a building exists
     */
    suspend fun exists(buildingId: String): Boolean = withContext(Dispatchers.IO) {
        campusRepository.getBuildingById(buildingId) != null
    }

    /**
     * Get building suggestions based on partial input
     */
    suspend fun getSuggestions(
        partialQuery: String,
        limit: Int = 5
    ): List<Building> = withContext(Dispatchers.IO) {
        if (partialQuery.length < 2) {
            return@withContext emptyList()
        }

        campusRepository.getAllBuildings()
            .filter { building ->
                building.name.contains(partialQuery, ignoreCase = true) ||
                        building.shortName.contains(partialQuery, ignoreCase = true)
            }
            .take(limit)
    }

    private fun calculateDistance(from: CampusLocation, to: CampusLocation): Double {
        val earthRadius = 6371000.0 // meters

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

/**
 * Building with calculated distance from user
 */
data class BuildingWithDistance(
    val building: Building,
    val distance: Double // meters
) {
    /**
     * Get formatted distance string
     */
    fun getFormattedDistance(): String {
        return when {
            distance < 1000 -> "${distance.toInt()} m"
            else -> String.format("%.1f km", distance / 1000)
        }
    }

    /**
     * Get estimated walking time (assuming 1.4 m/s walking speed)
     */
    fun getEstimatedWalkingTime(): Long {
        return (distance / 1.4).toLong() // seconds
    }

    /**
     * Get formatted walking time string
     */
    fun getFormattedWalkingTime(): String {
        val seconds = getEstimatedWalkingTime()
        val minutes = seconds / 60

        return when {
            minutes < 1 -> "< 1 min"
            minutes < 60 -> "$minutes min"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
    }
}