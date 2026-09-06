package com.tpov.schoolquiz.platform.firebase.auth

import com.tpov.schoolquiz.platform.firebase.push.DeviceTokenRelease
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.AccountSwitchGuard
import com.tpov.schoolquiz.shared.core.outbox.NoLocalEffect
import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.outbox.OutboxDecision
import com.tpov.schoolquiz.shared.core.outbox.OutboxEngine
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxPolicy
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxRemainder
import com.tpov.schoolquiz.shared.core.outbox.OutboxRemainderSource
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.core.outbox.OwnerDataKind
import com.tpov.schoolquiz.shared.core.outbox.OwnerTraces
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.AccountDataKind
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.GoogleLinkOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Порядок шагов смены аккаунта (AD-8, истории 6.3 и 6.4), снятый с Firebase и проверенный на фейках.
 *
 * Слив — до входа. Остаток — вопрос, а не вход. Согласие — токен снят, вход, и только после
 * удавшегося входа следы прежнего владельца: записи очереди, затем личные следы. Отказ и
 * неудавшийся вход не удаляют ничего.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountSwitchTest {

    private val previous = "uid-A"
    private val arrived = "uid-B"

    /** Часы теста двигаются: неудачная попытка ставит паузу повтора, и её надо пережить. */
    private var now = 30L * 24 * 60 * 60 * 1000

    /** Всё, что случилось, в том порядке, в каком случилось. */
    private val log = mutableListOf<String>()

    private val store = InMemoryOutboxStore(onForget = { log += "forget-queue:$it" })
    private var transport: suspend (OutboxRecord) -> Result<Unit> = { Result.success(Unit) }
    private val engine =
        OutboxEngine(
            store = store,
            transport = { record ->
                log += "send:${record.mutationId}"
                transport(record)
            },
            clock = { now },
            onQuarantined = NoLocalEffect(),
        )
    /**
     * Личные следы вне очереди: кто их оставил и что именно потеряет.
     *
     * [left] пуста по умолчанию — очередь тогда решает одна, как и было. Заполненная описывает
     * обычное стационарное состояние: записи уехали и исчезли, а черновики остались лежать.
     */
    private val left = mutableMapOf<String, List<OwnerDataKind>>()
    private val traces =
        object : OwnerTraces {
            override val namedLosses = listOf(OwnerDataKind.ANSWER_HISTORY, OwnerDataKind.UNPUBLISHED_QUEST_DRAFTS)

            override suspend fun forget(uid: String) {
                log += "forget-traces:$uid"
                left -= uid
            }

            override suspend fun ownersWithTraces(): List<String> = left.keys.toList()

            override suspend fun lossesOf(uid: String): List<OwnerDataKind> = left[uid].orEmpty()
        }
    private val tokens =
        object : DeviceTokenRelease {
            override suspend fun release(uid: String) {
                log += "release:$uid"
            }

            override suspend fun reclaim(uid: String) {
                log += "reclaim:$uid"
            }
        }

    /**
     * Переключение живёт в своём scope — том, что в приложении принадлежит процессу.
     *
     * В тесте это отдельный scope на том же планировщике: он и позволяет проверить, что уход с
     * экрана переключение не отменяет.
     */
    private val switchScope = TestScope(UnconfinedTestDispatcher())
    private val switch = AccountSwitch(AccountSwitchGuard(engine, store, store, traces), tokens, switchScope)

    private fun signIn(outcome: Result<Unit> = Result.success(Unit)): suspend () -> String =
        {
            log += "signIn"
            outcome.getOrThrow()
            arrived
        }

    private fun record(
        id: Long,
        owner: String = previous,
        state: OutboxState = OutboxState.WAITING,
    ) = OutboxRecord(
        id = id,
        mutationId = "m-$id",
        ownerUid = owner,
        operation = "OP",
        payload = "{}",
        state = state,
        createdAtMs = now,
    )

    @Test
    fun `given the queue drains then the switch completes at once and the traces go after the sign-in`() = runTest {
        store.seed(record(1))

        val outcome = switch.begin(previous, signIn())

        assertEquals(GoogleLinkOutcome.Switched, outcome)
        assertEquals(
            listOf("send:m-1", "release:$previous", "signIn", "forget-queue:$previous", "forget-traces:$previous"),
            log,
        )
        assertFalse(switch.hasPending)
    }

    @Test
    fun `given something stays unsent then nothing is signed in or removed and the question carries the numbers`() =
        runTest {
            store.seed(record(1), record(2))
            // Имена приходят не постоянным списком, а тем, что у этого владельца действительно
            // лежит: согласие на потерю несуществующей истории ответов — согласие не на то.
            left[previous] = listOf(OwnerDataKind.ANSWER_HISTORY, OwnerDataKind.UNPUBLISHED_QUEST_DRAFTS)
            transport = { Result.failure(SyncFailure(SyncError.NoNetwork)) }

            val outcome = switch.begin(previous, signIn())

            val loss = (outcome as GoogleLinkOutcome.SwitchAwaitsConsent).loss
            assertEquals(2, loss.waiting)
            assertEquals(2, loss.total)
            assertTrue(loss.isKnown)
            assertEquals(
                listOf(AccountDataKind.ANSWER_HISTORY, AccountDataKind.UNPUBLISHED_QUEST_DRAFTS),
                loss.namedData,
            )
            assertEquals("слив был, входа и удаления — нет", listOf("send:m-1", "send:m-2"), log)
            assertEquals(2, store.countPending(previous))
            assertTrue(switch.hasPending)
        }

    @Test
    fun `given only a conflict is left then the switch still stops and asks`() = runTest {
        // Пока остаток считался выборкой «что поедет», такой игрок входил в чужой аккаунт без
        // единого вопроса, а его конфликтная правка исчезала молча.
        store.seed(record(1, state = OutboxState.CONFLICT))

        val outcome = switch.begin(previous, signIn())

        val loss = (outcome as GoogleLinkOutcome.SwitchAwaitsConsent).loss
        assertEquals(1, loss.conflicted)
        assertTrue(log.none { it == "signIn" })
        assertTrue(switch.hasPending)
    }

    @Test
    fun `given no session but rows of the previous owner then the switch asks and later forgets him`() = runTest {
        // `auth.currentUser == null` приходит сюда пустой строкой. Прежний владелец при этом есть —
        // его знает очередь, и его строки обязаны быть сняты после входа.
        store.seed(record(1, state = OutboxState.QUARANTINED))

        val outcome = switch.begin("", signIn())

        assertEquals(1, (outcome as GoogleLinkOutcome.SwitchAwaitsConsent).loss.quarantined)
        assertTrue("без сессии сливать некому", log.isEmpty())

        switch.confirm()

        assertEquals(
            listOf("release:$previous", "signIn", "forget-queue:$previous", "forget-traces:$previous"),
            log,
        )
    }

    @Test
    fun `given the player declines then the previous account stays and nothing is touched`() = runTest {
        store.seed(record(1))
        transport = { Result.failure(SyncFailure(SyncError.NoNetwork)) }
        switch.begin(previous, signIn())
        log.clear()

        switch.decline()

        assertTrue(log.isEmpty())
        assertEquals(1, store.countPending(previous))
        assertFalse(switch.hasPending)
        // И очередь продолжает обрабатываться под прежним владельцем — как только пройдёт пауза,
        // которую поставила неудачная попытка: отказ игрока запись не тронул, она просто ждёт.
        transport = { Result.success(Unit) }
        now += OutboxPolicy().backoffMs(1) + 1
        val summary = engine.drain(previous)
        assertEquals(1, summary.sent)
    }

    @Test
    fun `given the player confirms then the order is sign-in, queue, traces`() = runTest {
        store.seed(record(1))
        transport = { Result.failure(SyncFailure(SyncError.NoNetwork)) }
        switch.begin(previous, signIn())
        log.clear()

        val outcome = switch.confirm()

        assertEquals(GoogleLinkOutcome.Switched, outcome)
        assertEquals(listOf("release:$previous", "signIn", "forget-queue:$previous", "forget-traces:$previous"), log)
        assertEquals(0, store.countPending(previous))
        assertFalse(switch.hasPending)
    }

    @Test
    fun `given the arrival is the same account then nothing of his is removed`() = runTest {
        // Вход в тот самый аккаунт, чьи строки лежат в очереди, — не потеря, а возвращение.
        store.seed(record(1, owner = arrived))
        transport = { Result.failure(SyncFailure(SyncError.NoNetwork)) }
        switch.begin(arrived, signIn())
        log.clear()

        switch.confirm()

        assertTrue("свои же строки не удаляются", log.none { it.startsWith("forget-") })
        assertEquals(1, store.countPending(arrived))
    }

    @Test
    fun `given the screen goes away right after the confirmation then the switch still finishes`() = runTest {
        // Экран профиля живёт до первого ухода с него, а между входом и снятием следов лежит сеть.
        // Отменённое ожидание не отменяет уже начатый вход Firebase — и не должно оставлять
        // аккаунт сменённым, а строки прежнего владельца на месте.
        store.seed(record(1))
        transport = { Result.failure(SyncFailure(SyncError.NoNetwork)) }
        val networkIsSlow = CompletableDeferred<Unit>()
        switch.begin(previous) {
            log += "signIn"
            networkIsSlow.await()
            arrived
        }
        log.clear()

        val screen = launch { switch.confirm() }
        advanceUntilIdle()
        screen.cancel()
        networkIsSlow.complete(Unit)
        switchScope.advanceUntilIdle()

        assertEquals(
            listOf("release:$previous", "signIn", "forget-queue:$previous", "forget-traces:$previous"),
            log,
        )
        assertEquals(0, store.countPending(previous))
    }

    @Test
    fun `given the player confirmed but the sign-in failed then nothing is removed and the token comes back`() =
        runTest {
            store.seed(record(1))
            transport = { Result.failure(SyncFailure(SyncError.NoNetwork)) }
            switch.begin(previous, signInFailing())
            log.clear()

            val failure = runCatching { switch.confirm() }.exceptionOrNull()

            assertTrue(failure is SyncFailure)
            assertEquals(listOf("release:$previous", "signIn", "reclaim:$previous"), log)
            assertEquals(1, store.countPending(previous))
            assertFalse("вопрос снят: второе нажатие не должно входить по тем же учётным данным", switch.hasPending)
        }

    @Test
    fun `given nothing is pending then confirm refuses`() = runTest {
        val failure = runCatching { switch.confirm() }.exceptionOrNull()

        // Без ожидающего переключения подтверждать нечего — это ошибка вызывающего, не игрока.
        assertTrue(failure is IllegalStateException)
        assertTrue(log.isEmpty())
    }

    @Test
    fun `given confirm is called twice then the second one has nothing to do`() = runTest {
        store.seed(record(1))
        transport = { Result.failure(SyncFailure(SyncError.NoNetwork)) }
        switch.begin(previous, signIn())
        switch.confirm()
        log.clear()

        val second = runCatching { switch.confirm() }

        assertTrue(second.isFailure)
        assertTrue(log.isEmpty())
    }

    @Test
    fun `given no previous account and an empty queue then nothing is flushed and nothing is removed`() = runTest {
        val outcome = switch.begin("", signIn())

        assertEquals(GoogleLinkOutcome.Switched, outcome)
        assertEquals("забывать некого — и токен снимать не с кого", listOf("signIn"), log)
    }

    @Test
    fun `given an empty queue but local drafts of the previous owner then the switch asks and later forgets him`() =
        runTest {
            // Ровно исходный симптом: запись уехала и исчезла, черновик остался в REVIEW_SENT, и
            // переключение проходило без вопроса и без уборки. Чужие строки оставались на
            // устройстве, а читающий проход просил у сервера чужие документы на каждом прогоне.
            left[previous] = listOf(OwnerDataKind.UNPUBLISHED_QUEST_DRAFTS)

            val outcome = switch.begin("", signIn())

            assertTrue(outcome is GoogleLinkOutcome.SwitchAwaitsConsent)
            val loss = (outcome as GoogleLinkOutcome.SwitchAwaitsConsent).loss
            assertEquals(listOf(AccountDataKind.UNPUBLISHED_QUEST_DRAFTS), loss.namedData)
            assertTrue("до ответа не тронуто ничего", log.isEmpty())

            switch.confirm()

            assertEquals(
                listOf("release:$previous", "signIn", "forget-queue:$previous", "forget-traces:$previous"),
                log,
            )
        }

    @Test
    fun `given local rows and a decline then the previous owner keeps everything`() = runTest {
        left[previous] = listOf(OwnerDataKind.ANSWER_HISTORY)

        switch.begin("", signIn())
        switch.decline()

        assertFalse("вопрос снят вместе с удерживаемым входом", switch.hasPending)
        assertTrue("отказ не удаляет ничего", log.isEmpty())
        assertEquals(listOf(OwnerDataKind.ANSWER_HISTORY), left[previous])
    }

    private fun signInFailing(): suspend () -> String = signIn(Result.failure(SyncFailure(SyncError.NoNetwork)))

    @Test
    fun `given a clean queue and a failing sign-in then the traces stay`() = runTest {
        store.seed(record(1))

        val failure = runCatching { switch.begin(previous, signInFailing()) }.exceptionOrNull()

        assertTrue(failure is SyncFailure)
        assertEquals(listOf("send:m-1", "release:$previous", "signIn", "reclaim:$previous"), log)
    }
}

/**
 * Очередь в памяти ровно в том объёме, который нужен этим тестам.
 *
 * Своя, потому что фейк из `shared/core/outbox` живёт в `commonTest` того модуля и сюда не
 * доезжает.
 */
private class InMemoryOutboxStore(
    private val onForget: (String) -> Unit,
    private val policy: OutboxPolicy = OutboxPolicy(),
) : OutboxStore, OutboxRemainderSource {
    private val records = MutableStateFlow<List<OutboxRecord>>(emptyList())
    private var nextId = 1L

    fun seed(vararg record: OutboxRecord) {
        records.value = records.value + record
    }

    override suspend fun enqueue(record: OutboxRecord): OutboxRecord {
        val stored = record.copy(id = nextId++)
        records.value = records.value + stored
        return stored
    }

    override suspend fun dueRecords(
        ownerUid: String,
        nowMs: Long,
        limit: Int,
    ): List<OutboxRecord> =
        records.value.filter { it.ownerUid == ownerUid && policy.isDue(it, nowMs) }.take(limit)

    override suspend fun apply(
        id: Long,
        decision: OutboxDecision,
    ) {
        records.value =
            records.value.map {
                if (it.id != id) {
                    it
                } else {
                    it.copy(
                        state = decision.state,
                        nextRetryAtMs = decision.nextRetryAtMs,
                        attemptCount = decision.attemptCount,
                        lastError = decision.lastError,
                        serverVersion = decision.serverVersion,
                    )
                }
            }
    }

    override suspend fun remove(id: Long) {
        records.value = records.value.filterNot { it.id == id }
    }

    override fun observeCounts(ownerUid: String): Flow<OutboxCounts> = records.map { OutboxCounts() }

    override fun observeEntity(
        ownerUid: String,
        entityRef: String,
    ): Flow<OutboxEntitySync> = records.map { OutboxEntitySync.None }

    override suspend fun findByEntityRef(
        ownerUid: String,
        entityRef: String,
    ): OutboxRecord? = null

    override suspend fun quarantined(ownerUid: String): List<OutboxRecord> =
        records.value.filter { it.ownerUid == ownerUid && it.state == OutboxState.QUARANTINED }

    override suspend fun countPending(ownerUid: String): Int =
        records.value.count { it.ownerUid == ownerUid && it.state.isPending }

    override suspend fun remainderOf(ownerUid: String): OutboxRemainder {
        val mine = records.value.filter { it.ownerUid == ownerUid }
        return OutboxRemainder(
            waiting = mine.count { it.state == OutboxState.WAITING },
            waitingPrecondition = mine.count { it.state == OutboxState.WAITING_PRECONDITION },
            conflicted = mine.count { it.state == OutboxState.CONFLICT },
            quarantined = mine.count { it.state == OutboxState.QUARANTINED },
            synchronous = mine.count { it.state == OutboxState.SYNCHRONOUS },
        )
    }

    override suspend fun ownersWithRecords(): List<String> = records.value.map { it.ownerUid }.distinct()

    override suspend fun forget(ownerUid: String) {
        onForget(ownerUid)
        records.value = records.value.filterNot { it.ownerUid == ownerUid }
    }
}
