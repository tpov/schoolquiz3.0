package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * NFR4 против настоящего SQLite: чужую ссылку не берёт ни один путь постановки.
 *
 * Проверка «префикс `entity_ref` совпадает с пространством имён `operation`» стояла у хранилища
 * очереди — то есть у одного пути из трёх. Прохождение, оценка и заявка на арену кладут строку
 * своей транзакцией (AD-23), минуя хранилище, и для них правила не существовало: разъехавшиеся
 * имена доезжали до базы молча, а всплывали позже пометкой, которая не встала ни на одну сущность,
 * и отличить это от «нечего показывать» уже нельзя.
 *
 * Поэтому здесь проверяются именно транзакционные пути — рядом с обычным, чтобы видеть, что
 * отвергают они одинаково и одними словами.
 */
class OutboxNamespaceGuardPersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var outbox: OutboxDao
    private lateinit var store: RoomOutboxStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
        outbox = db.outboxDao()
        store = RoomOutboxStore(outbox)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Заявка на арену: своя транзакция, своя вставка — и то же правило.
     *
     * Строка собирается внутри проверяемого блока намеренно: отвергать её обязано само появление
     * такой формы, а не отдельная память писателя о том, что имена надо сверить.
     */
    @Test
    fun theTransactionalArenaPathRefusesAForeignReference() = runTest {
        db.questAuthoringDao().insertDraft(draft())

        val failure =
            assertFailsWith<IllegalArgumentException> {
                db.questAuthoringDao().queueArenaSubmission(
                    outboxRow = row(
                        mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ARENA, "sub-1"),
                        operation = OutboxOperations.SUBMIT_ARENA,
                        entityRef = "lesson_runner:attempt:a1",
                    ),
                    draftId = DRAFT_ID,
                    updatedAtMs = 2_000L,
                )
            }

        assertNamesBothNamespaces(failure, ofEntityRef = "lesson_runner", ofOperation = "quest_authoring")
        assertEquals("отвергнутая заявка не оставляет строки в очереди", 0, outbox.countPending(OWNER_UID))
        assertEquals(
            "и не запирает черновик на редактирование",
            "DRAFT",
            db.questAuthoringDao().findDraftById(DRAFT_ID)!!.status,
        )
    }

    /** Прохождение урока: та же транзакция, что и локальная запись, — и то же правило. */
    @Test
    fun theTransactionalAttemptPathRefusesAForeignReference() = runTest {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                db.lessonAttemptDao().saveAttemptWithAnswers(
                    attempt = attempt(),
                    answers = emptyList(),
                    repetitions = emptyList(),
                    outboxRow = row(
                        mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, "a1"),
                        operation = OutboxOperations.SUBMIT_ATTEMPT,
                        entityRef = "quest_authoring:draft:draft-1",
                    ),
                )
            }

        assertNamesBothNamespaces(failure, ofEntityRef = "quest_authoring", ofOperation = "lesson_runner")
        assertEquals("отвергнутое прохождение не оставляет строки в очереди", 0, outbox.countPending(OWNER_UID))
        assertTrue(
            "и не остаётся показанным сохранённым",
            db.questionAnswerDao().findByAttempt("a1").isEmpty(),
        )
    }

    /** Оценка квеста: третий транзакционный путь, то же правило. */
    @Test
    fun theTransactionalRatingPathRefusesAForeignReference() = runTest {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                db.lessonRatingLocalDao().submitWithOutbox(
                    rating = LessonRatingSubmittedLocalEntity(
                        userId = OWNER_UID,
                        lessonId = "lesson-1",
                        submittedAt = 1_000L,
                    ),
                    outboxRow = row(
                        mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_RATING, "r-1"),
                        operation = OutboxOperations.SUBMIT_RATING,
                        entityRef = "quest.QUEST:7",
                    ),
                )
            }

        assertNamesBothNamespaces(failure, ofEntityRef = "quest", ofOperation = "lesson_runner")
        assertEquals("отвергнутая оценка не оставляет строки в очереди", 0, outbox.countPending(OWNER_UID))
    }

    /** Обычный путь — для сравнения: отказ тот же и теми же словами. */
    @Test
    fun theOrdinaryPathRefusesTheSameReferenceTheSameWay() = runTest {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                store.enqueue(
                    OutboxRecord(
                        id = 0L,
                        mutationId = "m-1",
                        ownerUid = OWNER_UID,
                        operation = OutboxOperations.SUBMIT_ATTEMPT,
                        payload = "{}",
                        state = OutboxState.WAITING,
                        createdAtMs = 1_000L,
                        entityRef = "quest_authoring:draft:draft-1",
                    ),
                )
            }

        assertNamesBothNamespaces(failure, ofEntityRef = "quest_authoring", ofOperation = "lesson_runner")
        assertEquals(0, outbox.countPending(OWNER_UID))
        assertNull(outbox.findByMutationId("m-1"))
    }

    /** Своя ссылка проходит везде: правило отсекает чужую, а не постановку вообще. */
    @Test
    fun aMatchingReferenceStillQueuesOnEveryPath() = runTest {
        db.questAuthoringDao().insertDraft(draft())

        db.questAuthoringDao().queueArenaSubmission(
            outboxRow = row(
                mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ARENA, "sub-1"),
                operation = OutboxOperations.SUBMIT_ARENA,
                entityRef = "quest_authoring:draft:$DRAFT_ID",
            ),
            draftId = DRAFT_ID,
            updatedAtMs = 2_000L,
        )
        db.lessonAttemptDao().saveAttemptWithAnswers(
            attempt = attempt(),
            answers = emptyList(),
            repetitions = emptyList(),
            outboxRow = row(
                mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, "a1"),
                operation = OutboxOperations.SUBMIT_ATTEMPT,
                entityRef = "lesson_runner:attempt:a1",
            ),
        )
        store.enqueue(
            OutboxRecord(
                id = 0L,
                mutationId = "m-1",
                ownerUid = OWNER_UID,
                operation = OutboxOperations.SUBMIT_RATING,
                payload = "{}",
                state = OutboxState.WAITING,
                createdAtMs = 1_000L,
                entityRef = "lesson_runner:rating:r-1",
            ),
        )

        assertEquals(3, outbox.countPending(OWNER_UID))
    }

    private fun assertNamesBothNamespaces(
        failure: IllegalArgumentException,
        ofEntityRef: String,
        ofOperation: String,
    ) {
        val message = failure.message.orEmpty()
        assertTrue("в тексте названо пространство ссылки: $message", message.contains(ofEntityRef))
        assertTrue("и пространство операции: $message", message.contains(ofOperation))
    }

    private fun row(
        mutationId: String,
        operation: String,
        entityRef: String,
    ) = OutboxEntity(
        mutationId = mutationId,
        ownerUid = OWNER_UID,
        operation = operation,
        payload = "{}",
        entityRef = entityRef,
        expectedVersion = null,
        state = OutboxState.WAITING.name,
        attemptCount = 0,
        nextRetryAtMs = 0L,
        lastError = null,
        createdAtMs = 1_000L,
    )

    private fun attempt() = LessonAttemptEntity(
        attemptId = "a1",
        userId = OWNER_UID,
        lessonId = "lesson-1",
        lessonVersion = 3L,
        isHard = 0,
        codeAnswer = "99",
        percentScore = 100,
        completedAt = 1_000L,
    )

    private fun draft() = QuestDraftEntity(
        id = DRAFT_ID,
        ownerUid = OWNER_UID,
        catalogId = "catalog-1",
        title = "Черновик",
        description = null,
        defaultLanguage = "ru",
        defaultDifficulty = "EASY",
        status = "DRAFT",
        localRevision = 1L,
        serverRevision = null,
        publicQuestId = null,
        createdAtMs = 1_000L,
        updatedAtMs = 1_000L,
        isActive = true,
    )

    private companion object {
        const val OWNER_UID = "user-1"
        const val DRAFT_ID = "draft-1"
    }
}
