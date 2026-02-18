package com.campus.arnav.ui.ar

import android.location.Location
import androidx.lifecycle.ViewModel
import com.campus.arnav.data.model.CampusLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ARViewModel @Inject constructor() : ViewModel() {

    private var targetLocation: CampusLocation? = null

    // Live Data
    private val _distanceToTarget = MutableStateFlow("0 m")
    val distanceToTarget = _distanceToTarget.asStateFlow()

    private val _navigationInstruction = MutableStateFlow("Locating...")
    val navigationInstruction = _navigationInstruction.asStateFlow()

    private val _arrowRotation = MutableStateFlow(0f)
    val arrowRotation = _arrowRotation.asStateFlow()

    private var currentHeading: Float = 0f
    private var currentLocation: Location? = null

    // --- FIX: Add 'name' parameter here ---
    fun setTarget(location: CampusLocation, name: String) {
        this.targetLocation = location
        _navigationInstruction.value = "Walk towards $name"
    }
    // --------------------------------------

    fun onLocationUpdated(location: Location) {
        currentLocation = location
        updateNavigationLogic()
    }

    fun onSensorHeadingChanged(azimuth: Float) {
        currentHeading = azimuth
        updateNavigationLogic()
    }

    private fun updateNavigationLogic() {
        val target = targetLocation ?: return
        val current = currentLocation ?: return

        // 1. Calculate Distance
        val results = FloatArray(1)
        Location.distanceBetween(
            current.latitude, current.longitude,
            target.latitude, target.longitude,
            results
        )
        val distance = results[0]
        _distanceToTarget.value = "${distance.toInt()} m"

        // 2. Calculate Bearing & Rotation
        val bearingToTarget = current.bearingTo(Location("").apply {
            latitude = target.latitude
            longitude = target.longitude
        })

        var rotation = bearingToTarget - currentHeading
        while (rotation < -180) rotation += 360
        while (rotation > 180) rotation -= 360

        _arrowRotation.value = rotation

        if (distance < 10) {
            _navigationInstruction.value = "You have arrived!"
        }
    }
}