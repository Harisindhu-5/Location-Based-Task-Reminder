package com.example.locationtaskreminder.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.locationtaskreminder.model.Location
import com.example.locationtaskreminder.ui.components.GeoapifyMap
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onLocationSelected: (Location, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    var selectedLocation by remember { mutableStateOf<Location?>(null) }
    var radius by remember { mutableStateOf(100f) }

    Box(modifier = modifier.fillMaxSize()) {
        GeoapifyMap(
            modifier = Modifier.fillMaxSize(),
            initialLocation = selectedLocation,
            onLocationSelected = { newLocation ->
                selectedLocation = newLocation
            }
        )

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Geofence Radius (meters): ${radius.toInt()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = 50f..500f,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Button(
                    onClick = {
                        selectedLocation?.let { location ->
                            scope.launch {
                                onLocationSelected(location, radius)
                            }
                        }
                    },
                    enabled = selectedLocation != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Confirm Location")
                }
            }
        }
    }
} 