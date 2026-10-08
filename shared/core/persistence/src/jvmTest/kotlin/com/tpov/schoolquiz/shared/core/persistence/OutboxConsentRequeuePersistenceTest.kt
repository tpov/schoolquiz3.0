package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.outbox.OutboxDecision
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Возврат ждущих согласия записей в отправку против настоящего SQLite (CAP-16, история 2.12).
 *
 * Отказ сервера `terms-consent-required` паркует платную запись в `CONFLICT`; согласие игрока —
 * единственное, что этот конфликт решает. Проверяется через хранилище: фильтр по операциям
 * живёт в самом запросе, и в памяти Kotlin его не повторить — проверить надо именно SQL-условие.
 */
class OutboxConsentRequeuePersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var store: RoomOutboxStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
        store = RoomOutboxStore(db.outboxDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun consentRequeuesOnlyTheTermsOperationsConflict() = runTest {
        parkedTermsRecord("attempt-1")
        val healed = parkedTermsRecord("attempt-2")
        store.apply(
            healed.id,
            termsRefusalDecision(),
        )
        val foreign = parkedTermsRecord("attempt-3", ownerUid = "uid-2")
        store.apply(
            foreign.id,
            termsRefusalDecision(),
        )
        val versionConflict = versionedRecord("sub-1")
        store.apply(
            versionConflict.id,
            OutboxDecision(
                state = OutboxState.CONFLICT,
                nextRetryAtMs = 0L,
                attemptCount = 0,
                lastError = "VersionConflict",
                serverVersion = 7L,
            ),
        )

        val requeued = store.requeueConflictedTerms()

        assertEquals("две ждущие согласия записи, конфликт версий не тронут", 2, requeued)
        val healedRow = db.outboxDao().findByMutationId(healed.mutationId)!!
        assertEquals(OutboxState.WAITING.name, healedRow.state)
        assertEquals("мутация та же: ключ идемпотентности не меняется (AD-2)", healed.mutationId, healedRow.mutationId)
        assertEquals(healed.payload, healedRow.payload)
        assertNull("причина отказа стёрта: следующим исходом будет переписана", healedRow.lastError)
        // И она поедет сама: возвращённая запись попадает в выборку.
        val due = store.dueRecords("uid-1", nowMs = 10_000L, limit = 10)
        assertTrue(
            "возвращённая запись обязана быть выбранной",
            due.any { it.mutationId == healed.mutationId },
        )
        // Чужой владелец тоже поехал: согласие не auth-scoped, записи уезжают под своими uid.
        val dueForeign = store.dueRecords("uid-2", nowMs = 10_000L, limit = 10)
        assertEquals(listOf(foreign.mutationId), dueForeign.map { it.mutationId })
        // Конфликт версий остался конфликтовать: его решает новая мутация (AD-24).
        val conflictedRow = db.outboxDao().findByMutationId(versionConflict.mutationId)!!
        assertEquals(OutboxState.CONFLICT.name, conflictedRow.state)
        assertEquals(7L, conflictedRow.serverVersion)
    }

    @Test
    fun requeuingTwiceReportsNothingTheSecondTime() = runTest {
        val parked = parkedTermsRecord("attempt-1")
        store.apply(parked.id, termsRefusalDecision())

        assertEquals(1, store.requeueConflictedTerms())
        assertEquals(0, store.requeueConflictedTerms())
    }

    private suspend fun parkedTermsRecord(
        attemptId: String,
        ownerUid: String = "uid-1",
    ) = store.enqueue(
        OutboxRecord(
            id = 0L,
            mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, attemptId),
            ownerUid = ownerUid,
            operation = OutboxOperations.SUBMIT_ATTEMPT,
            payload = """{"attemptId":"$attemptId"}""",
            state = OutboxState.WAITING,
            createdAtMs = 1_000L,
            entityRef = "lesson_runner:attempt:$attemptId",
            attemptCount = 1,
        ),
    )

    private suspend fun versionedRecord(submissionId: String) =
        store.enqueue(
            OutboxRecord(
                id = 0L,
                mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ARENA, submissionId),
                ownerUid = "uid-1",
                operation = OutboxOperations.SUBMIT_ARENA,
                payload = """{"submissionId":"$submissionId"}""",
                state = OutboxState.WAITING,
                createdAtMs = 1_000L,
                entityRef = "quest_authoring:draft:draft-1",
                expectedVersion = 4L,
            ),
        )

    /** Ровно то, что кладёт политика по ветви согласия: ожидание решения, счётчик не тронут. */
    private fun termsRefusalDecision() =
        OutboxDecision(
            state = OutboxState.CONFLICT,
            nextRetryAtMs = 2_000L,
            attemptCount = 1,
            lastError = "TermsConsentRequired",
            serverVersion = null,
        )
}
