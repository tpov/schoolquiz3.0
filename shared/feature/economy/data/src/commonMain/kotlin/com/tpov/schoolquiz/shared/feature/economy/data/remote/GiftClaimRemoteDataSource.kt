package com.tpov.schoolquiz.shared.feature.economy.data.remote

import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimOutcome

/**
 * Прямой вызываемый `submitGiftBoxOperation` — единственный транспорт трёх видов заявок.
 *
 * Тело уезжает ровно то, что лежало в намерении ([payloadJson]); разобранный ответ — типизированный
 * исход: CLAIMED/NO_CHANGE/STALE_BASE/PREMIUM_REQUIRED/DAILY_LIMIT возвращаются как успех. Отказ
 * транспорта, неопознанный конверт и чужая операция в ответе — исключение, при котором намерение
 * обязано остаться лежать.
 */
interface GiftClaimRemoteDataSource {
    suspend fun submitGiftBoxOperation(payloadJson: String): GiftClaimOutcome
}
