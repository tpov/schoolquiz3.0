package com.tpov.schoolquiz.presentation.main.profile_state

/**
 * Данные о задаче
 */
data class Task(
    val name: String,
    val maxCount: Int,
    val currentProgress: Int = 0
) 