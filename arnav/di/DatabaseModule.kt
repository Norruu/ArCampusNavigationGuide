package com.campus.arnav.di

import android.content.Context
import androidx.room.Room
import com.campus.arnav.data.local.CampusDatabase
import com.campus.arnav.data.local.dao.BuildingDao
import com.campus.arnav.data.local.dao.RouteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideCampusDatabase(
        @ApplicationContext context: Context
    ): CampusDatabase {
        return Room.databaseBuilder(
            context,
            CampusDatabase::class.java,
            "campus_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideBuildingDao(database: CampusDatabase): BuildingDao {
        return database.buildingDao()
    }

    @Provides
    @Singleton
    fun provideRouteDao(database: CampusDatabase): RouteDao {
        return database.routeDao()
    }
}