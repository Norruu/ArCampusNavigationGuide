package com.campus.arnav.ui.map.components

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.core.content.ContextCompat
import com.campus.arnav.R
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.BuildingType
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class CustomMarkerOverlay(
    mapView: MapView,
    val building: Building,
    private val onMarkerClick: (Building) -> Unit
) : Marker(mapView) {

    init {
        position = GeoPoint(building.location.latitude, building.location.longitude)
        title = building.name

        // 1. Get your base marker icon.
        // Note: Replace 'ic_destination' with your standard map pin drawable if you have a specific one!
        val iconDrawable = ContextCompat.getDrawable(mapView.context, R.drawable.ic_destination)?.mutate()

        // 2. Assign a premium map color to each category
        val markerColor = when (building.type) {
            BuildingType.ACADEMIC -> Color.parseColor("#FF453A")       // Red
            BuildingType.LIBRARY -> Color.parseColor("#34C759")        // Green
            BuildingType.CAFETERIA -> Color.parseColor("#FF9F0A")      // Orange
            BuildingType.SPORTS -> Color.parseColor("#BF5AF2")         // Violet
            BuildingType.ADMINISTRATIVE -> Color.parseColor("#0A84FF") // Blue
            BuildingType.LANDMARK -> Color.parseColor("#8E8E93")       // Gray
        }

        // 3. Apply the color to the marker icon
        iconDrawable?.colorFilter = PorterDuffColorFilter(markerColor, PorterDuff.Mode.SRC_IN)
        icon = iconDrawable

        // Ensure the bottom tip of the marker points exactly at the GPS coordinate
        setAnchor(ANCHOR_CENTER, ANCHOR_BOTTOM)

        // 4. Handle clicks to open the navigation panel
        setOnMarkerClickListener { _, _ ->
            onMarkerClick(building)
            true
        }
    }
}