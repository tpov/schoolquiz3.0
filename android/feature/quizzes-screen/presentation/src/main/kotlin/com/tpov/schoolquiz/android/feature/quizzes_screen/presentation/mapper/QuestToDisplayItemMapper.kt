package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper

import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest

fun Quest.toQuestDisplayItem(): QuestDisplayItem = QuestDisplayItem(
    id = id,
    catalogId = catalogId,
    title = title,
    pictureUrl = pictureUrl,
    averageRating = averageRating,
    averageRatingCount = averageRatingCount,
)
