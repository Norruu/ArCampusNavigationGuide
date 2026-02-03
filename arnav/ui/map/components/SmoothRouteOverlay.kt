package com.campus.arnav.ui.map.components

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Custom overlay for drawing smooth, animated route lines on the map
 * Similar to Apple Maps route visualization
 */
class SmoothRouteOverlay(
    private val routePoints: List<GeoPoint>,
    private val routeColor: Int = Color.parseColor("#007AFF"),
    private val strokeWidth: Float = 12f,                    // <-- Use Float
    private val useGradient: Boolean = false,
    private val animated: Boolean = false
) : Overlay() {

    // Main route paint
    private val routePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = routeColor
        strokeWidth = this@SmoothRouteOverlay.strokeWidth   // <-- Already Float
    }

    // Route outline/border paint (for better visibility)
    private val outlinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#40000000") // Semi-transparent black
        strokeWidth = this@SmoothRouteOverlay.strokeWidth + 4f  // <-- Use Float
    }

    // Walked path paint (for showing progress)
    private val walkedPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#34C759") // Green for walked portion
        strokeWidth = this@SmoothRouteOverlay.strokeWidth
    }

    // Dashed line paint (for alternative routes)
    private val dashedPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#80007AFF") // Semi-transparent blue
        strokeWidth = this@SmoothRouteOverlay.strokeWidth - 4f  // <-- Use Float
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)  // <-- Use Float
    }

    // Animation progress (0.0 to 1.0)
    private var animationProgress = 1f

    // Walked progress (0.0 to 1.0) - how much of the route has been walked
    private var walkedProgress = 0f

    // Path objects for drawing
    private val routePath = Path()
    private val walkedPath = Path()

    // Screen points cache
    private val screenPoints = mutableListOf<PointF>()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        if (routePoints.isEmpty()) return

        // Convert geo points to screen points
        updateScreenPoints(mapView)

        if (screenPoints.size < 2) return

        // Build the path
        buildSmoothPath()

        // Draw outline first (for depth effect)
        canvas.drawPath(routePath, outlinePaint)

        // Apply gradient if enabled
        if (useGradient) {
            applyGradient(mapView)
        }

        // Draw main route
        canvas.drawPath(routePath, routePaint)

        // Draw walked portion if any
        if (walkedProgress > 0f) {
            buildWalkedPath()
            canvas.drawPath(walkedPath, walkedPaint)
        }
    }

    /**
     * Convert GeoPoints to screen coordinates
     */
    private fun updateScreenPoints(mapView: MapView) {
        screenPoints.clear()

        val projection = mapView.projection

        for (geoPoint in routePoints) {
            val screenPoint = projection.toPixels(geoPoint, null)
            screenPoints.add(PointF(screenPoint.x.toFloat(), screenPoint.y.toFloat()))
        }
    }

    /**
     * Build a smooth curved path through all points using Bezier curves
     */
    private fun buildSmoothPath() {
        routePath.reset()

        if (screenPoints.size < 2) return

        // Calculate how many points to draw based on animation progress
        val pointsToDraw = if (animated) {
            (screenPoints.size * animationProgress).toInt().coerceAtLeast(2)
        } else {
            screenPoints.size
        }

        // Start at first point
        routePath.moveTo(screenPoints[0].x, screenPoints[0].y)

        if (pointsToDraw == 2) {
            // Just draw a line for 2 points
            routePath.lineTo(screenPoints[1].x, screenPoints[1].y)
            return
        }

        // Use Catmull-Rom spline for smooth curves
        for (i in 0 until pointsToDraw - 1) {
            val p0 = if (i > 0) screenPoints[i - 1] else screenPoints[i]
            val p1 = screenPoints[i]
            val p2 = screenPoints[i + 1]
            val p3 = if (i < pointsToDraw - 2) screenPoints[i + 2] else screenPoints[i + 1]

            // Calculate control points for cubic Bezier
            val tension = 0.5f

            val cp1x = p1.x + (p2.x - p0.x) * tension / 3f
            val cp1y = p1.y + (p2.y - p0.y) * tension / 3f

            val cp2x = p2.x - (p3.x - p1.x) * tension / 3f
            val cp2y = p2.y - (p3.y - p1.y) * tension / 3f

            routePath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
        }
    }

    /**
     * Build the walked portion of the path
     */
    private fun buildWalkedPath() {
        walkedPath.reset()

        if (screenPoints.size < 2 || walkedProgress <= 0f) return

        val pointsWalked = (screenPoints.size * walkedProgress).toInt().coerceAtLeast(1)

        walkedPath.moveTo(screenPoints[0].x, screenPoints[0].y)

        for (i in 0 until pointsWalked.coerceAtMost(screenPoints.size - 1)) {
            val p0 = if (i > 0) screenPoints[i - 1] else screenPoints[i]
            val p1 = screenPoints[i]
            val p2 = screenPoints[i + 1]
            val p3 = if (i < screenPoints.size - 2) screenPoints[i + 2] else screenPoints[i + 1]

            val tension = 0.5f

            val cp1x = p1.x + (p2.x - p0.x) * tension / 3f
            val cp1y = p1.y + (p2.y - p0.y) * tension / 3f

            val cp2x = p2.x - (p3.x - p1.x) * tension / 3f
            val cp2y = p2.y - (p3.y - p1.y) * tension / 3f

            walkedPath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
        }
    }

    /**
     * Apply gradient shader to the route paint
     */
    private fun applyGradient(mapView: MapView) {
        if (screenPoints.size < 2) return

        val startPoint = screenPoints.first()
        val endPoint = screenPoints.last()

        val gradient = LinearGradient(
            startPoint.x, startPoint.y,
            endPoint.x, endPoint.y,
            intArrayOf(
                Color.parseColor("#007AFF"),  // Start color (blue)
                Color.parseColor("#5856D6")   // End color (purple)
            ),
            null,
            Shader.TileMode.CLAMP
        )

        routePaint.shader = gradient
    }

    /**
     * Set animation progress (0.0 to 1.0)
     */
    fun setAnimationProgress(progress: Float) {
        animationProgress = progress.coerceIn(0f, 1f)
    }

    /**
     * Set walked progress (0.0 to 1.0)
     */
    fun setWalkedProgress(progress: Float) {
        walkedProgress = progress.coerceIn(0f, 1f)
    }

    /**
     * Update route points
     */
    fun updateRoutePoints(newPoints: List<GeoPoint>) {
        (routePoints as? MutableList)?.apply {
            clear()
            addAll(newPoints)
        }
    }

    /**
     * Set route color
     */
    fun setRouteColor(color: Int) {
        routePaint.color = color
        routePaint.shader = null // Remove gradient if color is set directly
    }

    /**
     * Set stroke width
     */
    fun setStrokeWidth(width: Float) {
        routePaint.strokeWidth = width
        outlinePaint.strokeWidth = width + 4f
        walkedPaint.strokeWidth = width
    }

    companion object {
        // Predefined route colors
        val COLOR_PRIMARY = Color.parseColor("#007AFF")      // Blue
        val COLOR_ALTERNATIVE = Color.parseColor("#8E8E93")  // Gray
        val COLOR_ACCESSIBLE = Color.parseColor("#34C759")   // Green
        val COLOR_WARNING = Color.parseColor("#FF9500")      // Orange
        val COLOR_WALKED = Color.parseColor("#34C759")       // Green

        /**
         * Create a primary route overlay
         */
        fun createPrimaryRoute(points: List<GeoPoint>): SmoothRouteOverlay {
            return SmoothRouteOverlay(
                routePoints = points,
                routeColor = COLOR_PRIMARY,
                strokeWidth = 12f,
                useGradient = true
            )
        }

        /**
         * Create an alternative route overlay
         */
        fun createAlternativeRoute(points: List<GeoPoint>): SmoothRouteOverlay {
            return SmoothRouteOverlay(
                routePoints = points,
                routeColor = COLOR_ALTERNATIVE,
                strokeWidth = 8f,
                useGradient = false
            )
        }

        /**
         * Create an accessible route overlay
         */
        fun createAccessibleRoute(points: List<GeoPoint>): SmoothRouteOverlay {
            return SmoothRouteOverlay(
                routePoints = points,
                routeColor = COLOR_ACCESSIBLE,
                strokeWidth = 12f,
                useGradient = false
            )
        }
    }
}