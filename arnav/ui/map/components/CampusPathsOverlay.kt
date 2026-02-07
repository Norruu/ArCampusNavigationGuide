package com.campus.arnav.ui.map.components

import android.graphics.Paint
import androidx.core.graphics.toColorInt
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

/**
 * Campus Paths Overlay - Visualize all campus paths on map
 */
class CampusPathsOverlay {

    private val pathOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()

    /**
     * Add all campus paths to the map
     */
    fun addPathsToMap(mapView: MapView) {
        // Clear existing paths
        clearPaths(mapView)

        // Draw all paths from CampusPaths.kt
        CampusPaths.campusPaths.forEach { path ->
            drawPath(mapView, path)
        }
    }

    /**
     * Draw a single campus path
     */
    private fun drawPath(mapView: MapView, path: CampusPaths.Path) {
        val polyline = Polyline().apply {
            // Set the path points
            setPoints(path.points)

            // Color based on path type
            val (pathColor, pathWidth) = when (path.type) {
                CampusPaths.PathType.MAIN_ROAD -> {
                    Pair("#FFD54F".toColorInt(), 12f)  // Yellow, wide
                }
                CampusPaths.PathType.WALKWAY -> {
                    Pair("#FFFFFF".toColorInt(), 10f)  // White, medium
                }
            }

            // Apply color and width
            color = pathColor
            width = pathWidth

            // Add border for better visibility
            outlinePaint.color = "#FFFFFF".toColorInt()  // Dark gray
            outlinePaint.strokeWidth = pathWidth + 35f
            outlinePaint.style = Paint.Style.STROKE


            // Smooth lines
            paint.isAntiAlias = true
            outlinePaint.isAntiAlias = true

            // Rounded ends
            paint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeCap = Paint.Cap.ROUND

            // Set title (shows on tap)
            title = path.name
            snippet = "Type: ${path.type}"
        }

        mapView.overlays.add(polyline)
        pathOverlays.add(polyline)
    }

    /**
     * Clear all path overlays from map
     */
    fun clearPaths(mapView: MapView) {
        pathOverlays.forEach { overlay ->
            mapView.overlays.remove(overlay)
        }
        pathOverlays.clear()
    }

    /**
     * Toggle path visibility on/off
     */
    fun toggleVisibility(mapView: MapView, visible: Boolean) {
        pathOverlays.forEach { overlay ->
            overlay.isEnabled = visible
        }
        mapView.invalidate()
    }

    /**
     * Show only specific path type
     */
    fun filterByType(mapView: MapView, type: CampusPaths.PathType?) {
        if (type == null) {
            // Show all
            pathOverlays.forEach { it.isEnabled = true }
        } else {
            // Filter by type
            pathOverlays.forEach { overlay ->
                // Check if it's a Polyline first
                if (overlay is Polyline) {
                    overlay.isEnabled = overlay.title?.contains(type.name) == true
                } else {
                    overlay.isEnabled = true  // Keep non-Polyline overlays visible
                }
            }
        }
        mapView.invalidate()
    }

    /**
     * Highlight a specific path
     */
    fun highlightPath(pathId: String, mapView: MapView) {
        val path = CampusPaths.campusPaths.find { it.id == pathId }

        if (path != null) {
            // Make all paths semi-transparent
            pathOverlays.forEach { overlay ->
                if (overlay is Polyline) {
                    overlay.paint.alpha = 100  // Dim
                }
            }

            // Find and highlight the target path
            val targetOverlay = pathOverlays.filterIsInstance<Polyline>().find { polyline ->
                polyline.title == path.name
            }

            if (targetOverlay != null) {
                targetOverlay.paint.alpha = 255  // Full brightness
                targetOverlay.width = 16f  // Make thicker
            }

            mapView.invalidate()
        }
    }

    /**
     * Reset all paths to normal visibility
     */
    fun resetHighlight(mapView: MapView) {
        pathOverlays.forEach { overlay ->
            if (overlay is Polyline) {
                overlay.paint.alpha = 255

                // Reset to original width based on type
                val originalWidth = when {
                    overlay.title?.contains("MAIN_ROAD") == true -> 12f
                    overlay.title?.contains("WALKWAY") == true -> 10f
                    else -> 10f
                }

                overlay.width = originalWidth
            }
        }
        mapView.invalidate()
    }

    /**
     * Get the number of paths currently displayed
     */
    fun getPathCount(): Int = pathOverlays.size

    /**
     * Check if paths are currently visible
     */
    fun isVisible(): Boolean = pathOverlays.firstOrNull()?.isEnabled ?: false
}