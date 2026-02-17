package com.campus.arnav.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * CompassManager - Professional Grade with Low-Pass Filter
 */
class CompassManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Sensors
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    // Data
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // State
    private val _heading = MutableStateFlow(0f)
    val heading: StateFlow<Float> = _heading.asStateFlow()

    // Smoothing (Low-Pass Filter)
    // 0.97 means "Keep 97% of old value, use 3% of new value" -> Very Smooth, less jitter
    private val ALPHA = 0.97f
    private var currentHeading = 0f

    // Listener for direct map updates
    private var onOrientationChanged: ((Float) -> Unit)? = null

    fun start(callback: ((Float) -> Unit)? = null) {
        this.onOrientationChanged = callback

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        this.onOrientationChanged = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            lowPass(event.values, accelerometerReading)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            lowPass(event.values, magnetometerReading)
        }
        updateOrientation()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed (e.g., show "Calibrate Compass" dialog)
    }

    private fun updateOrientation() {
        val success = SensorManager.getRotationMatrix(
            rotationMatrix, null, accelerometerReading, magnetometerReading
        )

        if (success) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            // Convert radians to degrees
            var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()

            // Normalize to 0-360
            azimuth = (azimuth + 360) % 360

            // Send negative azimuth for Map Rotation (Map rotates opposite to phone)
            val mapRotation = -azimuth

            _heading.value = azimuth
            onOrientationChanged?.invoke(mapRotation)
        }
    }

    // Standard Low-Pass Filter to remove jitter
    private fun lowPass(input: FloatArray, output: FloatArray) {
        for (i in input.indices) {
            output[i] = output[i] + ALPHA * (input[i] - output[i])
        }
    }
}