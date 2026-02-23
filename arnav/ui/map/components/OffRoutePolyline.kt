package com.campus.arnav.ui.map.components

import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.toColorInt
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Draws a grey "walked" line from the user's live dot to the nearest point on
 * the active route — shows clearly that the user is off the planned path.
 */
class OffRoutePolyline : Overlay() {

    private var userPoint: GeoPoint? = null
    private var nearestRoutePoint: GeoPoint? = null

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 10f
        color = "#99FF3B30".toColorInt() // translucent red
        strokeCap = Paint.Cap.ROUND
    }

    fun update(user: GeoPoint?, routePoints: List<GeoPoint>?) {
        userPoint = user
        nearestRoutePoint = if (user != null && !routePoints.isNullOrEmpty()) {
            routePoints.minByOrNull { p ->
                val dLat = p.latitude - user.latitude
                val dLon = p.longitude - user.longitude
                dLat * dLat + dLon * dLon
            }
        } else null
    }

    fun clear() {
        userPoint = null
        nearestRoutePoint = null
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val user = userPoint ?: return
        val nearest = nearestRoutePoint ?: return

        val proj = mapView.projection
        val p1 = proj.toPixels(user, null)
        val p2 = proj.toPixels(nearest, null)

        canvas.drawLine(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), paint)
    }
}