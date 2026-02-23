package com.campus.arnav.ui.map.components

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import com.campus.arnav.data.model.Route
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class SmoothRouteOverlay(
    private val mapView: MapView,
    private var routePoints: MutableList<GeoPoint> = mutableListOf(),
    // Base Apple Maps Dark Mode Blue
    private var routeColor: Int = Color.parseColor("#0A84FF"),
    // Massive Thickness
    private var strokeWidth: Float = 28f,
    private var useGradient: Boolean = true,
    private var animated: Boolean = false
) : Overlay() {

    // --- PAINTS ---

    // 1. THE CASING (Dark outline to separate the route from the dark map)
    private val outlinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        // This ensures sharp 90-degree coordinate turns get a smooth, rounded elbow!
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#B3000000") // 70% Black for dark mode contrast
        strokeWidth = this@SmoothRouteOverlay.strokeWidth + 10f
    }

    // 2. THE MAIN ROUTE (Vibrant blue with a glow)
    private val routePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = routeColor
        strokeWidth = this@SmoothRouteOverlay.strokeWidth
        // Subtle neon glow effect
        setShadowLayer(12f, 0f, 0f, Color.parseColor("#800A84FF"))
    }

    // 3. THE WALKED ROUTE (Apple's translucent gray)
    private val walkedPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#668E8E93")
        strokeWidth = this@SmoothRouteOverlay.strokeWidth
    }

    // --- STATE ---
    private var animationProgress = 1f
    private var walkedProgress = 0f
    private val routePath = Path()
    private val walkedPath = Path()
    private val screenPoints = ArrayList<PointF>()

    // --- DRAWING ---

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        if (routePoints.size < 2) return

        // 1. Convert Lat/Lon to Screen X/Y
        projectPoints(mapView)

        // 2. Build the EXACT Path (No more math altering your coordinates!)
        buildExactPath(routePath, screenPoints, animationProgress)

        // 3. Draw Dark Casing Outline first
        canvas.drawPath(routePath, outlinePaint)

        // 4. Update the vibrant gradient and draw the main blue route
        if (useGradient) updateGradient()
        canvas.drawPath(routePath, routePaint)

        // 5. Draw Walked Portion over the top
        if (walkedProgress > 0f) {
            buildExactPath(walkedPath, screenPoints, walkedProgress)
            canvas.drawPath(walkedPath, walkedPaint)
        }
    }

    private fun projectPoints(mapView: MapView) {
        val projection = mapView.projection

        while (screenPoints.size < routePoints.size) screenPoints.add(PointF())
        while (screenPoints.size > routePoints.size) screenPoints.removeAt(screenPoints.lastIndex)

        routePoints.forEachIndexed { index, geoPoint ->
            val p = projection.toPixels(geoPoint, null)
            screenPoints[index].set(p.x.toFloat(), p.y.toFloat())
        }
    }

    // --- THE FIX ---
    // Connects your exact coordinates with straight lines, relying on
    // Paint.Join.ROUND to make the corners look visually smooth.
    private fun buildExactPath(path: Path, points: List<PointF>, progress: Float) {
        path.reset()
        if (points.isEmpty()) return

        val limit = (points.size * progress).toInt().coerceAtLeast(1)
        val drawLimit = limit.coerceAtMost(points.size)

        path.moveTo(points[0].x, points[0].y)

        for (i in 1 until drawLimit) {
            path.lineTo(points[i].x, points[i].y)
        }
    }

    private fun updateGradient() {
        if (screenPoints.isEmpty()) return
        val start = screenPoints.first()
        val end = screenPoints.last()

        routePaint.shader = LinearGradient(
            start.x, start.y, end.x, end.y,
            intArrayOf(Color.parseColor("#32ADE6"), Color.parseColor("#0A84FF")),
            null,
            Shader.TileMode.CLAMP
        )
    }

    fun setRoute(route: Route) {
        this.routePoints.clear()
        route.waypoints.forEach { waypoint ->
            this.routePoints.add(GeoPoint(waypoint.location.latitude, waypoint.location.longitude))
        }

        useGradient = true
        mapView.invalidate()
    }

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