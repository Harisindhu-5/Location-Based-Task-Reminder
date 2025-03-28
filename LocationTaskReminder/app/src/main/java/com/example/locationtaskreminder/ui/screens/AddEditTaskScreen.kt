package com.example.locationtaskreminder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.locationtaskreminder.data.model.Task
import com.example.locationtaskreminder.data.model.TaskCategory
import com.google.android.gms.maps.model.LatLng

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskScreen(
    task: Task?,
    onSaveTask: (String, String, LatLng, Float, String, TaskCategory) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf(task?.title ?: "") }
    var description by remember { mutableStateOf(task?.description ?: "") }
    var selectedLocation by remember {
        mutableStateOf<LatLng?>(
            task?.let { LatLng(it.latitude, it.longitude) }
        )
    }
    var radius by remember { mutableStateOf(task?.radius ?: 100f) }
    var locationName by remember { mutableStateOf(task?.locationName ?: "") }
    var selectedCategory by remember { mutableStateOf(task?.category ?: TaskCategory.HOME) }
    var showMap by remember { mutableStateOf(false) }

    if (showMap) {
        MapScreen(
            onLocationSelected = { location, selectedRadius ->
                selectedLocation = location
                radius = selectedRadius
                showMap = false
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (task == null) "Add Task" else "Edit Task") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = "Navigate back"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                selectedLocation?.let { location ->
                                    onSaveTask(
                                        title,
                                        description,
                                        location,
                                        radius,
                                        locationName,
                                        selectedCategory
                                    )
                                }
                            },
                            enabled = title.isNotBlank() && selectedLocation != null && locationName.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save task"
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                OutlinedTextField(
                    value = locationName,
                    onValueChange = { locationName = it },
                    label = { Text("Location Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = false,
                    onExpandedChange = {},
                ) {
                    OutlinedTextField(
                        value = selectedCategory.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    DropdownMenu(
                        expanded = false,
                        onDismissRequest = {},
                    ) {
                        TaskCategory.values().forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = { selectedCategory = category }
                            )
                        }
                    }
                }

                Button(
                    onClick = { showMap = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (selectedLocation == null) "Select Location"
                        else "Change Location"
                    )
                }

                selectedLocation?.let { location ->
                    Text(
                        "Selected Location: ${location.latitude}, ${location.longitude}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Radius: ${radius.toInt()} meters",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
} 