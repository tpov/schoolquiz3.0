package com.tpov.schoolquiz.android.core.designsystem.model

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId

/**
 * Presentation model for a quest entry displayed in quest list and card UI.
 *
 * [pictureUrl] is a pre-resolved HTTPS URL (null if no picture or not yet resolved).
 * [averageRating] null → no ratings (show outline stars); 0.0..3.0 step 0.1.
 * [averageRatingCount] 0 → hide count label.
 * [catalogId] — required; used for breadcrumb resolution when navigating from MyQuests.
 *
 * Spec: docs/features/home-and-my-quests/06-api-contract.md §7
 *       docs/features/quizzes-screen/06-api-contract.md:81
 */
data class QuestDisplayItem(
    val id: QuestId,
    val catalogId: CatalogId,
    val title: String,
    val pictureUrl: String?,
    val averageRating: Float?,
    val averageRatingCount: Int = 0,
)
