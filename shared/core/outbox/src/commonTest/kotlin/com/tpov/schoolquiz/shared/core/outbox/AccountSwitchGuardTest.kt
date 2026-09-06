package com.tpov.schoolquiz.shared.core.outbox

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

/**
 * AD-8: слить очередь надо до переключения, а если слить не вышло — сказать словами. После
 * переключения следы прежнего владельца снимаются двумя операциями с закрытым перечнем каждая.
 *
 * Сегодня этот путь ничем не обслужен, и записи прежнего владельца после смены аккаунта получают
 * отказ сервера вечно.
 */
class AccountSwitchGuardTest {

    private val uid = "uid-1"
    private val other = "someone-else"
    private val now = 30L * 24 * 60 * 60 * 1000

    /**
     * Что снято вне очереди — и что нет.
     *
     * [left] — личные строки, лежащие на устройстве: кто их оставил и что именно потеряет. Пустая
     * карта значит «вне очереди чисто», а не «спрашивать не у кого».
     */
    private class RecordingTraces(
        override val namedLosses: List<OwnerDataKind> = emptyList(),
        private val left: Map<String, List<OwnerDataKind>> = emptyMap(),
    ) : OwnerTraces {
        val forgotten = mutableListOf<String>()

        override suspend fun forget(uid: String) {
            forgotten += uid
        }

        override suspend fun ownersWithTraces(): List<String> = left.keys.toList()

        override suspend fun lossesOf(uid: String): List<OwnerDataKind> = left[uid].orEmpty()
    }

    /** Следы без перечня — когда тесту важен только порядок вызовов. */
    private fun traces(onForget: (String) -> Unit) =
        object : OwnerTraces {
            override val namedLosses: List<OwnerDataKind> = emptyList()

            override suspend fun forget(uid: String) = onForget(uid)

            override suspend fun ownersWithTraces(): List<String> = emptyList()

            override suspend fun lossesOf(uid: String): List<OwnerDataKind> = emptyList()
        }

    private fun record(
        id: Long,
        owner: String = uid,
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

    private fun engine(
        store: FakeOutboxStore,
        send: suspend (OutboxRecord) -> Result<Unit>,
    ) = OutboxEngine(
        store = store,
        transport = { send(it) },
        clock = { now },
        onQuarantined = NoLocalEffect(),
    )

    private fun guard(
        store: FakeOutboxStore,
        traces: OwnerTraces = RecordingTraces(),
        send: suspend (OutboxRecord) -> Result<Unit>,
    ): AccountSwitchGuard = AccountSwitchGuard(engine(store, send), store, store, traces)

    @Test
    fun `given the database does not answer then the owner who is leaving is still remembered`() = runTest {
        // Кто уходит — известно точно, он пришёл параметром. Пустой набор здесь означал бы
        // «забывать некого», и после входа его uid уже не узнать: следы остались бы навсегда.
        val store = FakeOutboxStore()
        val blind =
            object : OwnerTraces {
                override val namedLosses: List<OwnerDataKind> = emptyList()

                override suspend fun forget(uid: String) = Unit

                override suspend fun ownersWithTraces(): List<String> = error("база не ответила")

                override suspend fun lossesOf(uid: String): List<OwnerDataKind> = emptyList()
            }

        val readiness = guard(store, traces = blind) { Result.success(Unit) }.flushBefore(uid)

        assertFalse(readiness.isKnown, "сосчитать не удалось — игрока предупреждают")
        assertTrue(readiness.needsWarning)
        assertEquals(listOf(uid), readiness.owners, "уходящий владелец остаётся в наборе на уборку")
    }

    @Test
    fun `given everything sends then the switch is clean`() = runTest {
        val store = FakeOutboxStore()
        store.seed(record(1), record(2))

        val readiness = guard(store) { Result.success(Unit) }.flushBefore(uid)

        assertTrue(readiness.isClean)
        assertFalse(readiness.needsWarning)
        assertEquals(0, readiness.remainder.total)
        assertEquals(listOf(uid), readiness.owners, "снять следы всё равно есть у кого")
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
        assertEquals(2, readiness.remainder.waiting)
        assertEquals(2, readiness.remainder.total)
    }

    @Test
    fun `given only a conflict is left then the player is still asked`() = runTest {
        // Конфликтная запись — правка игрока, ждущая его же решения (AD-24). Пока остаток считался
        // выборкой «что поедет», такой игрок входил в чужой аккаунт без единого вопроса, и его
        // правка исчезала вместе с возможностью её разрешить.
        val store = FakeOutboxStore()
        store.seed(record(1, state = OutboxState.CONFLICT))

        val readiness = guard(store) { Result.success(Unit) }.flushBefore(uid)

        assertTrue(readiness.needsWarning, "AC 6.3 перечисляет КОНФЛИКТ наравне с ЖДЁТ")
        assertEquals(1, readiness.remainder.conflicted)
    }

    @Test
    fun `given every state is left then all five are counted, each on its own`() = runTest {
        // Удаляются все пять состояний, значит и считаться должны все пять — иначе согласие
        // получено не на то, что удаляется. Считается без слива, чтобы числа были ровно теми,
        // какими их положили: прогон сам двигает записи между состояниями.
        val store = FakeOutboxStore()
        store.seed(
            record(1, owner = other, state = OutboxState.WAITING),
            record(2, owner = other, state = OutboxState.WAITING),
            record(3, owner = other, state = OutboxState.WAITING_PRECONDITION),
            record(4, owner = other, state = OutboxState.CONFLICT),
            record(5, owner = other, state = OutboxState.QUARANTINED),
            record(6, owner = other, state = OutboxState.SYNCHRONOUS),
        )

        val readiness = guard(store) { Result.success(Unit) }.flushBefore("")

        assertEquals(
            OutboxRemainder(
                waiting = 2,
                waitingPrecondition = 1,
                conflicted = 1,
                quarantined = 1,
                synchronous = 1,
            ),
            readiness.remainder,
        )
        assertTrue(readiness.needsWarning)
    }

    @Test
    fun `given a record was quarantined by the flush then the player is warned about it`() = runTest {
        // Карантинная запись не уедет никогда, но исчезнет вместе с аккаунтом — и это ровно то, о
        // чём предупреждение обязано сказать своим словом (AC 6.3).
        val store = FakeOutboxStore()
        store.seed(record(1))

        val readiness = guard(store) { Result.failure(SyncFailure(SyncError.Refused("nope"))) }.flushBefore(uid)

        assertTrue(readiness.needsWarning)
        assertEquals(1, readiness.remainder.quarantined)
    }

    @Test
    fun `given no session but rows of the previous owner then the queue names him and the player is asked`() = runTest {
        // `auth.currentUser == null` — не «терять нечего»: сессия слетела, а строки прежнего
        // владельца лежат на месте. Кто он, знает очередь.
        val store = FakeOutboxStore()
        store.seed(record(1, owner = other), record(2, owner = other, state = OutboxState.QUARANTINED))

        val readiness = guard(store) { Result.success(Unit) }.flushBefore("")

        assertTrue(readiness.needsWarning, "AC 6.3 называет этот случай явно")
        assertEquals(listOf(other), readiness.owners)
        assertEquals(2, readiness.remainder.total)
    }

    @Test
    fun `given no session and an empty queue then there is nothing to ask about`() = runTest {
        val readiness = guard(FakeOutboxStore()) { Result.success(Unit) }.flushBefore("")

        assertTrue(readiness.isClean)
        assertTrue(readiness.owners.isEmpty())
    }

    @Test
    fun `given no session then nothing is sent under nobody`() = runTest {
        val store = FakeOutboxStore()
        store.seed(record(1, owner = other))
        var attempts = 0

        guard(store) {
            attempts++
            Result.success(Unit)
        }.flushBefore("")

        assertEquals(0, attempts, "сливать под несуществующей сессией некуда")
    }

    @Test
    fun `given another account's records then they are not flushed`() = runTest {
        val store = FakeOutboxStore()
        store.seed(record(1, owner = other))
        var attempts = 0

        val readiness =
            guard(store) {
                attempts++
                Result.success(Unit)
            }.flushBefore(uid)

        assertEquals(0, attempts, "чужие записи не отправляются под этим аккаунтом")
        assertEquals(listOf(uid, other), readiness.owners, "забыть предстоит обоих: чужие строки тоже лежат здесь")
        assertEquals(1, readiness.remainder.waiting, "и про них игрока предупреждают")
        assertTrue(readiness.needsWarning)
    }

    @Test
    fun `given the named data list then the warning carries it`() = runTest {
        val store = FakeOutboxStore()
        store.seed(record(1))
        val named = listOf(OwnerDataKind.ANSWER_HISTORY, OwnerDataKind.UNPUBLISHED_QUEST_DRAFTS)

        val readiness =
            guard(store, RecordingTraces(left = mapOf(uid to named))) {
                Result.failure(SyncFailure(SyncError.NoNetwork))
            }.flushBefore(uid)

        assertEquals(named, readiness.namedLosses, "имена данных — из перечня второй операции")
    }

    // ── Следы вне очереди решают наравне с очередью ─────────────────────────────────────────

    @Test
    fun `given an empty queue but local rows of the previous owner then he is named and the player is asked`() =
        runTest {
            // Пустая очередь — обычное стационарное состояние: запись уехала и исчезла, а черновик
            // остался лежать. Пока набор владельцев спрашивался у одной очереди, такой игрок менял
            // аккаунт без вопроса и без уборки — строки прежнего оставались на устройстве навсегда.
            val store = FakeOutboxStore()
            val traces = RecordingTraces(left = mapOf(other to listOf(OwnerDataKind.UNPUBLISHED_QUEST_DRAFTS)))

            val readiness = guard(store, traces) { Result.success(Unit) }.flushBefore("")

            assertTrue(readiness.needsWarning, "черновики прежнего владельца сервер не хранит")
            assertEquals(listOf(other), readiness.owners, "забыть его есть кому — и без единой записи очереди")
            assertEquals(listOf(OwnerDataKind.UNPUBLISHED_QUEST_DRAFTS), readiness.namedLosses)
        }

    @Test
    fun `given a live session and an empty queue but an answer history then the player is still asked`() = runTest {
        // Очередь пуста, а история ответов и расписание повторов исчезнут вместе с аккаунтом:
        // сервер их не хранит. Молчаливое переключение стирало их без согласия.
        val store = FakeOutboxStore()
        val traces =
            RecordingTraces(
                left = mapOf(uid to listOf(OwnerDataKind.ANSWER_HISTORY, OwnerDataKind.REPETITION_SCHEDULE)),
            )

        val readiness = guard(store, traces) { Result.success(Unit) }.flushBefore(uid)

        assertFalse(readiness.isClean, "терять есть что, даже когда очередь пуста")
        assertTrue(readiness.needsWarning)
        assertEquals(0, readiness.remainder.total, "числа очереди тут ни при чём")
        assertEquals(
            listOf(OwnerDataKind.ANSWER_HISTORY, OwnerDataKind.REPETITION_SCHEDULE),
            readiness.namedLosses,
        )
    }

    @Test
    fun `given rows with a server twin only then nothing is asked but the owner is still cleaned up`() = runTest {
        // Профиль и статистика вернутся после входа: спрашивать не о чем. Но снять их всё равно
        // надо — иначе чужие строки останутся, и читающий проход попросит у сервера чужие документы.
        val store = FakeOutboxStore()
        val traces = RecordingTraces(left = mapOf(other to emptyList()))

        val readiness = guard(store, traces) { Result.success(Unit) }.flushBefore("")

        assertTrue(readiness.isClean, "терять нечего — вопроса нет")
        assertEquals(listOf(other), readiness.owners, "но забыть его всё равно есть кому")
    }

    @Test
    fun `given the player is asked about local rows and does not confirm then nothing is removed`() = runTest {
        // Слив — не снятие. Отказ игрока это просто отсутствие `forgetAfterSwitch`, и до него
        // никакие строки не трогаются, даже когда очередь пуста и вопрос вызван только следами.
        val store = FakeOutboxStore()
        val traces = RecordingTraces(left = mapOf(other to listOf(OwnerDataKind.ANSWER_HISTORY)))

        val readiness = guard(store, traces) { Result.success(Unit) }.flushBefore("")

        assertTrue(readiness.needsWarning, "вопрос вызван одними следами — записей очереди нет вовсе")
        assertTrue(traces.forgotten.isEmpty(), "до согласия личные строки не трогаются")
        assertEquals(listOf(other), readiness.owners, "снять их будет у кого — но только после ответа")
    }

    @Test
    fun `given a third owner left over from an earlier switch then he is forgotten with this one`() = runTest {
        // Снятие следов могло не удаться — база не ответила, — и на устройстве остался третий
        // владелец. Пока набор считался как «текущий uid и только он», другого случая забыть его
        // уже не было.
        val store = FakeOutboxStore()
        val traces = RecordingTraces(left = mapOf("uid-0" to listOf(OwnerDataKind.ANSWER_HISTORY)))

        val readiness = guard(store, traces) { Result.success(Unit) }.flushBefore(uid)

        assertEquals(listOf(uid, "uid-0"), readiness.owners)
        assertTrue(readiness.needsWarning)
    }

    // ── После переключения: две операции, каждая с закрытым перечнем ────────────────────────

    @Test
    fun `given the switch happened then every record of the previous owner is gone, in any state`() = runTest {
        val store = FakeOutboxStore()
        val states = OutboxState.entries
        states.forEachIndexed { i, state -> store.seed(record(i + 1L, state = state)) }
        store.seed(record(100, owner = other), record(101, owner = other, state = OutboxState.QUARANTINED))
        val traces = RecordingTraces()

        guard(store, traces) { Result.success(Unit) }.forgetAfterSwitch(listOf(uid))

        assertTrue(store.all.none { it.ownerUid == uid }, "у прежнего владельца не осталось ни одной строки")
        assertEquals(listOf(100L, 101L), store.all.map { it.id }, "строки другого владельца целы, включая карантин")
        assertEquals(listOf(uid), traces.forgotten, "личные следы сняты ровно у прежнего владельца")
    }

    @Test
    fun `given the switch happened then the queue is cut before the traces outside it`() = runTest {
        // Порядок закреплён историей 6.4: очередь, потом личные следы.
        val order = mutableListOf<String>()
        val store =
            object : FakeOutboxStore() {
                override suspend fun forget(ownerUid: String) {
                    order += "queue"
                    super.forget(ownerUid)
                }
            }
        store.seed(record(1))

        guard(store, traces { order += "traces" }) { Result.success(Unit) }.forgetAfterSwitch(listOf(uid))

        assertEquals(listOf("queue", "traces"), order)
    }

    @Test
    fun `given the flush alone then nothing is removed and the traces are untouched`() = runTest {
        // Слив — не снятие: до согласия игрока не удаляется ничего, даже если ничего не уехало.
        val store = FakeOutboxStore()
        store.seed(record(1), record(2))
        val traces = RecordingTraces()

        guard(store, traces) { Result.failure(SyncFailure(SyncError.NoNetwork)) }.flushBefore(uid)

        assertEquals(2, store.all.size)
        assertTrue(traces.forgotten.isEmpty())
    }

    @Test
    fun `given no previous account then there is nothing to forget`() = runTest {
        val store = FakeOutboxStore()
        store.seed(record(1, owner = other))
        val traces = RecordingTraces()

        guard(store, traces) { Result.success(Unit) }.forgetAfterSwitch(listOf(""))

        assertEquals(1, store.all.size)
        assertTrue(traces.forgotten.isEmpty())
    }

    @Test
    fun `given a pass is due while the traces are being removed then the transport is not called until it is done`() =
        runTest {
            // Прогон и удаление не пересекаются: между началом удаления и его концом транспорт молчит,
            // а проход, дождавшийся конца, уже не находит записей прежнего владельца.
            val store = FakeOutboxStore()
            store.seed(record(1), record(2))
            val tracesStarted = CompletableDeferred<Unit>()
            val letTracesFinish = CompletableDeferred<Unit>()
            var sentDuringRemoval = 0
            var removing = false
            val engine =
                engine(store) {
                    if (removing) sentDuringRemoval++
                    Result.success(Unit)
                }
            val slowTraces =
                object : OwnerTraces {
                    override val namedLosses: List<OwnerDataKind> = emptyList()

                    override suspend fun ownersWithTraces(): List<String> = emptyList()

                    override suspend fun lossesOf(uid: String): List<OwnerDataKind> = emptyList()

                    override suspend fun forget(uid: String) {
                        removing = true
                        tracesStarted.complete(Unit)
                        letTracesFinish.await()
                        removing = false
                    }
                }
            val guard = AccountSwitchGuard(engine, store, store, slowTraces)

            coroutineScope {
                val removal = async { guard.forgetAfterSwitch(listOf(uid)) }
                tracesStarted.await()
                val pass = async { engine.drain(uid) }
                yield()
                letTracesFinish.complete(Unit)
                removal.await()
                val summary = pass.await()
                assertEquals(0, sentDuringRemoval, "пока следы снимаются, транспорт не зовётся")
                assertEquals(0, summary.examined, "проход после снятия не находит записей прежнего владельца")
            }
        }
}
