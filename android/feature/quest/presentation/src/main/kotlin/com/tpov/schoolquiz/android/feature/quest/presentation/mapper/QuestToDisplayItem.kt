package com.tpov.schoolquiz.android.feature.quest.presentation.mapper

import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest

/**
 * Maps Quest domain model to QuestDisplayItem UI model.
 *
 * pictureUrl (pre-resolved HTTPS URL) is forwarded as-is.
 * Domain picturePath (relative Storage path) is not exposed to UI.
 *
 * Spec: docs/features/home-and-my-quests/06-api-contract.md §7 Extension functions
 */
fun Quest.toDisplayItem(): QuestDisplayItem =
    QuestDisplayItem(
        id = id,
        catalogId = catalogId,
        title = title,
        pictureUrl = pictureUrl,
        averageRating = averageRating,
        averageRatingCount = averageRatingCount,
    )
