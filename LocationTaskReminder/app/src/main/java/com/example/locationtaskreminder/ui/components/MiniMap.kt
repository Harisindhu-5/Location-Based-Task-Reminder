package com.example.locationtaskreminder.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.locationtaskreminder.BuildConfig
import com.example.locationtaskreminder.model.Location

@Composable
fun MiniMap(
    location: Location,
    radius: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapZoom = 15
    
    // Create Geoapify static map URL
    val staticMapUrl = remember(location, radius) {
        buildGeoapifyStaticMapUrl(
            latitude = location.latitude,
            longitude = location.longitude,
            zoom = mapZoom,
            radius = radius
        )
    }

    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Show map image
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(staticMapUrl)
                    .crossfade(true)
                    .listener(
                        onStart = { 
                            isLoading = true 
                            android.util.Log.d("MiniMap", "⭐ DEBUG: Started loading map from URL: ${staticMapUrl.take(100)}...")
                        },
                        onSuccess = { _, _ -> 
                            isLoading = false 
                            android.util.Log.d("MiniMap", "⭐ DEBUG: Successfully loaded map image")
                        },
                        onError = { _, result -> 
                            isLoading = false
                            isError = true
                            errorMessage = result.throwable.message
                            android.util.Log.e("MiniMap", "⭐ DEBUG: Failed to load map image: ${result.throwable.message}", result.throwable)
                            android.util.Log.e("MiniMap", "⭐ DEBUG: Error details: ${result.throwable.javaClass.simpleName}", result.throwable)
                            android.util.Log.e("MiniMap", "⭐ DEBUG: Stack trace: ${result.throwable.stackTraceToString()}")
                            
                            // Log the URL being loaded (but mask the API key for security)
                            val maskedUrl = staticMapUrl.replace(BuildConfig.GEOAPIFY_API_KEY, "API_KEY_MASKED")
                            android.util.Log.e("MiniMap", "⭐ DEBUG: Attempted to load URL: $maskedUrl")
                        }
                    )
                    .build(),
                contentDescription = "Map showing task location",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            
            // Show loading indicator
            if (isLoading) {
                CircularProgressIndicator()
            }
            
            // Show error message
            if (isError) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Could not load map", color = androidx.compose.ui.graphics.Color.Red)
                    errorMessage?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.Red)
                    }
                }
            }
        }
    }
}

private fun buildGeoapifyStaticMapUrl(
    latitude: Double,
    longitude: Double,
    zoom: Int,
    radius: Float
): String {
    val apiKey = BuildConfig.GEOAPIFY_API_KEY
    
    // Scale for better display on high-density screens
    val scale = 2
    
    // Calculate a good width and height for the map
    val width = 800
    val height = 400
    
    // Create marker for task location
    val marker = "lonlat:$longitude,$latitude;color:%23ff0000;size:medium"
    
    // Create circle for geofence radius
    val circle = "lonlat:$longitude,$latitude;radius:$radius;color:rgba(0,0,255,0.6);fill:rgba(0,0,255,0.2);width:2"
    
    // Build the URL with all parameters
    val url = "https://maps.geoapify.com/v1/staticmap" +
           "?style=osm-bright" +
           "&width=$width" +
           "&height=$height" +
           "&center=lonlat:$longitude,$latitude" +
           "&zoom=$zoom" +
           "&scale=$scale" +
           "&marker=$marker" +
           "&circle=$circle" +
           "&apiKey=$apiKey"
    
    // Log the URL construction details
    android.util.Log.d("MiniMap", "⭐ DEBUG: Map URL constructed with API key length: ${apiKey.length}")
    
    // Verify URL is properly formed
    try {
        val uri = java.net.URI(url)
        android.util.Log.d("MiniMap", "⭐ DEBUG: URL is valid: ${uri.scheme}://${uri.host}")
    } catch (e: Exception) {
        android.util.Log.e("MiniMap", "⭐ ERROR: Invalid URL format: ${e.message}")
    }
    
    return url
} 