package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper

import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest

fun Quest.toQuestDisplayItem(
    isDownloadable: Boolean = false,
    isDownloading: Boolean = false,
    isDownloadComplete: Boolean = false,
): QuestDisplayItem =
    QuestDisplayItem(
        id = id,
        catalogId = catalogId,
        title = title,
        pictureUrl = pictureUrl,
        averageRating = averageRating,
        averageRatingCount = averageRatingCount,
        isDownloadable = isDownloadable,
        isDownloading = isDownloading,
        isDownloadComplete = isDownloadComplete,
    )
