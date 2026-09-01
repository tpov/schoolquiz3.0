package com.tpov.schoolquiz.shared.core.outbox

import com.tpov.schoolquiz.shared.core.network.SyncError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * AD-22 целиком: отказ одной записи не роняет остальные, вечного повтора не бывает, а в карантин
 * ведут ровно три дороги — порог попыток, отказ сервера и предельный возраст.
 */
class OutboxPolicyTest {

    private val limits = OutboxLimits()
    private val policy = OutboxPolicy(limits)
    // Заведомо больше предельного возраста записи, иначе «сейчас минус возраст» уходит в минус.
    private val now = 30L * 24 * 60 * 60 * 1000

    private fun record(
        state: OutboxState = OutboxState.WAITING,
        attempts: Int = 0,
        createdAtMs: Long = now,
        nextRetryAtMs: Long = 0L,
    ) = OutboxRecord(
        id = 1L,
        mutationId = "m-1",
        ownerUid = "uid-1",
        operation = "UNLOCK_LESSON",
        payload = "{}",
        state = state,
        createdAtMs = createdAtMs,
        attemptCount = attempts,
        nextRetryAtMs = nextRetryAtMs,
    )

    // ── Повторяемое ───────────────────────────────────────────────────────────

    @Test
    fun `given no network then wait and try again later`() {
        val decision = policy.onFailure(record(), SyncError.NoNetwork, now)

        assertEquals(OutboxState.WAITING, decision.state)
        assertEquals(1, decision.attemptCount)
        assertTrue(decision.nextRetryAtMs > now, "следующая попытка должна быть отложена")
    }

    @Test
    fun `given precondition not met then wait in its own state`() {
        // Отдельное состояние, чтобы наружу было видно, что это не обычное ожидание (AD-14).
        val decision = policy.onFailure(record(), SyncError.PreconditionNotMet, now)

        assertEquals(OutboxState.WAITING_PRECONDITION, decision.state)
        assertTrue(decision.state.isPending)
    }

    @Test
    fun `given repeated failures then the pause grows but stops at the ceiling`() {
        val first = policy.backoffMs(1)
        val second = policy.backoffMs(2)
        val far = policy.backoffMs(1000)

        assertTrue(second > first, "пауза растёт")
        assertEquals(limits.maxBackoffMs, far, "и упирается в потолок, а не в переполнение")
        assertTrue(far > 0, "потолок положительный")
    }

    // ── Три дороги в карантин ─────────────────────────────────────────────────

    @Test
    fun `given a server refusal then quarantine at once, without retrying`() {
        val decision = policy.onFailure(record(), SyncError.Refused("Not enough nolics"), now)

        assertEquals(OutboxState.QUARANTINED, decision.state)
        assertEquals("Not enough nolics", decision.lastError)
    }

    @Test
    fun `given the attempt limit is reached then quarantine`() {
        val decision = policy.onFailure(record(attempts = limits.maxAttempts - 1), SyncError.NoNetwork, now)

        assertEquals(OutboxState.QUARANTINED, decision.state)
    }

    @Test
    fun `given the record outlived its age then quarantine even while retryable`() {
        // Запись старше предельного возраста стала бы для сервера новой операцией — двойное
        // применение, ради предотвращения которого и существует ключ (AD-1).
        val old = record(createdAtMs = now - limits.maxAgeMs)

        assertEquals(OutboxState.QUARANTINED, policy.onFailure(old, SyncError.NoNetwork, now).state)
    }

    // ── Конфликт — не карантин ────────────────────────────────────────────────

    @Test
    fun `given a version conflict then it waits for a decision, not for a retry`() {
        val decision = policy.onFailure(record(), SyncError.VersionConflict(7L), now)

        assertEquals(OutboxState.CONFLICT, decision.state)
        assertFalse(decision.state.isTerminal, "по конфликту ещё будет решение игрока")
        assertFalse(decision.state.isPending, "но вслепую он не повторяется")
    }

    // ── Выборка ───────────────────────────────────────────────────────────────

    @Test
    fun `given the pause has not passed then the record is not taken`() {
        assertFalse(policy.isDue(record(nextRetryAtMs = now + 1), now))
        assertTrue(policy.isDue(record(nextRetryAtMs = now), now))
    }

    @Test
    fun `given quarantined or conflicted then never taken again`() {
        assertFalse(policy.isDue(record(state = OutboxState.QUARANTINED), now))
        assertFalse(policy.isDue(record(state = OutboxState.CONFLICT), now))
    }

    @Test
    fun `given a synchronous key then it is never taken by the engine`() {
        // Строка синхронной мутации хранит только ключ — отправлять там нечего (AD-2).
        assertFalse(policy.isDue(record(state = OutboxState.SYNCHRONOUS), now))
    }

    @Test
    fun `given an overaged record then it is not taken even before it fails once`() {
        val old = record(createdAtMs = now - limits.maxAgeMs)

        assertFalse(policy.isDue(old, now))
    }
}
