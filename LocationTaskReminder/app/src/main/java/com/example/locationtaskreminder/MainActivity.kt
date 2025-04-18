package com.example.locationtaskreminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.locationtaskreminder.BuildConfig
import com.example.locationtaskreminder.data.model.Task
import com.example.locationtaskreminder.data.model.TaskCategory
import com.example.locationtaskreminder.model.Location
import com.example.locationtaskreminder.service.LocationService
import com.example.locationtaskreminder.ui.screens.AddEditTaskScreen
import com.example.locationtaskreminder.ui.screens.TaskListScreen
import com.example.locationtaskreminder.ui.theme.LocationTaskReminderTheme
import com.example.locationtaskreminder.ui.viewmodel.TaskViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: TaskViewModel by viewModels()
    private val TAG = "MainActivity"

    private val foregroundPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.FOREGROUND_SERVICE_LOCATION
    )
    
    private val backgroundPermissions = arrayOf(
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )
    
    // For notification permission on Android 13+
    private val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }
    
    // Create separate permission launchers
    private val foregroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        Log.d(TAG, "⭐ DEBUG: Foreground permissions result: $permissions")
        
        if (allGranted) {
            Log.d(TAG, "⭐ DEBUG: All foreground permissions granted, checking background")
            // Now request background permissions if needed and not already granted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                
                // Show dialog explaining why we need background permission
                showBackgroundPermissionRationale()
            } else {
                Log.d(TAG, "⭐ DEBUG: Background permission already granted or not needed")
                startLocationService()
            }
        } else {
            Log.e(TAG, "⭐ DEBUG: Not all foreground permissions granted. Cannot start location service.")
        }
    }
    
    // Use single permission launcher for background location
    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(TAG, "⭐ DEBUG: Background permission result: $isGranted")
        
        if (isGranted) {
            Log.d(TAG, "⭐ DEBUG: Background permission granted, starting location service")
        } else {
            Log.e(TAG, "⭐ DEBUG: Background permission denied, may affect functionality")
        }
        // Start service regardless - it will work with limited functionality if permission denied
        startLocationService()
    }
    
    // For notification permission on Android 13+
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(TAG, "⭐ DEBUG: Notification permission result: $isGranted")
        // Continue app flow regardless of notification permission
        // Now proceed to location permissions
        requestLocationPermissions()
    }

    private val locationSettingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (LocationManager.PROVIDERS_CHANGED_ACTION == intent.action) {
                Log.d(TAG, "Location providers changed, checking status")
                checkLocationEnabled()
            }
        }
    }

    // Add a CoroutineScope for the activity
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Update the notificationReceiver to use the correct scope
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.example.locationtaskreminder.SHOW_TASK_NOTIFICATION") {
                val taskId = intent.getLongExtra("taskId", -1)
                Log.d(TAG, "Received direct notification intent for task ID: $taskId")
                
                if (taskId != -1L) {
                    // Use the activity's coroutine scope
                    activityScope.launch {
                        try {
                            val task = viewModel.getTaskById(taskId)
                            if (task != null) {
                                Log.d(TAG, "Found task for notification: ${task.title}")
                                // Now we're in a coroutine context where withContext can be called
                                withContext(Dispatchers.Main) {
                                    showTaskNotification(task)
                                }
                            } else {
                                Log.e(TAG, "Task not found for ID: $taskId")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error getting task for notification: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "⭐ DEBUG: MainActivity onCreate started")

        // Delay permission requests slightly to ensure UI is initialized
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "⭐ DEBUG: Initiating permission requests")
            requestRequiredPermissions()
        }, 1000) // 1 second delay
        
        // Register for location provider changes
        registerReceiver(
            locationSettingsReceiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            Context.RECEIVER_NOT_EXPORTED
        )
        
        // Register the geofence broadcast receiver explicitly
        try {
            val geofenceFilter = IntentFilter()
            geofenceFilter.addAction("com.example.locationtaskreminder.GEOFENCE_EVENT")
            geofenceFilter.addAction("com.google.android.location.GEOFENCE_TRANSITION")
            registerReceiver(
                notificationReceiver,
                geofenceFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
            Log.d(TAG, "⭐ DEBUG: Successfully registered geofence broadcast receiver")
        } catch (e: Exception) {
            Log.e(TAG, "⭐ DEBUG: Failed to register geofence broadcast receiver: ${e.message}")
        }

        setContent {
            LocationTaskReminderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val pendingTasks by viewModel.pendingTasks.collectAsStateWithLifecycle()
                    val completedTasks by viewModel.completedTasks.collectAsStateWithLifecycle()
                    
                    // State to control location dialog visibility
                    var showLocationEnableDialog by remember { mutableStateOf(false) }
                    
                    // Check if location is enabled
                    LaunchedEffect(Unit) {
                        checkLocationStatusForUI { isEnabled ->
                            showLocationEnableDialog = !isEnabled
                        }
                    }
                    
                    // Location dialog
                    if (showLocationEnableDialog) {
                        AlertDialog(
                            onDismissRequest = { showLocationEnableDialog = false },
                            title = { Text("Location Required") },
                            text = { Text("This app requires location services to be enabled for geofence notifications. Please enable location services to use all features.") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showLocationEnableDialog = false
                                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                    }
                                ) {
                                    Text("Enable Location")
                                }
                            },
                            dismissButton = {
                                Button(onClick = { showLocationEnableDialog = false }) {
                                    Text("Not Now")
                                }
                            }
                        )
                    }

                    NavHost(
                        navController = navController,
                        startDestination = "tasks"
                    ) {
                        composable("tasks") {
                            TaskListScreen(
                                pendingTasks = pendingTasks,
                                completedTasks = completedTasks,
                                onAddTaskClick = {
                                    navController.navigate("add_task")
                                },
                                onTaskClick = { task ->
                                    navController.navigate("edit_task/${task.id}")
                                },
                                onCompleteTask = { task ->
                                    viewModel.markTaskAsCompleted(task.id, !task.isCompleted)
                                },
                                onDeleteTask = { task ->
                                    viewModel.deleteTask(task)
                                }
                            )
                        }

                        composable("add_task") {
                            AddEditTaskScreen(
                                task = null,
                                onSaveTask = { title, description, location, radius, locationName, category ->
                                    viewModel.addTask(
                                        title = title,
                                        description = description,
                                        location = location,
                                        radius = radius,
                                        locationName = locationName,
                                        category = category
                                    )
                                    navController.navigateUp()
                                },
                                onNavigateBack = {
                                    navController.navigateUp()
                                }
                            )
                        }

                        composable(
                            route = "edit_task/{taskId}",
                            arguments = listOf(
                                navArgument("taskId") { type = NavType.LongType }
                            )
                        ) { backStackEntry ->
                            val taskId = backStackEntry.arguments?.getLong("taskId") ?: return@composable
                            val task = remember(taskId) {
                                pendingTasks.find { it.id == taskId }
                                    ?: completedTasks.find { it.id == taskId }
                            }

                            AddEditTaskScreen(
                                task = task,
                                onSaveTask = { title, description, location, radius, locationName, category ->
                                    task?.copy(
                                        title = title,
                                        description = description,
                                        latitude = location.latitude,
                                        longitude = location.longitude,
                                        radius = radius,
                                        locationName = locationName,
                                        category = category
                                    )?.let { updatedTask ->
                                        viewModel.updateTask(updatedTask)
                                    }
                                    navController.navigateUp()
                                },
                                onNavigateBack = {
                                    navController.navigateUp()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(locationSettingsReceiver)
            unregisterReceiver(notificationReceiver)
            
            // Cancel the coroutine scope to prevent leaks
            activityScope.cancel()
            Log.d(TAG, "⭐ DEBUG: Successfully unregistered receivers and cancelled coroutine scope")
        } catch (e: Exception) {
            Log.e(TAG, "⭐ DEBUG: Error unregistering receivers", e)
        }
    }

    private fun requestRequiredPermissions() {
        Log.d(TAG, "⭐ DEBUG: Starting permission request flow")
        
        // Start with notification permission on Android 13+, then proceed to location
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "⭐ DEBUG: Requesting notification permission first")
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // No notification permission needed or already granted, proceed to location
            requestLocationPermissions()
        }
    }
    
    private fun requestLocationPermissions() {
        Log.d(TAG, "⭐ DEBUG: Checking location permissions")
        
        // Check foreground permissions
        val foregroundNotGranted = foregroundPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (foregroundNotGranted.isNotEmpty()) {
            Log.d(TAG, "⭐ DEBUG: Requesting foreground permissions: ${foregroundNotGranted.joinToString()}")
            foregroundPermissionLauncher.launch(foregroundNotGranted)
            return
        }
        
        // All foreground permissions granted, check background permission
        Log.d(TAG, "⭐ DEBUG: All foreground permissions already granted")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "⭐ DEBUG: Need to request background permission")
            showBackgroundPermissionRationale()
        } else {
            Log.d(TAG, "⭐ DEBUG: All permissions already granted, starting location service")
            startLocationService()
        }
    }
    
    private fun showBackgroundPermissionRationale() {
        Log.d(TAG, "Showing background permission rationale dialog")
        
        // Show a simple alert dialog using Android's standard AlertDialog
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Background Location Required")
            .setMessage("This app needs background location access to provide location-based reminders even when the app is closed. Please select 'Allow all the time' on the next screen.")
            .setPositiveButton("Continue") { dialog, _ ->
                dialog.dismiss()
                backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            .setNegativeButton("No thanks") { dialog, _ ->
                dialog.dismiss()
                Log.d(TAG, "User declined to request background permission")
                // Start service with limited functionality
                startLocationService()
            }
            .setCancelable(false)
            .show()
    }

    private fun startLocationService() {
        try {
            // First check if we have required foreground permissions
            val foregroundPermissionsGranted = foregroundPermissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
            
            // Background permission is optional but preferred
            val backgroundPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    this, 
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Not needed before Android 10
            }
            
            if (!foregroundPermissionsGranted) {
                Log.d(TAG, "Cannot start location service - missing foreground permissions")
                return
            }
            
            // Add background permission status to the intent so the service knows its capabilities
            val serviceIntent = Intent(this, LocationService::class.java).apply {
                putExtra("BACKGROUND_PERMISSION_GRANTED", backgroundPermissionGranted)
            }
            
            // Only start the service when the app is in the foreground
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Starting foreground service for location")
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            
            Log.d(TAG, "Location service started with background permission: $backgroundPermissionGranted")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location service", e)
        }
    }

    override fun onResume() {
        super.onResume()
        // Check location status when returning to the app
        checkLocationEnabled()
    }

    private fun checkLocationEnabled() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        
        if (isLocationEnabled) {
            Log.d(TAG, "Location is enabled, starting service")
            startLocationService()
        } else {
            Log.d(TAG, "Location is disabled, service not started")
            // Location is disabled, will be handled by the Compose UI dialog
            // The service won't be started until location is enabled
        }
    }

    private fun checkLocationStatusForUI(callback: (Boolean) -> Unit) {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        callback(isLocationEnabled)
    }

    private fun sendTestNotification() {
        Log.d(TAG, "Sending test notification")
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            Log.e(TAG, "Notification permission not granted")
            return
        }
        
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Ensure channel exists
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    LocationService.GEOFENCE_NOTIFICATION_CHANNEL_ID,
                    "Task Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                )
                channel.description = "Notifications for location-based task reminders"
                channel.enableVibration(true)
                channel.enableLights(true)
                notificationManager.createNotificationChannel(channel)
            }
            
            // Create notification
            val notification = NotificationCompat.Builder(this, LocationService.GEOFENCE_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Test Notification")
                .setContentText("This is a test to verify notifications are working")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("If you can see this notification, the notification system is working correctly. Geofence notifications should work when you enter a task location area.")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(longArrayOf(0, 500, 250, 500)) // Vibration pattern
                .setAutoCancel(true)
                .build()
                
            // Send notification
            notificationManager.notify(1000, notification)
            Log.d(TAG, "Test notification sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending test notification: ${e.message}", e)
        }
    }

    private fun getCurrentLocationForTest(callback: (Double, Double) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted for test")
            return
        }
        
        try {
            val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    Log.d(TAG, "Current location for test: ${location.latitude}, ${location.longitude}")
                    callback(location.latitude, location.longitude)
                } else {
                    Log.e(TAG, "Could not get current location for test")
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error getting location for test: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting location for test: ${e.message}", e)
        }
    }

    // Add this method to show a notification directly
    private fun showTaskNotification(task: Task) {
        Log.d(TAG, "⭐ DEBUG: Showing direct notification for task: ${task.title}")
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            Log.e(TAG, "⭐ DEBUG: Notification permission not granted")
            return
        }
        
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Ensure channel exists
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "⭐ DEBUG: Creating notification channel")
                val channel = NotificationChannel(
                    LocationService.GEOFENCE_NOTIFICATION_CHANNEL_ID,
                    "Task Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                )
                channel.description = "Notifications for location-based task reminders"
                channel.enableVibration(true)
                channel.enableLights(true)
                notificationManager.createNotificationChannel(channel)
            }
            
            // Create notification
            Log.d(TAG, "⭐ DEBUG: Building notification for task: ${task.title}")
            val notification = NotificationCompat.Builder(this, LocationService.GEOFENCE_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Task Reminder: ${task.title}")
                .setContentText("You are near: ${task.locationName}")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("You have arrived at: ${task.locationName}\n\nTask: ${task.title}\n\nDescription: ${task.description}")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(longArrayOf(0, 500, 250, 500)) // Vibration pattern
                .setAutoCancel(true)
                .build()
                
            // Send notification
            Log.d(TAG, "⭐ DEBUG: Sending notification with ID: ${task.id.toInt()}")
            notificationManager.notify(task.id.toInt(), notification)
            Log.d(TAG, "⭐ DEBUG: Direct notification sent successfully for task: ${task.title}")
        } catch (e: Exception) {
            Log.e(TAG, "⭐ DEBUG: Error sending direct notification: ${e.message}", e)
        }
    }
} 