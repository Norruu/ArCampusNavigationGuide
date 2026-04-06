package com.campus.arnav.ui.map.components

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.location.Location
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Draws a dotted connector from the user's raw GPS dot to the snapped route point.
 * Visible only when user is off-route by a threshold distance.
 */
class OffRoutePolyline : Overlay() {

    private var userPoint: GeoPoint? = null
    private var nearestRoutePoint: GeoPoint? = null

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.RED
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }

    /**
     * @param userPoint Raw user GPS position (blue dot)
     * @param routePoints Trimmed route points where first point is snapped point
     */
    fun update(userPoint: GeoPoint?, routePoints: List<GeoPoint>?) {
        if (userPoint == null || routePoints.isNullOrEmpty()) {
            this.userPoint = null
            this.nearestRoutePoint = null
            return
        }

        val snap = routePoints.firstOrNull() ?: run {
            this.userPoint = null
            this.nearestRoutePoint = null
            return
        }

        val userLoc = Location("").apply {
            latitude = userPoint.latitude
            longitude = userPoint.longitude
        }
        val snapLoc = Location("").apply {
            latitude = snap.latitude
            longitude = snap.longitude
        }

        val gap = userLoc.distanceTo(snapLoc)

        // Show dotted connector only if user is clearly off-route
        if (gap > 1.0f) {
            this.userPoint = userPoint
            this.nearestRoutePoint = snap
        } else {
            this.userPoint = null
            this.nearestRoutePoint = null
        }
    }

    fun clear() {
        userPoint = null
        nearestRoutePoint = null
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        val user = userPoint ?: return
        val nearest = nearestRoutePoint ?: return

        val projection = mapView.projection
        val p1 = projection.toPixels(user, null)
        val p2 = projection.toPixels(nearest, null)

        canvas.drawLine(
            p1.x.toFloat(),
            p1.y.toFloat(),
            p2.x.toFloat(),
            p2.y.toFloat(),
            paint
        )
    }
}