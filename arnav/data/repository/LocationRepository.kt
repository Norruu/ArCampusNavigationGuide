package com.campus.arnav.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.campus.arnav.data.model.CampusLocation
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // OPTIMIZED: 2s interval (was 500ms), 3m min distance — much easier on battery
    // and avoids GPS jitter that causes crashes on low-end phones
    @SuppressLint("MissingPermission")
    val locationUpdates: Flow<CampusLocation> = callbackFlow {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, // CHANGED: was HIGH_ACCURACY
            2000L  // CHANGED: was 1000ms
        ).apply {
            setMinUpdateIntervalMillis(1500L)   // CHANGED: was 500ms
            setMinUpdateDistanceMeters(3f)       // CHANGED: was 1m — reduces jitter
            setMaxUpdateDelayMillis(5000L)       // NEW: batch updates on low-end devices
            setWaitForAccurateLocation(false)    // NEW: don't stall startup on weak signal
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // Only emit if channel is still open — prevents crash on destroyed fragment
                result.lastLocation?.let { location ->
                    trySend(location.toCampusLocation())
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                // GPS became unavailable — don't crash, just stop emitting
                if (!availability.isLocationAvailable) {
                    // Optionally notify UI, but don't throw
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            // Permission was revoked mid-session — close gracefully
            close(e)
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(): CampusLocation? {
        return try {
            val location = fusedLocationClient.lastLocation.await()
            location?.toCampusLocation()
        } catch (e: Exception) {
            null
        }
    }

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