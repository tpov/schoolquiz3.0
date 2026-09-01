package com.tpov.schoolquiz.shared.core.outbox

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.syncErrorOrNull
import kotlin.coroutines.cancellation.CancellationException

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
            when (step(record, now)) {
                Step.SENT -> sent++
                Step.QUARANTINED -> quarantined++
                Step.CONFLICTED -> conflicted++
                Step.RETRY -> retried++
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

    /** Что стало с одной записью. */
    private suspend fun step(
        record: OutboxRecord,
        nowMs: Long,
    ): Step {
        if (policy.isExpired(record, nowMs)) {
            // Перезревшую отправлять нельзя — срок хранения ключа на сервере истёк, и повтор был бы
            // для него новой операцией (AD-1). Но и лежать она права не имеет: пока возраст резал
            // выборку, такая запись не уезжала, в карантин не попадала и откат не звала — черновик
            // оставался заперт, а счётчик вечно показывал «ожидает». Здесь она доходит до карантина
            // тем же путём, что и любая другая, только без попытки.
            return quarantine(record, policy.onExpired(record))
        }

        val outcome = transport.send(record)
        if (outcome.isSuccess) {
            store.remove(record.id)
            return Step.SENT
        }

        val error = outcome.exceptionOrNull().syncErrorOrNull() ?: SyncError.Unknown(outcome.exceptionOrNull())
        val decision = policy.onFailure(record, error, clock())
        if (decision.state == OutboxState.QUARANTINED) return quarantine(record, decision)

        store.apply(record.id, decision)
        return if (decision.state == OutboxState.CONFLICT) Step.CONFLICTED else Step.RETRY
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
    ): Step {
        val announced =
            record.copy(
                state = OutboxState.QUARANTINED,
                attemptCount = decision.attemptCount,
                lastError = decision.lastError,
            )
        val reaction = runCatching { onQuarantined.onQuarantined(announced) }
        val failure = reaction.exceptionOrNull()
        if (failure != null) {
            // Отмена — не отказ реакции: проход обязан оборваться целиком, а не оставить запись
            // с отложенным карантином.
            if (failure is CancellationException) throw failure
            store.apply(record.id, policy.onQuarantineDeferred(record, decision, clock(), failure.describe(decision)))
            // Считается как повтор: карантин не объявлен, но запись жива и будет разобрана снова.
            return Step.RETRY
        }
        store.apply(record.id, decision)
        return Step.QUARANTINED
    }

    /** Причина, по которой карантин не удалось объявить, — вместе с той, по которой он решён. */
    private fun Throwable.describe(decision: OutboxDecision): String {
        val detail = message ?: this::class.simpleName ?: "Unknown"
        return "${decision.lastError ?: "Quarantined"}; quarantine handoff failed: $detail"
    }

    /** Исход одной записи за проход. */
    private enum class Step { SENT, QUARANTINED, CONFLICTED, RETRY }

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
    /**
     * Осталась ли работа: если проход сдвинул хоть одну запись, стоит зайти ещё раз за следующей
     * порцией. Карантин считается сдвигом наравне с отправкой — иначе очередь, целиком набитая
     * перезревшими записями, разбиралась бы по одной порции за расписание.
     */
    val hasMoreLikely: Boolean get() = (sent > 0 || quarantined > 0) && examined > 0
}
