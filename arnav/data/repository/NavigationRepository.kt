package com.campus.arnav.data.repository

import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.data.model.Route
import com.campus.arnav.domain.pathfinding.HybridCampusPathfinding
import com.campus.arnav.domain.pathfinding.RouteResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationRepository @Inject constructor(
    private val hybridPathfinding: HybridCampusPathfinding
) {

    /**
     * Calculate route using the Hybrid Pathfinding Engine
     * (Stitches OSM + Campus Paths automatically)
     */
    suspend fun calculateRoute(
        start: CampusLocation,
        end: CampusLocation
    ): Route? = withContext(Dispatchers.Default) {
        try {
            // Convert CampusLocation -> GeoPoint for the engine
            val startPoint = GeoPoint(start.latitude, start.longitude)
            val endPoint = GeoPoint(end.latitude, end.longitude)

            // Call the Hybrid Engine
            val result = hybridPathfinding.findRoute(startPoint, endPoint)

            // Handle the result
            return@withContext when (result) {
                is RouteResult.Success -> result.route
                is RouteResult.NoRouteFound -> {
                    android.util.Log.w("NavRepo", "No route found: ${result.message}")
                    null
                }
                is RouteResult.Error -> {
                    android.util.Log.e("NavRepo", "Route error: ${result.message}")
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}