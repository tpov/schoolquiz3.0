package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewFeeQuote
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewFeeQuoteRepository

/**
 * Цитата платы для диалога подачи с карточки черновика (история 4.11).
 *
 * Заранее названная цена: диалог открывается только с цитатой на руках, и подтверждение без
 * неё невозможно. Отказ цитаты — не отказ подачи, автор остаётся на экране и может повторить.
 */
class QuoteQuestReviewFeeUseCase(
    private val repository: ReviewFeeQuoteRepository,
) {
    suspend operator fun invoke(
        questId: QuestDraftId,
        questionCount: Int,
    ): Result<ReviewFeeQuote> {
        if (questionCount < 1) {
            return Result.failure(IllegalArgumentException("questionCount must be positive"))
        }
        return repository.quote(questId, questionCount)
    }
}
