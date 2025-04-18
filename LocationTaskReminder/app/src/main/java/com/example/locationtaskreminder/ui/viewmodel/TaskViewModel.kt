package com.example.locationtaskreminder.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.locationtaskreminder.data.model.Task
import com.example.locationtaskreminder.data.model.TaskCategory
import com.example.locationtaskreminder.data.repository.GeoapifyRepository
import com.example.locationtaskreminder.data.repository.TaskRepository
import com.example.locationtaskreminder.model.Location
import com.example.locationtaskreminder.service.GeofenceManager
import com.example.locationtaskreminder.service.LocationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import androidx.core.app.ActivityCompat
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val geoapifyRepository: GeoapifyRepository,
    private val geofenceManager: GeofenceManager,
    private val fusedLocationClient: FusedLocationProviderClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<TaskCategory?>(null)
    val selectedCategory = _selectedCategory.asStateFlow()

    val pendingTasks = taskRepository.getPendingTasks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val completedTasks = taskRepository.getCompletedTasks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val tasksByCategory = _selectedCategory
        .filterNotNull()
        .flatMapLatest { category ->
            taskRepository.getTasksByCategory(category)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _selectedLocation = MutableStateFlow<Location?>(null)
    val selectedLocation: StateFlow<Location?> = _selectedLocation.asStateFlow()

    private val _locationAddress = MutableStateFlow<String?>(null)
    val locationAddress: StateFlow<String?> = _locationAddress.asStateFlow()
    
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    init {
        loadTasks()
        fetchCurrentLocation()
    }

    private fun loadTasks() {
        viewModelScope.launch {
            taskRepository.getAllTasks().collect { taskList ->
                _tasks.value = taskList
            }
        }
    }
    
    private fun fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("TaskViewModel", "Missing location permissions to fetch current location")
            return
        }
        
        viewModelScope.launch {
            try {
                // First attempt to get last location
                fusedLocationClient.lastLocation.addOnSuccessListener { androidLocation ->
                    androidLocation?.let {
                        _currentLocation.value = Location(it.latitude, it.longitude)
                        Log.d("TaskViewModel", "Current location fetched: ${it.latitude}, ${it.longitude}")
                        
                        // Get address for current location
                        viewModelScope.launch {
                            try {
                                val response = geoapifyRepository.reverseGeocode(
                                    it.latitude,
                                    it.longitude
                                )
                                val address = response.features.firstOrNull()?.properties?.formatted
                                if (_locationAddress.value == null) {
                                    _locationAddress.value = address
                                }
                            } catch (e: Exception) {
                                Log.e("TaskViewModel", "Error getting address for current location: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TaskViewModel", "Error fetching current location: ${e.message}")
            }
        }
    }

    fun setSelectedCategory(category: TaskCategory) {
        _selectedCategory.value = category
    }

    fun addTask(
        title: String,
        description: String,
        location: Location,
        radius: Float,
        locationName: String,
        category: TaskCategory
    ) {
        Log.d("TaskViewModel", "⭐ DEBUG: Adding task with location (${location.latitude}, ${location.longitude}) and radius $radius meters")
        
        viewModelScope.launch {
            val task = Task(
                title = title,
                description = description,
                latitude = location.latitude,
                longitude = location.longitude,
                radius = radius,
                locationName = locationName,
                category = category,
                isCompleted = false
            )
            val taskId = taskRepository.insertTask(task)
            Log.d("TaskViewModel", "⭐ DEBUG: Task inserted with ID: $taskId")
            
            // Create geofence for the task location
            Log.d("TaskViewModel", "⭐ DEBUG: About to add geofence for task $taskId")
            geofenceManager.addGeofence(taskId, location, radius)
            Log.d("TaskViewModel", "⭐ DEBUG: Geofence addition process started for task $taskId")
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            taskRepository.updateTask(task)
            // Update the geofence if location or radius changed
            geofenceManager.addGeofence(task.id, Location(task.latitude, task.longitude), task.radius)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            taskRepository.deleteTask(task)
        }
    }

    fun markTaskAsCompleted(taskId: Long, completed: Boolean) {
        viewModelScope.launch {
            taskRepository.updateTaskCompletionStatus(taskId, completed)
        }
    }

    fun setSelectedLocation(location: Location) {
        _selectedLocation.value = location
        viewModelScope.launch {
            try {
                val response = geoapifyRepository.reverseGeocode(
                    location.latitude,
                    location.longitude
                )
                _locationAddress.value = response.features.firstOrNull()?.properties?.formatted
            } catch (e: Exception) {
                // Handle error
                Log.e("TaskViewModel", "Error getting address for selected location: ${e.message}")
            }
        }
    }

    fun searchLocation(query: String) {
        if (query.isBlank()) {
            return  // Don't perform search with empty query
        }
        
        viewModelScope.launch {
            try {
                val response = geoapifyRepository.searchLocation(query)
                val firstResult = response.features.firstOrNull()
                firstResult?.let {
                    val coordinates = it.geometry.coordinates
                    val location = Location(coordinates[1], coordinates[0])
                    _selectedLocation.value = location
                    _locationAddress.value = it.properties.formatted
                }
            } catch (e: Exception) {
                // Handle error
                Log.e("TaskViewModel", "Error searching location: ${e.message}")
            }
        }
    }

    fun refreshCurrentLocation() {
        fetchCurrentLocation()
    }

    fun useCurrentLocationForTask() {
        _currentLocation.value?.let { location ->
            _selectedLocation.value = location
            viewModelScope.launch {
                try {
                    val response = geoapifyRepository.reverseGeocode(
                        location.latitude,
                        location.longitude
                    )
                    _locationAddress.value = response.features.firstOrNull()?.properties?.formatted ?: "Current Location"
                } catch (e: Exception) {
                    _locationAddress.value = "Current Location"
                    Log.e("TaskViewModel", "Error getting address for current location: ${e.message}")
                }
            }
        }
    }

    // Add a method to get a task by ID
    suspend fun getTaskById(taskId: Long): Task? {
        return taskRepository.getTaskById(taskId)
    }
} 