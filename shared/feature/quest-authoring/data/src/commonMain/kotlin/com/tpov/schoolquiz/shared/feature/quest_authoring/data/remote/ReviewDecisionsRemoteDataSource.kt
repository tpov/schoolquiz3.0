package com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMarkValue
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionsAccessRefusedException

/**
 * Транспорт «Решений» (история 4.17): две callable-поверхности — чтение страницы и отметка.
 *
 * Отказ по роли приезжает как [ReviewDecisionsAccessRefusedException]: реализация транспорта
 * обязана перевести PERMISSION_DENIED сервера в этот тип, потому что только он отличает
 * «не админ» от сбоя канала — экран решает ими по-разному.
 */
interface ReviewDecisionsRemoteDataSource {
    @Throws(ReviewDecisionsAccessRefusedException::class)
    suspend fun fetch(request: ReviewDecisionsWireRequest): ReviewDecisionsWirePage

    @Throws(ReviewDecisionsAccessRefusedException::class)
    suspend fun mark(
        verdictId: String,
        questionId: String,
        mark: ReviewDecisionMarkValue,
        comment: String,
    ): ReviewDecisionsWireMark
}
