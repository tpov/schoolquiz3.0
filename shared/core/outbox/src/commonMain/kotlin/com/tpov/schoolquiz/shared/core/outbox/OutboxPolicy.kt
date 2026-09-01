package com.tpov.schoolquiz.shared.core.outbox

import com.tpov.schoolquiz.shared.core.network.SyncError

/**
 * Что делать с записью после неудачной попытки — чистое решение, без хранилища и без часов.
 *
 * Здесь живёт правило AD-22: отказ одной записи не роняет остальные, вечного повтора не бывает,
 * и в карантин можно попасть тремя разными путями. Всё это — арифметика над ошибкой, числом
 * попыток и возрастом, поэтому проверяется без Room и без сети.
 */
class OutboxPolicy(
    private val limits: OutboxLimits = OutboxLimits(),
) {
    /**
     * Решение по записи, получившей [error] в момент [nowMs].
     *
     * Возраст считается от момента, когда игрок совершил действие, а не от последней попытки:
     * запись, пережившая срок хранения ключей на сервере, стала бы для него новой операцией —
     * ровно то двойное применение, ради которого ключ и существует (AD-1).
     */
    fun onFailure(
        record: OutboxRecord,
        error: SyncError,
        nowMs: Long,
    ): OutboxDecision {
        val attempt = record.attemptCount + 1
        val reason = error.describe()

        if (error.disposition == SyncError.Disposition.CONFLICT) {
            // Конфликт — не неудачная попытка, а ветвь (AD-24): мутация доехала, сервер её понял и
            // назвал свою версию. Счётчик попыток и пауза остаются нетронутыми намеренно — они
            // ведут к карантину, а карантин по AD-28 откатывает локальное изменение. Расхождение
            // версий ждёт решения игрока, а не уничтожения его работы.
            return OutboxDecision(
                state = OutboxState.CONFLICT,
                nextRetryAtMs = record.nextRetryAtMs,
                attemptCount = record.attemptCount,
                lastError = reason,
                serverVersion = (error as? SyncError.VersionConflict)?.serverVersion ?: record.serverVersion,
            )
        }
        if (error.disposition == SyncError.Disposition.QUARANTINE) {
            return OutboxDecision(
                state = OutboxState.QUARANTINED,
                nextRetryAtMs = 0L,
                attemptCount = attempt,
                lastError = reason,
                serverVersion = record.serverVersion,
            )
        }
        if (attempt >= limits.maxAttempts || isExpired(record, nowMs)) {
            return OutboxDecision(
                state = OutboxState.QUARANTINED,
                nextRetryAtMs = 0L,
                attemptCount = attempt,
                lastError = reason,
                serverVersion = record.serverVersion,
            )
        }

        val waiting =
            if (error == SyncError.PreconditionNotMet) OutboxState.WAITING_PRECONDITION else OutboxState.WAITING
        return OutboxDecision(
            state = waiting,
            nextRetryAtMs = nowMs + backoffMs(attempt),
            attemptCount = attempt,
            lastError = reason,
            serverVersion = record.serverVersion,
        )
    }

    /**
     * Берётся ли запись в проход. Ядро выборки, поэтому правило одно и здесь.
     *
     * Возраст здесь **не** проверяется намеренно. Пока проверялся, правило возраста стояло в двух
     * местах сразу — в выборке и в [onFailure], — и пересечения у них не было: перезревшую запись
     * выборка не отдавала, а [onFailure] по ней никто не звал, потому что звать его можно только по
     * выбранной. Запись, пролежавшая офлайн дольше предельного возраста, оставалась невидимой
     * навсегда: не уезжала, в карантин не попадала, откат не звала. Поэтому возраст решается ровно
     * в одном месте — [isExpired] на стороне движка, — а выборка отдаёт всё, что он обязан
     * рассмотреть.
     */
    fun isDue(
        record: OutboxRecord,
        nowMs: Long,
    ): Boolean = record.state.isPending && record.nextRetryAtMs <= nowMs

    /**
     * Пережила ли запись предельный возраст.
     *
     * Отправлять такую нельзя: срок хранения ключа на сервере истёк, и повтор был бы для него новой
     * операцией — ровно то двойное применение, ради которого ключ и существует (AD-1).
     */
    fun isExpired(
        record: OutboxRecord,
        nowMs: Long,
    ): Boolean = nowMs - record.createdAtMs >= limits.maxAgeMs

    /**
     * Решение по перезревшей записи: карантин без единой попытки отправки.
     *
     * Счётчик попыток не растёт — попытки не было. В карантин ведёт возраст, и причина названа
     * словом, а не пустотой, чтобы фича и игрок видели, за что.
     */
    fun onExpired(record: OutboxRecord): OutboxDecision =
        OutboxDecision(
            state = OutboxState.QUARANTINED,
            nextRetryAtMs = 0L,
            attemptCount = record.attemptCount,
            lastError = EXPIRED_REASON,
            serverVersion = record.serverVersion,
        )

    /**
     * Решение по записи, у которой карантин уже решён, но реакция владеющей фичи не выполнена.
     *
     * Записать карантин в этом случае нельзя: помеченную запись не выберет ни один следующий
     * проход, и откат локальной половины, ради которого написан AD-28, не случится уже никогда.
     * Поэтому запись остаётся в выборке — с причиной в [OutboxDecision.lastError] и паузой, чтобы
     * падающая реакция не крутилась вплотную. Повторная отправка на сервер при этом безопасна:
     * ключ идемпотентности не меняется (AD-2), а вот потерянный откат восстановить нечем.
     */
    fun onQuarantineDeferred(
        record: OutboxRecord,
        decision: OutboxDecision,
        nowMs: Long,
        reason: String,
    ): OutboxDecision =
        OutboxDecision(
            state = if (record.state.isPending) record.state else OutboxState.WAITING,
            nextRetryAtMs = nowMs + backoffMs(decision.attemptCount),
            attemptCount = decision.attemptCount,
            lastError = reason,
            serverVersion = record.serverVersion,
        )

    /**
     * Пауза перед следующей попыткой: удвоение от базовой, но не больше потолка.
     *
     * Без потолка две недели офлайна дали бы паузу длиннее самой записи.
     */
    fun backoffMs(attempt: Int): Long {
        if (attempt <= 1) return limits.baseBackoffMs
        val shift = (attempt - 1).coerceAtMost(MAX_DOUBLINGS)
        val grown = limits.baseBackoffMs shl shift
        return if (grown <= 0L) limits.maxBackoffMs else minOf(grown, limits.maxBackoffMs)
    }

    private fun SyncError.describe(): String =
        when (this) {
            is SyncError.Refused -> reason
            is SyncError.Unknown -> throwable?.message ?: "Unknown"
            else -> this::class.simpleName ?: "Unknown"
        }

    companion object {
        /** Причина карантина по возрасту. Отличима от отказа сервера и от порога попыток. */
        const val EXPIRED_REASON: String = "Expired"

        /** Дальше сдвигать бессмысленно: потолок всё равно ниже, а сдвиг переполняет Long. */
        private const val MAX_DOUBLINGS = 16
    }
}

/**
 * Числа, решённые владельцем 2026-09-01.
 *
 * [maxAgeMs] — не настройка клиента: это половина пары, объявленной в `config/sync-params.json`,
 * и вторая половина (срок хранения ключей на сервере) обязана быть строго больше (AD-1). Значение
 * продублировано здесь ради общего кода, который файлов не читает; совпадение с файлом стережёт
 * `SyncParamsContractTest` — разъедутся, и сборка упадёт.
 */
data class OutboxLimits(
    val maxAttempts: Int = 5,
    val baseBackoffMs: Long = 1_000L,
    val maxBackoffMs: Long = 60L * 60 * 1000,
    val maxAgeMs: Long = 7L * 24 * 60 * 60 * 1000,
) {
    init {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        require(baseBackoffMs > 0) { "baseBackoffMs must be positive" }
        require(maxBackoffMs >= baseBackoffMs) { "maxBackoffMs must not be below baseBackoffMs" }
        require(maxAgeMs > 0) { "maxAgeMs must be positive" }
    }
}

/**
 * Новое состояние записи после попытки.
 *
 * [serverVersion] едет здесь, а не выводится хранилищем: решение принимается в одном месте, и
 * запись обязана унести версию, названную сервером, целой через перезапуск (AD-24).
 */
data class OutboxDecision(
    val state: OutboxState,
    val nextRetryAtMs: Long,
    val attemptCount: Int,
    val lastError: String?,
    val serverVersion: Long? = null,
)
