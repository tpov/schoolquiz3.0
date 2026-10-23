package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.PrivateQuizDoorRefusal
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.PrivateQuizRunRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizAttemptReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizAttemptStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizEntryRefusal
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizOwnResult
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.PrivateQuizRunRepository
import kotlinx.coroutines.CancellationException

/**
 * Реализация порта прогона приватного квиза: коды двери — в типизированные отказы, сырые
 * ответы — строгим разбором в домен (AD-29, история 8.4).
 *
 * Транспортная неудача приходит [PrivateQuizDoorRefusal]'ом с пустым кодом или общей ошибкой —
 * она НЕ отказ двери: наверх едет [PrivateQuizEntryRefusal.UNAVAILABLE], и повтор входа с тем
 * же `startId` остаётся честным.
 */
class PrivateQuizRunRepositoryImpl(
    private val remote: PrivateQuizRunRemoteDataSource,
) : PrivateQuizRunRepository {

    override suspend fun start(
        ownerUid: String,
        catalogId: String,
        questId: String,
        startId: String,
    ): PrivateQuizEntry =
        try {
            val reply = remote.start(ownerUid, catalogId, questId, startId)
            // Дверь вернула запись старта; прогон забирается отдельным чтением, чтобы ответ
            // двери оставался лёгким. Отказ выдачи — тоже типизированный вход.
            val run = remote.fetchRun(ownerUid, catalogId, questId, startId)
            PrivateQuizEntry.Ready(PrivateQuizRunWireParser.run(run, startId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: PrivateQuizDoorRefusal) {
            PrivateQuizEntry.Refused(e.reason.toRefusal())
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Транспортная неудача (сеть, недоступность) — не отказ двери: повтор с тем же
            // startId остаётся честным, наверх едет UNAVAILABLE, а не вымышленная причина.
            PrivateQuizEntry.Refused(PrivateQuizEntryRefusal.UNAVAILABLE)
        }

    override suspend fun readOwnResult(
        ownerUid: String,
        catalogId: String,
        questId: String,
        startId: String,
    ): Result<PrivateQuizOwnResult> =
        try {
            val reply = remote.readResult(ownerUid, catalogId, questId, startId)
            Result.success(PrivateQuizRunWireParser.ownResult(reply, startId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: PrivateQuizDoorRefusal) {
            Result.failure(e)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Result.failure(e)
        }

    private fun String?.toRefusal(): PrivateQuizEntryRefusal =
        when (this) {
            "LINK_DISABLED" -> PrivateQuizEntryRefusal.LINK_DISABLED
            "LINK_EXPIRED" -> PrivateQuizEntryRefusal.LINK_EXPIRED
            "ATTEMPT_LIMIT_REACHED" -> PrivateQuizEntryRefusal.ATTEMPT_LIMIT_REACHED
            "NOT_FOUND" -> PrivateQuizEntryRefusal.NOT_FOUND
            "RUN_CONTENT_CHANGED" -> PrivateQuizEntryRefusal.RUN_CONTENT_CHANGED
            "PRIVATE_ATTEMPT_TERMINAL" -> PrivateQuizEntryRefusal.ATTEMPT_TERMINAL
            else -> PrivateQuizEntryRefusal.UNAVAILABLE
        }
}
