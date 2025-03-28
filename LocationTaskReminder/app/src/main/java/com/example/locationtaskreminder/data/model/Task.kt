package com.example.locationtaskreminder.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float, // Geofence radius in meters
    val locationName: String,
    val category: TaskCategory,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

enum class TaskCategory {
    HOME,
    WORK,
    SHOPPING,
    OTHER
} 