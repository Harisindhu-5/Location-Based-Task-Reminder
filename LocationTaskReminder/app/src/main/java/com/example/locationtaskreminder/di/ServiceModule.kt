package com.example.locationtaskreminder.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    // Removed duplicate provider methods that are already in AppModule
} 