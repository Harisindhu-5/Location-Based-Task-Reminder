package com.example.locationtaskreminder.model

data class Location(
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        fun fromCoordinates(latitude: Double, longitude: Double): Location {
            return Location(latitude, longitude)
        }
    }
} 