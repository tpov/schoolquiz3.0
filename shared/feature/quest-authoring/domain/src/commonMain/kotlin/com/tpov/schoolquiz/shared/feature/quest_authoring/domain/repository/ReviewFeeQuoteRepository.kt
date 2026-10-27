package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewFeeQuote

/**
 * Читающая проекция платы за проверку (AD-20): только чтение, владелец выводит сервер.
 *
 * `priorCycle` — серверная правда; клиент её не угадывает, потому цитата и живёт в отдельном
 * view, а не в локальном состоянии черновика.
 */
interface ReviewFeeQuoteRepository {
    suspend fun quote(
        questId: QuestDraftId,
        questionCount: Int,
    ): Result<ReviewFeeQuote>
}
