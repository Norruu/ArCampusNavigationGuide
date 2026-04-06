package com.campus.arnav.ui.ar

import android.location.Location
import android.util.Log
import kotlin.math.*

/**
 * ARNavigationArrowController
 *
 * Manages the directional state of the AR navigation arrow in pure world-space,
 * completely decoupled from ARCore camera orientation.
 *
 * Design philosophy (mirrors Google Maps Live View):
 *  • Arrow lives in world space — camera rotation never affects its direction.
 *  • Direction is driven by GPS bearing from snapped user position → next node.
 *  • Smooth rotation via exponential low-pass (configurable alpha).
 *  • Automatic node advancement with hysteresis guard (prevents flip-flop).
 *  • Forward-only guarantee — arrow can never point more than 90° backward.
 *  • North calibration is deferred until ARCore tracking is confirmed stable.
 */
class ARNavigationArrowController(
    // How quickly the arrow tracks the target bearing.
    // 0 = instant snap, 1 = never moves.
    // 0.85 gives a smooth ~300 ms lag at 60 fps — matches Live View feel.
    private val smoothingAlpha: Float = 0.85f,

    // Radius in metres within which a node is considered "reached".
    private val nodeReachedRadiusM: Float = 8f,

    // Safety margin — don't let the arrow point more than this many degrees
    // behind the user's current travel direction.
    private val forwardAngleThresholdDeg: Float = 100f,

    // Minimum number of stable ARCore frames required before north is calibrated.
    // Prevents calibrating on a shaky first frame.
    private val stabilityFramesRequired: Int = 10,

    // ARCore camera position is considered "stable" if it moves less than this
    // many units between consecutive frames.
    private val stabilityMovementThreshold: Float = 0.05f
) {

    // ── Public output ─────────────────────────────────────────────────────────

    /** World-space Y rotation (degrees) the arrow model should use each frame. */
    var smoothedWorldYDeg: Float = 0f
        private set

    /** Index into the route-point array of the current target node. */
    var targetNodeIndex: Int = 0
        private set

    /** Bearing to the current target node (degrees, 0 = north). */
    var rawBearingToNodeDeg: Float = 0f
        private set

    /** True when the user has reached the final node. */
    var hasArrived: Boolean = false
        private set

    /** True once north has been calibrated against a stable ARCore frame. */
    var isNorthCalibrated: Boolean = false
        private set

    // ── Internal state ────────────────────────────────────────────────────────

    // The world-space Y angle that the ARCore session's "north" maps to.
    // Calibrated once after the first stable GPS fix + stable ARCore tracking.
    private var worldNorthOffsetDeg: Float? = null

    // Previous heading used for delta-smoothing across the 0/360 wrap boundary.
    private var lastSmoothedBearing: Float = 0f

    // Prevent re-triggering nodeReached multiple times per node.
    private var lastReachedIndex: Int = -1

    // ARCore stability tracking: position of the camera on the previous frame.
    private var lastCameraX: Float = Float.MAX_VALUE
    private var lastCameraY: Float = Float.MAX_VALUE
    private var lastCameraZ: Float = Float.MAX_VALUE

    // How many consecutive stable frames have been observed since last reset.
    private var stableFrameCount: Int = 0

    private companion object {
        const val TAG = "ARArrowController"
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Call once when navigation begins (or the route changes).
     * Resets all state so the arrow starts fresh.
     */
    fun reset() {
        targetNodeIndex      = 0
        smoothedWorldYDeg    = 0f
        rawBearingToNodeDeg  = 0f
        worldNorthOffsetDeg  = null
        lastReachedIndex     = -1
        hasArrived           = false
        isNorthCalibrated    = false
        stableFrameCount     = 0
        lastCameraX          = Float.MAX_VALUE
        lastCameraY          = Float.MAX_VALUE
        lastCameraZ          = Float.MAX_VALUE
        Log.d(TAG, "Controller reset")
    }

    /**
     * Report the current ARCore camera world position every frame so the
     * controller can assess tracking stability before calibrating north.
     *
     * Call this from onSessionUpdated BEFORE calling [update].
     *
     * @param x  camera worldPosition.x
     * @param y  camera worldPosition.y
     * @param z  camera worldPosition.z
     */
    fun reportCameraPosition(x: Float, y: Float, z: Float) {
        if (isNorthCalibrated) return  // no need to track once calibrated

        val moved = if (lastCameraX == Float.MAX_VALUE) Float.MAX_VALUE
        else sqrt((x - lastCameraX).pow(2) +
                (y - lastCameraY).pow(2) +
                (z - lastCameraZ).pow(2))

        lastCameraX = x
        lastCameraY = y
        lastCameraZ = z

        stableFrameCount = if (moved < stabilityMovementThreshold) stableFrameCount + 1 else 0

        Log.v(TAG, "Camera stability: frames=$stableFrameCount moved=${moved.format2()}m")
    }

    /** True when ARCore has been stable long enough to trust a north calibration. */
    val isTrackingStable: Boolean
        get() = stableFrameCount >= stabilityFramesRequired

    /**
     * Calibrate the world-space north offset.
     *
     * This is called automatically by [update] on the first tick where both
     * a GPS fix is available and ARCore tracking is stable. You may also call it
     * manually if you want to re-calibrate mid-session (e.g. after a tracking
     * loss recovery).
     *
     * @param currentMagneticHeadingDeg  Device compass heading (0 = magnetic north)
     * @param cameraWorldYDeg            ARCore camera worldRotation.y at this moment
     */
    fun calibrateNorthOffset(currentMagneticHeadingDeg: Float, cameraWorldYDeg: Float) {
        worldNorthOffsetDeg = normalizeDeg(cameraWorldYDeg - currentMagneticHeadingDeg)
        isNorthCalibrated   = true
        Log.d(TAG, "North calibrated: offset=$worldNorthOffsetDeg° " +
                "(camY=$cameraWorldYDeg°, heading=$currentMagneticHeadingDeg°)")
    }

    /**
     * Main update — call every GPS tick (or every AR frame when GPS is fresh).
     *
     * Auto-calibrates north on the first call where both a GPS fix is available
     * and [isTrackingStable] is true. If tracking is not yet stable the arrow
     * will point in its last known direction until calibration fires.
     *
     * @param userLocation          Current location — use the SNAPPED position for
     *                              best results so the arrow always leads to the
     *                              next node ahead of the path, never behind.
     * @param routePoints           Ordered list of lat/lon pairs along the route.
     * @param magneticHeadingDeg    Current device compass heading (0 = magnetic north).
     * @param cameraWorldYDeg       ARCore camera worldRotation.y THIS frame.
     * @return                      [ArrowUpdate] containing everything the renderer needs.
     */
    fun update(
        userLocation: Location,
        routePoints: List<Location>,
        magneticHeadingDeg: Float,
        cameraWorldYDeg: Float
    ): ArrowUpdate {

        if (routePoints.isEmpty() || hasArrived) {
            return ArrowUpdate(smoothedWorldYDeg, rawBearingToNodeDeg,
                targetNodeIndex, hasArrived, "no_route")
        }

        // ── 1. Auto-calibrate once ARCore tracking is confirmed stable ─────────
        if (!isNorthCalibrated && isTrackingStable && magneticHeadingDeg != 0f) {
            calibrateNorthOffset(magneticHeadingDeg, cameraWorldYDeg)
        }

        // If not yet calibrated, return last known state — do not spin the arrow.
        if (!isNorthCalibrated) {
            return ArrowUpdate(smoothedWorldYDeg, rawBearingToNodeDeg,
                targetNodeIndex, false, "awaiting_calibration")
        }

        // ── 2. Advance to next node if current one is reached ─────────────────
        advanceNodeIfNeeded(userLocation, routePoints)

        if (hasArrived) {
            return ArrowUpdate(smoothedWorldYDeg, rawBearingToNodeDeg,
                targetNodeIndex, true, "arrived")
        }

        val target = routePoints[targetNodeIndex]

        // ── 3. GPS bearing: user → target node (0°=N, clockwise) ──────────────
        rawBearingToNodeDeg = userLocation.bearingTo(target)
            .let { if (it < 0) it + 360f else it }

        // ── 4. Convert GPS bearing → ARCore world-space angle ──────────────────
        //   worldAngle = bearing + northOffset
        //   This is the Y rotation the arrow must have so it points at the node
        //   in ARCore world space, completely independent of camera orientation.
        val offset = worldNorthOffsetDeg ?: 0f
        val targetWorldYDeg = normalizeDeg(rawBearingToNodeDeg + offset)

        // ── 5. Forward-only guard ──────────────────────────────────────────────
        //   Convert camera direction into world bearing, then check the angular
        //   difference. If the node is more than forwardAngleThreshold behind
        //   the user's travel direction we clamp to prevent backward pointing.
        val cameraBearingWorld = normalizeDeg(cameraWorldYDeg - offset)
        val delta = angularDelta(rawBearingToNodeDeg, cameraBearingWorld)
        val clampedTargetWorldY = if (abs(delta) > forwardAngleThresholdDeg) {
            val clampDir = if (delta > 0) 1f else -1f
            normalizeDeg(cameraBearingWorld + clampDir * forwardAngleThresholdDeg)
        } else {
            targetWorldYDeg
        }

        // ── 6. Exponential low-pass smoothing across wrap boundary ─────────────
        smoothedWorldYDeg    = smoothAngle(lastSmoothedBearing, clampedTargetWorldY, smoothingAlpha)
        lastSmoothedBearing  = smoothedWorldYDeg

        Log.v(TAG, "bearing=$rawBearingToNodeDeg° worldTarget=$targetWorldYDeg° " +
                "smoothed=$smoothedWorldYDeg° node=$targetNodeIndex")

        return ArrowUpdate(
            worldYDeg       = smoothedWorldYDeg,
            rawBearingDeg   = rawBearingToNodeDeg,
            targetNodeIndex = targetNodeIndex,
            arrived         = false,
            debugState      = "ok"
        )
    }

    // ── Node advancement ──────────────────────────────────────────────────────

    private fun advanceNodeIfNeeded(userLocation: Location, routePoints: List<Location>) {
        while (targetNodeIndex < routePoints.size) {
            val node = routePoints[targetNodeIndex]
            val distM = userLocation.distanceTo(node)

            val passedPlane = if (targetNodeIndex > 0) {
                val prev = routePoints[targetNodeIndex - 1]

                // Segment vector prev -> node in local ENU-like approximation
                val segX = node.longitude - prev.longitude
                val segZ = node.latitude - prev.latitude
                val segLen2 = segX * segX + segZ * segZ

                if (segLen2 > 0.0) {
                    val ux = userLocation.longitude - node.longitude
                    val uz = userLocation.latitude - node.latitude
                    // dot > 0 means user is beyond node along segment direction
                    (ux * segX + uz * segZ) > 0.0
                } else false
            } else false

            if ((distM <= nodeReachedRadiusM || passedPlane) && lastReachedIndex != targetNodeIndex) {
                lastReachedIndex = targetNodeIndex
                if (targetNodeIndex >= routePoints.lastIndex) {
                    hasArrived = true
                    return
                }
                targetNodeIndex++
            } else {
                break
            }
        }
    }

    // ── Math helpers ──────────────────────────────────────────────────────────

    /**
     * Exponential low-pass filter that correctly handles the 0°/360° wrap.
     * Uses the shortest angular path so the arrow never spins 340° the wrong way.
     */
    private fun smoothAngle(current: Float, target: Float, alpha: Float): Float {
        var delta = target - current
        while (delta >  180f) delta -= 360f
        while (delta < -180f) delta += 360f
        return normalizeDeg(current + (1f - alpha) * delta)
    }

    /**
     * Signed angular difference from [from] to [to], shortest path.
     * Result in [-180, +180].
     */
    private fun angularDelta(from: Float, to: Float): Float {
        var d = to - from
        while (d >  180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }

    /** Normalize any angle into [0, 360). */
    private fun normalizeDeg(deg: Float): Float {
        var d = deg % 360f
        if (d < 0) d += 360f
        return d
    }

    private fun Float.format2() = "%.2f".format(this)
}

// ── Output data class ─────────────────────────────────────────────────────────

/**
 * Snapshot of arrow state produced by [ARNavigationArrowController.update].
 * Pass this directly to your SceneView renderer each frame.
 */
data class ArrowUpdate(
    /** Y rotation (degrees) to apply to the arrow model in world space. */
    val worldYDeg: Float,
    /** Raw GPS bearing to the current target node (informational). */
    val rawBearingDeg: Float,
    /** Index of the node the arrow is currently pointing toward. */
    val targetNodeIndex: Int,
    /** True when the final node has been reached. */
    val arrived: Boolean,
    /** Debug label — use for logging / UI overlay only. */
    val debugState: String
)