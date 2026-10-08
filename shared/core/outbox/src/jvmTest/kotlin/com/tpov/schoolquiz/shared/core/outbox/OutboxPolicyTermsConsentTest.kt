package com.tpov.schoolquiz.shared.core.outbox

import com.tpov.schoolquiz.shared.core.network.SyncError
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Отказ по согласию с платными условиями в политике очереди: ожидание решения игрока, а не
 * карантин и не сгорание попыток (CAP-16, история 2.12).
 *
 * Карантин по AD-28 откатывает локальную половину — здесь это написанная попытка урока. Вечный
 * повтор без согласия вернул бы тот же отказ. Обе двери закрыты: ветвь едет в CONFLICT, чьё
 * состояние берегёт счётчик и паузу, как конфликт версий (AD-24).
 */
class OutboxPolicyTermsConsentTest {

    private val policy = OutboxPolicy()

    private fun refusedAttempt(attemptCount: Int = 0) =
        OutboxRecord(
            id = 1L,
            mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, "attempt-1"),
            ownerUid = "uid-1",
            operation = OutboxOperations.SUBMIT_ATTEMPT,
            payload = "{}",
            state = OutboxState.WAITING,
            createdAtMs = 1_000L,
            attemptCount = attemptCount,
            nextRetryAtMs = 2_000L,
        )

    @Test
    fun `given the terms refusal then the record parks as conflict`() {
        val decision = policy.onFailure(refusedAttempt(), SyncError.TermsConsentRequired, nowMs = 3_000L)

        assertEquals(OutboxState.CONFLICT, decision.state)
    }

    @Test
    fun `given the terms refusal then the attempt counter and pause stay untouched`() {
        val decision = policy.onFailure(refusedAttempt(attemptCount = 2), SyncError.TermsConsentRequired, nowMs = 3_000L)

        // Согласие — не неудачная попытка: сгорание счётчика ведёт к карантину, а карантин к откату.
        assertEquals(2, decision.attemptCount)
        assertEquals(2_000L, decision.nextRetryAtMs)
    }

    @Test
    fun `given the terms refusal then it is not terminal`() {
        assertEquals(false, OutboxState.CONFLICT.isTerminal)
    }
}
