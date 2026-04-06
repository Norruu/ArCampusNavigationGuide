package com.campus.arnav.ui.map.components

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.campus.arnav.domain.graph.EdgeSource
import com.campus.arnav.domain.graph.NodeSource
import com.campus.arnav.domain.graph.UnifiedGraphManager
import com.campus.arnav.domain.graph.UnifiedGraphSnapshot
import com.campus.arnav.domain.graph.UnifiedNode
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * UnifiedGraphDebugOverlay
 * ────────────────────────
 * Renders the unified graph (nodes + edges) on top of the OSMDroid map.
 *
 * Colour legend
 * ─────────────
 * • HARDCODED nodes  → solid blue circle
 * • ADMIN nodes      → solid orange circle
 * • SYNTHETIC nodes  → solid purple circle (merged / snapped)
 * • HARDCODED edges  → semi-transparent blue line
 * • ADMIN edges      → semi-transparent orange line
 * • Node labels      → white text (visible when zoomed in past level 17)
 *
 * Usage
 * ─────
 *   val debugOverlay = UnifiedGraphDebugOverlay(graphManager)
 *   mapView.overlays.add(debugOverlay)
 *   debugOverlay.setVisible(BuildConfig.DEBUG)
 *
 * Toggling visibility does not remove the overlay; it just skips drawing.
 * This lets you flip it on/off without removing/re-adding.
 */
class UnifiedGraphDebugOverlay(
    private val graphManager: UnifiedGraphManager
) : Overlay() {

    // ── Paints ────────────────────────────────────────────────────────────────

    private val edgeHardcodedPaint = Paint().apply {
        isAntiAlias = true
        style       = Paint.Style.STROKE
        strokeWidth = 6f
        color       = Color.argb(160, 30, 120, 255)   // blue
        strokeCap   = android.graphics.Paint.Cap.ROUND
    }

    private val edgeAdminPaint = Paint().apply {
        isAntiAlias = true
        style       = Paint.Style.STROKE
        strokeWidth = 6f
        color       = Color.argb(160, 255, 140, 0)    // orange
        strokeCap   = android.graphics.Paint.Cap.ROUND
    }

    private val nodeHardcodedPaint = Paint().apply {
        isAntiAlias = true
        style       = Paint.Style.FILL
        color       = Color.argb(230, 30, 120, 255)   // blue
    }

    private val nodeAdminPaint = Paint().apply {
        isAntiAlias = true
        style       = Paint.Style.FILL
        color       = Color.argb(230, 255, 140, 0)    // orange
    }

    private val nodeSyntheticPaint = Paint().apply {
        isAntiAlias = true
        style       = Paint.Style.FILL
        color       = Color.argb(230, 160, 32, 240)   // purple
    }

    private val nodeBorderPaint = Paint().apply {
        isAntiAlias = true
        style       = Paint.Style.STROKE
        strokeWidth = 2f
        color       = Color.WHITE
    }

    private val labelPaint = Paint().apply {
        isAntiAlias = true
        color       = Color.WHITE
        textSize    = 22f
        typeface    = Typeface.DEFAULT_BOLD
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private var visible = true
    private var cachedSnapshot: UnifiedGraphSnapshot? = null

    fun setVisible(v: Boolean) { visible = v }
    fun isDebugVisible() = visible

    /**
     * Pre-fetch a fresh snapshot so [draw] does not need to lock the manager
     * on the main thread.  Call this after the graph changes, e.g. in a
     * [com.campus.arnav.domain.graph.GraphChangeListener].
     */
    fun refresh() {
        cachedSnapshot = graphManager.snapshot()
    }

    // ── Overlay draw ──────────────────────────────────────────────────────────

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !visible) return

        val snap  = cachedSnapshot ?: graphManager.snapshot()
        val proj  = mapView.projection
        val zoom  = mapView.zoomLevelDouble
        val nodeRadius = when {
            zoom >= 19 -> 14f
            zoom >= 18 -> 10f
            else       -> 7f
        }
        val showLabels = zoom >= 17.5

        // ── Draw edges ────────────────────────────────────────────────────────
        val drawnEdges = mutableSetOf<String>()

        for ((fromId, edges) in snap.adjacency) {
            val fromNode = snap.nodes[fromId] ?: continue
            val fromPx   = proj.toPixels(fromNode.toGeoPoint(), null)

            for (edge in edges) {
                val key = "${minOf(fromId, edge.toId)}|${maxOf(fromId, edge.toId)}"
                if (!drawnEdges.add(key)) continue

                val toNode = snap.nodes[edge.toId] ?: continue
                val toPx   = proj.toPixels(toNode.toGeoPoint(), null)

                val paint = if (edge.source == EdgeSource.ADMIN) edgeAdminPaint else edgeHardcodedPaint
                canvas.drawLine(fromPx.x.toFloat(), fromPx.y.toFloat(),
                    toPx.x.toFloat(),   toPx.y.toFloat(), paint)
            }
        }

        // ── Draw nodes ────────────────────────────────────────────────────────
        for (node in snap.nodes.values) {
            val px = proj.toPixels(node.toGeoPoint(), null)
            val cx = px.x.toFloat()
            val cy = px.y.toFloat()

            val fillPaint = when (node.source) {
                NodeSource.ADMIN      -> nodeAdminPaint
                NodeSource.SYNTHETIC  -> nodeSyntheticPaint
                NodeSource.HARDCODED  -> nodeHardcodedPaint
            }

            canvas.drawCircle(cx, cy, nodeRadius, fillPaint)
            canvas.drawCircle(cx, cy, nodeRadius, nodeBorderPaint)

            if (showLabels) {
                val label = when (node.source) {
                    NodeSource.ADMIN     -> "A"
                    NodeSource.SYNTHETIC -> "S"
                    NodeSource.HARDCODED -> "H"
                }
                canvas.drawText(label, cx - labelPaint.textSize / 4, cy + labelPaint.textSize / 3, labelPaint)
            }
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun UnifiedNode.toGeoPoint() =
        GeoPoint(location.latitude, location.longitude, location.altitude)
}