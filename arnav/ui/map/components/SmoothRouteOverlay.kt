package com.campus.arnav.ui.map.components

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import com.campus.arnav.data.model.Route //
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * A Specialized Painter that renders Hybrid Routes with Apple-Maps-like smoothing.
 *
 * UPDATES FOR HYBRID SYSTEM:
 * - Added setRoute() to accept Domain objects directly
 * - Added logic to handle "Stitched" route smoothing
 * - Optimized memory usage during draw cycles
 */
class SmoothRouteOverlay(
    private val mapView: MapView, // Pass MapView in constructor for better projection handling
    private var routePoints: MutableList<GeoPoint> = mutableListOf(),
    private var routeColor: Int = Color.parseColor("#007AFF"),
    private var strokeWidth: Float = 14f,
    private var useGradient: Boolean = false,
    private var animated: Boolean = false
) : Overlay() {

    // --- PAINTS (Pre-allocated for performance) ---

    private val routePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = routeColor
        strokeWidth = this@SmoothRouteOverlay.strokeWidth
    }

    private val outlinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#40000000") // Semi-transparent shadow/outline
        strokeWidth = this@SmoothRouteOverlay.strokeWidth + 6f
    }

    private val walkedPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#34C759") // Apple Maps Green
        strokeWidth = this@SmoothRouteOverlay.strokeWidth
    }

    // --- STATE ---
    private var animationProgress = 1f
    private var walkedProgress = 0f
    private val routePath = Path()
    private val walkedPath = Path()
    private val screenPoints = ArrayList<PointF>() // Reusable list

    // --- DRAWING ---

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        if (routePoints.size < 2) return

        // 1. Projection (Convert Lat/Lon to Screen X/Y)
        // We must do this every frame because the map moves/zooms
        projectPoints(mapView)

        // 2. Build the Smooth Path (Bezier Curves)
        buildSmoothPath(routePath, screenPoints, animationProgress)

        // 3. Draw Outline (Depth)
        canvas.drawPath(routePath, outlinePaint)

        // 4. Draw Main Route
        if (useGradient) updateGradient() // Re-align gradient to current screen path
        canvas.drawPath(routePath, routePaint)

        // 5. Draw Walked Portion (if navigation is active)
        if (walkedProgress > 0f) {
            buildSmoothPath(walkedPath, screenPoints, walkedProgress)
            canvas.drawPath(walkedPath, walkedPaint)
        }
    }

    /**
     * Optimized projection: Reuses PointF objects to avoid Garbage Collection stutter
     */
    private fun projectPoints(mapView: MapView) {
        val projection = mapView.projection

        // Ensure list size matches
        while (screenPoints.size < routePoints.size) screenPoints.add(PointF())
        while (screenPoints.size > routePoints.size) screenPoints.removeAt(screenPoints.lastIndex)

        // Update coordinates
        routePoints.forEachIndexed { index, geoPoint ->
            val p = projection.toPixels(geoPoint, null)
            screenPoints[index].set(p.x.toFloat(), p.y.toFloat())
        }
    }

    /**
     * The Logic: Catmull-Rom Spline to Cubic Bezier conversion
     * This makes the "Stitched" joint between OSM and Campus look seamless.
     */
    private fun buildSmoothPath(path: Path, points: List<PointF>, progress: Float) {
        path.reset()
        if (points.isEmpty()) return

        // Calculate how many points to include based on progress
        val limit = (points.size * progress).toInt().coerceAtLeast(1)
        val drawLimit = limit.coerceAtMost(points.size)

        path.moveTo(points[0].x, points[0].y)

        if (drawLimit < 2) return

        for (i in 0 until drawLimit - 1) {
            val p0 = points[(i - 1).coerceAtLeast(0)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[(i + 2).coerceAtMost(points.size - 1)]

            // Tension: 0.5 is standard Catmull-Rom (smooth but tight)
            val tension = 0.5f

            val cp1x = p1.x + (p2.x - p0.x) * tension / 6f
            val cp1y = p1.y + (p2.y - p0.y) * tension / 6f

            val cp2x = p2.x - (p3.x - p1.x) * tension / 6f
            val cp2y = p2.y - (p3.y - p1.y) * tension / 6f

            path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
        }
    }

    private fun updateGradient() {
        if (screenPoints.isEmpty()) return
        val start = screenPoints.first()
        val end = screenPoints.last()

        // Dynamic gradient that follows the route direction on screen
        routePaint.shader = LinearGradient(
            start.x, start.y, end.x, end.y,
            intArrayOf(Color.parseColor("#007AFF"), Color.parseColor("#5856D6")), // Blue -> Purple
            null,
            Shader.TileMode.CLAMP
        )
    }

    // --- HYBRID INTEGRATION METHODS ---

    /**
     * Directly accepts the Hybrid Route object.
     * This bridges the Domain Layer (HybridCampusPathfinding) and UI Layer (Painter).
     */
    fun setRoute(route: Route) {
        this.routePoints.clear()

        // Convert CampusLocation -> GeoPoint
        route.waypoints.forEach { waypoint ->
            this.routePoints.add(GeoPoint(waypoint.location.latitude, waypoint.location.longitude))
        }

        // Auto-configure style based on route type if you want
        // e.g., if it's very long, force gradient; if short, use solid color
        if (route.totalDistance > 500) {
            useGradient = true
        }

        mapView.invalidate()
    }

    /**
     * Updates the "Walked" progress based on current user location index.
     * @param currentStepIndex The index from NavigationViewModel
     */
    fun updateProgress(currentStepIndex: Int, totalSteps: Int) {
        if (totalSteps > 0) {
            this.walkedProgress = currentStepIndex.toFloat() / totalSteps.toFloat()
            mapView.invalidate()
        }
    }

    fun clear() {
        routePoints.clear()
        screenPoints.clear()
        routePath.reset()
        mapView.invalidate()
    }
}