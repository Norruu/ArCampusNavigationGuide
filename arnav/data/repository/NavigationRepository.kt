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
     * Calculate route using the Hybrid Pathfinding Engine.
     * NOW RETURNS RouteResult instead of Route? to expose errors.
     */
    suspend fun calculateRoute(
        start: CampusLocation,
        end: CampusLocation
    ): RouteResult = withContext(Dispatchers.Default) {
        try {
            val startPoint = GeoPoint(start.latitude, start.longitude)
            val endPoint = GeoPoint(end.latitude, end.longitude)

            // Call the Hybrid Engine
            return@withContext hybridPathfinding.findRoute(startPoint, endPoint)

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext RouteResult.Error("System Error: ${e.message}")
        }
    }
}