package com.tpov.schoolquiz.shared.core.outbox

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * AD-8: слить очередь надо до переключения, а если слить не вышло — сказать словами.
 *
 * Сегодня этот путь ничем не обслужен, и записи прежнего владельца после смены аккаунта получают
 * отказ сервера вечно.
 */
class AccountSwitchGuardTest {

    private val uid = "uid-1"
    private val now = 30L * 24 * 60 * 60 * 1000

    private fun record(id: Long) =
        OutboxRecord(
            id = id,
            mutationId = "m-$id",
            ownerUid = uid,
            operation = "OP",
            payload = "{}",
            state = OutboxState.WAITING,
            createdAtMs = now,
        )

    private fun guard(
        store: FakeOutboxStore,
        send: suspend (OutboxRecord) -> Result<Unit>,
    ): AccountSwitchGuard {
        val engine =
            OutboxEngine(
                store = store,
                transport = { send(it) },
                clock = { now },
                onQuarantined = NoLocalEffect(),
            )
        return AccountSwitchGuard(engine, store)
    }

    @Test
    fun `given everything sends then the switch is clean`() = runTest {
        val store = FakeOutboxStore()
        store.seed(record(1), record(2))

        val readiness = guard(store) { Result.success(Unit) }.flushBefore(uid)

        assertTrue(readiness.isClean)
        assertFalse(readiness.needsWarning)
        assertEquals(0, readiness.unsent)
    }

    @Test
    fun `given the network is down then the player is warned with a number`() = runTest {
        // «Два действия могут не сохраниться» — это то, что игрок должен увидеть до переключения,
        // а не обнаружить потом.
        val store = FakeOutboxStore()
        store.seed(record(1), record(2))

        val readiness = guard(store) { Result.failure(SyncFailure(SyncError.NoNetwork)) }.flushBefore(uid)

        assertTrue(readiness.needsWarning)
        assertTrue(readiness.isKnown)
        assertEquals(2, readiness.unsent)
    }

    @Test
    fun `given a record was quarantined then it does not count as pending`() = runTest {
        // Карантинная запись не уедет никогда, но и предупреждать про неё как про «ждёт отправки»
        // нечестно: её судьбу разбирает фича (AD-28), а не смена аккаунта.
        val store = FakeOutboxStore()
        store.seed(record(1))

        val readiness = guard(store) { Result.failure(SyncFailure(SyncError.Refused("nope"))) }.flushBefore(uid)

        assertTrue(readiness.isClean)
    }

    @Test
    fun `given no account then there is nothing to flush`() = runTest {
        val readiness = guard(FakeOutboxStore()) { Result.success(Unit) }.flushBefore("")

        assertTrue(readiness.isClean)
    }

    @Test
    fun `given another account's records then they are not flushed and not counted`() = runTest {
        val store = FakeOutboxStore()
        store.seed(record(1).copy(ownerUid = "someone-else"))
        var attempts = 0

        val readiness = guard(store) { attempts++; Result.success(Unit) }.flushBefore(uid)

        assertEquals(0, attempts, "чужие записи не отправляются под этим аккаунтом")
        assertTrue(readiness.isClean)
    }
}
