package com.example.locationtaskreminder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.locationtaskreminder.data.model.Task
import com.example.locationtaskreminder.data.model.TaskCategory
import com.example.locationtaskreminder.ui.components.TaskItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    pendingTasks: List<Task>,
    completedTasks: List<Task>,
    onAddTaskClick: () -> Unit,
    onTaskClick: (Task) -> Unit,
    onCompleteTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("Pending", "Completed")
    var selectedTabIndex by remember { mutableStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Location Tasks") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            if (selectedTabIndex == 0) {
                FloatingActionButton(
                    onClick = onAddTaskClick,
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Task"
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> PendingTasksList(
                    tasks = pendingTasks,
                    onTaskClick = onTaskClick,
                    onCompleteTask = onCompleteTask,
                    onDeleteTask = onDeleteTask
                )
                1 -> CompletedTasksList(
                    tasks = completedTasks,
                    onTaskClick = onTaskClick,
                    onCompleteTask = onCompleteTask,
                    onDeleteTask = onDeleteTask
                )
            }
        }
    }
}

@Composable
private fun PendingTasksList(
    tasks: List<Task>,
    onTaskClick: (Task) -> Unit,
    onCompleteTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tasks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "No pending tasks",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = 8.dp)
        ) {
            tasks.groupBy { it.category }.forEach { (category, categoryTasks) ->
                CategorySection(
                    category = category,
                    tasks = categoryTasks,
                    onTaskClick = onTaskClick,
                    onCompleteTask = onCompleteTask,
                    onDeleteTask = onDeleteTask
                )
            }
        }
    }
}

@Composable
private fun CompletedTasksList(
    tasks: List<Task>,
    onTaskClick: (Task) -> Unit,
    onCompleteTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tasks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "No completed tasks",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = 8.dp)
        ) {
            tasks.forEach { task ->
                TaskItem(
                    task = task,
                    onTaskClick = onTaskClick,
                    onCompleteClick = onCompleteTask,
                    onDeleteClick = onDeleteTask
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySection(
    category: TaskCategory,
    tasks: List<Task>,
    onTaskClick: (Task) -> Unit,
    onCompleteTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }

    Column(modifier = modifier) {
        ListItem(
            headlineContent = {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            trailingContent = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
        )

        if (expanded) {
            tasks.forEach { task ->
                TaskItem(
                    task = task,
                    onTaskClick = onTaskClick,
                    onCompleteClick = onCompleteTask,
                    onDeleteClick = onDeleteTask
                )
            }
        }
    }
} 