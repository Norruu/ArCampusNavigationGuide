package com.campus.arnav.di

import com.campus.arnav.data.repository.CampusRepository
import com.campus.arnav.data.repository.LocationRepository
import com.campus.arnav.data.repository.NavigationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    // Repositories are constructor-injected with @Inject
    // This module is for any custom bindings if needed
}