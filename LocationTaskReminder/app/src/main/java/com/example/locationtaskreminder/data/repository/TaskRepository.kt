package com.example.locationtaskreminder.data.repository

import com.example.locationtaskreminder.data.local.TaskDao
import com.example.locationtaskreminder.data.model.Task
import com.example.locationtaskreminder.data.model.TaskCategory
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao
) {
    fun getPendingTasks(): Flow<List<Task>> = taskDao.getPendingTasks()

    fun getCompletedTasks(): Flow<List<Task>> = taskDao.getCompletedTasks()

    fun getTasksByCategory(category: TaskCategory): Flow<List<Task>> =
        taskDao.getTasksByCategory(category)

    suspend fun insertTask(task: Task): Long = taskDao.insertTask(task)

    suspend fun updateTask(task: Task) = taskDao.updateTask(task)

    suspend fun deleteTask(task: Task) = taskDao.deleteTask(task)

    suspend fun getTaskById(taskId: Long): Task? = taskDao.getTaskById(taskId)

    suspend fun updateTaskCompletionStatus(taskId: Long, completed: Boolean) =
        taskDao.updateTaskCompletionStatus(taskId, completed)

    fun getAllTasks(): Flow<List<Task>> = taskDao.getAllTasks()
} 