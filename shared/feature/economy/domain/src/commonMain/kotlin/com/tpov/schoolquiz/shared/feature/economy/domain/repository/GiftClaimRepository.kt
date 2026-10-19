package com.tpov.schoolquiz.shared.feature.economy.domain.repository

import com.tpov.schoolquiz.shared.feature.economy.domain.logic.GiftBoxStreak
import com.tpov.schoolquiz.shared.feature.economy.domain.logic.OfflineAccrualClaim
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimOutcome
import kotlinx.coroutines.flow.Flow

/**
 * Одна заявка подарка: вид — и всё, что только у OFFLINE_STREAK есть своего.
 *
 * `base` уезжает дословно, последним серверным словом: сервер сверяет заявку с ним, и хоть один
 * переставленный байт в базе оборачивается STALE_BASE. VISIT и PREMIUM_DAILY несут только
 * тройку «вид/владелец/operationId».
 */
data class GiftClaimRequest(
    val kind: GiftClaimKind,
    val base: GiftBoxStreak? = null,
    val claim: OfflineAccrualClaim? = null,
) {
    init {
        require(kind != GiftClaimKind.OFFLINE_STREAK || (base != null && claim != null)) {
            "OFFLINE_STREAK requires the saved server base and the clamped local claim"
        }
    }
}

/**
 * Заявки подарков серверу — прямые вызываемые с локальной дисциплиной очереди.
 *
 * Намерение (operationId + тело) лежит на устройстве ДО запроса и до ответа; умер процесс —
 * [resumePending] уезжает тем же телом, и сервер отвечает сохранённым результатом. Статусы
 * сервера — исходы, не ошибки: NO_CHANGE, DAILY_LIMIT, PREMIUM_REQUIRED и STALE_BASE приходят
 * как [GiftClaimOutcome]; неопознанный ответ или отказ транспорта оставляют намерение жить.
 */
interface GiftClaimRepository {
    /**
     * Отправляет одну заявку. Активное намерение того же вида переиспользуется дословно — второй
     * тап не создаёт вторую операцию; офлайн — отказ по имени ещё до намерения.
     */
    suspend fun submit(request: GiftClaimRequest): Result<GiftClaimOutcome>

    /** Повтор незавершённого намерения тем же телом; `null` — незавершённых нет. */
    suspend fun resumePending(kind: GiftClaimKind): Result<GiftClaimOutcome>?

    /** Живёт ли сейчас незавершённое намерение этого вида у текущего аккаунта. */
    fun observePending(kind: GiftClaimKind): Flow<Boolean>
}
