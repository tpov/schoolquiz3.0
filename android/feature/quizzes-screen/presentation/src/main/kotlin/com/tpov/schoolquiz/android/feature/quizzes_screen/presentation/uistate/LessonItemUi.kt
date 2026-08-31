package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate

import com.tpov.schoolquiz.shared.feature.lesson.domain.logic.LessonAccess

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
    /**
     * Whether the lesson is open, bought open, or shut behind the one before it.
     * Only course catalogs gate; everything else stays [LessonAccess.OPEN].
     */
    val access: LessonAccess = LessonAccess.OPEN,
    /** Nolics to open this lesson, or null when it is not for sale. Priced by the server. */
    val unlockPriceNolics: Int? = null,
)
