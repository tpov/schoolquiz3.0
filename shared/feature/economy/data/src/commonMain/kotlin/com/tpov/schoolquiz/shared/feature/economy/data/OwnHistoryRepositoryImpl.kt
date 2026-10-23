package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.feature.economy.data.remote.OwnHistoryRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.domain.model.OwnHistoryPage
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.OwnHistoryRepository
import kotlinx.coroutines.CancellationException

/**
 * Реализация порта собственной истории (AD-30, история 8.6).
 *
 * Ограда аккаунта: ответ принимается только под тем владельцем, под которым читался.
 * Смена аккаунта (A→B→A) отсекает поздний ответ старого сеанса — чужая страница не рендерится
 * и не кэшируется (AD-30/AD-8). Чтение эффекта не создаёт: здесь нет ни записи, ни команды.
 */
class OwnHistoryRepositoryImpl(
    private val remote: OwnHistoryRemoteDataSource,
    private val currentUid: suspend () -> String?,
) : OwnHistoryRepository {

    override suspend fun historyPage(
        ownerUid: String,
        cursor: String?,
        pageSize: Int,
    ): Result<OwnHistoryPage> =
        read(ownerUid) { remote.historyPage(cursor = cursor, pageSize = pageSize) }

    override suspend fun lessonEarningsPage(
        ownerUid: String,
        lessonId: String,
        cursor: String?,
        pageSize: Int,
    ): Result<OwnHistoryPage> =
        read(ownerUid) { remote.lessonEarningsPage(lessonId = lessonId, cursor = cursor, pageSize = pageSize) }

    private suspend fun read(
        ownerUid: String,
        call: suspend () -> Any?,
    ): Result<OwnHistoryPage> =
        try {
            require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
            val session = currentUid()
            if (session != ownerUid) {
                // Смена владельца между запросом и ответом: страница того не рендерится.
                Result.failure(IllegalStateException("own history session changed"))
            } else {
                Result.success(OwnHistoryWireParser.page(call()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
}
