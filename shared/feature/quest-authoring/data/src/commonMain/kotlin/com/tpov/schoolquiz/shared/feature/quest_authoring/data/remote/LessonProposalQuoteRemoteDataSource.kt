package com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonProposalFeeQuote

/**
 * Транспорт цитаты платы предложения (UX-23, история 4.22): один читающий view-коллбл.
 *
 * Владелец выводится сервером из Firebase Auth — в полезной нагрузке его нет. Ответ приводится
 * к доменной проекции строгим парсером; расхождение конверта — отказ чтения.
 */
interface LessonProposalQuoteRemoteDataSource {
    suspend fun quoteProposalFee(
        kind: String,
        changedQuestions: Int,
    ): LessonProposalFeeQuote
}
