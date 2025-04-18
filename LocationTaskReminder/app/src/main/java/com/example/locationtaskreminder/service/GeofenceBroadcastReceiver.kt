package com.example.locationtaskreminder.service

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.locationtaskreminder.R
import com.example.locationtaskreminder.data.repository.TaskRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    @Inject
    lateinit var taskRepository: TaskRepository

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val TAG = "GeofenceBroadcastReceiver"
    
    // Keep track of recently triggered geofences to avoid duplicate notifications
    private val recentlyTriggeredGeofences = mutableSetOf<String>()

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "⭐ DEBUG: Geofence event received: ${intent.action}")
        Log.d(TAG, "⭐ DEBUG: Intent extras: ${intent.extras?.keySet()?.joinToString(", ") ?: "no extras"}")
        
        // Check if this is our manual trigger
        if (intent.action == "com.example.locationtaskreminder.GEOFENCE_EVENT" &&
            intent.hasExtra("com.example.locationtaskreminder.GEOFENCE_ID")) {
            
            val geofenceId = intent.getStringExtra("com.example.locationtaskreminder.GEOFENCE_ID")
            if (geofenceId != null) {
                Log.d(TAG, "⭐ DEBUG: Manual geofence trigger received for ID: $geofenceId")
                handleGeofenceTrigger(context, listOf(geofenceId))
                return
            }
        }
        
        // Normal geofence trigger from system
        val geofencingEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GeofencingEvent.fromIntent(intent)
        } else {
            @Suppress("DEPRECATION")
            GeofencingEvent.fromIntent(intent)
        }
        
        if (geofencingEvent == null) {
            Log.e(TAG, "⭐ DEBUG: GeofencingEvent is null")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorCode = geofencingEvent.errorCode
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(errorCode)
            Log.e(TAG, "⭐ DEBUG: Error with geofence event: $errorMessage (code: $errorCode)")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        val transitionName = when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
            Geofence.GEOFENCE_TRANSITION_DWELL -> "DWELL (10 seconds)"
            else -> "UNKNOWN($geofenceTransition)"
        }
        Log.d(TAG, "⭐ DEBUG: Geofence transition type: $transitionName")

        // Handle both ENTER and DWELL transitions
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER || 
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL) {
            
            val triggeringGeofences = geofencingEvent.triggeringGeofences
            if (triggeringGeofences == null) {
                Log.e(TAG, "⭐ DEBUG: No triggering geofences")
                return
            }
            
            Log.d(TAG, "⭐ DEBUG: Number of triggering geofences: ${triggeringGeofences.size}")
            
            // Extract geofence IDs
            val geofenceIds = triggeringGeofences.map { it.requestId }
            Log.d(TAG, "⭐ DEBUG: $transitionName transition for geofence IDs: $geofenceIds")
            handleGeofenceTrigger(context, geofenceIds)
        } else {
            Log.d(TAG, "⭐ DEBUG: Ignoring geofence transition: $transitionName")
        }
    }
    
    private fun handleGeofenceTrigger(context: Context, geofenceIds: List<String>) {
        Log.d(TAG, "⭐ DEBUG: Handling geofence triggers for IDs: $geofenceIds")
        
        for (geofenceId in geofenceIds) {
            val taskId = geofenceId.toLongOrNull()
            
            if (taskId == null) {
                Log.e(TAG, "⭐ DEBUG: Invalid task ID in geofence request: $geofenceId")
                continue
            }
            
            // Skip if we've recently triggered this geofence to avoid duplicate notifications
            if (!shouldProcessGeofence(geofenceId)) {
                Log.d(TAG, "⭐ DEBUG: Skipping recently triggered geofence: $geofenceId")
                continue
            }

            Log.d(TAG, "⭐ DEBUG: Processing notification for geofence ID: $geofenceId (task ID: $taskId)")
            
            scope.launch {
                try {
                    val task = taskRepository.getTaskById(taskId)
                    if (task == null) {
                        Log.e(TAG, "⭐ DEBUG: Task not found for ID: $taskId")
                        return@launch
                    }
                    
                    // Skip completed tasks
                    if (task.isCompleted) {
                        Log.d(TAG, "⭐ DEBUG: Task ${task.id} is already completed, skipping notification")
                        return@launch
                    }
                    
                    Log.d(TAG, "⭐ DEBUG: Creating notification for task: ${task.title}")

                    val notification = NotificationCompat.Builder(context, LocationService.GEOFENCE_NOTIFICATION_CHANNEL_ID)
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

                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                    ) {
                        withContext(Dispatchers.Main) {
                            try {
                                Log.d(TAG, "⭐ DEBUG: Sending notification for task: ${task.title}")
                                NotificationManagerCompat.from(context).notify(taskId.toInt(), notification)
                                Log.d(TAG, "⭐ DEBUG: Notification sent for task: ${task.title}")
                            } catch (e: Exception) {
                                Log.e(TAG, "⭐ DEBUG: Failed to send notification: ${e.message}")
                                
                                // Try with system notification manager as fallback
                                try {
                                    Log.d(TAG, "⭐ DEBUG: Trying system notification manager as fallback")
                                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                    notificationManager.notify(taskId.toInt(), notification)
                                    Log.d(TAG, "⭐ DEBUG: Notification sent via system notification manager")
                                } catch (e2: Exception) {
                                    Log.e(TAG, "⭐ DEBUG: Final notification attempt failed: ${e2.message}")
                                }
                            }
                        }
                    } else {
                        Log.e(TAG, "⭐ DEBUG: Notification permission not granted")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "⭐ DEBUG: Error processing geofence: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }
    
    private fun shouldProcessGeofence(geofenceId: String): Boolean {
        // If we've recently handled this geofence, don't process it again
        if (recentlyTriggeredGeofences.contains(geofenceId)) {
            return false
        }
        
        // Add to recently handled set and schedule removal after 1 minute
        recentlyTriggeredGeofences.add(geofenceId)
        scope.launch {
            delay(60000) // 1 minute debounce
            recentlyTriggeredGeofences.remove(geofenceId)
        }
        
        return true
    }
    
    // Note: BroadcastReceivers don't have onDestroy, so we need a different approach
    // for cleanup. Since the receiver instance might be recreated by the system,
    // we'll rely on static reference cleanup instead.
    
    companion object {
        // Clean up coroutines if needed
        fun cleanupResources() {
            // This could be called from the application class if needed
        }
    }
} 