package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate

data class LessonItemUi(
    val id: String,
    val title: String,
    val orderLabel: String? = null,
    val subtitleCount: String? = null,
    val averageRating: Float? = null,
    val ratingCount: Int = 0,
    val bestStarsRawTenths: Int = 0,
    val hardUnlocked: Boolean = false,
    val isHardChecked: Boolean = false,
    val isDownloaded: Boolean = true,
    val isDownloading: Boolean = false,
)
