package com.campus.arnav.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.campus.arnav.data.local.dao.BuildingDao
import com.campus.arnav.data.local.dao.RouteDao
import com.campus.arnav.data.local.entity.BuildingEntity
import com.campus.arnav.data.local.entity.NavigationStepEntity
import com.campus.arnav.data.local.entity.RouteEntity
import com.campus.arnav.data.local.entity.WaypointEntity

@Database(
    entities = [
        BuildingEntity::class,
        RouteEntity::class,
        WaypointEntity::class,
        NavigationStepEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CampusDatabase : RoomDatabase() {
    abstract fun buildingDao(): BuildingDao
    abstract fun routeDao(): RouteDao
}