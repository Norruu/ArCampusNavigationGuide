package com.campus.arnav.data.local.dao

import androidx.room.*
import com.campus.arnav.data.local.entity.BuildingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildingDao {

    @Query("SELECT * FROM buildings")
    suspend fun getAllBuildings(): List<BuildingEntity>

    @Query("SELECT * FROM buildings")
    fun getAllBuildingsFlow(): Flow<List<BuildingEntity>>

    @Query("SELECT * FROM buildings WHERE id = :id")
    suspend fun getBuildingById(id: String): BuildingEntity?

    @Query("SELECT * FROM buildings WHERE name LIKE '%' || :query || '%' OR shortName LIKE '%' || :query || '%'")
    suspend fun searchBuildings(query: String): List<BuildingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuilding(building: BuildingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuildings(buildings: List<BuildingEntity>)

    @Delete
    suspend fun deleteBuilding(building: BuildingEntity)

    @Query("DELETE FROM buildings")
    suspend fun deleteAllBuildings()
}