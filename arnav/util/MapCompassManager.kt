package com.campus.arnav.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

class MapCompassManager(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val accelReading = FloatArray(3)
    private val magReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val ALPHA = 0.97f

    private var onMapOrientationChanged: ((Float) -> Unit)? = null

    fun start(callback: (Float) -> Unit) {
        onMapOrientationChanged = callback
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        onMapOrientationChanged = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) lowPass(event.values, accelReading)
        else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) lowPass(event.values, magReading)

        if (SensorManager.getRotationMatrix(rotationMatrix, null, accelReading, magReading)) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            // Calculate Pitch
            val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()

            // STRICT RULE: ONLY FIRE IF FLAT (Under 45 degrees of tilt)
            if (abs(pitch) < 45f) {
                var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                azimuth = (azimuth + 360) % 360

                // Map expects negative azimuth to rotate the map correctly under the user
                onMapOrientationChanged?.invoke(-azimuth)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun lowPass(input: FloatArray, output: FloatArray) {
        for (i in input.indices) output[i] = output[i] + ALPHA * (input[i] - output[i])
    }
}