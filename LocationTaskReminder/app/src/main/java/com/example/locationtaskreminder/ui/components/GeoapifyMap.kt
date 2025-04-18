package com.example.locationtaskreminder.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.locationtaskreminder.BuildConfig
import com.example.locationtaskreminder.model.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Composable
fun GeoapifyMap(
    modifier: Modifier = Modifier,
    initialLocation: Location? = null,
    onLocationSelected: (Location) -> Unit
) {
    val context = LocalContext.current
    val client = remember { OkHttpClient.Builder().build() }
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<GeocodingResult>>(emptyList()) }
    var showSearchResults by remember { mutableStateOf(false) }
    var selectedLocation by remember { mutableStateOf(initialLocation) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    // Test the API key at component initialization
    LaunchedEffect(Unit) {
        testApiKey(client)
        
        // Test static map endpoint
        testStaticMapEndpoint(client)
    }

    LaunchedEffect(initialLocation) {
        if (initialLocation != null && selectedLocation == null) {
            selectedLocation = initialLocation
            onLocationSelected(initialLocation)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { 
                searchQuery = it
                showSearchResults = it.isNotEmpty()
            },
            placeholder = { Text("Search for a location") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { 
                        searchQuery = ""
                        showSearchResults = false
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        // Search button
        Button(
            onClick = {
                if (searchQuery.isNotEmpty()) {
                    isSearching = true
                    searchError = null
                    showSearchResults = true
                    
                    scope.launch {
                        try {
                            val results = searchLocation(client, searchQuery) { results ->
                                searchResults = results
                            }
                            searchResults = results
                            isSearching = false
                        } catch (e: Exception) {
                            Log.e("GeoapifyMap", "Search error: ${e.message}", e)
                            searchError = "Failed to search: ${e.message}"
                            isSearching = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Search")
        }

        // Search results
        if (showSearchResults) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                tonalElevation = 2.dp,
                shadowElevation = 2.dp
            ) {
                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (searchError != null) {
                    Text(
                        text = searchError ?: "Error during search",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                } else if (searchResults.isEmpty()) {
                    Text(
                        text = "No results found",
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        searchResults.forEach { result ->
                            ListItem(
                                headlineContent = { Text(result.formatted) },
                                modifier = Modifier.clickable {
                                    val location = Location(
                                        latitude = result.latitude,
                                        longitude = result.longitude
                                    )
                                    selectedLocation = location
                                    onLocationSelected(location)
                                    showSearchResults = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Map display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (selectedLocation != null) {
                val mapUrl = buildStaticMapUrl(selectedLocation!!.latitude, selectedLocation!!.longitude)
                Log.d("GeoapifyMap", "⭐ DEBUG: Loading map with URL length: ${mapUrl.length}")
                
                var isLoading by remember { mutableStateOf(true) }
                var isError by remember { mutableStateOf(false) }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    // Use AsyncImage without error parameter
                AsyncImage(
                    model = ImageRequest.Builder(context)
                            .data(mapUrl)
                        .crossfade(true)
                        .listener(
                                onStart = { 
                                    isLoading = true
                                    isError = false
                                    Log.d("GeoapifyMap", "⭐ DEBUG: Started loading map image")
                                },
                                onSuccess = { _, _ ->
                                    isLoading = false
                                    Log.d("GeoapifyMap", "⭐ DEBUG: Successfully loaded map image")
                                },
                                onError = { _, result ->
                                isLoading = false
                                    isError = true
                                    Log.e("GeoapifyMap", "⭐ DEBUG: Failed to load map image: ${result.throwable.message}", result.throwable)
                                    // Add more detailed diagnostic info
                                    Log.e("GeoapifyMap", "⭐ DEBUG: Error details: ${result.throwable.javaClass.simpleName}", result.throwable)
                                    // Log the complete stack trace as a string for easier viewing
                                    val stackTraceString = result.throwable.stackTraceToString()
                                    Log.e("GeoapifyMap", "⭐ DEBUG: Stack trace: $stackTraceString")
                                    // Log the URL being loaded (but mask the API key)
                                    val maskedUrl = mapUrl.replace(BuildConfig.GEOAPIFY_API_KEY, "API_KEY_MASKED")
                                    Log.e("GeoapifyMap", "⭐ DEBUG: Attempted to load URL: $maskedUrl")
                            }
                        )
                        .build(),
                    contentDescription = "Map",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
                    // Show loading indicator
                if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.LightGray.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                    CircularProgressIndicator()
                        }
                    }
                    
                    // Show error state
                    if (isError) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = "Failed to load map image", color = Color.Red)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = "Check logs for details", color = Color.Red, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = {
                                    // Trigger reload with a timestamp to avoid caching
                                    isLoading = true
                                    isError = false
                                    val newMapUrl = buildStaticMapUrl(selectedLocation!!.latitude, selectedLocation!!.longitude) + "&_t=${System.currentTimeMillis()}"
                                    Log.d("GeoapifyMap", "⭐ DEBUG: Retrying map load with timestamped URL")
                                }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
            } else {
                Text("Search for a location to see the map")
            }
        }
    }
}

private data class GeocodingResult(
    val latitude: Double,
    val longitude: Double,
    val formatted: String
)

private suspend fun searchLocation(
    client: OkHttpClient,
    query: String,
    onResults: (List<GeocodingResult>) -> Unit
): List<GeocodingResult> {
    val results = mutableListOf<GeocodingResult>()
    try {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://api.geoapify.com/v1/geocode/search?text=$encodedQuery&apiKey=${BuildConfig.GEOAPIFY_API_KEY}"
        
        val request = Request.Builder()
            .url(url)
            .build()

        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.let { jsonString ->
                        val jsonObject = JSONObject(jsonString)
                        val features = jsonObject.getJSONArray("features")

                        for (i in 0 until features.length()) {
                            val feature = features.getJSONObject(i)
                            val properties = feature.getJSONObject("properties")
                            val geometry = feature.getJSONObject("geometry")
                            val coordinates = geometry.getJSONArray("coordinates")

                            results.add(
                                GeocodingResult(
                                    latitude = coordinates.getDouble(1),
                                    longitude = coordinates.getDouble(0),
                                    formatted = properties.getString("formatted")
                                )
                            )
                        }

                        withContext(Dispatchers.Main) {
                            onResults(results)
                        }
                    }
                } else {
                    Log.e("GeoapifyMap", "Geocoding failed: ${response.code}")
                }
            }
        }
    } catch (e: Exception) {
        Log.e("GeoapifyMap", "Error during geocoding", e)
    }
    return results
}

private fun buildStaticMapUrl(latitude: Double, longitude: Double): String {
    val url = "https://maps.geoapify.com/v1/staticmap" +
            "?style=osm-carto" +
            "&width=600" +
            "&height=400" +
            "&center=lonlat:$longitude,$latitude" +
            "&zoom=15" +
            "&marker=lonlat:$longitude,$latitude;color:%23ff0000;size:medium" +
            "&apiKey=${BuildConfig.GEOAPIFY_API_KEY}"
    
    // Check that API key is not empty
    if (BuildConfig.GEOAPIFY_API_KEY.isBlank()) {
        Log.e("GeoapifyMap", "⭐ ERROR: API Key is empty or blank!")
    } else {
        Log.d("GeoapifyMap", "⭐ DEBUG: Map URL constructed with API key length: ${BuildConfig.GEOAPIFY_API_KEY.length}")
    }
    
    // Verify URL is properly formed
    try {
        val uri = java.net.URI(url)
        Log.d("GeoapifyMap", "⭐ DEBUG: URL is valid: ${uri.scheme}://${uri.host}")
    } catch (e: Exception) {
        Log.e("GeoapifyMap", "⭐ ERROR: Invalid URL format: ${e.message}")
    }
    
    return url
}

private suspend fun testApiKey(client: OkHttpClient) {
    try {
        Log.d("GeoapifyMap", "⭐ Testing API key: ${BuildConfig.GEOAPIFY_API_KEY}")
        
        val testUrl = "https://api.geoapify.com/v1/geocode/search?text=Berlin&apiKey=${BuildConfig.GEOAPIFY_API_KEY}"
        Log.d("GeoapifyMap", "⭐ Testing with URL: $testUrl")
        
        val request = Request.Builder()
            .url(testUrl)
            .build()
        
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                Log.d("GeoapifyMap", "⭐ API key test status code: ${response.code}")
                
                if (response.isSuccessful) {
                    Log.d("GeoapifyMap", "⭐ API key test successful!")
                    val responseBody = response.body?.string()
                    Log.d("GeoapifyMap", "⭐ API response preview: ${responseBody?.take(100)}...")
                } else {
                    Log.e("GeoapifyMap", "⭐ API key test failed with status: ${response.code}")
                    if (response.body != null) {
                        val errorBody = response.body?.string()
                        Log.e("GeoapifyMap", "⭐ API error response: $errorBody")
                    } else {
                        Log.e("GeoapifyMap", "⭐ API error response: no response body")
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e("GeoapifyMap", "⭐ API key test exception: ${e.message}", e)
    }
}

private suspend fun testStaticMapEndpoint(client: OkHttpClient) {
    try {
        Log.d("GeoapifyMap", "⭐ Testing static map API endpoint...")
        
        // Create a simple test URL for the static map API
        val testLat = 40.7128
        val testLon = -74.0060
        val testUrl = "https://maps.geoapify.com/v1/staticmap" +
                "?style=osm-carto" +
                "&width=100" +
                "&height=100" +
                "&center=lonlat:$testLon,$testLat" +
                "&zoom=15" +
                "&marker=lonlat:$testLon,$testLat" +
                "&apiKey=${BuildConfig.GEOAPIFY_API_KEY}"
        
        Log.d("GeoapifyMap", "⭐ Testing with static map URL: ${testUrl.replace(BuildConfig.GEOAPIFY_API_KEY, "API_KEY_MASKED")}")
        
        val request = Request.Builder()
            .url(testUrl)
            .build()
        
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    Log.d("GeoapifyMap", "⭐ Static map API test status code: ${response.code}")
                    
                    if (response.isSuccessful) {
                        Log.d("GeoapifyMap", "⭐ Static map API test successful! Response content type: ${response.body?.contentType()}")
                        val bodySize = response.body?.contentLength() ?: -1
                        Log.d("GeoapifyMap", "⭐ Received image data of size: $bodySize bytes")
                    } else {
                        Log.e("GeoapifyMap", "⭐ Static map API test failed with status: ${response.code}")
                        if (response.body != null) {
                            val errorBody = response.body?.string()
                            Log.e("GeoapifyMap", "⭐ Static map API error response: $errorBody")
                        } else {
                            Log.e("GeoapifyMap", "⭐ Static map API error response: no response body")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GeoapifyMap", "⭐ Static map API test exception: ${e.message}", e)
            }
        }
    } catch (e: Exception) {
        Log.e("GeoapifyMap", "⭐ Static map API setup exception: ${e.message}", e)
    }
} 