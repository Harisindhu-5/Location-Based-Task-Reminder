package com.example.locationtaskreminder.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface GeoapifyService {
    @GET("v1/geocode/reverse")
    suspend fun reverseGeocode(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("apiKey") apiKey: String
    ): GeoapifyResponse

    @GET("v1/geocode/search")
    suspend fun searchLocation(
        @Query("text") query: String,
        @Query("apiKey") apiKey: String
    ): GeoapifyResponse

    @GET("v1/routing")
    suspend fun getRoute(
        @Query("waypoints") waypoints: String,
        @Query("mode") mode: String = "drive",
        @Query("apiKey") apiKey: String
    ): GeoapifyRoutingResponse
}

data class GeoapifyResponse(
    val features: List<Feature>
)

data class Feature(
    val properties: Properties,
    val geometry: Geometry
)

data class Properties(
    val name: String?,
    val country: String?,
    val city: String?,
    val street: String?,
    val housenumber: String?,
    val postcode: String?,
    val formatted: String?
)

data class Geometry(
    val coordinates: List<Double>
)

data class GeoapifyRoutingResponse(
    val features: List<RoutingFeature>
)

data class RoutingFeature(
    val properties: RoutingProperties,
    val geometry: Geometry
)

data class RoutingProperties(
    val distance: Double,
    val time: Int
) 