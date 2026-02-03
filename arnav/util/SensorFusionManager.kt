package com.campus.arnav.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import com.campus.arnav.data.model.CampusLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

/**
 * Combines GPS, accelerometer, and compass data for better positioning
 *
 * Features:
 * - Dead reckoning when GPS is unavailable
 * - Heading smoothing using compass + gyroscope
 * - Speed estimation from accelerometer
 * - GPS drift detection and correction
 */
class SensorFusionManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Sensors
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    // State
    private val _fusedLocation = MutableStateFlow<FusedLocation?>(null)
    val fusedLocation: StateFlow<FusedLocation?> = _fusedLocation.asStateFlow()

    private val _heading = MutableStateFlow(0f)
    val heading: StateFlow<Float> = _heading.asStateFlow()

    // Sensor readings
    private val accelerometerValues = FloatArray(3)
    private val magnetometerValues = FloatArray(3)
    private val gyroscopeValues = FloatArray(3)

    // Dead reckoning state
    private var lastGpsLocation: CampusLocation? = null
    private var lastGpsTime: Long = 0
    private var stepCount = 0
    private var lastStepTime: Long = 0
    private val averageStepLength = 0.75f // meters

    // Kalman filter for position smoothing
    private val kalmanFilter = SimpleKalmanFilter()

    // GPS drift detection
    private var gpsReadings = mutableListOf<GpsReading>()
    private val maxGpsReadings = 5

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        stepDetector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /**
     * Update with new GPS location
     */
    fun updateGpsLocation(location: CampusLocation) {
        val currentTime = System.currentTimeMillis()

        // Add to GPS readings for drift detection
        gpsReadings.add(GpsReading(location, currentTime))
        if (gpsReadings.size > maxGpsReadings) {
            gpsReadings.removeAt(0)
        }

        // Check for GPS drift
        if (isGpsDrifting()) {
            // Use dead reckoning instead
            val deadReckonedLocation = calculateDeadReckonedPosition()
            deadReckonedLocation?.let {
                _fusedLocation.value = FusedLocation(
                    location = it,
                    accuracy = 10f, // Lower accuracy for dead reckoning
                    source = LocationSource.DEAD_RECKONING
                )
            }
            return
        }

        // Apply Kalman filter
        val filteredLocation = kalmanFilter.filter(location)

        // Update state
        lastGpsLocation = filteredLocation
        lastGpsTime = currentTime
        stepCount = 0 // Reset step count

        _fusedLocation.value = FusedLocation(
            location = filteredLocation,
            accuracy = location.accuracy,
            source = LocationSource.GPS_FILTERED
        )
    }

    /**
     * Detect GPS drift (jumping locations)
     */
    private fun isGpsDrifting(): Boolean {
        if (gpsReadings.size < 3) return false

        // Check for sudden jumps in position
        for (i in 1 until gpsReadings.size) {
            val prev = gpsReadings[i - 1]
            val curr = gpsReadings[i]

            val distance = calculateDistance(prev.location, curr.location)
            val timeDiff = (curr.timestamp - prev.timestamp) / 1000.0 // seconds

            if (timeDiff > 0) {
                val speed = distance / timeDiff // m/s

                // Walking speed shouldn't exceed 3 m/s
                if (speed > 3.0) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Calculate position using dead reckoning (step counting + heading)
     */
    private fun calculateDeadReckonedPosition(): CampusLocation? {
        val lastLocation = lastGpsLocation ?: return null

        if (stepCount == 0) return lastLocation

        // Calculate displacement
        val distance = stepCount * averageStepLength
        val headingRad = Math.toRadians(_heading.value.toDouble())

        // Calculate new position
        val deltaLat = distance * cos(headingRad) / 111320.0 // meters to degrees
        val deltaLon = distance * sin(headingRad) / (111320.0 * cos(Math.toRadians(lastLocation.latitude)))

        return lastLocation.copy(
            latitude = lastLocation.latitude + deltaLat,
            longitude = lastLocation.longitude + deltaLon
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerValues, 0, 3)
                updateHeading()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerValues, 0, 3)
                updateHeading()
            }
            Sensor.TYPE_GYROSCOPE -> {
                System.arraycopy(event.values, 0, gyroscopeValues, 0, 3)
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                onStepDetected()
            }
        }
    }

    private fun updateHeading() {
        val rotationMatrix = FloatArray(9)
        val orientationValues = FloatArray(3)

        val success = SensorManager.getRotationMatrix(
            rotationMatrix, null,
            accelerometerValues, magnetometerValues
        )

        if (success) {
            SensorManager.getOrientation(rotationMatrix, orientationValues)
            var heading = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
            if (heading < 0) heading += 360

            // Apply smoothing
            _heading.value = smoothHeading(heading)
        }
    }

    private var lastHeading = 0f
    private fun smoothHeading(newHeading: Float): Float {
        var diff = newHeading - lastHeading
        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360

        lastHeading += diff * 0.1f
        if (lastHeading < 0) lastHeading += 360
        if (lastHeading >= 360) lastHeading -= 360

        return lastHeading
    }

    private fun onStepDetected() {
        stepCount++
        lastStepTime = System.currentTimeMillis()

        // Update dead reckoned position if GPS is stale
        val gpsAge = System.currentTimeMillis() - lastGpsTime
        if (gpsAge > 5000) { // GPS older than 5 seconds
            calculateDeadReckonedPosition()?.let { location ->
                _fusedLocation.value = FusedLocation(
                    location = location,
                    accuracy = 10f + (stepCount * 0.5f), // Accuracy degrades with steps
                    source = LocationSource.DEAD_RECKONING
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun calculateDistance(from: CampusLocation, to: CampusLocation): Double {
        val earthRadius = 6371000.0
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = Math.toRadians(to.latitude - from.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)

        val a = sin(deltaLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(deltaLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }
}

// Data classes
data class FusedLocation(
    val location: CampusLocation,
    val accuracy: Float,
    val source: LocationSource
)

enum class LocationSource {
    GPS_RAW,
    GPS_FILTERED,
    DEAD_RECKONING,
    QR_CODE
}

data class GpsReading(
    val location: CampusLocation,
    val timestamp: Long
)

/**
 * Simple Kalman filter for location smoothing
 */
class SimpleKalmanFilter {
    private var estimatedLat = 0.0
    private var estimatedLon = 0.0
    private var errorLat = 1.0
    private var errorLon = 1.0

    private val processNoise = 0.00001

    fun filter(location: CampusLocation): CampusLocation {
        val measurementNoise = location.accuracy.toDouble() / 111320.0 // Convert meters to degrees

        // Prediction step
        errorLat += processNoise
        errorLon += processNoise

        // Update step
        val kalmanGainLat = errorLat / (errorLat + measurementNoise)
        val kalmanGainLon = errorLon / (errorLon + measurementNoise)

        if (estimatedLat == 0.0) {
            estimatedLat = location.latitude
            estimatedLon = location.longitude
        } else {
            estimatedLat += kalmanGainLat * (location.latitude - estimatedLat)
            estimatedLon += kalmanGainLon * (location.longitude - estimatedLon)
        }

        errorLat *= (1 - kalmanGainLat)
        errorLon *= (1 - kalmanGainLon)

        return location.copy(
            latitude = estimatedLat,
            longitude = estimatedLon
        )
    }

    fun reset() {
        estimatedLat = 0.0
        estimatedLon = 0.0
        errorLat = 1.0
        errorLon = 1.0
    }
}