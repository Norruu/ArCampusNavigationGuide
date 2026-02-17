package com.campus.arnav.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

class CompassManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // UPDATED: Now defines a callback with TWO parameters: (Float, Boolean)
    private var listener: ((Float, Boolean) -> Unit)? = null

    fun start(onOrientationChanged: (Float, Boolean) -> Unit) {
        listener = onOrientationChanged
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        listener = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            val orientation = FloatArray(3)

            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)

            // 1. Azimuth (Rotation)
            val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
            val finalAzimuth = (azimuthDeg + 360) % 360

            // 2. Pitch and Roll (Tilt/Flatness)
            val pitch = Math.toDegrees(orientation[1].toDouble())
            val roll = Math.toDegrees(orientation[2].toDouble())

            // Check if phone is flat (< 35 degrees tilt)
            val isFlat = abs(pitch) < 35 && abs(roll) < 35

            // Send BOTH values to MapFragment
            listener?.invoke(-finalAzimuth, isFlat)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}