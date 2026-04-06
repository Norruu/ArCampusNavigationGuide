package com.campus.arnav.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * ARHeadingManager
 * ─────────────────
 * Converts raw magnetic-compass readings into a smooth, stable heading
 * that the AR HUD arrow can use without jittering.
 *
 * Why a separate class?
 * ─────────────────────
 * The raw compass fires 20+ readings/sec, each potentially ±10° off due to
 * magnetic interference. Feeding that straight into a 3D rotation causes the
 * arrow to flicker violently. This class applies two layers of filtering:
 *
 *   1. Circular exponential moving average (EMA) — handles 0°/360° wrap
 *      correctly using sin/cos averaging so the heading never "teleports"
 *      across the North pole.
 *
 *   2. Dead-zone filter — heading updates are suppressed when the phone
 *      is nearly still (delta < MIN_DELTA_DEG), eliminating micro-jitter
 *      from sensor noise when the user is standing still.
 *
 * Usage
 * ─────
 *   val mgr = ARHeadingManager()
 *   // call from your compass callback:
 *   mgr.update(rawMagneticNorthDegrees)
 *   // read smooth result:
 *   val heading = mgr.smoothed   // 0–360, north = 0
 */
class ARHeadingManager {

    companion object {
        /** EMA alpha — higher = faster response, more jitter. 0.12 is a good default. */
        private const val ALPHA = 0.12f

        /** Suppress updates smaller than this to kill standing-still noise (degrees). */
        private const val MIN_DELTA_DEG = 0.4f
    }

    // Accumulated sin/cos components — circular EMA avoids 0/360 wrap issues
    private var sinAcc = 0.0
    private var cosAcc = 0.0
    private var initialized = false

    /** Last smoothed heading in degrees [0, 360). North = 0, East = 90. */
    var smoothed: Float = 0f
        private set

    /**
     * Feed a new raw compass reading (degrees, 0=North, clockwise).
     * Call this every time ARCompassManager fires.
     */
    fun update(rawDegrees: Float) {
        val rad = Math.toRadians(rawDegrees.toDouble())
        val s   = sin(rad)
        val c   = cos(rad)

        if (!initialized) {
            sinAcc      = s
            cosAcc      = c
            initialized = true
            smoothed    = rawDegrees
            return
        }

        // Circular EMA: blend sin/cos components independently
        sinAcc = sinAcc * (1.0 - ALPHA) + s * ALPHA
        cosAcc = cosAcc * (1.0 - ALPHA) + c * ALPHA

        val candidate = Math.toDegrees(atan2(sinAcc, cosAcc)).toFloat()
            .let { if (it < 0f) it + 360f else it }

        // Dead-zone: ignore tiny changes to suppress standing-still jitter
        val delta = shortAngleDiff(candidate, smoothed)
        if (Math.abs(delta) >= MIN_DELTA_DEG) {
            smoothed = candidate
        }
    }

    /** Reset to uninitialized state (call when activity pauses). */
    fun reset() {
        initialized = false
        sinAcc      = 0.0
        cosAcc      = 0.0
        smoothed    = 0f
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Shortest signed angular difference from [from] to [to] in degrees.
     * Always in (-180, +180].
     */
    fun shortAngleDiff(to: Float, from: Float): Float {
        var d = to - from
        while (d >  180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }
}