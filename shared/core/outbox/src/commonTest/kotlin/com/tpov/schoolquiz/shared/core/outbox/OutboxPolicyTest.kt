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

    @Test
    fun `given a version conflict then the server version is carried into the record`() {
        val decision = policy.onFailure(record(), SyncError.VersionConflict(7L), now)

        assertEquals(7L, decision.serverVersion, "разрешать конфликт нечем без числа (AD-24)")
        assertEquals(
            "VersionConflict",
            decision.lastError,
            "в причине ветвь, а не текст сервера (AD-15)",
        )
    }

    @Test
    fun `given a version conflict then attempts and the pause stay untouched`() {
        // Счётчик попыток и пауза ведут в карантин, а карантин по AD-28 откатывает локальное
        // изменение. Расхождение версий ждёт решения игрока, а не уничтожения его работы: конфликт
        // это не неудачная попытка, мутация доехала и была понята.
        val queued = record(attempts = 2, nextRetryAtMs = now + 5_000)

        val decision = policy.onFailure(queued, SyncError.VersionConflict(7L), now)

        assertEquals(2, decision.attemptCount)
        assertEquals(now + 5_000, decision.nextRetryAtMs)
    }

    @Test
    fun `given a conflict without a number then the previously known version survives`() {
        // Сервер назвал конфликт, но версию не прислал: затирать уже известное число пустотой
        // значит терять единственное, чем автор может разрешить расхождение.
        val known = record(state = OutboxState.CONFLICT, attempts = 1).copy(serverVersion = 7L)

        val decision = policy.onFailure(known, SyncError.VersionConflict(null), now)

        assertEquals(7L, decision.serverVersion)
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
    fun `given an overaged record then it is still taken into the run, but not sent`() {
        // Возраст режет не выборку, а отправку. Пока он резал выборку, правило стояло в двух
        // местах сразу и пересечения у них не было: перезревшую запись не выбирали, а значит и
        // решения по ней не принимали — она оставалась невидимой навсегда.
        val old = record(createdAtMs = now - limits.maxAgeMs)

        assertTrue(policy.isDue(old, now), "проход обязан её увидеть")
        assertTrue(policy.isExpired(old, now), "и понять, что отправлять её нельзя")
        assertEquals(OutboxState.QUARANTINED, policy.onExpired(old).state)
        assertEquals(OutboxPolicy.EXPIRED_REASON, policy.onExpired(old).lastError)
        assertEquals(old.attemptCount, policy.onExpired(old).attemptCount, "попытки не было")
    }

    @Test
    fun `given a fresh record then it is not expired`() {
        assertFalse(policy.isExpired(record(), now))
    }

    // ── Карантин, объявить который не удалось ─────────────────────────────────

    @Test
    fun `given the quarantine reaction failed then the record stays in the run with a pause`() {
        // Помеченную карантином запись не выберет ни один следующий проход. Пока реакция фичи не
        // выполнена, помечать нельзя — иначе откат теряется молча (AD-28).
        val decision = policy.onFailure(record(state = OutboxState.WAITING_PRECONDITION), SyncError.Refused("no"), now)

        val deferred =
            policy.onQuarantineDeferred(
                record = record(state = OutboxState.WAITING_PRECONDITION),
                decision = decision,
                nowMs = now,
                reason = "no; quarantine handoff failed: boom",
            )

        assertTrue(deferred.state.isPending, "запись осталась в выборке")
        assertEquals(OutboxState.WAITING_PRECONDITION, deferred.state, "и не потеряла своё состояние")
        assertTrue(deferred.nextRetryAtMs > now, "но не крутится вплотную")
        assertEquals(decision.attemptCount, deferred.attemptCount)
        assertEquals("no; quarantine handoff failed: boom", deferred.lastError, "причина видна наружу")
    }
}
