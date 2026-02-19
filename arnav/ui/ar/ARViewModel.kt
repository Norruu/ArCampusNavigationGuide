package com.campus.arnav.ui.ar

import android.location.Location
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class ARViewModel @Inject constructor() : ViewModel() {

    // Store target as primitives instead of CampusLocation object
    private var targetLat: Double = 0.0
    private var targetLon: Double = 0.0
    private var targetName: String = "Destination"

    private val _distanceToTarget = MutableStateFlow("...")
    val distanceToTarget = _distanceToTarget.asStateFlow()

    private val _navigationInstruction = MutableStateFlow("Locating...")
    val navigationInstruction = _navigationInstruction.asStateFlow()

    private val _arrowRotation = MutableStateFlow(0f)
    val arrowRotation = _arrowRotation.asStateFlow()

    private var currentHeading: Float = 0f
    private var currentLocation: Location? = null

    // --- FIX: Accept Doubles directly ---
    fun setTarget(latitude: Double, longitude: Double, name: String) {
        this.targetLat = latitude
        this.targetLon = longitude
        this.targetName = name
        _navigationInstruction.value = "Walk towards $name"
    }
    // ------------------------------------

    fun onLocationUpdated(location: Location) {
        currentLocation = location
        updateNavigationLogic()
    }

    fun onSensorHeadingChanged(azimuth: Float) {
        currentHeading = azimuth
        updateNavigationLogic()
    }

    private fun updateNavigationLogic() {
        val current = currentLocation ?: return
        if (targetLat == 0.0 && targetLon == 0.0) return

        // 1. Calculate Distance
        val results = FloatArray(1)
        Location.distanceBetween(
            current.latitude, current.longitude,
            targetLat, targetLon,
            results
        )
        val distanceMeters = results[0]

        _distanceToTarget.value = if (distanceMeters < 1000) {
            "${distanceMeters.roundToInt()} m"
        } else {
            String.format("%.1f km", distanceMeters / 1000)
        }

        // 2. Calculate Bearing
        val targetLocation = Location("").apply {
            latitude = targetLat
            longitude = targetLon
        }
        val bearingToTarget = current.bearingTo(targetLocation)

        // 3. Calculate Arrow Rotation
        var rotation = bearingToTarget - currentHeading
        while (rotation < -180) rotation += 360
        while (rotation > 180) rotation -= 360

        _arrowRotation.value = rotation

        if (distanceMeters < 15) {
            _navigationInstruction.value = "You have arrived at $targetName!"
        }
    }
}