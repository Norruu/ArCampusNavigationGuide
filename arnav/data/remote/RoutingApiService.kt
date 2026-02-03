package com.campus.arnav.data.remote

import com.campus.arnav.data.model.CampusLocation
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * API Service for routing operations
 *
 * This can connect to:
 * 1. Your own campus backend server
 * 2. OSRM (Open Source Routing Machine)
 * 3. GraphHopper
 * 4. OpenRouteService
 *
 * For this app, we primarily use local A* pathfinding,
 * but this service can be used for:
 * - Fetching updated campus map data
 * - Getting real-time route updates
 * - Syncing with a campus server
 */
interface RoutingApiService {

    // ============== ROUTING ENDPOINTS ==============

    /**
     * Get walking route between two points
     * Compatible with OSRM API format
     */
    @GET("route/v1/walking/{coordinates}")
    suspend fun getRoute(
        @retrofit2.http.Path("coordinates") coordinates: String,  // "lon1,lat1;lon2,lat2"
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "geojson",
        @Query("steps") steps: Boolean = true,
        @Query("annotations") annotations: Boolean = true
    ): Response<OSRMRouteResponse>

    /**
     * Get route from your campus backend
     */
    @POST("api/v1/route")
    suspend fun getCampusRoute(
        @Body request: CampusRouteRequest
    ): Response<CampusRouteResponse>

    /**
     * Get alternative routes
     */
    @POST("api/v1/routes/alternatives")
    suspend fun getAlternativeRoutes(
        @Body request: CampusRouteRequest,
        @Query("alternatives") count: Int = 3
    ): Response<List<CampusRouteResponse>>

    // ============== CAMPUS DATA ENDPOINTS ==============

    /**
     * Get all buildings from server
     */
    @GET("api/v1/buildings")
    suspend fun getBuildings(): Response<List<BuildingResponse>>

    /**
     * Get building by ID
     */
    @GET("api/v1/buildings/{id}")
    suspend fun getBuildingById(
        @retrofit2.http.Path("id") buildingId: String
    ): Response<BuildingResponse>

    /**
     * Search buildings
     */
    @GET("api/v1/buildings/search")
    suspend fun searchBuildings(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): Response<List<BuildingResponse>>

    /**
     * Get campus graph/waypoints for pathfinding
     */
    @GET("api/v1/campus/graph")
    suspend fun getCampusGraph(): Response<CampusGraphResponse>

    /**
     * Get campus map data version (for cache invalidation)
     */
    @GET("api/v1/campus/version")
    suspend fun getCampusDataVersion(): Response<DataVersionResponse>

    // ============== REAL-TIME ENDPOINTS ==============

    /**
     * Report user location (for analytics/crowd density)
     */
    @POST("api/v1/location/report")
    suspend fun reportLocation(
        @Body location: LocationReport
    ): Response<Unit>

    /**
     * Get crowd density for buildings
     */
    @GET("api/v1/buildings/density")
    suspend fun getBuildingDensity(): Response<List<BuildingDensityResponse>>
}

// ============== REQUEST/RESPONSE MODELS ==============

/**
 * Request for campus route
 */
data class CampusRouteRequest(
    val originLat: Double,
    val originLon: Double,
    val destinationLat: Double,
    val destinationLon: Double,
    val destinationBuildingId: String? = null,
    val preferAccessible: Boolean = false,
    val preferIndoor: Boolean = false,
    val avoidStairs: Boolean = false
)

/**
 * Response for campus route
 */
data class CampusRouteResponse(
    val id: String,
    val distance: Double,           // meters
    val duration: Long,             // seconds
    val waypoints: List<WaypointResponse>,
    val steps: List<StepResponse>,
    val geometry: GeometryResponse?
)

data class WaypointResponse(
    val lat: Double,
    val lon: Double,
    val type: String,
    val instruction: String?
)

data class StepResponse(
    val instruction: String,
    val distance: Double,
    val duration: Long,
    val direction: String,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val isIndoor: Boolean
)

data class GeometryResponse(
    val type: String,               // "LineString"
    val coordinates: List<List<Double>>  // [[lon, lat], [lon, lat], ...]
)

/**
 * OSRM Route Response (if using OSRM server)
 */
data class OSRMRouteResponse(
    val code: String,
    val routes: List<OSRMRoute>,
    val waypoints: List<OSRMWaypoint>
)

data class OSRMRoute(
    val distance: Double,
    val duration: Double,
    val geometry: OSRMGeometry,
    val legs: List<OSRMLeg>
)

data class OSRMGeometry(
    val type: String,
    val coordinates: List<List<Double>>
)

data class OSRMLeg(
    val distance: Double,
    val duration: Double,
    val steps: List<OSRMStep>,
    val summary: String
)

data class OSRMStep(
    val distance: Double,
    val duration: Double,
    val geometry: OSRMGeometry,
    val name: String,
    val mode: String,
    val maneuver: OSRMManeuver
)

data class OSRMManeuver(
    val type: String,               // "turn", "arrive", "depart"
    val modifier: String?,          // "left", "right", "straight"
    val location: List<Double>,     // [lon, lat]
    val instruction: String?
)

data class OSRMWaypoint(
    val name: String,
    val location: List<Double>      // [lon, lat]
)

/**
 * Building response from server
 */
data class BuildingResponse(
    val id: String,
    val name: String,
    val shortName: String,
    val description: String,
    val lat: Double,
    val lon: Double,
    val altitude: Double?,
    val type: String,
    val isAccessible: Boolean,
    val imageUrl: String?,
    val entrances: List<EntranceResponse>?
)

data class EntranceResponse(
    val lat: Double,
    val lon: Double,
    val isAccessible: Boolean
)

/**
 * Campus graph response for pathfinding
 */
data class CampusGraphResponse(
    val version: String,
    val nodes: List<GraphNodeResponse>,
    val edges: List<GraphEdgeResponse>
)

data class GraphNodeResponse(
    val id: String,
    val lat: Double,
    val lon: Double,
    val altitude: Double?,
    val type: String,
    val buildingId: String?,
    val floor: Int?
)

data class GraphEdgeResponse(
    val fromId: String,
    val toId: String,
    val distance: Double,
    val isIndoor: Boolean,
    val isAccessible: Boolean
)

/**
 * Data version for cache invalidation
 */
data class DataVersionResponse(
    val version: String,
    val buildingsVersion: String,
    val graphVersion: String,
    val lastUpdated: Long
)

/**
 * Location report for analytics
 */
data class LocationReport(
    val lat: Double,
    val lon: Double,
    val accuracy: Float,
    val timestamp: Long,
    val buildingId: String?
)

/**
 * Building crowd density
 */
data class BuildingDensityResponse(
    val buildingId: String,
    val density: Float,             // 0.0 to 1.0
    val estimatedCount: Int,
    val lastUpdated: Long
)