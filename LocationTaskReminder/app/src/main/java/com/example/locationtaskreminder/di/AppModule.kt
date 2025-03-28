package com.example.locationtaskreminder.di

import android.content.Context
import com.example.locationtaskreminder.data.local.TaskDao
import com.example.locationtaskreminder.data.local.TaskDatabase
import com.example.locationtaskreminder.data.repository.TaskRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideTaskDatabase(
        @ApplicationContext context: Context
    ): TaskDatabase = TaskDatabase.getDatabase(context)

    @Provides
    @Singleton
    fun provideTaskDao(database: TaskDatabase): TaskDao = database.taskDao()

    @Provides
    @Singleton
    fun provideTaskRepository(taskDao: TaskDao): TaskRepository = TaskRepository(taskDao)
} 