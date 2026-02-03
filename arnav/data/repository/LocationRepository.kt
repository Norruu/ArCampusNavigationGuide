package com.campus.arnav.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.campus.arnav.data.model.CampusLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await    // <-- Add this import
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Flow of location updates
     */
    @SuppressLint("MissingPermission")
    val locationUpdates: Flow<CampusLocation> = callbackFlow {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L // Update interval in milliseconds
        ).apply {
            setMinUpdateIntervalMillis(500L)
            setMinUpdateDistanceMeters(1f)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(location.toCampusLocation())
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    /**
     * Get last known location
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(): CampusLocation? {
        return try {
            val location = fusedLocationClient.lastLocation.await()  // Uses imported await()
            location?.toCampusLocation()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Convert Android Location to CampusLocation
     */
    private fun Location.toCampusLocation(): CampusLocation {
        return CampusLocation(
            id = "user_location",
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            accuracy = accuracy
        )
    }
}