package com.campus.arnav.data.local.dao

import androidx.room.*
import com.campus.arnav.data.local.entity.NavigationStepEntity
import com.campus.arnav.data.local.entity.RouteEntity
import com.campus.arnav.data.local.entity.WaypointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {

    // ============== ROUTE OPERATIONS ==============

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: RouteEntity)

    @Query("SELECT * FROM routes WHERE id = :routeId")
    suspend fun getRouteById(routeId: String): RouteEntity?

    @Query("SELECT * FROM routes ORDER BY createdAt DESC")
    suspend fun getAllRoutes(): List<RouteEntity>

    @Query("SELECT * FROM routes ORDER BY createdAt DESC")
    fun getAllRoutesFlow(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE isFavorite = 1 ORDER BY createdAt DESC")
    suspend fun getFavoriteRoutes(): List<RouteEntity>

    @Query("SELECT * FROM routes ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentRoutes(limit: Int = 10): List<RouteEntity>

    @Query("UPDATE routes SET isFavorite = :isFavorite WHERE id = :routeId")
    suspend fun updateFavoriteStatus(routeId: String, isFavorite: Boolean)

    @Delete
    suspend fun deleteRoute(route: RouteEntity)

    @Query("DELETE FROM routes WHERE id = :routeId")
    suspend fun deleteRouteById(routeId: String)

    @Query("DELETE FROM routes")
    suspend fun deleteAllRoutes()

    @Query("DELETE FROM routes WHERE createdAt < :timestamp")
    suspend fun deleteOldRoutes(timestamp: Long)

    // ============== WAYPOINT OPERATIONS ==============

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoint(waypoint: WaypointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoints(waypoints: List<WaypointEntity>)

    @Query("SELECT * FROM waypoints WHERE routeId = :routeId ORDER BY orderIndex ASC")
    suspend fun getWaypointsForRoute(routeId: String): List<WaypointEntity>

    @Query("DELETE FROM waypoints WHERE routeId = :routeId")
    suspend fun deleteWaypointsForRoute(routeId: String)

    // ============== NAVIGATION STEP OPERATIONS ==============

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNavigationStep(step: NavigationStepEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNavigationSteps(steps: List<NavigationStepEntity>)

    @Query("SELECT * FROM navigation_steps WHERE routeId = :routeId ORDER BY orderIndex ASC")
    suspend fun getNavigationStepsForRoute(routeId: String): List<NavigationStepEntity>

    @Query("DELETE FROM navigation_steps WHERE routeId = :routeId")
    suspend fun deleteNavigationStepsForRoute(routeId: String)

    // ============== TRANSACTION OPERATIONS ==============

    /**
     * Save a complete route with all waypoints and steps
     */
    @Transaction
    suspend fun saveCompleteRoute(
        route: RouteEntity,
        waypoints: List<WaypointEntity>,
        steps: List<NavigationStepEntity>
    ) {
        insertRoute(route)
        insertWaypoints(waypoints)
        insertNavigationSteps(steps)
    }

    /**
     * Delete a complete route with all associated data
     */
    @Transaction
    suspend fun deleteCompleteRoute(routeId: String) {
        deleteWaypointsForRoute(routeId)
        deleteNavigationStepsForRoute(routeId)
        deleteRouteById(routeId)
    }

    /**
     * Get route with all waypoints
     */
    @Transaction
    @Query("SELECT * FROM routes WHERE id = :routeId")
    suspend fun getRouteWithWaypoints(routeId: String): RouteWithWaypoints?
}

/**
 * Data class for route with waypoints relationship
 */
data class RouteWithWaypoints(
    @Embedded
    val route: RouteEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "routeId"
    )
    val waypoints: List<WaypointEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "routeId"
    )
    val steps: List<NavigationStepEntity>
)