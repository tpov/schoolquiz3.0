package com.tpov.schoolquiz.shared.core.outbox

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Главное свойство цикла — он не прерывается.
 *
 * Сегодня отказ одной попытки урока навсегда глушит отправку всех оценок: исключение уходит
 * наверх до того, как до них дойдёт очередь. Ради этого дефекта эпик и существует, поэтому он
 * проверяется первым.
 */
class OutboxEngineTest {

    private val uid = "uid-1"
    private val now = 30L * 24 * 60 * 60 * 1000
    private val limits = OutboxLimits()

    private fun record(
        id: Long,
        operation: String = "OP",
        createdAtMs: Long = now,
    ) = OutboxRecord(
        id = id,
        mutationId = "m-$id",
        ownerUid = uid,
        operation = operation,
        payload = "{}",
        state = OutboxState.WAITING,
        createdAtMs = createdAtMs,
    )

    private fun engine(
        store: OutboxStore,
        onQuarantined: QuarantineListener = QuarantineListener { },
        clock: () -> Long = { now },
        send: suspend (OutboxRecord) -> Result<Unit>,
    ) = OutboxEngine(
        store = store,
        transport = { send(it) },
        clock = clock,
        onQuarantined = onQuarantined,
    )

    // ── Ради чего эпик ────────────────────────────────────────────────────────

    @Test
    fun `given one record is refused then the rest still go`() = runTest {
        val store = FakeOutboxStore()
        store.seed(record(1, "ATTEMPT"), record(2, "RATING"), record(3, "RATING"))

        val summary =
            engine(store) { r ->
                if (r.id == 1L) {
                    Result.failure(SyncFailure(SyncError.Refused("nope")))
                } else {
                    Result.success(Unit)
                }
            }.drain(uid)

        assertEquals(3, summary.examined)
        assertEquals(2, summary.sent, "соседние записи обязаны уехать")
        assertEquals(1, summary.quarantined)
        assertContentEquals(listOf(2L, 3L), store.removed)
    }

    @Test
    fun `given the transport throws nothing but keeps failing then drain still returns`() = runTest {
        // Цикл не выпускает исключение наверх ни при каких условиях.
        val store = FakeOutboxStore()
        store.seed(record(1), record(2))

        val summary = engine(store) { Result.failure(SyncFailure(SyncError.NoNetwork)) }.drain(uid)

        assertEquals(2, summary.retried)
        assertEquals(0, summary.sent)
    }

    // ── Отправленное удаляется ────────────────────────────────────────────────

    @Test
    fun `given a record is sent then it is removed, not marked`() = runTest {
        // AD-4: очередь — это очередь, а не архив.
        val store = FakeOutboxStore()
        store.seed(record(1))

        engine(store) { Result.success(Unit) }.drain(uid)

        assertContentEquals(listOf(1L), store.removed)
        assertTrue(store.all.isEmpty(), "надгробия не остаётся")
    }

    // ── Карантин отдаётся фиче ────────────────────────────────────────────────

    @Test
    fun `given a record is quarantined then the owning feature is told`() = runTest {
        // Движок не имеет права трогать таблицы фичи, поэтому обязан сообщить (AD-28).
        val store = FakeOutboxStore()
        store.seed(record(1, "UNLOCK_LESSON"))
        val told = mutableListOf<OutboxRecord>()

        engine(store, onQuarantined = { told += it }) {
            Result.failure(SyncFailure(SyncError.Refused("Not enough nolics")))
        }.drain(uid)

        assertEquals(1, told.size)
        assertEquals("UNLOCK_LESSON", told.first().operation)
        assertEquals("Not enough nolics", told.first().lastError, "причина доезжает до фичи")
    }

    @Test
    fun `given a conflict then the feature is not told it is quarantined`() = runTest {
        // Конфликт не терминален, откатывать по нему нечего (AD-28).
        val store = FakeOutboxStore()
        store.seed(record(1))
        val told = mutableListOf<OutboxRecord>()

        val summary =
            engine(store, onQuarantined = { told += it }) {
                Result.failure(SyncFailure(SyncError.VersionConflict(3L)))
            }.drain(uid)

        assertEquals(1, summary.conflicted)
        assertTrue(told.isEmpty())
        assertEquals(OutboxState.CONFLICT, store.all.first().state)
    }

    @Test
    fun `given a conflict then the server version is stored on the record`() = runTest {
        // Без числа конфликт неразрешим: «отправить заново поверх версии 7» собрать не из чего, а
        // разбирать текст ошибки запрещено (AD-15).
        val store = FakeOutboxStore()
        store.seed(record(1))

        engine(store) { Result.failure(SyncFailure(SyncError.VersionConflict(7L))) }.drain(uid)

        val conflicted = store.all.first()
        assertEquals(7L, conflicted.serverVersion)
        assertEquals(0, conflicted.attemptCount, "конфликт — не неудачная попытка")
        assertEquals(0L, conflicted.nextRetryAtMs, "и пауза не сдвигается")
    }

    @Test
    fun `given a conflicted record then a later run never touches the transport`() = runTest {
        // Так выглядит перезапуск приложения: очередь та же, конфликт на месте, прогон первый.
        // Отправить его вслепую значило бы получить тот же отказ и, если сервер вдруг ответит
        // иначе, затереть чужую работу (AD-24).
        val store = FakeOutboxStore()
        store.seed(record(1))
        engine(store) { Result.failure(SyncFailure(SyncError.VersionConflict(7L))) }.drain(uid)

        var sends = 0
        val summary =
            engine(store) {
                sends++
                Result.success(Unit)
            }.drain(uid)

        assertEquals(0, sends, "по конфликтной записи транспорт не зовут")
        assertEquals(0, summary.examined)
        assertEquals(OutboxState.CONFLICT, store.all.first().state)
        assertEquals(7L, store.all.first().serverVersion, "версия пережила прогон")
    }

    @Test
    fun `given a conflicted record older than the age limit then it stays in conflict`() = runTest {
        // Предельный возраст уводит в карантин то, что ещё ждёт отправки. Конфликт отправки не
        // ждёт — он ждёт игрока, и карантин по нему откатил бы работу, которую никто не терял
        // (AD-28).
        val store = FakeOutboxStore()
        store.seed(record(1, createdAtMs = now - limits.maxAgeMs))
        val told = mutableListOf<OutboxRecord>()

        engine(store, onQuarantined = { told += it }) {
            Result.failure(SyncFailure(SyncError.VersionConflict(7L)))
        }.drain(uid)
        // Первый прогон отправить её не мог — она перезрела, и по возрасту ушла бы в карантин.
        // Поэтому конфликт ставим руками, как если бы он приехал раньше, чем истёк возраст.
        store.apply(1L, OutboxDecision(OutboxState.CONFLICT, 0L, 0, "VersionConflict", serverVersion = 7L))
        told.clear()

        val summary = engine(store) { Result.success(Unit) }.drain(uid)

        assertEquals(0, summary.quarantined)
        assertTrue(told.isEmpty(), "обработчик карантина владеющей фичи не зовут")
        assertEquals(OutboxState.CONFLICT, store.all.first().state)
    }

    // ── Перезревшая запись ────────────────────────────────────────────────────

    @Test
    fun `given a record older than the age limit then it reaches quarantine, not oblivion`() = runTest {
        // Находка 4: возраст резал выборку, а решение по возрасту принималось только по уже
        // выбранной записи. Пересечения не было — пролежавшая неделю офлайн запись не уезжала, в
        // карантин не попадала и откат не звала: черновик заперт, счётчик вечно «ожидает».
        val store = FakeOutboxStore()
        store.seed(record(1, "SUBMIT_ATTEMPT", createdAtMs = now - limits.maxAgeMs - 1))
        val told = mutableListOf<OutboxRecord>()
        var attempts = 0

        val summary =
            engine(store, onQuarantined = { told += it }) {
                attempts++
                Result.success(Unit)
            }.drain(uid)

        assertEquals(0, attempts, "перезревшую отправлять нельзя: ключ на сервере уже не хранится")
        assertEquals(1, summary.quarantined)
        assertEquals(OutboxState.QUARANTINED, store.all.single().state, "запись дошла до карантина")
        assertEquals(1, told.size, "и реакция позвана")
        assertEquals(OutboxPolicy.EXPIRED_REASON, told.single().lastError, "причина названа возрастом")
    }

    @Test
    fun `given the record crosses the age line while waiting then the next run picks it up`() = runTest {
        // Дозревание не спасает: запись, которая была свежей на прошлом проходе, обязана быть
        // видимой на следующем, а не выпасть из выборки вместе с переходом через предел.
        val store = FakeOutboxStore()
        store.seed(record(1, createdAtMs = now))
        val told = mutableListOf<OutboxRecord>()
        val later = now + limits.maxAgeMs + 1

        val summary =
            engine(store, onQuarantined = { told += it }, clock = { later }) {
                Result.failure(SyncFailure(SyncError.NoNetwork))
            }.drain(uid)

        assertEquals(1, summary.examined, "выборка обязана её отдать")
        assertEquals(1, summary.quarantined)
        assertEquals(1, told.size)
    }

    // ── Реакция на карантин упала ─────────────────────────────────────────────

    @Test
    fun `given the quarantine handler throws then the record is not left where nobody touches it`() = runTest {
        // Находка 7: карантин записывался до реакции. Упади реакция — запись помечена, откат не
        // сделан, второй попытки нет: молчаливое расхождение, запрещённое AD-28.
        val store = FakeOutboxStore()
        store.seed(record(1, "UNLOCK_LESSON"), record(2, "RATING"))
        var calls = 0

        val summary =
            engine(store, onQuarantined = { calls++; error("rollback failed") }) { r ->
                if (r.id == 1L) Result.failure(SyncFailure(SyncError.Refused("nope"))) else Result.success(Unit)
            }.drain(uid)

        val stuck = store.all.single { it.id == 1L }
        assertEquals(1, calls, "реакция была позвана")
        assertEquals(0, summary.quarantined, "карантин не объявлен, пока фича о нём не знает")
        assertTrue(stuck.state.isPending, "запись осталась в выборке")
        assertTrue(
            store.dueRecords(uid, now + HOUR_MS, 10).any { it.id == 1L },
            "следующий проход её увидит",
        )
        assertEquals(1, summary.sent, "и соседняя запись всё равно уехала")
        assertContentEquals(listOf(2L), store.removed)
    }

    @Test
    fun `given the handler recovers then the retried run announces the quarantine`() = runTest {
        // Вторая попытка существует — иначе «повторим позже» было бы просто другим способом
        // потерять откат.
        val store = FakeOutboxStore()
        store.seed(record(1, "UNLOCK_LESSON"))
        val refuse: suspend (OutboxRecord) -> Result<Unit> = { Result.failure(SyncFailure(SyncError.Refused("nope"))) }

        engine(store, onQuarantined = { error("rollback failed") }, send = refuse).drain(uid)
        val told = mutableListOf<OutboxRecord>()
        val summary = engine(store, onQuarantined = { told += it }, clock = { now + HOUR_MS }, send = refuse).drain(uid)

        assertEquals(1, summary.quarantined)
        assertEquals(1, told.size, "откат позван на второй раз")
        assertEquals(OutboxState.QUARANTINED, store.all.single().state)
    }

    // ── Чужие записи ──────────────────────────────────────────────────────────

    @Test
    fun `given a record of another account then it is never sent`() = runTest {
        // AD-8: запись принадлежит тому uid, который её создал.
        val store = FakeOutboxStore()
        store.seed(record(1).copy(ownerUid = "someone-else"))
        var attempts = 0

        val summary = engine(store) { attempts++; Result.success(Unit) }.drain(uid)

        assertEquals(0, summary.examined)
        assertEquals(0, attempts)
    }

    // ── Неопознанная неудача ──────────────────────────────────────────────────

    @Test
    fun `given a plain exception then it is treated as unknown and retried`() = runTest {
        // Транспорт может вернуть неудачу без разобранной ошибки — это не повод падать.
        val store = FakeOutboxStore()
        store.seed(record(1))

        val summary = engine(store) { Result.failure(IllegalStateException("boom")) }.drain(uid)

        assertEquals(1, summary.retried)
        assertEquals(OutboxState.WAITING, store.all.first().state)
    }

    private companion object {
        /** Заведомо больше паузы, назначенной после неудавшейся реакции. */
        const val HOUR_MS = 60L * 60 * 1000
    }
}
