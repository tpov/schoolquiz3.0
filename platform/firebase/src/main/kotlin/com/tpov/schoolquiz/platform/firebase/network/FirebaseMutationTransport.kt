package com.tpov.schoolquiz.platform.firebase.network

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.MutationResponse
import com.tpov.schoolquiz.shared.core.outbox.MutationTransport
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import kotlinx.coroutines.tasks.await
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

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
    private val capturedTerms: CapturedTermsJson,
) : MutationTransport {
    override suspend fun send(record: OutboxRecord): Result<MutationResponse> {
        android.util.Log.w("MvpOutbox", "send guard isOnline=" + networkMonitor.isOnline())
        if (!networkMonitor.isOnline()) return Result.failure(SyncFailure(SyncError.NoNetwork))
        return try {
            val response =
                functions
                    .getHttpsCallable(SUBMIT_MUTATION)
                    .withAppTimeout()
                    .call(mutationEnvelope(record, capturedTerms.of(record.operation)))
                    .await()
            Result.success(MutationResponse(mutationResponseJson(response.data)))
        } catch (e: CancellationException) {
            // Отмена корутины — не неудача отправки, и подменять её ошибкой значит проглотить
            // остановку. Единственное, что отсюда бросается.
            throw e
        } catch (e: FirebaseFunctionsException) {
            android.util.Log.w("MvpOutbox", "mutation send functions code=" + e.code.name + " msg=" + e.message + " cause=" + e.cause)
            Result.failure(
                SyncFailure(
                    mutationErrorForCode(
                        record.operation,
                        e.code.name,
                        e.message,
                        e.details,
                        e.cause is IOException,
                    ),
                    e,
                ),
            )
        } catch (e: IOException) {
            android.util.Log.w("MvpOutbox", "mutation send IOException", e)
            Result.failure(SyncFailure(SyncError.NoNetwork, e))
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            android.util.Log.w("MvpOutbox", "mutation send generic", e)
            // Ловим широко намеренно. Контракт транспорта — «неудача возвращается, а не
            // бросается», потому что решение о повторе принимает цикл, а не вызывающий (AD-22).
            // Ловя два конкретных типа, транспорт нарушал собственный контракт: любое третье
            // исключение — а SDK бросает и IllegalState, и IllegalArgument, и ошибки сериализации
            // — вылетало наверх и обрывало проход, унося с собой и соседние записи.
            Result.failure(SyncFailure(e.toSyncError(), e))
        }
    }

    /**
     * Тело запроса.
     *
     * `payload` едет строкой, как и лежит в очереди: ядро в него не смотрит, а разбирает его
     * обработчик на сервере — одна таблица на все типы действий (AD-5) тем и держится.
     */
    private companion object {
        const val SUBMIT_MUTATION = "submitMutation"
    }
}

/**
 * Конверт мутации — чистая сборка без SDK: [OutboxRecord] плюс согласие с платными условиями,
 * если для этой операции оно есть.
 *
 * `capturedTerms` едет строкой — сервер читает обе формы (объект и строку) одной трактовкой с
 * `parseMutationPayload`, а согласие у клиента и так хранится строкой настроек. Отсутствие поля —
 * не пропуск, а честное «согласия нет»: сервер ответит `terms-consent-required`, и запись
 * переедет в ветвь ожидания согласия, вместо того чтобы притвориться согласованной.
 */
internal fun mutationEnvelope(
    record: OutboxRecord,
    capturedTermsJson: String?,
): Map<String, Any?> =
    buildMap {
        put(KEY_MUTATION_ID, record.mutationId)
        put(KEY_OPERATION, record.operation)
        put(KEY_PAYLOAD, record.payload)
        record.expectedVersion?.let { put(KEY_EXPECTED_VERSION, it) }
        capturedTermsJson?.let { put(KEY_CAPTURED_TERMS, it) }
    }

private const val KEY_MUTATION_ID = "mutationId"
private const val KEY_OPERATION = "operation"
private const val KEY_PAYLOAD = "payload"
private const val KEY_EXPECTED_VERSION = "expectedVersion"
private const val KEY_CAPTURED_TERMS = "capturedTerms"

/**
 * Захваченное согласие с платными условиями одной операции — строкой для конверта.
 *
 * Реализация знает реестр операций с релевантными условиями (`OutboxOperations
 * .termsConsentOperations`) и хранилище согласия; транспорт — нет: он не разбирает ни смысл
 * операций, ни смысл строк, которые возит (AD-7).
 */
fun interface CapturedTermsJson {
    /** JSON согласия — или `null`, когда для операции согласия нет и не должно быть. */
    fun of(operation: String): String?
}

/** A permission/deployment/auth problem must not silently roll back a held financial attempt.
 * Persistent failures require explicit user cancellation/recovery; only structurally invalid
 * payloads are terminal. The distinction is the SDK code, never the error message text.
 */
internal fun mutationErrorForCode(
    operation: String,
    code: String,
    message: String?,
    details: Any?,
    causedByIo: Boolean = false,
): SyncError {
    // Реплей арена-settle: сервер ответил already-exists с reason SETTLEMENT_REUSE —
    // результат попытки уже зафиксирован сохранённым settlement'ом (тот же attemptId,
    // другой отпечаток тела — например, ручная доставка поверх осевшего результата).
    // Эффект уже на сервере: запись очереди ACK-ается движком, а не крутится как «неизвестная».
    val reuseReason = (details as? Map<*, *>)?.get("reason") as? String
    if (code == "ALREADY_EXISTS" && operation == OutboxOperations.SUBMIT_ATTEMPT &&
        reuseReason == SETTLEMENT_REUSE_REASON
    ) {
        return SyncError.SettlementAlreadyRecorded()
    }
    val mapped = syncErrorForCode(code, message, details, causedByIo)
    val protectedRefusal = operation in OutboxOperations.permanentReceiptOperations && mapped is SyncError.Refused
    val malformedPayload = code == "INVALID_ARGUMENT" || code == "OUT_OF_RANGE"
    return if (protectedRefusal && !malformedPayload) {
        SyncError.Unknown()
    } else {
        mapped
    }
}
