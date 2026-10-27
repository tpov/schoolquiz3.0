package com.tpov.schoolquiz.android.feature.quest.presentation.fake

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewFeeQuote
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewFeeQuoteRepository

/** Цитата платы по заказу теста: фиксированный ответ или фиксированный отказ (история 4.11). */
class FakeReviewFeeQuoteRepository(
    private val quote: ReviewFeeQuote? = null,
    private val error: Exception? = null,
) : ReviewFeeQuoteRepository {
    override suspend fun quote(
        questId: QuestDraftId,
        questionCount: Int,
    ): Result<ReviewFeeQuote> {
        error?.let { return Result.failure(it) }
        return Result.success(
            requireNotNull(quote) { "FakeReviewFeeQuoteRepository requires either a quote or an error" },
        )
    }
}
