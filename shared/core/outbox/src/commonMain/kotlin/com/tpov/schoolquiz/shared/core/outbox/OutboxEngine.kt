package com.tpov.schoolquiz.shared.core.outbox

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.syncErrorOrNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Единственный транспорт отложенной мутации (AD-6).
 *
 * Прямая запись в Firestore из очереди запрещена: проверить ключ идемпотентности можно только
 * там, где выполняется код. Реализация — один callable-приёмник на сервере.
 */
fun interface MutationTransport {
    /** Отправляет запись. Неудача возвращается как `Result.failure`, а не бросается. */
    suspend fun send(record: OutboxRecord): Result<MutationResponse>
}

/**
 * Кому сообщать про карантин. Решение о локальной половине принимает фича, а не ядро (AD-28).
 *
 * Умолчания у этого параметра нет намеренно. Карантин терминален: запись больше не уйдёт, а
 * локальное изменение, сделанное вместе с ней одной транзакцией, осталось. Реализация, забывшая
 * реакцию, оставила бы ровно то молчаливое расхождение, ради запрета которого AD-28 написан, —
 * поэтому её нельзя забыть, её отсутствие не компилируется.
 */
fun interface QuarantineListener {
    suspend fun onQuarantined(record: OutboxRecord)
}

/**
 * Цикл отправки.
 *
 * Главное свойство — он не прерывается (AD-22). Сегодня одна отвергнутая попытка урока навсегда
 * глушит отправку всех оценок, потому что исключение уходит наверх до того, как до них дойдёт
 * очередь. Здесь отказ одной записи меняет только её собственное состояние.
 *
 * The explicit public DI/test constructor is preserved; grouping dependencies would change its API.
 */
class OutboxEngine
    @Suppress("LongParameterList")
    constructor(
        private val store: OutboxStore,
        private val transport: MutationTransport,
        private val policy: OutboxPolicy = OutboxPolicy(),
        private val clock: () -> Long,
        private val onQuarantined: QuarantineListener,
        private val receipts: MutationReceiptStore,
        private val sessions: OutboxSessionProvider,
        private val batchSize: Int = DEFAULT_BATCH,
        private val maxBatchesPerRun: Int = DEFAULT_MAX_BATCHES,
    ) {
        init {
            require(batchSize in 1..DEFAULT_BATCH) { "batchSize must be between 1 and 50" }
            require(maxBatchesPerRun in 1..DEFAULT_MAX_BATCHES) { "maxBatchesPerRun must be between 1 and 100" }
        }

        /**
         * Один проход за раз.
         *
         * Нужен смене аккаунта (AD-8): пока записи прежнего владельца вырезаются, плановый проход не
         * должен успеть отправить одну из них. Проход, пришедший вторым, ждёт, а не отменяется.
         */
        private val passes = Mutex()

        /**
         * Проходит по записям, до которых дошёл срок, и возвращает итог прохода.
         *
         * Наружу не бросает ничего: единственный способ остановить проход — отмена корутины, и она
         * проходит насквозь, потому что не ловится.
         */
        suspend fun drain(ownerUid: String): OutboxRunSummary = passes.withLock { drainLocked(ownerUid) }

        /**
         * Выполняет [block], пока ни один проход не идёт, и не пускает новый, пока он не кончится.
         *
         * Единственный потребитель — снятие следов прежнего владельца при смене аккаунта: удаление
         * записей и прогон очереди не должны пересекаться во времени.
         */
        suspend fun <T> withoutPasses(block: suspend () -> T): T = passes.withLock { block() }

        private suspend fun drainLocked(ownerUid: String): OutboxRunSummary {
            val now = clock()
            val session =
                sessions.current()?.takeIf { it.ownerUid == ownerUid && ownerUid.isNotBlank() }
                    ?: return OutboxRunSummary(0, 0, 0, 0, 0)
            val seen = mutableSetOf<Long>()
            var sent = 0
            var retried = 0
            var quarantined = 0
            var conflicted = 0
            // Ветвь последней записи, которая не уехала. Держится здесь, а не вычитывается потом из
            // очереди: тут она уже разобрана в тип (AD-15), а в таблице от неё остаётся текст, и
            // восстанавливать ветвь разбором этого текста запрещено.
            var lastError: SyncError? = null

            var batches = 0
            while (batches < maxBatchesPerRun) {
                currentCoroutineContext().ensureActive()
                val due = dueBatch(ownerUid, now, session, seen)
                if (due.isEmpty()) break
                batches++
                for (record in due) {
                    currentCoroutineContext().ensureActive()
                    if (sessions.current() != session) break
                    seen += record.id
                    val outcome = step(record, now, session)
                    outcome.error?.let { lastError = it }
                    when (outcome.step) {
                        Step.SENT -> sent++
                        Step.QUARANTINED -> quarantined++
                        Step.CONFLICTED -> conflicted++
                        Step.RETRY -> retried++
                    }
                }
            }

            return OutboxRunSummary(
                examined = seen.size,
                sent = sent,
                retried = retried,
                quarantined = quarantined,
                conflicted = conflicted,
                lastError = lastError,
            )
        }

        private suspend fun dueBatch(
            ownerUid: String,
            nowMs: Long,
            session: OutboxSession,
            seen: Set<Long>,
        ): List<OutboxRecord> =
            if (sessions.current() != session) {
                emptyList()
            } else {
                // A failed local receipt write may leave a due row unchanged. Widen the prefix to
                // look past all rows already examined, so it cannot hide the next page or spin.
                store.dueRecords(ownerUid, nowMs, seen.size + batchSize)
                    .filter { it.id !in seen }.take(batchSize)
            }

        /** Что стало с одной записью. */
        private suspend fun step(
            record: OutboxRecord,
            nowMs: Long,
            session: OutboxSession,
        ): StepOutcome {
            if (record.ownerUid != session.ownerUid) return StepOutcome(Step.RETRY, null)
            // After a crash between receipt persistence and queue removal, acknowledge the durable
            // response locally. No repeat network call, TTL check or feature rollback is necessary.
            val cached = readReceipt(record)
            val receipt = cached.getOrNull()
            return when {
                cached.isFailure -> StepOutcome(Step.RETRY, SyncError.Unknown(cached.exceptionOrNull()))
                sessions.current() != session -> StepOutcome(Step.RETRY, null)
                receipt != null -> acknowledge(record, receipt, session, persist = false)
                else -> processUnacknowledged(record, nowMs, session)
            }
        }

        // Receipt storage has no shared typed failure hierarchy. Isolate any local Exception from
        // transport retry/quarantine policy, but never turn coroutine cancellation into a retry.
        @Suppress("TooGenericExceptionCaught")
        private suspend fun readReceipt(record: OutboxRecord): Result<MutationReceipt?> =
            try {
                Result.success(receipts.find(record.ownerUid, record.mutationId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }

        private suspend fun processUnacknowledged(
            record: OutboxRecord,
            nowMs: Long,
            session: OutboxSession,
        ): StepOutcome =
            if (policy.isExpired(record, nowMs)) {
                // Перезревшую отправлять нельзя — срок хранения ключа на сервере истёк, и повтор был бы
                // для него новой операцией (AD-1). Но и лежать она права не имеет: пока возраст резал
                // выборку, такая запись не уезжала, в карантин не попадала и откат не звала — черновик
                // оставался заперт, а счётчик вечно показывал «ожидает». Здесь она доходит до карантина
                // тем же путём, что и любая другая, только без попытки.
                // Ветви ошибки здесь нет и быть не может: попытки не было, в карантин ведёт возраст.
                quarantine(record, policy.onExpired(record), error = null, session = session)
            } else {
                val outcome = transport.send(record)
                processTransportOutcome(record, outcome, session)
            }

        private suspend fun processTransportOutcome(
            record: OutboxRecord,
            outcome: Result<MutationResponse>,
            session: OutboxSession,
        ): StepOutcome =
            when {
                sessions.current() != session -> StepOutcome(Step.RETRY, null)
                outcome.isSuccess ->
                    acknowledge(
                        record,
                        MutationReceipt(
                            record.ownerUid,
                            record.mutationId,
                            record.operation,
                            record.entityRef,
                            outcome.getOrThrow().payloadJson,
                            clock(),
                        ),
                        session,
                        persist = true,
                    )
                else -> processTransportFailure(record, outcome.exceptionOrNull(), session)
            }

        private suspend fun processTransportFailure(
            record: OutboxRecord,
            failure: Throwable?,
            session: OutboxSession,
        ): StepOutcome {
            if (failure is CancellationException) throw failure
            val error = failure.syncErrorOrNull() ?: SyncError.Unknown(failure)
            val decision = policy.onFailure(record, error, clock())
            return if (decision.state == OutboxState.QUARANTINED) {
                quarantine(record, decision, error, session)
            } else {
                applyDecision(record, decision, error, session)
            }
        }

        private suspend fun applyDecision(
            record: OutboxRecord,
            decision: OutboxDecision,
            error: SyncError?,
            session: OutboxSession,
        ): StepOutcome =
            if (sessions.current() != session) {
                StepOutcome(Step.RETRY, null)
            } else {
                store.apply(record.id, decision)
                val step =
                    when (decision.state) {
                        OutboxState.QUARANTINED -> Step.QUARANTINED
                        OutboxState.CONFLICT -> Step.CONFLICTED
                        else -> Step.RETRY
                    }
                StepOutcome(step, error)
            }

        // The durable response must survive arbitrary local receipt/ack storage failures. Keep this
        // boundary catch separate from transport policy; CancellationException always propagates.
        @Suppress("TooGenericExceptionCaught")
        private suspend fun acknowledge(
            record: OutboxRecord,
            receipt: MutationReceipt,
            session: OutboxSession,
            persist: Boolean,
        ): StepOutcome =
            try {
                check(
                    receipt.ownerUid == record.ownerUid && receipt.mutationId == record.mutationId &&
                        receipt.operation == record.operation && receipt.entityRef == record.entityRef,
                ) {
                    "Receipt identity does not match the pending mutation"
                }
                if (sessions.current() != session) {
                    StepOutcome(Step.RETRY, null)
                } else {
                    if (persist) receipts.persist(receipt)
                    if (sessions.current() != session) {
                        StepOutcome(Step.RETRY, null)
                    } else {
                        store.remove(record.id)
                        StepOutcome(Step.SENT, null)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // The server effect has succeeded. Storage failure must never enter the transport's
                // max-attempt/age quarantine path, which would roll back an already settled action.
                StepOutcome(Step.RETRY, SyncError.Unknown(error))
            }

        /**
         * Объявляет карантин: сперва реакция фичи, и только её успех делает карантин записанным.
         *
         * Порядок здесь — половина смысла. Пометить запись карантинной первой значит вывести её из
         * любой будущей выборки: упади после этого реакция, второй попытки не будет никогда — запись
         * помечена, откат не сделан, локальное состояние разошлось с сервером молча, ровно против чего
         * написан AD-28. Обратный порядок стоит дешевле: пока карантин не записан, запись остаётся
         * видимой, и следующий проход повторит и её, и реакцию. Повтор отправки безопасен — ключ
         * идемпотентности не меняется (AD-2); потерянный откат восстановить нечем.
         *
         * Признак «реакция не выполнена» отдельной колонкой не хранится намеренно: она потребовала бы
         * миграции схемы ради состояния, которое уже выражено тем, что запись просто не помечена.
         *
         * Ядро не имеет права трогать таблицы фичи (AD-7), поэтому только сообщает.
         */
        private suspend fun quarantine(
            record: OutboxRecord,
            decision: OutboxDecision,
            error: SyncError?,
            session: OutboxSession,
        ): StepOutcome {
            if (sessions.current() != session) return StepOutcome(Step.RETRY, null)
            val announced =
                record.copy(
                    state = OutboxState.QUARANTINED,
                    attemptCount = decision.attemptCount,
                    lastError = decision.lastError,
                )
            val reaction = runCatching { onQuarantined.onQuarantined(announced) }
            val failure = reaction.exceptionOrNull()
            // Отмена — не отказ реакции: проход обязан оборваться целиком, а не оставить запись
            // с отложенным карантином.
            if (failure is CancellationException) throw failure
            // A callback can suspend across A -> B or A -> B -> A. Neither its successful return nor
            // failure authorizes a state write under the old generation; report no fake quarantine.
            val finalDecision =
                if (failure == null) {
                    decision
                } else {
                    policy.onQuarantineDeferred(record, decision, clock(), failure.describe(decision))
                }
            return applyDecision(record, finalDecision, error, session)
        }

        /** Причина, по которой карантин не удалось объявить, — вместе с той, по которой он решён. */
        private fun Throwable.describe(decision: OutboxDecision): String {
            val detail = message ?: this::class.simpleName ?: "Unknown"
            return "${decision.lastError ?: "Quarantined"}; quarantine handoff failed: $detail"
        }

        /** Исход одной записи за проход. */
        private enum class Step { SENT, QUARANTINED, CONFLICTED, RETRY }

        /**
         * Исход одной записи вместе с ветвью, по которой он получен.
         *
         * Ветвь едет отдельно от исхода, потому что один и тот же исход даёт разные ветви: в карантин
         * ведёт и отказ сервера, и переживший срок возраст, а у второго ветви нет вовсе.
         */
        private data class StepOutcome(
            val step: Step,
            val error: SyncError?,
        )

        private companion object {
            /** Page size, not the total number of records drained by one run. */
            const val DEFAULT_BATCH = 50
            const val DEFAULT_MAX_BATCHES = 100
        }
    }

/** Что случилось за один проход. Нужен планировщику, чтобы решить, звать ли снова. */
data class OutboxRunSummary(
    val examined: Int,
    val sent: Int,
    val retried: Int,
    val quarantined: Int,
    val conflicted: Int,
    /**
     * Ветвь последней записи, которая за этот проход не уехала, — или пусто, если не уехавших не
     * было (либо единственной причиной был возраст, у которого ветви нет).
     *
     * Тип, а не текст: показать игроку надо ветвь (AD-15), а разбирать её обратно из
     * [OutboxRecord.lastError] значило бы выводить решение из строки, собранной для журнала.
     */
    val lastError: SyncError? = null,
) {
    /**
     * Осталась ли работа: если проход сдвинул хоть одну запись, стоит зайти ещё раз за следующей
     * порцией. Карантин считается сдвигом наравне с отправкой — иначе очередь, целиком набитая
     * перезревшими записями, разбиралась бы по одной порции за расписание.
     */
    val hasMoreLikely: Boolean get() = (sent > 0 || quarantined > 0) && examined > 0
}
