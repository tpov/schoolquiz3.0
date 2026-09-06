package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.outbox.OutboxConflictResolution
import com.tpov.schoolquiz.shared.core.outbox.OutboxDecision
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Конфликт против настоящего SQLite: версия, названная сервером, обязана лечь на диск и оттуда же
 * подняться, а решение автора — снять прежнюю запись и поставить новую одной транзакцией.
 *
 * Проверяется через хранилище, а не через движок: движок работает с записью в памяти и о том,
 * доехало ли число до колонки, не знает. Перезапуск приложения — это ровно перечитывание из базы,
 * и здесь оно настоящее.
 */
class OutboxConflictPersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var authoring: QuestAuthoringDao
    private lateinit var store: RoomOutboxStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
        authoring = db.questAuthoringDao()
        store = RoomOutboxStore(db.outboxDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun conflictDecisionStoresTheServerVersionAndItSurvivesAReread() = runTest {
        val queued = store.enqueue(record("sub-1"))

        store.apply(
            queued.id,
            OutboxDecision(
                state = OutboxState.CONFLICT,
                nextRetryAtMs = 0L,
                attemptCount = 0,
                lastError = "VersionConflict",
                serverVersion = 7L,
            ),
        )

        // Перезапуск: та же база, новое хранилище, ни одного значения из памяти прошлого.
        val afterRestart = RoomOutboxStore(db.outboxDao()).findByEntityRef(OWNER_UID, DRAFT_REF)
        assertNotNull(afterRestart)
        assertEquals(OutboxState.CONFLICT, afterRestart!!.state)
        assertEquals(7L, afterRestart.serverVersion)
        assertEquals("с чем уходила — тоже на месте", 4L, afterRestart.expectedVersion)
        assertEquals("VersionConflict", afterRestart.lastError)
    }

    @Test
    fun theConflictedRecordIsTheOneTheEntityScreenGets() = runTest {
        // На одну сущность записей может быть несколько. Показывать надо ту, по которой автору
        // решать, а не просто самую свежую.
        val conflicted = store.enqueue(record("sub-1", createdAtMs = 1_000L))
        store.apply(conflicted.id, conflictDecision())
        store.enqueue(record("sub-2", createdAtMs = 9_000L))

        val seen = store.observeEntity(OWNER_UID, DRAFT_REF).first()

        assertTrue("по ссылке есть запись, и это не «неотправленного нет»", seen is OutboxEntitySync.Queued)
        seen as OutboxEntitySync.Queued
        assertTrue(seen.isConflicted)
        assertEquals(7L, seen.serverVersion)
        assertEquals(4L, seen.expectedVersion)
        assertEquals(conflicted.id, seen.recordId)
    }

    @Test
    fun aQueueWithoutAConflictOffersNothingToResolve() = runTest {
        store.enqueue(record("sub-1"))

        val seen = store.observeEntity(OWNER_UID, DRAFT_REF).first()

        assertTrue(seen is OutboxEntitySync.Queued)
        seen as OutboxEntitySync.Queued
        assertTrue("ожидающая запись — не расхождение", !seen.isConflicted)
        assertNull(seen.serverVersion)
    }

    @Test
    fun resolvingReplacesTheRecordWithANewOneAndUnlocksTheDraft() = runTest {
        authoring.insertDraft(draft(status = "CONFLICT"))
        val conflicted = store.enqueue(record("sub-1"))
        store.apply(conflicted.id, conflictDecision())
        val stored = store.findByEntityRef(OWNER_UID, DRAFT_REF)!!

        val replacement = OutboxConflictResolution.replacementFor(stored, nowMs = 5_000L)
        authoring.resolveArenaConflict(
            conflictedRowId = stored.id,
            replacement = replacement.toOutboxEntity(),
            draftId = DRAFT_ID,
            updatedAtMs = 5_000L,
        )

        assertNull("прежняя запись обязана исчезнуть", db.outboxDao().findById(stored.id))
        val fresh = db.outboxDao().findByMutationId(replacement.mutationId)
        assertNotNull("новая запись обязана встать в очередь", fresh)
        assertEquals(OutboxState.WAITING.name, fresh!!.state)
        assertEquals("отправляем поверх серверной версии", 7L, fresh.expectedVersion)
        assertNull(fresh.serverVersion)
        assertEquals("момент намерения новый", 5_000L, fresh.createdAtMs)
        assertEquals("тело и операция едут дословно", stored.payload, fresh.payload)
        assertEquals(stored.operation, fresh.operation)
        assertEquals(stored.entityRef, fresh.entityRef)
        assertEquals("одно действие — одна запись", 1, store.countPending(OWNER_UID))
        assertEquals("REVIEW_QUEUED", authoring.findDraftById(DRAFT_ID)!!.status)

        // И она поедет сама: очередной прогон её выбирает, в отличие от конфликтной.
        val due = store.dueRecords(OWNER_UID, nowMs = 6_000L, limit = 10)
        assertEquals(1, due.size)
        assertEquals(replacement.mutationId, due.single().mutationId)
    }

    @Test
    fun resolvingTwiceQueuesOnce() = runTest {
        // Второе нажатие — то же намерение: ключ выводится из прежнего и серверной версии, а не
        // придумывается заново.
        authoring.insertDraft(draft(status = "CONFLICT"))
        val conflicted = store.enqueue(record("sub-1"))
        store.apply(conflicted.id, conflictDecision())
        val stored = store.findByEntityRef(OWNER_UID, DRAFT_REF)!!
        val replacement = OutboxConflictResolution.replacementFor(stored, nowMs = 5_000L)

        authoring.resolveArenaConflict(stored.id, replacement.toOutboxEntity(), DRAFT_ID, 5_000L)
        authoring.resolveArenaConflict(stored.id, replacement.toOutboxEntity(), DRAFT_ID, 6_000L)

        assertEquals(1, store.countPending(OWNER_UID))
    }

    private fun conflictDecision() =
        OutboxDecision(
            state = OutboxState.CONFLICT,
            nextRetryAtMs = 0L,
            attemptCount = 0,
            lastError = "VersionConflict",
            serverVersion = 7L,
        )

    private fun record(
        submissionId: String,
        createdAtMs: Long = 1_000L,
    ) = OutboxRecord(
        id = 0L,
        mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ARENA, submissionId),
        ownerUid = OWNER_UID,
        operation = OutboxOperations.SUBMIT_ARENA,
        payload = """{"submissionId":"$submissionId","draftId":"$DRAFT_ID"}""",
        state = OutboxState.WAITING,
        createdAtMs = createdAtMs,
        entityRef = DRAFT_REF,
        expectedVersion = 4L,
    )

    private fun draft(status: String) =
        QuestDraftEntity(
            id = DRAFT_ID,
            ownerUid = OWNER_UID,
            catalogId = "catalog-1",
            title = "Черновик",
            description = null,
            defaultLanguage = "ru",
            defaultDifficulty = "EASY",
            status = status,
            localRevision = 5L,
            serverRevision = 4L,
            publicQuestId = null,
            createdAtMs = 1_000L,
            updatedAtMs = 1_000L,
            isActive = true,
        )

    private companion object {
        const val OWNER_UID = "author-1"
        const val DRAFT_ID = "draft-1"
        const val DRAFT_REF = "quest_authoring:draft:$DRAFT_ID"
    }
}
