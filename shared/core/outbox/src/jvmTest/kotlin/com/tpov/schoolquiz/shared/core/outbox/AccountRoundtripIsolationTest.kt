package com.tpov.schoolquiz.shared.core.outbox

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Доказательство истории 2.3 (account-roundtrip-isolation, CAP-19).
 *
 * Круг A→B→A и новая сессия того же uid сохраняют владение: ни чужих действий в очереди, ни
 * приватных данных, ни двойного расчёта.
 *
 * Три механизма разбираются по частям и одним сквозным прогоном:
 * 1. **Guard** ([AccountSwitchGuard.flushBefore] + [AccountSwitchGuard.switchAndForget]): слив
 *    до переключения, вопрос про остаток, и снятие следов прежнего владельца — записей очереди
 *    (все пять состояний) и личных строк — строго после удавшегося входа и под одним замком с
 *    проходом движка. Возвращение к себе (`signIn` вернул прежний uid) ничего из его не удаляет:
 *    это не потеря, а приход владельца (AD-8).
 * 2. **Смена эпохи** (`OutboxSession.generation`, AD-8/ONLINE-O2 §5): успех, вернувшийся под
 *    старой сессией, не признаётся — квитанция не пишется, запись не выдаётся за отправленную.
 *    Новая сессия того же uid дочитывает очередь под тем же ключом идемпотентности (AD-2), так
 *    что эффект считается сервером ровно один раз.
 * 3. **Owner-гейты движка**: проход без сессии, под чужой сессией и по чужой строке, затесавшейся
 *    в выборку, не зовёт транспорт ни разу — запись отправляет только её владелец.
 *
 * Пишется против общего кода: общий [OutboxEngine], [AccountSwitchGuard], общий fake-сторон
 * [FakeOutboxStore] из commonTest (виден отсюда через иерархию исходников KMP).
 */
class AccountRoundtripIsolationTest {
    private val uidA = "uid-A"
    private val uidB = "uid-B"
    private val now = 30L * 24 * 60 * 60 * 1000

    /** Действующая сессия;signIn'ы и транспорт двигают её, как это делает FirebaseOutboxSessionProvider. */
    private var session: OutboxSession? = null

    /** Исход отправки по ключу мутации; не названный — успех с авторитетным расчётом. */
    private val outcomes = mutableMapOf<String, Result<Unit>>()
    private val sendLog = mutableListOf<String>()
    private val receipts = CountingReceipts()

    /** Порядок уборки: `queue:<uid>` — записи, `traces:<uid>` — личные следы. */
    private val cleanUpLog = mutableListOf<String>()
    private val tracesLeft = mutableMapOf<String, List<OwnerDataKind>>()

    /** Авторитетный расчёт, который возвращает fake-транспорт — единственный источник успеха. */
    private val authoritativePayload = """{"settlement":"authoritative"}"""

    private class CountingReceipts : FakeMutationReceiptStore() {
        val persisted = mutableListOf<MutationReceipt>()

        override suspend fun persist(receipt: MutationReceipt) {
            val known = find(receipt.ownerUid, receipt.mutationId) != null
            super.persist(receipt)
            if (!known) persisted += receipt
        }
    }

    /** Личные следы вне очереди: кто их оставил и что именно потеряет. */
    private class RoundtripTraces(
        private val left: MutableMap<String, List<OwnerDataKind>>,
        private val log: MutableList<String>,
    ) : OwnerTraces {
        override val namedLosses: List<OwnerDataKind> =
            listOf(OwnerDataKind.ANSWER_HISTORY, OwnerDataKind.UNPUBLISHED_QUEST_DRAFTS)

        override suspend fun forget(uid: String) {
            left -= uid
            log += "traces:$uid"
        }

        override suspend fun ownersWithTraces(): List<String> = left.keys.toList()

        override suspend fun lossesOf(uid: String): List<OwnerDataKind> = left[uid].orEmpty()
    }

    private open class LoggingStore(
        private val log: MutableList<String>,
    ) : FakeOutboxStore() {
        override suspend fun forget(ownerUid: String) {
            log += "queue:$ownerUid"
            super.forget(ownerUid)
        }
    }

    private var nextId = 1L

    private fun attempt(
        sourceId: String,
        owner: String = uidA,
    ) = OutboxRecord(
        id = nextId++,
        mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, sourceId),
        ownerUid = owner,
        operation = OutboxOperations.SUBMIT_ATTEMPT,
        payload = """{"attemptId":"$sourceId"}""",
        state = OutboxState.WAITING,
        createdAtMs = now,
        entityRef = "lesson_runner:$sourceId",
    )

    private fun store(): FakeOutboxStore = LoggingStore(cleanUpLog)

    private fun engine(store: OutboxStore, onSend: (OutboxRecord) -> Unit = {}): OutboxEngine =
        OutboxEngine(
            store = store,
            transport = { record ->
                onSend(record)
                sendLog += record.mutationId
                (outcomes[record.mutationId] ?: Result.success(Unit))
                    .map { MutationResponse(authoritativePayload) }
            },
            clock = { now },
            onQuarantined = NoLocalEffect(),
            receipts = receipts,
            sessions = { session },
        )

    private fun traces(): RoundtripTraces = RoundtripTraces(tracesLeft, cleanUpLog)

    private fun guard(engine: OutboxEngine, store: FakeOutboxStore): AccountSwitchGuard =
        AccountSwitchGuard(engine, store, store, traces())

    /**
     * Вход, каким его делает Firebase: каждое событие аутентификации — новая эпоха, даже когда uid
     * вернулся к прежнему (FirebaseOutboxSessionProvider считает события, а не смену владельца).
     */
    private fun signInAs(newUid: String): suspend () -> String = {
        session = OutboxSession(newUid, (session?.generation ?: 0L) + 1)
        newUid
    }

    @Test
    fun `the roundtrip A - B - A leaves every action to its owner and settles each exactly once`() =
        runTest {
            session = OutboxSession(uidA, 1)
            tracesLeft[uidA] = listOf(OwnerDataKind.ANSWER_HISTORY)
            val store = store()
            val engine = engine(store)
            val guard = guard(engine, store)

            // Обычная работа под A: a1 доезжает и признаётся, a2 ждёт сети.
            val a1 = attempt("attempt-a1")
            val a2 = attempt("attempt-a2")
            outcomes[a2.mutationId] = Result.failure(SyncFailure(SyncError.NoNetwork))
            store.seed(a1, a2)
            engine.drain(uidA)
            assertEquals(listOf(a1.id), store.removed)

            // Смена A→B: слив не дошёл — игрок предупреждён и согласился. Следы A сняты после
            // входа: сначала записи очереди (a2 во всех состояниях), потом личные строки.
            val leaveA = guard.flushBefore(uidA)
            assertTrue(leaveA.needsWarning, "и недосланная запись, и история ответов — повод спросить")
            assertEquals(1, leaveA.remainder.waiting)
            assertEquals(listOf(uidA), leaveA.owners)
            assertEquals(uidB, guard.switchAndForget(leaveA.owners, signInAs(uidB)))
            assertTrue(store.all.isEmpty(), "у прежнего владельца не осталось ни одной строки")
            assertEquals(OutboxSession(uidB, 2), session, "вход под B — новая эпоха")

            // Работа под B: его запись доезжает под его же сессией.
            val b1 = attempt("attempt-b1", owner = uidB)
            store.seed(b1)
            assertEquals(1, engine.drain(uidB).sent)

            // Возвращение B→A: приватные данные B (черновики) — то, что спрашивают и снимают;
            // вернувшемуся A при этом ничего не забывается.
            tracesLeft[uidB] = listOf(OwnerDataKind.UNPUBLISHED_QUEST_DRAFTS)
            val leaveB = guard.flushBefore(uidB)
            assertTrue(leaveB.needsWarning, "черновики B сервер не хранит — без вопроса их снимать нельзя")
            assertTrue(leaveB.remainder.isEmpty)
            assertEquals(listOf(uidB), leaveB.owners)
            assertEquals(uidA, guard.switchAndForget(leaveB.owners, signInAs(uidA)))
            assertEquals(
                listOf("queue:$uidA", "traces:$uidA", "queue:$uidB", "traces:$uidB"),
                cleanUpLog,
                "каждая уборка — очередь, потом следы; возвращенец в уборку не попадает",
            )
            assertTrue(store.all.isEmpty(), "после полного круга в очереди нет ни одной чужой строки")

            // Возвращение — новая сессия того же uid (эпоха 3, не 1): владение сохранено, его
            // новое действие доезжает ровно один раз.
            val a3 = attempt("attempt-a3")
            store.seed(a3)
            assertEquals(1, engine.drain(uidA).sent)
            assertEquals(OutboxSession(uidA, 3), session)

            // Двойного расчёта нет: каждый ключ посылался по разу, расчётных квитанций ровно три,
            // каждая — под владельцем самой записи.
            assertEquals(1, sendLog.count { it == a1.mutationId }, "уехавшее не посылается второй раз")
            assertEquals(1, sendLog.count { it == b1.mutationId })
            assertEquals(1, sendLog.count { it == a3.mutationId })
            assertEquals(1, sendLog.count { it == a2.mutationId }, "недоезжавшее ждёт, но ключ у него один")
            assertEquals(
                listOf(a1.mutationId, b1.mutationId, a3.mutationId).sorted(),
                receipts.persisted.map { it.mutationId }.sorted(),
            )
            val byKey = receipts.persisted.associateBy { it.mutationId }
            assertEquals(uidA, byKey[a1.mutationId]?.ownerUid)
            assertEquals(uidB, byKey[b1.mutationId]?.ownerUid)
            assertEquals(uidA, byKey[a3.mutationId]?.ownerUid)
            assertTrue(receipts.persisted.all { it.payloadJson == authoritativePayload })
            assertTrue(store.all.isEmpty())
        }

    @Test
    fun `a late response of the old session settles nothing and the new session of the same uid settles it once`() =
        runTest {
            // Повторный вход под тем же uid — новая эпоха без смены владельца. Успех, вернувшийся
            // под прежней сессией, не признаётся: ни квитанции, ни удаления. Очередь дочитывает
            // уже новая сессия, под тем же ключом — второй эффект исключён (AD-2).
            session = OutboxSession(uidA, 1)
            val store = store()
            val row = attempt("attempt-late")
            store.seed(row)
            var flights = 0
            val engine =
                engine(store, onSend = {
                    if (++flights == 1) session = OutboxSession(row.ownerUid, 2)
                })

            val crossed = engine.drain(row.ownerUid)

            assertEquals(0, crossed.sent, "поздний успех прежней сессии не выдаётся за отправку")
            assertEquals(1, crossed.retried)
            assertNull(receipts.find(row.ownerUid, row.mutationId), "расчёт под старой эпохой не записан")
            assertTrue(store.removed.isEmpty())

            val takeover = engine.drain(row.ownerUid)

            assertEquals(1, takeover.sent, "новая сессия того же владельца дочитывает его очередь")
            assertEquals(OutboxSession(row.ownerUid, 2), session)
            assertEquals(row.mutationId, receipts.persisted.single().mutationId)
            assertEquals(authoritativePayload, receipts.persisted.single().payloadJson)
            assertEquals(
                listOf(row.mutationId, row.mutationId),
                sendLog,
                "оба захода — под одним ключом идемпотентности: расчёт один",
            )
            assertTrue(store.all.isEmpty())
        }

    @Test
    fun `a pass under another owner's session or without any session sends nothing of anyone`() =
        runTest {
            val mine = attempt("attempt-a")
            val theirs = attempt("attempt-b", owner = uidB)
            val store = store()
            store.seed(mine, theirs)

            session = OutboxSession(uidB, 7)
            assertEquals(0, engine(store).drain(uidA).examined, "чужая сессия не отправляет записи A")

            session = null
            assertEquals(0, engine(store).drain(uidB).examined, "без сессии не отправляется ничего")
            assertTrue(sendLog.isEmpty())
            assertEquals(listOf(mine, theirs), store.all, "записи целы и ждут своих владельцев")
        }

    @Test
    fun `a foreign row that reaches the due page is skipped untouched and cannot wedge the pass`() =
        runTest {
            // Гейт шага — последняя линия: даже если выборка (наследуемый запрос без фильтра
            // владельца) привела чужую строку, попытки под её владельцем не было, строка не
            // тронута, а проход кончился, а не закрутился.
            session = OutboxSession(uidA, 1)
            val mine = attempt("attempt-mine")
            val foreign = attempt("attempt-foreign", owner = uidB)
            val store =
                object : LoggingStore(cleanUpLog) {
                    override suspend fun dueRecords(
                        ownerUid: String,
                        nowMs: Long,
                        limit: Int,
                    ): List<OutboxRecord> = listOf(foreign, mine)
                }
            store.seed(foreign, mine)

            val summary = engine(store).drain(uidA)

            assertEquals(listOf(mine.mutationId), sendLog, "транспорт зовётся только для записи своего владельца")
            assertEquals(1, summary.sent)
            assertEquals(1, summary.retried, "чужая строка возвращена без попытки")
            assertEquals(2, summary.examined)
            assertEquals(foreign, store.all.single { it.id == foreign.id }, "чужая строка не тронута ни в чём")
            assertEquals(listOf(mine.id), store.removed)
        }
}
