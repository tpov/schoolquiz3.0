package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionCursor
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionFilter
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewDecisionsRepository

/**
 * Страница «Решений» (история 4.17, UX-22): тонкая обёртка чтения — фильтр и курсор принадлежат
 * экрану, право уже решено сервером. Отказ доступа остаётся отказом доступа, экрану он виден.
 */
class FetchReviewDecisionsUseCase(
    private val repository: ReviewDecisionsRepository,
) {
    suspend operator fun invoke(
        filter: ReviewDecisionFilter,
        cursor: ReviewDecisionCursor? = null,
    ): Result<ReviewDecisionPage> = repository.fetch(filter, cursor)
}
