package com.campus.arnav.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

class ARCompassManager(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val accelReading = FloatArray(3)
    private val magReading = FloatArray(3)

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9) // Required for vertical phones
    private val orientationAngles = FloatArray(3)
    private val ALPHA = 0.97f

    private var targetPathBearing: Float = 0f
    private var onAROrientationChanged: ((Float) -> Unit)? = null

    // Call this whenever the user's next waypoint changes
    fun setPathBearing(bearing: Float) {
        targetPathBearing = bearing
    }

    fun start(callback: (Float) -> Unit) {
        onAROrientationChanged = callback
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        onAROrientationChanged = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) lowPass(event.values, accelReading)
        else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) lowPass(event.values, magReading)

        if (SensorManager.getRotationMatrix(rotationMatrix, null, accelReading, magReading)) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()

            // STRICT RULE: ONLY FIRE IF VERTICAL (Looking through AR camera)
            if (abs(pitch) >= 45f) {

                // 1. Remap the coordinate system for portrait/upright hold
                SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_X,
                    SensorManager.AXIS_Z,
                    remappedMatrix
                )
                SensorManager.getOrientation(remappedMatrix, orientationAngles)

                var currentMagneticAzimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                currentMagneticAzimuth = (currentMagneticAzimuth + 360) % 360

                // 2. THE MAGIC: Set "True North" to be the Path Bearing
                var relativeAzimuth = currentMagneticAzimuth - targetPathBearing
                relativeAzimuth = (relativeAzimuth + 360) % 360

                // Emits 0 when looking at path, 90 when looking right of path, etc.
                onAROrientationChanged?.invoke(relativeAzimuth)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun lowPass(input: FloatArray, output: FloatArray) {
        for (i in input.indices) output[i] = output[i] + ALPHA * (input[i] - output[i])
    }
}