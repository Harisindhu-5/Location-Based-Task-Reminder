package com.example.locationtaskreminder.data.repository

import com.example.locationtaskreminder.BuildConfig
import com.example.locationtaskreminder.data.api.GeoapifyService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeoapifyRepository @Inject constructor(
    private val geoapifyService: GeoapifyService
) {
    suspend fun reverseGeocode(latitude: Double, longitude: Double) =
        geoapifyService.reverseGeocode(
            latitude = latitude,
            longitude = longitude,
            apiKey = BuildConfig.GEOAPIFY_API_KEY
        )

    suspend fun searchLocation(query: String) =
        geoapifyService.searchLocation(
            query = query,
            apiKey = BuildConfig.GEOAPIFY_API_KEY
        )

    suspend fun getRoute(startLat: Double, startLon: Double, endLat: Double, endLon: Double) =
        geoapifyService.getRoute(
            waypoints = "$startLat,$startLon|$endLat,$endLon",
            apiKey = BuildConfig.GEOAPIFY_API_KEY
        )
} 