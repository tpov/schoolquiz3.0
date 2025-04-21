package com.tpov.schoolquiz.presentation.setting

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap


data class Task(
    val name: String,
    val maxCount: Int
)

data class ProgressState(
    val currentTime: Long = 0,
    val estimatedTotalTime: Long = 0,
    val tasks: List<Task> = emptyList(),
    val isRunning: Boolean = false
) {
    val progressPercentage: Float = if (estimatedTotalTime > 0) {
        (currentTime.toFloat() / estimatedTotalTime).coerceIn(0f, 1f)
    } else 0f
}

object TaskProgressTracker {
    private val averageExecutionTimes = ConcurrentHashMap<String, Float>()

    private val _progressState = MutableStateFlow(ProgressState())
    val progressState: StateFlow<ProgressState> = _progressState

    private var timerJob: Job? = null

    fun addTask(name: String, maxCount: Int) {
        val task = Task(name, maxCount)
        val currentTasks = _progressState.value.tasks.toMutableList()

        currentTasks.add(task)

        _progressState.update { currentState ->
            currentState.copy(
                tasks = currentTasks,
                estimatedTotalTime = calculateEstimatedTotalTime(currentTasks)
            )
        }

        if (_progressState.value.tasks.size == 1) {
            startTimer()
        }
    }

    fun completeTask(name: String) {
        val taskToComplete = _progressState.value.tasks.find { it.name == name }
        if (taskToComplete != null) {
            val actualTime = _progressState.value.currentTime

            updateAverageExecutionTime(name, actualTime)

            val updatedTasks = _progressState.value.tasks.filter { it.name != name }

            _progressState.update { currentState ->
                currentState.copy(
                    tasks = updatedTasks,
                    estimatedTotalTime = calculateEstimatedTotalTime(updatedTasks)
                )
            }

            if (_progressState.value.tasks.isEmpty()) {
                stopTimer()
            }
        }
    }

    private fun updateAverageExecutionTime(taskName: String, actualTime: Long) {
        val currentAverage = averageExecutionTimes[taskName] ?: 0f
        val newAverage = if (currentAverage > 0) {
            (currentAverage * 0.7f) + (actualTime * 0.3f)
        } else {
            actualTime.toFloat()
        }
        averageExecutionTimes[taskName] = newAverage
    }

    private fun calculateEstimatedTotalTime(tasks: List<Task>): Long {
        return tasks.sumOf { task ->
            val averageTime = averageExecutionTimes[task.name] ?: 1f
            (averageTime * task.maxCount).toLong()
        }
    }

    private fun startTimer() {
        stopTimer()

        timerJob = CoroutineScope(Dispatchers.Default).launch {
            _progressState.update { it.copy(isRunning = true) }

            while (isActive) {
                delay(333)

                _progressState.update { currentState ->
                    val updatedTime = currentState.currentTime + 1
                    val updatedEstimatedTime = calculateEstimatedTotalTime(currentState.tasks)

                    currentState.copy(
                        currentTime = updatedTime,
                        estimatedTotalTime = updatedEstimatedTime
                    )
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null

        _progressState.update { it.copy(isRunning = false) }
    }

    fun reset() {
        stopTimer()
        averageExecutionTimes.clear()
        _progressState.value = ProgressState()
    }
}