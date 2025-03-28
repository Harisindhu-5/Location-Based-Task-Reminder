package com.example.locationtaskreminder

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.locationtaskreminder.service.LocationService
import com.example.locationtaskreminder.ui.screens.AddEditTaskScreen
import com.example.locationtaskreminder.ui.screens.TaskListScreen
import com.example.locationtaskreminder.ui.theme.LocationTaskReminderTheme
import com.example.locationtaskreminder.ui.viewmodel.TaskViewModel
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: TaskViewModel by viewModels()

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
        ) {
            startLocationService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestLocationPermissions()

        setContent {
            LocationTaskReminderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val pendingTasks by viewModel.pendingTasks.collectAsStateWithLifecycle()
                    val completedTasks by viewModel.completedTasks.collectAsStateWithLifecycle()

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

    private fun requestLocationPermissions() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )
    }

    private fun startLocationService() {
        Intent(this, LocationService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }
} 