package com.campus.arnav.domain.usecase

import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.data.repository.LocationRepository
import com.campus.arnav.util.SensorFusionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Use case for tracking user location
 *
 * Responsibilities:
 * - Provide continuous location updates
 * - Filter inaccurate readings
 * - Apply smoothing if needed
 * - Handle location permissions
 */
class TrackLocationUseCase @Inject constructor(
    private val locationRepository: LocationRepository
) {

    // Minimum accuracy threshold (meters)
    private val minAccuracyThreshold = 50f

    // Minimum distance to report new location (meters)
    private val minDistanceThreshold = 2.0

    // Last known location for filtering
    private var lastLocation: CampusLocation? = null

    /**
     * Get continuous location updates
     */
    fun execute(): Flow<LocationResult> {
        return locationRepository.locationUpdates
            .map { location ->
                // Filter by accuracy
                if (location.accuracy > minAccuracyThreshold) {
                    LocationResult.LowAccuracy(location)
                } else {
                    // Check if location changed significantly
                    val shouldUpdate = lastLocation?.let { last ->
                        calculateDistance(last, location) >= minDistanceThreshold
                    } ?: true

                    if (shouldUpdate) {
                        lastLocation = location
                        LocationResult.Success(location)
                    } else {
                        LocationResult.NoSignificantChange(location)
                    }
                }
            }
            .catch { e ->
                emit(LocationResult.Error(e.message ?: "Unknown location error"))
            }
    }

    /**
     * Get location updates with custom accuracy filter
     */
    fun execute(minAccuracy: Float): Flow<LocationResult> {
        return locationRepository.locationUpdates
            .map { location ->
                if (location.accuracy > minAccuracy) {
                    LocationResult.LowAccuracy(location)
                } else {
                    lastLocation = location
                    LocationResult.Success(location)
                }
            }
            .catch { e ->
                emit(LocationResult.Error(e.message ?: "Unknown location error"))
            }
    }

    /**
     * Get single location (last known)
     */
    suspend fun getLastLocation(): CampusLocation? {
        return locationRepository.getLastLocation()
    }

    /**
     * Get location updates only (no filtering, raw data)
     */
    fun getRawLocationUpdates(): Flow<CampusLocation> {
        return locationRepository.locationUpdates
    }

    /**
     * Check if user is near a specific location
     */
    fun isNearLocation(
        target: CampusLocation,
        thresholdMeters: Double = 20.0
    ): Flow<Boolean> {
        return locationRepository.locationUpdates
            .map { currentLocation ->
                calculateDistance(currentLocation, target) <= thresholdMeters
            }
    }

    /**
     * Get distance to a target location
     */
    fun getDistanceTo(target: CampusLocation): Flow<Double> {
        return locationRepository.locationUpdates
            .map { currentLocation ->
                calculateDistance(currentLocation, target)
            }
    }

    /**
     * Reset tracking state
     */
    fun reset() {
        lastLocation = null
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
 * Result wrapper for location updates
 */
sealed class LocationResult {
    data class Success(val location: CampusLocation) : LocationResult()
    data class LowAccuracy(val location: CampusLocation) : LocationResult()
    data class NoSignificantChange(val location: CampusLocation) : LocationResult()
    data class Error(val message: String) : LocationResult()
}