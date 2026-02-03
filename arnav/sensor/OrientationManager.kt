package com.campus.arnav.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * Manages device orientation using sensor fusion
 * Combines accelerometer and magnetometer for stable heading
 */
class OrientationManager(
    private val sensorManager: SensorManager,
    private val onOrientationChanged: (Float) -> Unit
) : SensorEventListener {

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Low-pass filter for smoothing
    private var lastAzimuth = 0f
    private val smoothingFactor = 0.15f

    // Sensors
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var rotationVector: Sensor? = null

    // Use rotation vector if available (more accurate)
    private var useRotationVector = false

    init {
        // Prefer rotation vector sensor (fused sensor)
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (rotationVector != null) {
            useRotationVector = true
        } else {
            // Fall back to accelerometer + magnetometer
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }
    }

    fun start() {
        if (useRotationVector) {
            sensorManager.registerListener(
                this,
                rotationVector,
                SensorManager.SENSOR_DELAY_GAME
            )
        } else {
            accelerometer?.let {
                sensorManager.registerListener(
                    this,
                    it,
                    SensorManager.SENSOR_DELAY_GAME
                )
            }
            magnetometer?.let {
                sensorManager.registerListener(
                    this,
                    it,
                    SensorManager.SENSOR_DELAY_GAME
                )
            }
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                handleRotationVector(event)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(
                    event.values, 0,
                    accelerometerReading, 0,
                    accelerometerReading.size
                )
                updateOrientation()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(
                    event.values, 0,
                    magnetometerReading, 0,
                    magnetometerReading.size
                )
                updateOrientation()
            }
        }
    }

    private fun handleRotationVector(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val azimuthRad = orientationAngles[0]
        var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()

        // Normalize to 0-360
        if (azimuthDeg < 0) azimuthDeg += 360f

        // Apply smoothing
        val smoothedAzimuth = smoothAzimuth(azimuthDeg)
        onOrientationChanged(smoothedAzimuth)
    }

    private fun updateOrientation() {
        // Calculate rotation matrix from accelerometer and magnetometer
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )

        if (success) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            var azimuthDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()

            // Normalize to 0-360
            if (azimuthDeg < 0) azimuthDeg += 360f

            // Apply smoothing
            val smoothedAzimuth = smoothAzimuth(azimuthDeg)
            onOrientationChanged(smoothedAzimuth)
        }
    }

    /**
     * Apply low-pass filter for smooth heading changes
     * Handles wrap-around at 0/360 degrees
     */
    private fun smoothAzimuth(newAzimuth: Float): Float {
        // Handle wrap-around
        var diff = newAzimuth - lastAzimuth

        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360

        // Only update if change is significant (reduces jitter)
        if (abs(diff) > 0.5f) {
            lastAzimuth += diff * smoothingFactor

            // Normalize
            if (lastAzimuth < 0) lastAzimuth += 360f
            if (lastAzimuth >= 360) lastAzimuth -= 360f
        }

        return lastAzimuth
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Can notify user if compass needs calibration
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            when (accuracy) {
                SensorManager.SENSOR_STATUS_UNRELIABLE -> {
                    // Compass needs calibration
                }
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> {
                    // Low accuracy
                }
            }
        }
    }
}