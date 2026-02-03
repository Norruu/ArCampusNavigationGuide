package com.campus.arnav.ui.map.components

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.campus.arnav.R
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.BuildingType
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Custom marker for buildings on the map
 */
class CustomMarkerOverlay(
    mapView: MapView,
    val building: Building,
    private val onMarkerClick: ((Building) -> Unit)? = null
) : Marker(mapView) {

    init {
        // Set position from building location
        position = GeoPoint(
            building.location.latitude,
            building.location.longitude
        )

        // Set marker info
        title = building.name
        snippet = building.description

        // Set anchor to bottom center
        setAnchor(ANCHOR_CENTER, ANCHOR_BOTTOM)

        // Set click listener
        setOnMarkerClickListener { _, _ ->
            onMarkerClick?.invoke(building)
            true
        }
    }

    companion object {
        /**
         * Create a list of markers for buildings
         */
        @JvmStatic
        fun createMarkersForBuildings(
            context: Context,
            mapView: MapView,
            buildings: List<Building>,
            onMarkerClick: ((Building) -> Unit)?
        ): List<CustomMarkerOverlay> {
            val markers = mutableListOf<CustomMarkerOverlay>()

            for (building in buildings) {
                val marker = CustomMarkerOverlay(
                    mapView = mapView,
                    building = building,
                    onMarkerClick = onMarkerClick
                )
                markers.add(marker)
            }

            return markers
        }
    }
}