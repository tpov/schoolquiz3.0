package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftClaimRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftClaimRequest
import kotlinx.coroutines.flow.Flow

/**
 * Явная заявка суточного премиального подарка — только по кнопке, вход в приложение её не зовёт.
 *
 * Премиум здесь не проверяется: сервер перепроверяет своё слово при самой заявке и отвечает
 * PREMIUM_REQUIRED, если срок истёк. Двойной тап закрыт лежащим намерением: пока первая заявка
 * не разрешилась, повтор уезжает тем же operationId и тем же телом, и второй операции сервер не
 * создаст — забор `box_operations` вернёт сохранённый результат.
 */
class ClaimPremiumDailyGiftUseCase(
    private val claims: GiftClaimRepository,
) {
    suspend operator fun invoke(): Result<GiftClaimOutcome> =
        claims.resumePending(GiftClaimKind.PREMIUM_DAILY)
            ?: claims.submit(GiftClaimRequest(GiftClaimKind.PREMIUM_DAILY))

    /** Живёт ли сейчас незавершённая заявка подарка — состояние «забираем…» на кнопке. */
    fun observePending(): Flow<Boolean> = claims.observePending(GiftClaimKind.PREMIUM_DAILY)
}
