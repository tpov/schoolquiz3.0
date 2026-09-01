package com.tpov.schoolquiz.platform.firebase.network

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.MutationTransport
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import kotlinx.coroutines.tasks.await
import java.io.IOException

/**
 * Отправка отложенной мутации — через единственный приёмник (AD-6).
 *
 * Прямая запись в Firestore отсюда запрещена: ключ идемпотентности проверяется только там, где
 * выполняется код. Поэтому у очереди ровно один адрес, и новая операция появляется добавлением
 * обработчика на сервере, а не нового пути на клиенте.
 *
 * Неудача не бросается, а возвращается: цикл отправки не должен прерываться на одной записи
 * (AD-22), поэтому решение о повторе принимает он, по типу ошибки.
 */
class FirebaseMutationTransport(
    private val functions: FirebaseFunctions,
    private val networkMonitor: NetworkMonitor,
) : MutationTransport {
    override suspend fun send(record: OutboxRecord): Result<Unit> {
        if (!networkMonitor.isOnline()) return Result.failure(SyncFailure(SyncError.NoNetwork))
        return try {
            functions
                .getHttpsCallable(SUBMIT_MUTATION)
                .withAppTimeout()
                .call(record.toPayload())
                .await()
            Result.success(Unit)
        } catch (e: FirebaseFunctionsException) {
            Result.failure(SyncFailure(e.toSyncError(), e))
        } catch (e: IOException) {
            Result.failure(SyncFailure(SyncError.NoNetwork, e))
        }
    }

    /**
     * Тело запроса.
     *
     * `payload` едет строкой, как и лежит в очереди: ядро в него не смотрит, а разбирает его
     * обработчик на сервере — одна таблица на все типы действий (AD-5) тем и держится.
     */
    private fun OutboxRecord.toPayload(): Map<String, Any?> =
        buildMap {
            put(MUTATION_ID, mutationId)
            put(OPERATION, operation)
            put(PAYLOAD, payload)
            expectedVersion?.let { put(EXPECTED_VERSION, it) }
        }

    private companion object {
        const val SUBMIT_MUTATION = "submitMutation"
        const val MUTATION_ID = "mutationId"
        const val OPERATION = "operation"
        const val PAYLOAD = "payload"
        const val EXPECTED_VERSION = "expectedVersion"
    }
}
