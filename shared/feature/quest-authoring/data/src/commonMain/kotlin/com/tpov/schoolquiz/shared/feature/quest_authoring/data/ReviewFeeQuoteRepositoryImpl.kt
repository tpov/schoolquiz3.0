package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewFeeQuoteRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewFeeQuote
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewFeeQuoteRepository

/**
 * Проекция цитаты платы; отказ сервера — [SyncFailure] поверх исходной ошибки.
 *
 * Локальной половины нет и не надо: тариф и `priorCycle` — серверная правда (AD-20), кэш цитаты
 * только врал бы между изменениями тарифа.
 */
class ReviewFeeQuoteRepositoryImpl(
    private val remote: ReviewFeeQuoteRemoteDataSource,
) : ReviewFeeQuoteRepository {
    override suspend fun quote(
        questId: QuestDraftId,
        questionCount: Int,
    ): Result<ReviewFeeQuote> =
        try {
            Result.success(remote.quoteReviewFee(questId = questId.value, questionCount = questionCount))
        } catch (error: SyncFailure) {
            // Транспорт уже назвал свою ветвь — перепревязывать нельзя.
            Result.failure(error)
        }
}
