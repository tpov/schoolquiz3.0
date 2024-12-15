package com.tpov.common

import com.bumptech.glide.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max

object Core {

    var token: String = ""
    var tpovId = 0
        set(value) {
            field = value
            _tpovIdFlow.value = value
        }

    val priority: HashMap<String, Int> = hashMapOf()

    var progressLoadProgress: MutableList<Pair<Int, Int>> = mutableListOf()
        set(value) {
            field = value
            _progressLoadDataProgress.value =
                (value.sumOf { it.first }.toDouble() / max(1, value.sumOf { it.second }) * 100).toInt()
        }

    var listTasksLoad: MutableList<String> = mutableListOf()
        set(value) {
            field = value
            _progressLoadDataText.value = calculatePriorityText(field)
        }

    private val _tpovIdFlow = MutableStateFlow(tpovId)
    val tpovIdFlow: StateFlow<Int> = _tpovIdFlow

    private val _progressLoadDataProgress = MutableStateFlow(0)
    val progressLoadDataProgress: StateFlow<Int> = _progressLoadDataProgress

    private val _progressLoadDataText: MutableStateFlow<String> = MutableStateFlow("")
    val progressLoadDataText: StateFlow<String> = _progressLoadDataText

    private fun calculatePriorityText(tasks: List<String>): String {
        return tasks.maxByOrNull { priority[it] ?: Priority.NORMAL.ordinal } ?: ""
    }
}