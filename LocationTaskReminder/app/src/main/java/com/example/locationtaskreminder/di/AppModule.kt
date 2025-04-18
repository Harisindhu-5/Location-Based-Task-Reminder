package com.example.locationtaskreminder.di

import android.content.Context
import androidx.room.Room
import com.example.locationtaskreminder.data.local.TaskDatabase
import com.example.locationtaskreminder.data.repository.GeoapifyRepository
import com.example.locationtaskreminder.data.repository.TaskRepository
import com.example.locationtaskreminder.service.GeofenceManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.example.locationtaskreminder.data.api.GeoapifyService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TaskDatabase {
        return TaskDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideTaskRepository(database: TaskDatabase): TaskRepository {
        return TaskRepository(database.taskDao())
    }

    @Provides
    @Singleton
    fun provideGeofenceManager(
        @ApplicationContext context: Context,
        geofencingClient: GeofencingClient
    ): GeofenceManager {
        return GeofenceManager(context, geofencingClient)
    }
    
    @Provides
    @Singleton
    fun provideGeoapifyRepository(geoapifyService: GeoapifyService): GeoapifyRepository {
        return GeoapifyRepository(geoapifyService)
    }
    
    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(@ApplicationContext context: Context): FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(context)
    }
    
    @Provides
    @Singleton
    fun provideGeofencingClient(@ApplicationContext context: Context): GeofencingClient {
        return LocationServices.getGeofencingClient(context)
    }
} 