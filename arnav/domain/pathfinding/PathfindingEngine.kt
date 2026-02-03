package com.campus.arnav.domain.pathfinding

import com.campus.arnav.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PathfindingEngine - Main entry point for all pathfinding operations
 *
 * This class serves as a facade that:
 * - Manages the campus graph
 * - Provides easy-to-use pathfinding methods
 * - Handles graph initialization and updates
 * - Caches computed routes
 */
@Singleton
class PathfindingEngine @Inject constructor() {

    private var campusGraph: CampusGraph? = null
    private var pathfinder: AStarPathfinder? = null
    private var isInitialized = false

    // Route cache for recently calculated routes
    private val routeCache = mutableMapOf<String, Route>()
    private val maxCacheSize = 20

    /**
     * Initialize the pathfinding engine with campus data
     */
    suspend fun initialize(
        buildings: List<Building>,
        pathNodes: List<PathNode>,
        pathConnections: List<PathConnection>
    ) = withContext(Dispatchers.Default) {
        val builder = CampusGraphBuilder()

        // Add buildings with entrances
        buildings.forEach { building ->
            builder.addBuilding(building)
        }

        // Add path nodes (outdoor walkways, intersections)
        pathNodes.forEach { node ->
            builder.addPathNode(node.id, node.location)
        }

        // Connect paths
        pathConnections.forEach { connection ->
            builder.connectPath(
                connection.fromId,
                connection.toId,
                connection.isAccessible
            )
        }

        campusGraph = builder.build()
        pathfinder = AStarPathfinder(campusGraph!!)
        isInitialized = true
    }

    /**
     * Initialize with a pre-built graph
     */
    fun initializeWithGraph(graph: CampusGraph) {
        campusGraph = graph
        pathfinder = AStarPathfinder(graph)
        isInitialized = true
    }

    /**
     * Check if the engine is ready
     */
    fun isReady(): Boolean = isInitialized && pathfinder != null

    /**
     * Find the best route between two locations
     */
    suspend fun findRoute(
        start: CampusLocation,
        end: CampusLocation,
        options: RouteOptions = RouteOptions()
    ): RouteResult = withContext(Dispatchers.Default) {
        if (!isReady()) {
            return@withContext RouteResult.Error("Pathfinding engine not initialized")
        }

        // Check cache first
        val cacheKey = generateCacheKey(start, end, options)
        routeCache[cacheKey]?.let { cachedRoute ->
            return@withContext RouteResult.Success(cachedRoute)
        }

        try {
            val config = AStarPathfinder.PathfindingConfig(
                preferAccessible = options.accessible,
                preferOutdoor = options.preferOutdoor,
                avoidStairs = options.avoidStairs,
                walkingSpeed = options.walkingSpeed
            )

            val route = pathfinder?.findPath(start, end, config)

            if (route != null) {
                // Cache the result
                cacheRoute(cacheKey, route)
                RouteResult.Success(route)
            } else {
                RouteResult.NoRouteFound("No route found between the specified locations")
            }
        } catch (e: Exception) {
            RouteResult.Error("Error calculating route: ${e.message}")
        }
    }

    /**
     * Find route to a building
     */
    suspend fun findRouteToBuilding(
        start: CampusLocation,
        building: Building,
        options: RouteOptions = RouteOptions()
    ): RouteResult {
        // Find the nearest entrance
        val destination = if (building.entrances.isNotEmpty()) {
            findNearestEntrance(start, building.entrances)
        } else {
            building.location
        }

        return findRoute(start, destination, options)
    }

    /**
     * Find multiple alternative routes
     */
    suspend fun findAlternativeRoutes(
        start: CampusLocation,
        end: CampusLocation,
        maxAlternatives: Int = 3
    ): List<Route> = withContext(Dispatchers.Default) {
        if (!isReady()) {
            return@withContext emptyList()
        }

        val routes = mutableListOf<Route>()

        // Standard route
        val standardConfig = AStarPathfinder.PathfindingConfig()
        pathfinder?.findPath(start, end, standardConfig)?.let { routes.add(it) }

        // Accessible route
        val accessibleConfig = AStarPathfinder.PathfindingConfig(
            preferAccessible = true,
            avoidStairs = true
        )
        pathfinder?.findPath(start, end, accessibleConfig)?.let { route ->
            if (!routes.any { isSimilarRoute(it, route) }) {
                routes.add(route)
            }
        }

        // Outdoor-preferred route
        val outdoorConfig = AStarPathfinder.PathfindingConfig(
            preferOutdoor = true
        )
        pathfinder?.findPath(start, end, outdoorConfig)?.let { route ->
            if (!routes.any { isSimilarRoute(it, route) }) {
                routes.add(route)
            }
        }

        // Indoor-preferred route (for bad weather)
        val indoorConfig = AStarPathfinder.PathfindingConfig(
            preferOutdoor = false
        )
        pathfinder?.findPath(start, end, indoorConfig)?.let { route ->
            if (!routes.any { isSimilarRoute(it, route) }) {
                routes.add(route)
            }
        }

        routes.take(maxAlternatives)
    }

    /**
     * Find nearest point of interest
     */
    fun findNearestBuilding(
        location: CampusLocation,
        buildings: List<Building>,
        type: BuildingType? = null
    ): Building? {
        val filteredBuildings = if (type != null) {
            buildings.filter { it.type == type }
        } else {
            buildings
        }

        return filteredBuildings.minByOrNull { building ->
            calculateDistance(location, building.location)
        }
    }

    /**
     * Calculate walking time between two points
     */
    suspend fun estimateWalkingTime(
        start: CampusLocation,
        end: CampusLocation,
        walkingSpeed: Double = 1.4 // m/s
    ): Long? {
        val result = findRoute(start, end, RouteOptions(walkingSpeed = walkingSpeed))
        return when (result) {
            is RouteResult.Success -> result.route.estimatedTime
            else -> null
        }
    }

    /**
     * Get distance to a location
     */
    fun getDirectDistance(from: CampusLocation, to: CampusLocation): Double {
        return calculateDistance(from, to)
    }

    /**
     * Check if two locations are within walking distance
     */
    fun isWithinWalkingDistance(
        from: CampusLocation,
        to: CampusLocation,
        maxDistance: Double = 2000.0 // 2km default
    ): Boolean {
        return calculateDistance(from, to) <= maxDistance
    }

    /**
     * Find the nearest graph node to a location
     */
    fun findNearestNode(location: CampusLocation): CampusGraph.GraphNode? {
        return campusGraph?.findNearestNode(location)
    }

    /**
     * Update a portion of the graph (for dynamic obstacles, closures, etc.)
     */
    fun updateGraphEdge(
        fromId: String,
        toId: String,
        isAccessible: Boolean
    ) {
        // This would update the graph edge accessibility
        // Useful for temporary closures, construction, etc.
        // Implementation depends on CampusGraph supporting updates
    }

    /**
     * Clear the route cache
     */
    fun clearCache() {
        routeCache.clear()
    }

    /**
     * Reset the engine
     */
    fun reset() {
        campusGraph = null
        pathfinder = null
        isInitialized = false
        routeCache.clear()
    }

    // ============== PRIVATE HELPER METHODS ==============

    private fun findNearestEntrance(
        location: CampusLocation,
        entrances: List<CampusLocation>
    ): CampusLocation {
        return entrances.minByOrNull { entrance ->
            calculateDistance(location, entrance)
        } ?: entrances.first()
    }

    private fun calculateDistance(from: CampusLocation, to: CampusLocation): Double {
        val earthRadius = 6371000.0 // meters

        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = Math.toRadians(to.latitude - from.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)

        val a = kotlin.math.sin(deltaLat / 2).let { it * it } +
                kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
                kotlin.math.sin(deltaLon / 2).let { it * it }
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return earthRadius * c
    }

    private fun isSimilarRoute(route1: Route, route2: Route): Boolean {
        // Consider routes similar if they share more than 80% of waypoints
        if (route1.waypoints.size != route2.waypoints.size) {
            val sizeDiff = kotlin.math.abs(route1.waypoints.size - route2.waypoints.size)
            if (sizeDiff > 2) return false
        }

        val sharedWaypoints = route1.waypoints.count { wp1 ->
            route2.waypoints.any { wp2 ->
                calculateDistance(wp1.location, wp2.location) < 15.0
            }
        }

        val similarity = sharedWaypoints.toDouble() / route1.waypoints.size
        return similarity > 0.8
    }

    private fun generateCacheKey(
        start: CampusLocation,
        end: CampusLocation,
        options: RouteOptions
    ): String {
        return "${start.latitude},${start.longitude}-" +
                "${end.latitude},${end.longitude}-" +
                "${options.accessible}-${options.preferOutdoor}-${options.avoidStairs}"
    }

    private fun cacheRoute(key: String, route: Route) {
        // Remove oldest entry if cache is full
        if (routeCache.size >= maxCacheSize) {
            routeCache.remove(routeCache.keys.first())
        }
        routeCache[key] = route
    }
}

// ============== SUPPORTING DATA CLASSES ==============

/**
 * Options for route calculation
 */
data class RouteOptions(
    val accessible: Boolean = false,
    val preferOutdoor: Boolean = true,
    val avoidStairs: Boolean = false,
    val walkingSpeed: Double = 1.4 // meters per second
)

/**
 * Result of a route calculation
 */
sealed class RouteResult {
    data class Success(val route: Route) : RouteResult()
    data class NoRouteFound(val message: String) : RouteResult()
    data class Error(val message: String) : RouteResult()
}

/**
 * Path node for graph construction
 */
data class PathNode(
    val id: String,
    val location: CampusLocation
)

/**
 * Path connection for graph construction
 */
data class PathConnection(
    val fromId: String,
    val toId: String,
    val isAccessible: Boolean = true
)