package com.campus.arnav.ui.map.components

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.core.graphics.toColorInt
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Draws a dashed line from the last road-snap point to the building marker pin.
 * This is the "last 20m" connector so the route always visually reaches the icon.
 */
class DestinationConnectorOverlay : Overlay() {

    private var snapPoint: GeoPoint? = null
    private var markerPoint: GeoPoint? = null

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = "#0A84FF".toColorInt()
        pathEffect = DashPathEffect(floatArrayOf(20f, 12f), 0f)
        strokeCap = Paint.Cap.ROUND
    }

    fun set(snap: GeoPoint, marker: GeoPoint) {
        snapPoint = snap
        markerPoint = marker
    }

    fun clear() {
        snapPoint = null
        markerPoint = null
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val snap = snapPoint ?: return
        val marker = markerPoint ?: return

        val proj = mapView.projection
        val p1 = proj.toPixels(snap, null)
        val p2 = proj.toPixels(marker, null)

        canvas.drawLine(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), paint)
    }
}