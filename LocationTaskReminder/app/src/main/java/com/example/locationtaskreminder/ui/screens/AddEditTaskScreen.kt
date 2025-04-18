package com.example.locationtaskreminder.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.locationtaskreminder.data.model.Task
import com.example.locationtaskreminder.data.model.TaskCategory
import com.example.locationtaskreminder.model.Location
import com.example.locationtaskreminder.ui.components.CategoryDropdown
import com.example.locationtaskreminder.ui.components.GeoapifyMap
import com.example.locationtaskreminder.ui.components.MiniMap
import com.example.locationtaskreminder.ui.viewmodel.TaskViewModel

@SuppressLint("MissingPermission")
@Composable
fun AddEditTaskScreen(
    task: Task? = null,
    onSaveTask: (String, String, Location, Float, String, TaskCategory) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: TaskViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // State variables
    var title by remember { mutableStateOf(task?.title ?: "") }
    var description by remember { mutableStateOf(task?.description ?: "") }
    var radius by remember { mutableStateOf(task?.radius?.toString() ?: "100") } // Default radius in meters
    var category by remember { mutableStateOf(task?.category ?: TaskCategory.OTHER) }
    
    val selectedLocation by viewModel.selectedLocation.collectAsState()
    val locationAddress by viewModel.locationAddress.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    
    // Observe the currentLocation and set selectedLocation if it's null
    LaunchedEffect(currentLocation) {
        if (selectedLocation == null && currentLocation != null) {
            viewModel.useCurrentLocationForTask()
        }
    }
    
    // Load existing task data if in edit mode
    LaunchedEffect(task) {
        if (task != null) {
            title = task.title
            description = task.description
            radius = task.radius.toString()
            category = task.category
            viewModel.setSelectedLocation(Location(task.latitude, task.longitude))
        } else {
            // For new tasks, request current location refresh
            viewModel.refreshCurrentLocation()
        }
    }
    
    // Observe the location address and update locationName
    var locationName by remember { mutableStateOf(task?.locationName ?: "") }
    
    LaunchedEffect(locationAddress) {
        locationAddress?.let {
            locationName = it
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title field
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
        
        // Description field
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
        
        // Location picker
        Spacer(modifier = Modifier.height(16.dp))
        Text("Select Location", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        
        // Button to use current location
        if (currentLocation != null) {
            Button(
                onClick = { viewModel.useCurrentLocationForTask() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Use current location"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Use My Current Location")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Geoapify Map for location selection
        GeoapifyMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            initialLocation = currentLocation,
            onLocationSelected = { location ->
                viewModel.setSelectedLocation(location)
            }
        )
        
        // Radius slider
        Spacer(modifier = Modifier.height(16.dp))
        Text("Geofence Radius: $radius meters", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = radius,
            onValueChange = { newValue ->
                if (newValue.isEmpty() || newValue.toFloatOrNull() != null) {
                    radius = newValue
                }
            },
            label = { Text("Radius (meters)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
        
        // Category dropdown
        Spacer(modifier = Modifier.height(16.dp))
        Text("Category", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        CategoryDropdown(
            selectedCategory = category,
            onCategorySelected = { category = it },
            modifier = Modifier.fillMaxWidth()
        )
        
        // Action buttons
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Button(
                onClick = {
                    val selectedLoc = selectedLocation ?: currentLocation
                    if (title.isNotBlank() && selectedLoc != null) {
                        val radiusValue = radius.toFloatOrNull() ?: 100f
                        onSaveTask(
                            title,
                            description,
                            selectedLoc,
                            radiusValue,
                            locationName,
                            category
                        )
                    } else {
                        // Show error if required fields are missing
                        Log.d("AddEditTaskScreen", "Missing required fields: title=${title.isNotBlank()}, location=${selectedLoc != null}")
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (task == null) "Add Task" else "Update Task")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
} 