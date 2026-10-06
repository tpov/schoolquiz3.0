package com.tpov.schoolquiz.shared.core.outbox

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Доказательство истории 2.2 (outbox-120-drain, CAP-18).
 *
 * Очередь из 120 действий сливается одним проходом движка — три страницы по 50, третья добирает
 * остаток в 20 — без повторной ручной синхронизации, и каждое намерение находит свой авторитетный
 * результат в прочной квитанции. Ветви карантина (отказ сервера), конфликта (версия устарела) и
 * перезревания (предельный возраст) разбираются в той же сотне, не глуша соседей (AD-22).
 *
 * Отменённый ответ — бросок [CancellationException] и неудача, упаковавшая его в `Result`, — не
 * фабрикует успех: проход обрывается целиком, запись остаётся ждущей, а догоняющий прогон
 * переиспользует прежний ключ идемпотентности, так что второго эффекта нет (AD-2). Искажённый
 * ответ — квитанция, чья идентичность не совпадает с мутацией, — не признаётся и не зовёт
 * транспорт: неопределённость остаётся неопределённой, ждущее свидетельство не стирается.
 *
 * Пишется против общего кода: общий [OutboxEngine] и общий fake-сторон [FakeOutboxStore] из
 * commonTest (виден отсюда через иерархию исходников KMP).
 */
class OutboxDrain120Test {
    private val uid = "owner"
    private val now = 30L * 24 * 60 * 60 * 1000
    private val limits = OutboxLimits()
    private val session = OutboxSession(uid, 0)

    /** Авторитетный расчёт, который возвращает fake-транспорт — единственный источник успеха. */
    private val authoritativePayload = """{"settlement":"authoritative"}"""

    /** Ключи перезревших действий — они не должны дойти до транспорта ни при какой ветви. */
    private val expiredIds = 115L..120L
    private val refusedIds = 101L..108L
    private val conflictedIds = 109L..114L

    /** Квартплата с очередью за один прогон: квитанция только подлинному ответу сервера. */
    private class RecordingReceipts : MutationReceiptStore {
        val persisted = mutableListOf<MutationReceipt>()
        private val stored = MutableStateFlow<Map<Pair<String, String>, MutationReceipt>>(emptyMap())

        override suspend fun persist(receipt: MutationReceipt) {
            val key = receipt.ownerUid to receipt.mutationId
            val previous = stored.value[key]
            check(previous == null || previous.copy(receivedAtMs = receipt.receivedAtMs) == receipt) {
                "another response under the same identity"
            }
            if (previous == null) {
                stored.value = stored.value + (key to receipt)
                persisted += receipt
            }
        }

        override suspend fun find(
            ownerUid: String,
            mutationId: String,
        ): MutationReceipt? = stored.value[ownerUid to mutationId]

        override fun observeEntity(
            ownerUid: String,
            entityRef: String,
        ): Flow<List<MutationReceipt>> =
            stored.map { rows ->
                rows.values.filter { it.ownerUid == ownerUid && it.entityRef == entityRef }
            }
    }

    /**
     * [operation] различает перманентные и обычные мутации не случайно: у SUBMIT_ATTEMPT прочный
     * серверный receipt, и предельный возраст на неё не действует вовсе (OutboxPolicy: только
     * `!hasPermanentServerReceipt` перезревает) — перезревание в тестах задаётся операцией без
     * перманентного receipt.
     */
    private fun action(
        id: Long,
        createdAtMs: Long = now,
        operation: String = OutboxOperations.SUBMIT_ATTEMPT,
    ) = OutboxRecord(
        id = id,
        mutationId = OutboxOperations.mutationKey(operation, "attempt-$id"),
        ownerUid = uid,
        operation = operation,
        payload = """{"attemptId":"attempt-$id"}""",
        state = OutboxState.WAITING,
        createdAtMs = createdAtMs,
        entityRef = "lesson_runner:attempt-$id",
    )

    private fun engine(
        store: OutboxStore,
        receipts: MutationReceiptStore,
        transport: MutationTransport,
        clock: () -> Long = { now },
        onQuarantined: QuarantineListener = NoLocalEffect(),
    ) = OutboxEngine(
        store = store,
        transport = transport,
        clock = clock,
        onQuarantined = onQuarantined,
        receipts = receipts,
        sessions = { session },
        batchSize = 50,
    )

    /** Страницы по 50: движок пакует выборку порциями размера страницы, а не всей сотни. */
    private fun pagedStore(pageLimits: MutableList<Int>): FakeOutboxStore =
        object : FakeOutboxStore() {
            override suspend fun dueRecords(
                ownerUid: String,
                nowMs: Long,
                limit: Int,
            ): List<OutboxRecord> {
                pageLimits += limit
                return super.dueRecords(ownerUid, nowMs, limit)
            }
        }

    // ── 120 действий сливаются одним проходом ─────────────────────────────────

    @Test
    fun `given 120 queued actions then one pass settles all three pages into authoritative receipts`() =
        runTest {
            val pageLimits = mutableListOf<Int>()
            val store = pagedStore(pageLimits)
            store.seed(*(1L..120L).map { action(it) }.toTypedArray())
            val receipts = RecordingReceipts()
            val sends = mutableListOf<String>()

            val summary =
                engine(store, receipts, transport = { record ->
                    sends += record.mutationId
                    Result.success(MutationResponse(authoritativePayload))
                }).drain(uid)

            assertEquals(120, summary.examined)
            assertEquals(120, summary.sent)
            assertEquals(0, summary.retried)
            assertEquals(0, summary.quarantined)
            assertEquals(0, summary.conflicted)
            assertNull(summary.lastError)

            // Три страницы по 50: лимиты выборки растут по числу разобранного, третья добирает
            // остаток в 20, четвёртый вызов закрывает проход пустой страницей.
            assertEquals(listOf(50, 100, 150, 170), pageLimits)

            // Каждое намерение уехало ровно один раз и под своим неизменным ключом (AD-2):
            // дублирования эффектов нет.
            assertEquals((1L..120L).map { action(it).mutationId }, sends)
            assertEquals(120, sends.distinct().size)

            // Очередь опустела: отправленное удаляется, надгробий нет (AD-4) — повторная ручная
            // синхронизация не нужна.
            assertEquals((1L..120L).toList(), store.removed)
            assertTrue(store.all.isEmpty())

            // Авторитетный результат каждого действия доезжает в прочную квитанцию — не в локальную
            // выдумку: ровно 120 квитанций, каждая с тем, что сказал сервер.
            assertEquals(120, receipts.persisted.size)
            assertTrue(
                receipts.persisted.all {
                    it.ownerUid == uid && it.operation == OutboxOperations.SUBMIT_ATTEMPT &&
                        it.payloadJson == authoritativePayload
                },
            )

            // Следующий проход работы не находит: слив закончился, а не прервался.
            val rerun =
                engine(store, receipts, transport = { error("The queue must be empty after one pass") }).drain(uid)
            assertEquals(0, rerun.examined)
        }

    // ── Карантин, конфликт и перезревание в той же сотне ───────────────────────

    @Test
    fun `given a mixed 120 queue then quarantine conflict and expiry keep their own outcomes and nobody stalls`() =
        runTest {
            val store = FakeOutboxStore()
            val actions =
                (1L..120L).map { id ->
                    // Перезревание задаётся операцией без перманентного receipt: у SUBMIT_ATTEMPT
                    // предельный возраст не действует вовсе — это свойство 2.1, а не дефект.
                    if (id in expiredIds) {
                        action(
                            id,
                            createdAtMs = now - limits.maxAgeMs - 1,
                            operation = OutboxOperations.SUBMIT_RATING,
                        )
                    } else {
                        action(id)
                    }
                }
            store.seed(*actions.toTypedArray())
            val receipts = RecordingReceipts()
            val sends = mutableListOf<String>()
            val told = mutableListOf<OutboxRecord>()

            val summary =
                engine(
                    store,
                    receipts,
                    transport = { record ->
                        sends += record.mutationId
                        when (record.id) {
                            in refusedIds -> Result.failure(SyncFailure(SyncError.Refused("quota exhausted")))
                            in conflictedIds -> Result.failure(SyncFailure(SyncError.VersionConflict(41L)))
                            else -> Result.success(MutationResponse(authoritativePayload))
                        }
                    },
                    onQuarantined = NoLocalEffect { told += it },
                ).drain(uid)

            assertEquals(120, summary.examined, "отказ одной записи не глушит остальные (AD-22)")
            assertEquals(100, summary.sent)
            assertEquals(6, summary.conflicted)
            assertEquals(14, summary.quarantined, "восемь отказов сервера плюс шесть перезревших")
            assertEquals(0, summary.retried)
            // Сумма ветвей сходится к 120 разобранным: отправлено + карантин + конфликт.
            assertEquals(120, summary.sent + summary.quarantined + summary.conflicted + summary.retried)

            // Каждый ключ пытался уехать максимум один раз: конфликт и карантин не переотправляются.
            assertEquals(114, sends.size, "перезревшие до транспорта не доходят")
            assertEquals(114, sends.distinct().size)
            val expiredKeys =
                expiredIds.map { action(it, operation = OutboxOperations.SUBMIT_RATING).mutationId }
            assertTrue(
                sends.none { it in expiredKeys },
                "перезревшую отправлять нельзя: ключ на сервере уже не хранится (AD-1)",
            )

            // Карантинная ветвь: восемь отказов плюс шесть перезревших, фиче объявлены все.
            assertEquals(14, told.size)
            assertEquals(8, told.count { it.lastError == "quota exhausted" })
            assertEquals(
                6,
                told.count { it.lastError == OutboxPolicy.EXPIRED_REASON },
                "перезревание названо словом, а не отказом сервера",
            )
            assertEquals(14, store.all.count { it.state == OutboxState.QUARANTINED })
            val quarantinedExpired = store.all.filter { it.id in expiredIds }
            assertTrue(
                quarantinedExpired.all { it.state == OutboxState.QUARANTINED && it.attemptCount == 0 },
                "у перезревших попытки не было — счётчик не растёт",
            )
            val quarantinedRefused = store.all.filter { it.id in refusedIds }
            assertTrue(
                quarantinedRefused.all { it.state == OutboxState.QUARANTINED && it.attemptCount == 1 },
                "у отказанных попытка была одна — отказ сразу терминален",
            )

            // Конфликтная ветвь: серверная версия сохранена на записи, попытки и пауза нетронуты,
            // обработчик карантина по конфликту не зовётся — он не терминален (AD-24, AD-28).
            val conflicted = store.all.filter { it.id in conflictedIds }
            assertEquals(6, conflicted.size)
            assertTrue(conflicted.all { it.state == OutboxState.CONFLICT && it.serverVersion == 41L })
            assertTrue(conflicted.all { it.attemptCount == 0 && it.nextRetryAtMs == 0L })
            assertTrue(told.none { it.id in conflictedIds })

            assertEquals(100, store.removed.size)
            assertEquals(100, receipts.persisted.size, "квитанция только у доехавших")

            // Прогон повторной ручной синхронизации не меняет ничего: конфликт ждёт игрока,
            // карантин терминален, отправлять по ним транспорт не зовётся.
            val rerun =
                engine(store, receipts, transport = { error("Settled branches must not be re-sent") }).drain(uid)
            assertEquals(0, rerun.examined)
        }

    // ── Отменённый ответ не фабрикует успех (CAP-18) ──────────────────────────

    @Test
    fun `given the transport cancels mid-pass then nothing is fabricated and the retry reuses the same key`() =
        runTest {
            val store = FakeOutboxStore()
            store.seed(*(1L..120L).map { action(it) }.toTypedArray())
            val receipts = RecordingReceipts()
            val sends = mutableListOf<String>()
            val cancelledId = action(61L).mutationId

            val interrupted =
                engine(store, receipts, transport = { record ->
                    sends += record.mutationId
                    if (record.mutationId == cancelledId) throw CancellationException("pass cancelled")
                    Result.success(MutationResponse(authoritativePayload))
                })
            assertFailsWith<CancellationException> { interrupted.drain(uid) }

            // Отмена не фабрикует успех: у прерванной записи нет ни квитанции, ни удаления,
            // ни пометки карантином — ждущее свидетельство цело. Вызовов транспорта 61: шестьдесят
            // уехавших плюс сама прерванная попытка, ключ которой записан до броска отмены.
            assertEquals(61, sends.size)
            assertEquals(60, receipts.persisted.size)
            assertEquals(60, store.removed.size)
            val pending = store.all.single { it.mutationId == cancelledId }
            assertEquals(OutboxState.WAITING, pending.state)
            assertEquals(0, pending.attemptCount)
            assertNull(receipts.find(uid, cancelledId))

            // Догоняющий прогон отправляет прерванное первым, под тем же ключом: второй эффект
            // под новым ключом не фабрикуется (AD-2), остальные 60 уезжают.
            sends.clear()
            val resumed =
                engine(store, receipts, transport = { record ->
                    sends += record.mutationId
                    Result.success(MutationResponse(authoritativePayload))
                }).drain(uid)
            assertEquals(60, resumed.sent)
            assertEquals(cancelledId, sends.first())
            assertEquals(1, sends.count { it == cancelledId })
            assertEquals(60, sends.distinct().size)
            assertTrue(store.all.isEmpty())
            assertEquals(120, receipts.persisted.size)
        }

    @Test
    fun `given a cancelled response wrapped as failure then the pass aborts without marking anything`() =
        runTest {
            // Отмена, упакованная в Result.failure, — не отказ: ни повтор, ни карантин, ни успех.
            val row = action(1)
            val store = FakeOutboxStore().also { it.seed(row) }

            assertFailsWith<CancellationException> {
                engine(store, RecordingReceipts(), transport = {
                    Result.failure(CancellationException("transport cancelled"))
                }).drain(uid)
            }

            assertEquals(listOf(row), store.all, "запись осталась ровно той же")
            assertTrue(store.removed.isEmpty())
        }

    // ── Искажённый ответ не фабрикует успех (CAP-18) ──────────────────────────

    @Test
    fun `given a distorted receipt then the engine neither acknowledges it nor re-sends and the row stays pending`() =
        runTest {
            // Квитанция с чужой операцией под ключом попытки — искажённый ответ: признать её
            // значило бы засчитать попытке чужой результат, а повторная отправка — второй эффект.
            val row = action(7)
            val receipts = RecordingReceipts()
            receipts.persist(
                MutationReceipt(
                    ownerUid = row.ownerUid,
                    mutationId = row.mutationId,
                    operation = OutboxOperations.SUBMIT_RATING,
                    entityRef = row.entityRef,
                    payloadJson = authoritativePayload,
                    receivedAtMs = now,
                ),
            )
            val store = FakeOutboxStore().also { it.seed(row) }
            var sends = 0

            val summary =
                engine(store, receipts, transport = {
                    sends++
                    Result.success(MutationResponse(authoritativePayload))
                }).drain(uid)

            assertEquals(1, summary.examined)
            assertEquals(0, summary.sent, "искажённая идентичность не признаётся успехом")
            assertEquals(1, summary.retried)
            assertIs<SyncError.Unknown>(summary.lastError)
            assertEquals(0, sends, "транспорт по искажённой квитанции не зовётся")
            assertEquals(1, receipts.persisted.size, "искажённая квитанция не перезаписана")
            assertEquals(listOf(row), store.all, "ждущее свидетельство не стёрто")
        }
}
