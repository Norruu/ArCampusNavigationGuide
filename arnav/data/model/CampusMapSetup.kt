import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView

// Define your campus boundary coordinates
private val campusNorth = 9.857365352521331
private val campusSouth = 9.843818207123961
private val campusEast = 122.89307299341952
private val campusWest = 122.88305672555643

private val defaultZoom = 17.5

fun setupCampusMap(mapView: MapView) {
    // Create OSMDroid BoundingBox (N, E, S, W)
    val boundingBox = BoundingBox(campusNorth, campusEast, campusSouth, campusWest)

    // Restrict map to campus area
    mapView.setScrollableAreaLimitDouble(boundingBox)
    mapView.setMinZoomLevel(defaultZoom)
    mapView.setMaxZoomLevel(20.0)
    mapView.setHorizontalMapRepetitionEnabled(false)
    mapView.setVerticalMapRepetitionEnabled(false)

    // Center the map at campus center on first load
    val centerLat = (campusNorth + campusSouth) / 2
    val centerLon = (campusEast + campusWest) / 2
    mapView.controller.setZoom(defaultZoom)
    mapView.controller.setCenter(org.osmdroid.util.GeoPoint(centerLat, centerLon))
}