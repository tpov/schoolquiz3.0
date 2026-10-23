package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.feature.economy.domain.model.OwnHistoryPage
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.OwnHistoryRepository
import kotlinx.coroutines.CancellationException

/**
 * Чтение собственной истории операций (AD-30/UX-20, история 8.6).
 *
 * Владелец берётся из авторизации, а не из тела: смена аккаунта (A→B→A) начинает новое
 * чтение с нулевым курсором, и страница, выпущенная прежнему владельцу, под нового не
 * подставляется (AD-30/AD-6). Чтение создаёт ноль финансовых эффектов — это слово порта.
 */
class ObserveOwnHistoryUseCase(
    private val repository: OwnHistoryRepository,
    /** Часы сессии: тот же источник uid, что у остальных доменов economy (границы без SDK). */
    private val currentUid: suspend () -> String?,
) {
    suspend operator fun invoke(
        cursor: String? = null,
        pageSize: Int = OwnHistoryRepository.DEFAULT_PAGE_SIZE,
    ): Result<OwnHistoryPage> {
        val uid =
            currentUid()
                ?: return Result.failure(IllegalStateException("own history requires a session"))
        return try {
            repository.historyPage(ownerUid = uid, cursor = cursor, pageSize = pageSize)
        } catch (e: CancellationException) {
            throw e
        }
    }

    /** «Начисления» конкретного авторского урока — та же проекция, lesson-scoped отбор. */
    suspend fun lessonEarnings(
        lessonId: String,
        cursor: String? = null,
        pageSize: Int = OwnHistoryRepository.DEFAULT_PAGE_SIZE,
    ): Result<OwnHistoryPage> {
        if (lessonId.isBlank()) return Result.failure(IllegalArgumentException("lessonId must not be blank"))
        val uid =
            currentUid()
                ?: return Result.failure(IllegalStateException("lesson earnings require a session"))
        return try {
            repository.lessonEarningsPage(ownerUid = uid, lessonId = lessonId, cursor = cursor, pageSize = pageSize)
        } catch (e: CancellationException) {
            throw e
        }
    }
}
