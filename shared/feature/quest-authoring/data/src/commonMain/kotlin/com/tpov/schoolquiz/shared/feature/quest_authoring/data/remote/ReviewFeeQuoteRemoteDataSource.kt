package com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewFeeQuote

/**
 * Транспорт цитаты платы (история 4.11): один читающий view-коллбл.
 *
 * Владелец выводится сервером из Firebase Auth — в полезной нагрузке его нет. Ответ приводится
 * к доменной проекции строгим парсером; расхождение конверта — отказ чтения.
 */
interface ReviewFeeQuoteRemoteDataSource {
    suspend fun quoteReviewFee(
        questId: String,
        questionCount: Int,
    ): ReviewFeeQuote
}
