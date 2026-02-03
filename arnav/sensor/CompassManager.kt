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
 * CompassManager - Handles compass/heading functionality
 *
 * Features:
 * - Real-time compass heading
 * - Smooth heading updates (filtered)
 * - Magnetic declination support
 * - Compass accuracy monitoring
 * - Calibration detection
 */
class CompassManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Sensors
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val rotationVector: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // Sensor data
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Use rotation vector sensor if available (more accurate)
    private val useRotationVector = rotationVector != null

    // Compass state
    private val _heading = MutableStateFlow(0f)
    val heading: StateFlow<Float> = _heading.asStateFlow()

    private val _accuracy = MutableStateFlow(CompassAccuracy.UNKNOWN)
    val accuracy: StateFlow<CompassAccuracy> = _accuracy.asStateFlow()

    private val _needsCalibration = MutableStateFlow(false)
    val needsCalibration: StateFlow<Boolean> = _needsCalibration.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    // Smoothing
    private var lastHeading = 0f
    private val smoothingFactor = 0.15f // Lower = smoother, higher = more responsive

    // Magnetic declination (difference between magnetic north and true north)
    private var magneticDeclination = 0f

    // Listener callback
    private var onHeadingChangedListener: ((Float) -> Unit)? = null

    /**
     * Start compass updates
     */
    fun start() {
        if (_isActive.value) return

        if (useRotationVector) {
            // Use rotation vector sensor (fused, more accurate)
            sensorManager.registerListener(
                this,
                rotationVector,
                SensorManager.SENSOR_DELAY_GAME
            )
        } else {
            // Fall back to accelerometer + magnetometer
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

        _isActive.value = true
    }

    /**
     * Stop compass updates
     */
    fun stop() {
        sensorManager.unregisterListener(this)
        _isActive.value = false
    }

    /**
     * Set magnetic declination for true north calculation
     *
     * @param declination Magnetic declination in degrees
     */
    fun setMagneticDeclination(declination: Float) {
        this.magneticDeclination = declination
    }

    /**
     * Calculate magnetic declination from location
     * Uses Android's GeomagneticField
     */
    fun updateMagneticDeclination(latitude: Double, longitude: Double, altitude: Double = 0.0) {
        val geoField = android.hardware.GeomagneticField(
            latitude.toFloat(),
            longitude.toFloat(),
            altitude.toFloat(),
            System.currentTimeMillis()
        )
        magneticDeclination = geoField.declination
    }

    /**
     * Set listener for heading changes
     */
    fun setOnHeadingChangedListener(listener: (Float) -> Unit) {
        onHeadingChangedListener = listener
    }

    /**
     * Remove heading listener
     */
    fun removeOnHeadingChangedListener() {
        onHeadingChangedListener = null
    }

    /**
     * Get current heading (0-360 degrees, 0 = North)
     */
    fun getCurrentHeading(): Float = _heading.value

    /**
     * Get true north heading (adjusted for magnetic declination)
     */
    fun getTrueNorthHeading(): Float {
        var trueHeading = _heading.value + magneticDeclination
        if (trueHeading < 0) trueHeading += 360
        if (trueHeading >= 360) trueHeading -= 360
        return trueHeading
    }

    /**
     * Get cardinal direction from heading
     */
    fun getCardinalDirection(): CardinalDirection {
        return CardinalDirection.fromHeading(_heading.value)
    }

    /**
     * Get cardinal direction string (N, NE, E, etc.)
     */
    fun getCardinalDirectionString(): String {
        return getCardinalDirection().abbreviation
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
                updateHeadingFromAccelMag()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(
                    event.values, 0,
                    magnetometerReading, 0,
                    magnetometerReading.size
                )
                updateHeadingFromAccelMag()
            }
        }
    }

    private fun handleRotationVector(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // Azimuth is in radians, convert to degrees
        var azimuthDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()

        // Normalize to 0-360
        if (azimuthDeg < 0) azimuthDeg += 360f

        // Apply smoothing and update
        updateHeading(azimuthDeg)
    }

    private fun updateHeadingFromAccelMag() {
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

            // Apply smoothing and update
            updateHeading(azimuthDeg)
        }
    }

    private fun updateHeading(newHeading: Float) {
        // Apply low-pass filter for smooth updates
        val smoothedHeading = smoothHeading(newHeading)

        // Only update if changed significantly (reduces jitter)
        if (abs(smoothedHeading - _heading.value) > 0.5f) {
            _heading.value = smoothedHeading
            onHeadingChangedListener?.invoke(smoothedHeading)
        }
    }

    /**
     * Apply low-pass filter for smooth heading changes
     * Handles wrap-around at 0/360 degrees
     */
    private fun smoothHeading(newHeading: Float): Float {
        // Calculate difference, handling wrap-around
        var diff = newHeading - lastHeading

        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360

        // Apply smoothing
        lastHeading += diff * smoothingFactor

        // Normalize to 0-360
        if (lastHeading < 0) lastHeading += 360f
        if (lastHeading >= 360) lastHeading -= 360f

        return lastHeading
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        when (sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_ROTATION_VECTOR -> {
                val compassAccuracy = when (accuracy) {
                    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassAccuracy.HIGH
                    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassAccuracy.MEDIUM
                    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassAccuracy.LOW
                    SensorManager.SENSOR_STATUS_UNRELIABLE -> CompassAccuracy.UNRELIABLE
                    else -> CompassAccuracy.UNKNOWN
                }

                _accuracy.value = compassAccuracy

                // Needs calibration if accuracy is low or unreliable
                _needsCalibration.value = compassAccuracy == CompassAccuracy.LOW ||
                        compassAccuracy == CompassAccuracy.UNRELIABLE
            }
        }
    }

    /**
     * Check if compass sensors are available
     */
    fun isCompassAvailable(): Boolean {
        return rotationVector != null || (accelerometer != null && magnetometer != null)
    }

    /**
     * Get bearing from current location to target
     *
     * @param currentLat Current latitude
     * @param currentLon Current longitude
     * @param targetLat Target latitude
     * @param targetLon Target longitude
     * @return Bearing in degrees (0-360)
     */
    fun getBearingToTarget(
        currentLat: Double,
        currentLon: Double,
        targetLat: Double,
        targetLon: Double
    ): Float {
        val lat1 = Math.toRadians(currentLat)
        val lat2 = Math.toRadians(targetLat)
        val deltaLon = Math.toRadians(targetLon - currentLon)

        val x = kotlin.math.sin(deltaLon) * kotlin.math.cos(lat2)
        val y = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) -
                kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(deltaLon)

        var bearing = Math.toDegrees(kotlin.math.atan2(x, y)).toFloat()

        // Normalize to 0-360
        if (bearing < 0) bearing += 360f

        return bearing
    }

    /**
     * Get relative bearing to target (relative to current heading)
     *
     * @return Relative bearing (-180 to 180, negative = left, positive = right)
     */
    fun getRelativeBearingToTarget(
        currentLat: Double,
        currentLon: Double,
        targetLat: Double,
        targetLon: Double
    ): Float {
        val absoluteBearing = getBearingToTarget(currentLat, currentLon, targetLat, targetLon)
        var relativeBearing = absoluteBearing - _heading.value

        // Normalize to -180 to 180
        if (relativeBearing > 180) relativeBearing -= 360
        if (relativeBearing < -180) relativeBearing += 360

        return relativeBearing
    }

    /**
     * Get turn direction to face target
     */
    fun getTurnDirectionToTarget(
        currentLat: Double,
        currentLon: Double,
        targetLat: Double,
        targetLon: Double
    ): TurnDirection {
        val relativeBearing = getRelativeBearingToTarget(
            currentLat, currentLon,
            targetLat, targetLon
        )

        return when {
            abs(relativeBearing) <= 15 -> TurnDirection.STRAIGHT
            relativeBearing > 0 && relativeBearing <= 45 -> TurnDirection.SLIGHT_RIGHT
            relativeBearing > 45 && relativeBearing <= 135 -> TurnDirection.RIGHT
            relativeBearing > 135 -> TurnDirection.SHARP_RIGHT
            relativeBearing < 0 && relativeBearing >= -45 -> TurnDirection.SLIGHT_LEFT
            relativeBearing < -45 && relativeBearing >= -135 -> TurnDirection.LEFT
            relativeBearing < -135 -> TurnDirection.SHARP_LEFT
            else -> TurnDirection.STRAIGHT
        }
    }
}

/**
 * Compass accuracy levels
 */
enum class CompassAccuracy {
    HIGH,
    MEDIUM,
    LOW,
    UNRELIABLE,
    UNKNOWN;

    fun isUsable(): Boolean = this == HIGH || this == MEDIUM
}

/**
 * Cardinal directions
 */
enum class CardinalDirection(
    val abbreviation: String,
    val fullName: String,
    val minHeading: Float,
    val maxHeading: Float
) {
    NORTH("N", "North", 337.5f, 22.5f),
    NORTH_EAST("NE", "Northeast", 22.5f, 67.5f),
    EAST("E", "East", 67.5f, 112.5f),
    SOUTH_EAST("SE", "Southeast", 112.5f, 157.5f),
    SOUTH("S", "South", 157.5f, 202.5f),
    SOUTH_WEST("SW", "Southwest", 202.5f, 247.5f),
    WEST("W", "West", 247.5f, 292.5f),
    NORTH_WEST("NW", "Northwest", 292.5f, 337.5f);

    companion object {
        fun fromHeading(heading: Float): CardinalDirection {
            val normalizedHeading = if (heading < 0) heading + 360 else heading % 360

            return when {
                normalizedHeading >= 337.5f || normalizedHeading < 22.5f -> NORTH
                normalizedHeading >= 22.5f && normalizedHeading < 67.5f -> NORTH_EAST
                normalizedHeading >= 67.5f && normalizedHeading < 112.5f -> EAST
                normalizedHeading >= 112.5f && normalizedHeading < 157.5f -> SOUTH_EAST
                normalizedHeading >= 157.5f && normalizedHeading < 202.5f -> SOUTH
                normalizedHeading >= 202.5f && normalizedHeading < 247.5f -> SOUTH_WEST
                normalizedHeading >= 247.5f && normalizedHeading < 292.5f -> WEST
                normalizedHeading >= 292.5f && normalizedHeading < 337.5f -> NORTH_WEST
                else -> NORTH
            }
        }
    }
}

/**
 * Turn directions
 */
enum class TurnDirection(val instruction: String) {
    STRAIGHT("Continue straight"),
    SLIGHT_LEFT("Turn slightly left"),
    LEFT("Turn left"),
    SHARP_LEFT("Turn sharp left"),
    SLIGHT_RIGHT("Turn slightly right"),
    RIGHT("Turn right"),
    SHARP_RIGHT("Turn sharp right"),
    U_TURN("Make a U-turn")
}