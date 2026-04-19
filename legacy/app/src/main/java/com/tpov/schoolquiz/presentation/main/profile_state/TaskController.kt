package com.tpov.schoolquiz.presentation.main.profile_state

import android.content.Context
import com.bumptech.glide.Priority
import com.tpov.schoolquiz.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Контроллер для управления и отображения задач загрузки
 */
class TaskController(private val context: Context) {

    companion object {
        private const val UPDATE_INTERVAL = 1000L // 1 секунда
    }

    private val averageExecutionTimes = ConcurrentHashMap<String, Float>()
    private val taskPriorities = ConcurrentHashMap<String, Int>()
    private val listTasksLoad = mutableListOf<String>()
    private val progressLoadProgress = mutableListOf<Pair<Int, Int>>()
    
    private val _taskState = MutableStateFlow(TaskState())
    val taskState: StateFlow<TaskState> = _taskState
    
    private var timerJob: Job? = null

    init {
        initTaskPriorities()
    }

    /**
     * Добавляет новую задачу в очередь отслеживания
     * @param name название задачи (из R.string)
     * @param maxCount максимальное значение прогресса
     */
    fun addTask(name: String, maxCount: Int) {
        val currentTasks = _taskState.value.tasks.toMutableList()
        currentTasks.add(Task(name, maxCount))
        
        listTasksLoad.add(name)
        
        _taskState.value = _taskState.value.copy(
            tasks = currentTasks,
            estimatedTotalTime = calculateEstimatedTotalTime(currentTasks),
            progressPercentage = calculateProgressPercentage(currentTasks),
            currentTaskName = calculateCurrentTaskName(currentTasks)
        )
        
        if (currentTasks.size == 1) {
            startTimer()
        }
    }

    /**
     * Обновляет прогресс для задачи
     * @param name название задачи
     * @param progress текущий прогресс
     * @param total общее количество
     */
    fun updateTaskProgress(name: String, progress: Int, total: Int) {
        val currentTasks = _taskState.value.tasks.toMutableList()
        val taskIndex = currentTasks.indexOfFirst { it.name == name }
        
        if (taskIndex >= 0) {
            val task = currentTasks[taskIndex]
            currentTasks[taskIndex] = task.copy(currentProgress = progress, maxCount = total)
            
            val existingPairIndex = progressLoadProgress.indexOfFirst { it.second == total }
            if (existingPairIndex >= 0) {
                progressLoadProgress[existingPairIndex] = progress to total
            } else {
                progressLoadProgress.add(progress to total)
            }
            
            _taskState.value = _taskState.value.copy(
                tasks = currentTasks,
                progressPercentage = calculateProgressPercentage(currentTasks),
                currentTaskName = calculateCurrentTaskName(currentTasks)
            )
        }
    }

    /**
     * Помечает задачу как завершенную и удаляет ее из списка отслеживания
     * @param name название задачи
     */
    fun completeTask(name: String) {
        val taskToComplete = _taskState.value.tasks.find { it.name == name }
        if (taskToComplete != null) {
            val actualTime = _taskState.value.currentTime
            
            updateAverageExecutionTime(name, actualTime)
            
            val updatedTasks = _taskState.value.tasks.filter { it.name != name }
            listTasksLoad.remove(name)
            
            _taskState.value = _taskState.value.copy(
                tasks = updatedTasks,
                estimatedTotalTime = calculateEstimatedTotalTime(updatedTasks),
                progressPercentage = calculateProgressPercentage(updatedTasks),
                currentTaskName = calculateCurrentTaskName(updatedTasks)
            )
            
            if (updatedTasks.isEmpty()) {
                stopTimer()
            }
        }
    }

    /**
     * Сбрасывает состояние контроллера задач
     */
    fun reset() {
        stopTimer()
        averageExecutionTimes.clear()
        listTasksLoad.clear()
        progressLoadProgress.clear()
        _taskState.value = TaskState()
    }

    private fun startTimer() {
        stopTimer()
        
        timerJob = CoroutineScope(Dispatchers.Default).launch {
            _taskState.value = _taskState.value.copy(isRunning = true)
            
            while (isActive) {
                delay(UPDATE_INTERVAL)
                
                _taskState.value = _taskState.value.copy(
                    currentTime = _taskState.value.currentTime + 1,
                    estimatedTotalTime = calculateEstimatedTotalTime(_taskState.value.tasks)
                )
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _taskState.value = _taskState.value.copy(isRunning = false)
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

    private fun calculateProgressPercentage(tasks: List<Task>): Float {
        if (tasks.isEmpty()) return 0f
        
        val totalProgress = tasks.sumOf { it.currentProgress }
        val totalMaxCount = tasks.sumOf { it.maxCount }
        
        return if (totalMaxCount > 0) {
            (totalProgress.toFloat() / totalMaxCount).coerceIn(0f, 1f)
        } else 0f
    }

    private fun calculateCurrentTaskName(tasks: List<Task>): String {
        if (tasks.isEmpty()) return ""
        
        return tasks.maxByOrNull { taskPriorities[it.name] ?: Priority.NORMAL.ordinal }?.let {
            getTaskDisplayName(it.name)
        } ?: ""
    }

    private fun getTaskDisplayName(taskKey: String): String {
        val resourceId = context.resources.getIdentifier(
            "fb_load_$taskKey", 
            "string", 
            context.packageName
        )
        
        return if (resourceId != 0) {
            context.getString(resourceId)
        } else {
            taskKey
        }
    }

    private fun initTaskPriorities() {
        taskPriorities.apply {
            put("text_load_leaders", Priority.LOW.ordinal)
            put("text_load_chat", Priority.LOW.ordinal)
            put("text_load_profile", Priority.HIGH.ordinal)
            put("text_load_quiz", Priority.NORMAL.ordinal)
            put("text_send_profile", Priority.HIGH.ordinal)
            put("text_send_quizz", Priority.NORMAL.ordinal)
            put("text_send_profile_error", Priority.IMMEDIATE.ordinal)
            put("text_get_profile_error", Priority.IMMEDIATE.ordinal)
            put("text_weit_unlock_server", Priority.LOW.ordinal)
            put("text_error", Priority.IMMEDIATE.ordinal)
            put("server_load", Priority.NORMAL.ordinal)
            put("text_load_new_massage", Priority.HIGH.ordinal)
        }
    }
} 