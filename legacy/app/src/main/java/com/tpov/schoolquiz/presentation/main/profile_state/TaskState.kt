package com.tpov.schoolquiz.presentation.main.profile_state

/**
 * Состояние задач для UI
 */
data class TaskState(
    val currentTime: Long = 0,
    val estimatedTotalTime: Long = 0,
    val tasks: List<Task> = emptyList(),
    val isRunning: Boolean = false,
    val progressPercentage: Float = 0f,
    val currentTaskName: String = ""
) 