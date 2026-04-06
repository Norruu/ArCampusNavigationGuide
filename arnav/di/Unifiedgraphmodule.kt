package com.campus.arnav.di

import com.campus.arnav.domain.graph.AdminRoadSyncAdapter
import com.campus.arnav.domain.graph.UnifiedGraphManager
import com.campus.arnav.domain.graph.UnifiedPathfinder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that wires the unified graph pipeline.
 *
 * All three classes are @Singleton, so there is exactly one graph instance
 * for the lifetime of the application.
 *
 * [UnifiedGraphManager] → [UnifiedPathfinder]
 *                       → [AdminRoadSyncAdapter] (feeds Firestore → graph)
 *
 * Note: [UnifiedGraphManager] and [UnifiedPathfinder] are @Inject-constructor
 * classes, so Hilt can provide them automatically.  This module provides
 * [AdminRoadSyncAdapter] explicitly only to demonstrate the wiring;
 * it too could be @Inject-constructor if preferred.
 */
@Module
@InstallIn(SingletonComponent::class)
object UnifiedGraphModule {

    // UnifiedGraphManager is @Singleton + @Inject constructor → auto-provided by Hilt
    // UnifiedPathfinder   is @Singleton + @Inject constructor → auto-provided by Hilt
    // AdminRoadSyncAdapter is @Singleton + @Inject constructor → auto-provided by Hilt

    // Expose them explicitly here only if you want named bindings or interception.
    // For this project the @Inject constructors are sufficient.

    @Provides
    @Singleton
    fun provideUnifiedGraphManager(): UnifiedGraphManager = UnifiedGraphManager()

    @Provides
    @Singleton
    fun provideUnifiedPathfinder(
        graphManager: UnifiedGraphManager
    ): UnifiedPathfinder = UnifiedPathfinder(graphManager)
}