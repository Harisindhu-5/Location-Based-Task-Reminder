package com.example.locationtaskreminder.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.locationtaskreminder.model.Location
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.app.NotificationManager
import androidx.core.app.NotificationCompat

@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geofencingClient: GeofencingClient
) {
    private val TAG = "GeofenceManager"
    private var pendingIntent: PendingIntent? = null

    fun addGeofence(taskId: Long, location: Location, radius: Float) {
        Log.d(TAG, "⭐ DEBUG: Adding geofence for task $taskId at location (${location.latitude}, ${location.longitude}) with radius $radius meters")

        // Check for permission first
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "⭐ DEBUG: Missing location permissions for geofence")
            return
        }

        // Ensure minimum radius for better reliability
        val effectiveRadius = if (radius < 100f) 100f else radius
        Log.d(TAG, "⭐ DEBUG: Using effective radius of $effectiveRadius meters for more reliable geofencing")

        val geofence = Geofence.Builder()
            .setRequestId(taskId.toString())
            .setCircularRegion(location.latitude, location.longitude, effectiveRadius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            // Add both ENTER and DWELL transitions for more reliable triggering
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL)
            // Make notification more responsive
            .setNotificationResponsiveness(100) // 100ms for quicker response
            .setLoiteringDelay(10000) // 10 seconds dwell time as requested by user
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            // INITIAL_TRIGGER_ENTER is crucial for triggering if user is already inside the geofence
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL)
            .addGeofence(geofence)
            .build()

        try {
            // First remove old geofence with this ID if it exists
            removeGeofence(taskId.toString())
            
            // Add new geofence after a short delay to ensure system registers it as new
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                // Check current location to see if already in the geofence
                Log.d(TAG, "⭐ DEBUG: About to check if user is already in geofence for task $taskId")
                checkIfAlreadyInGeofence(taskId, location, effectiveRadius, geofencingRequest)
            }, 500)
        } catch (e: SecurityException) {
            Log.e(TAG, "⭐ DEBUG: Security exception when handling geofence: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun removeGeofence(geofenceId: String) {
        if (!hasRequiredPermissions()) return

        try {
            geofencingClient.removeGeofences(listOf(geofenceId))
                .addOnSuccessListener { 
                    Log.d(TAG, "Successfully removed geofence $geofenceId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to remove geofence $geofenceId: ${e.message}")
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception: ${e.message}")
        }
    }

    private fun addGeofenceInternal(request: GeofencingRequest, taskId: Long) {
        if (!hasRequiredPermissions()) return

        try {
            geofencingClient.addGeofences(request, getGeofencePendingIntent())
                .addOnSuccessListener {
                    Log.d(TAG, "Geofence added successfully for task $taskId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to add geofence for task $taskId: ${e.message}")
                    e.printStackTrace()
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security permission not granted for geofencing: ${e.message}")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "Exception when adding geofence: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun getGeofencePendingIntent(): PendingIntent {
        // Reuse the PendingIntent if already created
        pendingIntent?.let { return it }

        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        // Use a unique action to ensure the system doesn't treat this as a duplicate
        intent.action = "com.example.locationtaskreminder.GEOFENCE_EVENT"
        
        // Create and store the PendingIntent
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        ).also {
            pendingIntent = it
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val hasFineLocation = ActivityCompat.checkSelfPermission(
            context, 
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        // For Android 10+ we also need background location permission
        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Background location permission not needed before Android 10
        }
        
        // For Android 14+ we also need FOREGROUND_SERVICE_LOCATION permission
        val hasForegroundServiceLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // FOREGROUND_SERVICE_LOCATION permission not needed before Android 14
        }
        
        if (!hasFineLocation || !hasBackgroundLocation || !hasForegroundServiceLocation) {
            Log.e(TAG, "Missing required permissions: " +
                   "Fine Location: $hasFineLocation, " +
                   "Background Location: $hasBackgroundLocation, " +
                   "Foreground Service Location: $hasForegroundServiceLocation")
            return false
        }
        
        return true
    }

    private fun checkIfAlreadyInGeofence(taskId: Long, taskLocation: Location, radius: Float, geofencingRequest: GeofencingRequest) {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "⭐ DEBUG: Missing permissions, can't check if already in geofence")
            // Just add the geofence without checking current location
            addGeofenceInternal(geofencingRequest, taskId)
            return
        }
        
        try {
            // Get the current location and see if already in geofence
            val locationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
            Log.d(TAG, "⭐ DEBUG: Requesting last location to check if already in geofence")
            
            // Double-check permissions explicitly
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "⭐ DEBUG: Fine location permission not granted, adding geofence without location check")
                addGeofenceInternal(geofencingRequest, taskId)
                return
            }
            
            locationClient.lastLocation.addOnSuccessListener { currentLocation ->
                if (currentLocation != null) {
                    val distanceInMeters = calculateDistance(
                        currentLocation.latitude, currentLocation.longitude,
                        taskLocation.latitude, taskLocation.longitude
                    )
                    
                    Log.d(TAG, "⭐ DEBUG: LOCATION CHECK: Current location: (${currentLocation.latitude}, ${currentLocation.longitude})")
                    Log.d(TAG, "⭐ DEBUG: LOCATION CHECK: Task location: (${taskLocation.latitude}, ${taskLocation.longitude})")
                    Log.d(TAG, "⭐ DEBUG: LOCATION CHECK: Distance to geofence: $distanceInMeters meters (radius: $radius meters)")
                    
                    // Add the geofence normally
                    addGeofenceInternal(geofencingRequest, taskId)
                    
                    // If already in the geofence area, manually trigger notification
                    if (distanceInMeters <= radius) {
                        Log.d(TAG, "⭐ DEBUG: NOTIFICATION TRIGGER: Already in geofence area for task $taskId, manually triggering notification")
                        Log.d(TAG, "⭐ DEBUG: NOTIFICATION TRIGGER: Distance: $distanceInMeters meters is less than radius: $radius meters")
                        
                        // Add a small delay to ensure geofence is registered before triggering
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            Log.d(TAG, "⭐ DEBUG: NOTIFICATION TRIGGER: Triggering notification after delay")
                            triggerGeofenceNotification(taskId)
                        }, 2000) // 2 second delay to ensure everything is ready
                    } else {
                        Log.d(TAG, "⭐ DEBUG: LOCATION OUTSIDE: User is outside the geofence area by ${distanceInMeters - radius} meters")
                    }
                } else {
                    // Can't get location, just add the geofence
                    Log.e(TAG, "⭐ DEBUG: LOCATION ERROR: Couldn't get current location, adding geofence without checking")
                    addGeofenceInternal(geofencingRequest, taskId)
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "⭐ DEBUG: LOCATION ERROR: Failed to get current location: ${e.message}")
                // Add geofence anyway
                addGeofenceInternal(geofencingRequest, taskId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "⭐ DEBUG: LOCATION ERROR: Exception checking current location: ${e.message}")
            e.printStackTrace()
            // Add geofence anyway
            addGeofenceInternal(geofencingRequest, taskId)
        }
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
    
    private fun triggerGeofenceNotification(taskId: Long) {
        Log.d(TAG, "⭐ DEBUG: Manual trigger for task $taskId notification")
        
        // Simulate the behavior of a user dwelling in the geofence
        Log.d(TAG, "⭐ DEBUG: Waiting 10 seconds to simulate dwell time before sending notification")
        
        // Use a handler to delay the notification by the dwell time
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // Create an intent to send to the GeofenceBroadcastReceiver
            val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
            intent.action = "com.example.locationtaskreminder.GEOFENCE_EVENT"
            intent.putExtra("com.example.locationtaskreminder.GEOFENCE_ID", taskId.toString())
            
            Log.d(TAG, "⭐ DEBUG: Sending manual geofence broadcast for task $taskId after dwell time")
            context.sendBroadcast(intent)
            
            // Also try direct notification method as fallback
            showDirectNotification(taskId)
        }, 10000) // 10 seconds, matching the dwell time
    }
    
    private fun showDirectNotification(taskId: Long) {
        Log.d(TAG, "⭐ DEBUG: DIRECT NOTIFICATION: Attempting to show direct notification for task ID: $taskId")
        
        try {
            // This is a backup method that directly creates a notification instead of going through the broadcast
            val coroutineScope = CoroutineScope(Dispatchers.IO)
            
            coroutineScope.launch {
                try {
                    // Get the task from repository
                    val taskRepository = context.applicationContext.let {
                        if (it is com.example.locationtaskreminder.LocationTaskReminderApp) {
                            // Try to get repository from Hilt component
                            com.example.locationtaskreminder.data.repository.TaskRepository::class.java.getDeclaredConstructor().newInstance()
                        } else {
                            null
                        }
                    }
                    
                    if (taskRepository != null) {
                        val task = taskRepository.getTaskById(taskId)
                        if (task != null) {
                            // Create and show notification directly
                            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            
                            val notification = NotificationCompat.Builder(context, LocationService.GEOFENCE_NOTIFICATION_CHANNEL_ID)
                                .setSmallIcon(android.R.drawable.ic_dialog_info)
                                .setContentTitle("Task Reminder: ${task.title}")
                                .setContentText("You are near: ${task.locationName}")
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setVibrate(longArrayOf(0, 500, 250, 500)) // Vibration pattern
                                .setAutoCancel(true)
                                .build()
                                
                            notificationManager.notify(taskId.toInt(), notification)
                            Log.d(TAG, "⭐ DEBUG: DIRECT NOTIFICATION: Showed notification directly for task: ${task.title}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "⭐ DEBUG: DIRECT NOTIFICATION: Error getting task: ${e.message}")
                }
                
                // As a last resort, create a backup broadcast intent
                Log.d(TAG, "⭐ DEBUG: DIRECT NOTIFICATION: Creating intent for MainActivity")
                val notificationIntent = Intent("com.example.locationtaskreminder.SHOW_TASK_NOTIFICATION")
                notificationIntent.putExtra("taskId", taskId)
                notificationIntent.setPackage(context.packageName)
                context.sendBroadcast(notificationIntent)
                
                Log.d(TAG, "⭐ DEBUG: DIRECT NOTIFICATION: Sent direct notification intent for task ID: $taskId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "⭐ DEBUG: DIRECT NOTIFICATION ERROR: ${e.message}")
            e.printStackTrace()
        }
    }
} 