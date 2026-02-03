package com.campus.arnav.ui.map.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Custom overlay that applies Apple Maps-like styling to the map
 *
 * Features:
 * - Rounded corners overlay
 * - Subtle shadows
 * - Clean, minimal appearance
 */
class AppleMapsStyleOverlay(
    private val context: Context
) : Overlay() {

    // Corner radius for map edges (optional visual effect)
    private var cornerRadius = 0f

    // Whether to show a subtle vignette effect
    private var showVignette = false

    // Vignette paint
    private val vignettePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    // Attribution background paint
    private val attributionBackgroundPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.parseColor("#E6FFFFFF") // Semi-transparent white
    }

    // Map bounds for clipping
    private val mapBounds = RectF()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        // Update map bounds
        mapBounds.set(
            0f,
            0f,
            mapView.width.toFloat(),
            mapView.height.toFloat()
        )

        // Draw vignette effect if enabled
        if (showVignette) {
            drawVignette(canvas, mapView)
        }

        // Draw corner overlays if corner radius is set
        if (cornerRadius > 0) {
            drawRoundedCorners(canvas, mapView)
        }
    }

    /**
     * Draw a subtle vignette effect around the edges
     */
    private fun drawVignette(canvas: Canvas, mapView: MapView) {
        val width = mapView.width.toFloat()
        val height = mapView.height.toFloat()

        // Create radial gradient for vignette
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = kotlin.math.max(width, height) / 1.5f

        val gradient = android.graphics.RadialGradient(
            centerX, centerY, radius,
            intArrayOf(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                Color.parseColor("#10000000")
            ),
            floatArrayOf(0f, 0.7f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )

        vignettePaint.shader = gradient
        canvas.drawRect(mapBounds, vignettePaint)
    }

    /**
     * Draw rounded corners overlay
     */
    private fun drawRoundedCorners(canvas: Canvas, mapView: MapView) {
        val width = mapView.width.toFloat()
        val height = mapView.height.toFloat()

        val cornerPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.WHITE // Or match your background
        }

        // Draw corner masks
        // Top-left corner
        drawCornerMask(canvas, 0f, 0f, cornerRadius, cornerPaint, Corner.TOP_LEFT)

        // Top-right corner
        drawCornerMask(canvas, width - cornerRadius, 0f, cornerRadius, cornerPaint, Corner.TOP_RIGHT)

        // Bottom-left corner
        drawCornerMask(canvas, 0f, height - cornerRadius, cornerRadius, cornerPaint, Corner.BOTTOM_LEFT)

        // Bottom-right corner
        drawCornerMask(canvas, width - cornerRadius, height - cornerRadius, cornerRadius, cornerPaint, Corner.BOTTOM_RIGHT)
    }

    private fun drawCornerMask(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        paint: Paint,
        corner: Corner
    ) {
        val path = android.graphics.Path()

        when (corner) {
            Corner.TOP_LEFT -> {
                path.moveTo(x, y)
                path.lineTo(x + radius, y)
                path.arcTo(
                    RectF(x, y, x + radius * 2, y + radius * 2),
                    -90f, -90f, false
                )
                path.lineTo(x, y)
            }
            Corner.TOP_RIGHT -> {
                path.moveTo(x + radius, y)
                path.lineTo(x + radius, y)
                path.arcTo(
                    RectF(x - radius, y, x + radius, y + radius * 2),
                    -90f, 90f, false
                )
                path.lineTo(x + radius, y)
            }
            Corner.BOTTOM_LEFT -> {
                path.moveTo(x, y + radius)
                path.lineTo(x, y)
                path.arcTo(
                    RectF(x, y - radius, x + radius * 2, y + radius),
                    180f, -90f, false
                )
                path.lineTo(x, y + radius)
            }
            Corner.BOTTOM_RIGHT -> {
                path.moveTo(x + radius, y + radius)
                path.lineTo(x + radius, y)
                path.arcTo(
                    RectF(x - radius, y - radius, x + radius, y + radius),
                    0f, 90f, false
                )
                path.lineTo(x + radius, y + radius)
            }
        }

        path.close()
        canvas.drawPath(path, paint)
    }

    /**
     * Enable/disable vignette effect
     */
    fun setVignetteEnabled(enabled: Boolean) {
        showVignette = enabled
    }

    /**
     * Set corner radius for rounded map edges
     */
    fun setCornerRadius(radius: Float) {
        cornerRadius = radius
    }

    private enum class Corner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    companion object {
        /**
         * Create default Apple Maps style overlay
         */
        fun createDefault(context: Context): AppleMapsStyleOverlay {
            return AppleMapsStyleOverlay(context).apply {
                setVignetteEnabled(false)
                setCornerRadius(0f)
            }
        }

        /**
         * Create Apple Maps style overlay with vignette
         */
        fun createWithVignette(context: Context): AppleMapsStyleOverlay {
            return AppleMapsStyleOverlay(context).apply {
                setVignetteEnabled(true)
                setCornerRadius(0f)
            }
        }

        /**
         * Create Apple Maps style overlay with rounded corners
         */
        fun createWithRoundedCorners(context: Context, radius: Float = 24f): AppleMapsStyleOverlay {
            return AppleMapsStyleOverlay(context).apply {
                setVignetteEnabled(false)
                setCornerRadius(radius)
            }
        }
    }
}