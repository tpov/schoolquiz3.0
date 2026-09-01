package com.tpov.schoolquiz.shared.core.outbox

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.syncErrorOrNull

/**
 * Единственный транспорт отложенной мутации (AD-6).
 *
 * Прямая запись в Firestore из очереди запрещена: проверить ключ идемпотентности можно только
 * там, где выполняется код. Реализация — один callable-приёмник на сервере.
 */
fun interface MutationTransport {
    /** Отправляет запись. Неудача возвращается как `Result.failure`, а не бросается. */
    suspend fun send(record: OutboxRecord): Result<Unit>
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
 */
class OutboxEngine(
    private val store: OutboxStore,
    private val transport: MutationTransport,
    private val policy: OutboxPolicy = OutboxPolicy(),
    private val clock: () -> Long,
    private val onQuarantined: QuarantineListener,
    private val batchSize: Int = DEFAULT_BATCH,
) {
    /**
     * Проходит по записям, до которых дошёл срок, и возвращает итог прохода.
     *
     * Наружу не бросает ничего: единственный способ остановить проход — отмена корутины, и она
     * проходит насквозь, потому что не ловится.
     */
    suspend fun drain(ownerUid: String): OutboxRunSummary {
        val now = clock()
        val due = store.dueRecords(ownerUid, now, batchSize)
        var sent = 0
        var retried = 0
        var quarantined = 0
        var conflicted = 0

        for (record in due) {
            val outcome = transport.send(record)
            if (outcome.isSuccess) {
                store.remove(record.id)
                sent++
                continue
            }

            val error = outcome.exceptionOrNull().syncErrorOrNull() ?: SyncError.Unknown(outcome.exceptionOrNull())
            val decision = policy.onFailure(record, error, clock())
            store.apply(record.id, decision)

            when (decision.state) {
                OutboxState.QUARANTINED -> {
                    quarantined++
                    // Ядро не имеет права трогать таблицы фичи (AD-7), поэтому только сообщает.
                    onQuarantined.onQuarantined(record.copy(state = decision.state, lastError = decision.lastError))
                }
                OutboxState.CONFLICT -> conflicted++
                else -> retried++
            }
        }

        return OutboxRunSummary(
            examined = due.size,
            sent = sent,
            retried = retried,
            quarantined = quarantined,
            conflicted = conflicted,
        )
    }

    private companion object {
        /** Сколько записей за один проход. Больше — дольше держим соединение без пользы. */
        const val DEFAULT_BATCH = 50
    }
}

/** Что случилось за один проход. Нужен планировщику, чтобы решить, звать ли снова. */
data class OutboxRunSummary(
    val examined: Int,
    val sent: Int,
    val retried: Int,
    val quarantined: Int,
    val conflicted: Int,
) {
    /** Осталась ли работа: если что-то ушло, стоит зайти ещё раз за следующей порцией. */
    val hasMoreLikely: Boolean get() = sent > 0 && examined > 0
}
