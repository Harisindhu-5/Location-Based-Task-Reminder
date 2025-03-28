package com.example.locationtaskreminder.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    @Inject
    lateinit var repository: com.example.locationtaskreminder.data.repository.TaskRepository

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private const val CHANNEL_ID = "geofence_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        handleGeofencingEvent(context, intent)
    }

    private fun handleGeofencingEvent(context: Context, intent: Intent) {
        try {
            val geofencingEvent = GeofencingEvent.fromIntent(intent)
            if (geofencingEvent == null) {
                return
            }

            if (geofencingEvent.hasError()) {
                val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
                android.util.Log.e("GeofenceBroadcastReceiver", "Error: $errorMessage")
                return
            }

            if (geofencingEvent.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                processGeofenceTransition(context, geofencingEvent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processGeofenceTransition(context: Context, geofencingEvent: GeofencingEvent) {
        val triggeringGeofences = geofencingEvent.triggeringGeofences
        if (triggeringGeofences.isNullOrEmpty()) {
            return
        }

        triggeringGeofences.forEach { geofence ->
            val taskId = geofence.requestId.toLongOrNull() ?: return@forEach

            scope.launch {
                repository.getTaskById(taskId)?.let { task ->
                    if (!task.isCompleted) {
                        showNotification(context, task.title, task.description)
                    }
                }
            }
        }
    }

    private fun showNotification(context: Context, title: String, description: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Geofence Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                this.description = "Notifications for location-based tasks"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(description)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
} 