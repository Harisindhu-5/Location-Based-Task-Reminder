package com.example.locationtaskreminder.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.locationtaskreminder.data.model.Task
import com.example.locationtaskreminder.data.model.TaskCategory
import com.example.locationtaskreminder.data.repository.TaskRepository
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskViewModel @Inject constructor(
    private val repository: TaskRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<TaskCategory?>(null)
    val selectedCategory = _selectedCategory.asStateFlow()

    val pendingTasks = repository.getPendingTasks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val completedTasks = repository.getCompletedTasks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val tasksByCategory = _selectedCategory
        .filterNotNull()
        .flatMapLatest { category ->
            repository.getTasksByCategory(category)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setSelectedCategory(category: TaskCategory) {
        _selectedCategory.value = category
    }

    fun addTask(
        title: String,
        description: String,
        location: LatLng,
        radius: Float,
        locationName: String,
        category: TaskCategory
    ) {
        val task = Task(
            title = title,
            description = description,
            latitude = location.latitude,
            longitude = location.longitude,
            radius = radius,
            locationName = locationName,
            category = category
        )
        viewModelScope.launch {
            repository.insertTask(task)
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    fun markTaskAsCompleted(taskId: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.updateTaskCompletionStatus(taskId, completed)
        }
    }
} 