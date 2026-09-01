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
            return OutboxDecision(OutboxState.CONFLICT, nextRetryAtMs = 0L, attemptCount = attempt, lastError = reason)
        }
        if (error.disposition == SyncError.Disposition.QUARANTINE) {
            return OutboxDecision(OutboxState.QUARANTINED, nextRetryAtMs = 0L, attemptCount = attempt, lastError = reason)
        }
        if (attempt >= limits.maxAttempts || isTooOld(record, nowMs)) {
            return OutboxDecision(OutboxState.QUARANTINED, nextRetryAtMs = 0L, attemptCount = attempt, lastError = reason)
        }

        val waiting =
            if (error == SyncError.PreconditionNotMet) OutboxState.WAITING_PRECONDITION else OutboxState.WAITING
        return OutboxDecision(
            state = waiting,
            nextRetryAtMs = nowMs + backoffMs(attempt),
            attemptCount = attempt,
            lastError = reason,
        )
    }

    /** Пора ли пробовать эту запись. Ядро выборки, поэтому правило одно и здесь. */
    fun isDue(
        record: OutboxRecord,
        nowMs: Long,
    ): Boolean = record.state.isPending && record.nextRetryAtMs <= nowMs && !isTooOld(record, nowMs)

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

    private fun isTooOld(
        record: OutboxRecord,
        nowMs: Long,
    ): Boolean = nowMs - record.createdAtMs >= limits.maxAgeMs

    private fun SyncError.describe(): String =
        when (this) {
            is SyncError.Refused -> reason
            is SyncError.Unknown -> throwable?.message ?: "Unknown"
            else -> this::class.simpleName ?: "Unknown"
        }

    private companion object {
        /** Дальше сдвигать бессмысленно: потолок всё равно ниже, а сдвиг переполняет Long. */
        const val MAX_DOUBLINGS = 16
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

/** Новое состояние записи после попытки. */
data class OutboxDecision(
    val state: OutboxState,
    val nextRetryAtMs: Long,
    val attemptCount: Int,
    val lastError: String?,
)
