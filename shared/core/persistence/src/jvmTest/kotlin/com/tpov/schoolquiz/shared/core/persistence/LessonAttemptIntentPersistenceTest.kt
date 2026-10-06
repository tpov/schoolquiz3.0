package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.outbox.OutboxLimits
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LessonAttemptIntentPersistenceTest {
    private lateinit var dir: File
    private lateinit var db: AppDatabase
    private val attempt = LessonAttemptEntity("a1", "uid-A", "lesson-1", 1, 0, "9", 100, 1000)
    private val answer = QuestionAnswerEntity("a1", "q1", "uid-A", "lesson-1", 1, 0, 0, 9, "{}", 900, 100, 0)
    private val repetition = QuestionRepetitionEntity("uid-A", "q1", "lesson-1", 1, 2500, 1, 900, 2000)

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("attempt-intents").toFile()
        db = open()
    }

    private fun open(): AppDatabase =
        Room.databaseBuilder<AppDatabase>(name = File(dir, "attempts.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private fun row(kind: String = "COMPLETED") =
        OutboxEntity(
            mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, "a1"),
            ownerUid = "uid-A", operation = OutboxOperations.SUBMIT_ATTEMPT,
            payload = "{\"attemptId\":\"a1\",\"completionKind\":\"$kind\"}",
            entityRef = "lesson_runner:attempt:a1", expectedVersion = null, state = "WAITING",
            attemptCount = 0, nextRetryAtMs = 0, lastError = null, createdAtMs = 1000,
        )

    private suspend fun save(outbox: OutboxEntity = row()) {
        db.lessonAttemptDao().saveAttemptWithAnswers(attempt, listOf(answer), listOf(repetition), outbox)
    }

    @Test
    fun identicalRetryDoesNotRewriteScheduleOrTransportBackoff() =
        runTest {
            save()
            val before = db.outboxDao().findByMutationId(row().mutationId)!!
            db.outboxDao().applyDecision(before.id, "WAITING", 9000, 7, "offline", null)
            db.lessonAttemptDao().saveAttemptWithAnswers(
                attempt,
                listOf(answer),
                listOf(repetition.copy(nextReviewAtMs = 9999)),
                row(),
            )
            val pending = db.outboxDao().findByMutationId(row().mutationId)!!
            assertEquals(before.id, pending.id)
            assertEquals(7, pending.attemptCount)
            assertEquals(9000L, pending.nextRetryAtMs)
            assertEquals(2000L, db.questionRepetitionDao().findByQuestion("uid-A", "q1")!!.nextReviewAtMs)
        }

    @Test
    fun changedTerminalBodyOrAnswersCannotRewriteTheFirstLocalResult() =
        runTest {
            save()
            assertTrue(runCatching { save(row("ABORTED")) }.exceptionOrNull() is AttemptIntentConflict)
            assertTrue(
                runCatching {
                    db.lessonAttemptDao().saveAttemptWithAnswers(
                        attempt.copy(codeAnswer = "1", percentScore = 0),
                        listOf(answer.copy(score = 1)),
                        emptyList(),
                        row(),
                    )
                }.exceptionOrNull() is AttemptIntentConflict,
            )
            assertEquals(attempt, db.lessonAttemptDao().findAttempt("a1"))
            assertEquals(listOf(answer), db.lessonAttemptDao().findAttemptAnswers("a1"))
            assertEquals(row().payload, db.outboxDao().findByMutationId(row().mutationId)!!.payload)
        }

    @Test
    fun firstIntentSurvivesAckAndReopenAndIdenticalRetryDoesNotReenqueue() =
        runTest {
            save()
            db.outboxDao().delete(db.outboxDao().findByMutationId(row().mutationId)!!.id)
            db.close()
            db = open()
            save()
            assertNull(db.outboxDao().findByMutationId(row().mutationId))
            assertNotNull(db.lessonAttemptDao().findAttemptIntent("a1"))
            assertTrue(runCatching { save(row("ABORTED")) }.exceptionOrNull() is AttemptIntentConflict)
            assertEquals(attempt, db.lessonAttemptDao().findAttempt("a1"))
        }

    @Test
    fun legacyAttemptWithoutOriginalRequestIsNotPromotedToCompletedProof() =
        runTest {
            db.lessonAttemptDao().saveAttemptWithAnswers(attempt, listOf(answer), emptyList())
            assertTrue(runCatching { save() }.exceptionOrNull() is AttemptIntentConflict)
            assertNull(db.lessonAttemptDao().findAttemptIntent("a1"))
            assertNull(db.outboxDao().findByMutationId(row().mutationId))
            assertEquals(attempt, db.lessonAttemptDao().findAttempt("a1"))
        }

    @Test
    fun exactLegacyQueuedIntentCanBeFrozenWithoutAddingCompletionKind() =
        runTest {
            val legacy = row().copy(payload = "{\"attemptId\":\"a1\"}")
            db.lessonAttemptDao().saveAttemptWithAnswers(attempt, listOf(answer), emptyList())
            db.outboxDao().insert(legacy)
            save(legacy)
            assertEquals(legacy.payload, db.lessonAttemptDao().findAttemptIntent("a1")!!.payload)
            assertTrue(runCatching { save() }.exceptionOrNull() is AttemptIntentConflict)
        }

    @Test
    fun quarantineRollbackKeepsMarkerAndCannotReviveTheTerminalAttempt() =
        runTest {
            save()
            val queued = db.outboxDao().findByMutationId(row().mutationId)!!
            db.outboxDao().applyDecision(queued.id, "QUARANTINED", 0, 1, "invalid payload", null)
            db.lessonAttemptDao().rollbackAttempt("a1")
            assertNotNull(db.lessonAttemptDao().findAttemptIntent("a1"))
            assertTrue(runCatching { save() }.exceptionOrNull() is AttemptIntentConflict)
            assertNull(db.lessonAttemptDao().findAttempt("a1"))
            assertTrue(db.lessonAttemptDao().findAttemptAnswers("a1").isEmpty())
            assertEquals("QUARANTINED", db.outboxDao().findByMutationId(row().mutationId)!!.state)
        }

    @Test
    fun concurrentCompleteAndAbortChooseExactlyOneImmutableTerminalRequest() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val results =
                listOf("COMPLETED", "ABORTED").map { kind ->
                    async(Dispatchers.Default) {
                        gate.await()
                        runCatching { save(row(kind)) }
                    }
                }
            gate.complete(Unit)
            val outcomes = results.awaitAll()
            assertEquals(1, outcomes.count { it.isSuccess })
            assertEquals(1, outcomes.count { it.exceptionOrNull() is AttemptIntentConflict })
            val queued = db.outboxDao().findByMutationId(row().mutationId)!!
            assertEquals(queued.payload, db.lessonAttemptDao().findAttemptIntent("a1")!!.payload)
            assertEquals(attempt, db.lessonAttemptDao().findAttempt("a1"))
        }

    @Test
    fun ownerCleanupRemovesOnlyThatOwnersImmutableMarker() =
        runTest {
            save()
            db.lessonAttemptDao().insertAttemptIntent(
                LessonAttemptIntentEntity(
                    "b1",
                    "uid-B",
                    "b-mutation",
                    OutboxOperations.SUBMIT_ATTEMPT,
                    "{}",
                    null,
                    null,
                ),
            )
            db.ownerRowsDao().forgetOwner("uid-A", emptyList())
            assertNull(db.lessonAttemptDao().findAttemptIntent("a1"))
            assertNotNull(db.lessonAttemptDao().findAttemptIntent("b1"))
        }

    @Test
    fun oldFinancialAttemptStillHonorsBackoffWhileGenericExpiryIsImmediatelyVisible() =
        runTest {
            val now = OutboxLimits().maxAgeMs + 10000
            db.outboxDao().insert(row().copy(nextRetryAtMs = now + 5000))
            val generic =
                row().copy(
                    mutationId = "generic",
                    operation = OutboxOperations.SUBMIT_RATING,
                    entityRef = "lesson_runner:rating:r1",
                    nextRetryAtMs = now + 5000,
                )
            db.outboxDao().insert(generic)
            val store = RoomOutboxStore(db.outboxDao())
            assertEquals(listOf("generic"), store.dueRecords("uid-A", now, 50).map { it.mutationId })
            assertEquals(2, store.dueRecords("uid-A", now + 5000, 50).size)
        }

    // ── ordinary-lesson-offline-restart (epic 2.1) ─────────────────────────────

    /**
     * Обычный урок завершён офлайн; процесс умер; приложение открыло ту же базу заново.
     * Показанной после рестарта попыткой/исходом остаются ровно те же строки — и ряд очереди,
     * который ещё не уехал (сеть не участвовала), не мешает их читать.
     */
    @Test
    fun restartReopenShowsTheSameAttemptOutcomeAndTheStillWaitingQueueRow() =
        runTest {
            save()
            val attemptBefore = db.lessonAttemptDao().findAttempt("a1")
            val answersBefore = db.lessonAttemptDao().findAttemptAnswers("a1")
            val intentBefore = db.lessonAttemptDao().findAttemptIntent("a1")
            val queuedBefore = db.outboxDao().findByMutationId(row().mutationId)!!

            db.close()
            db = open()

            assertEquals(attemptBefore, db.lessonAttemptDao().findAttempt("a1"))
            assertEquals(answersBefore, db.lessonAttemptDao().findAttemptAnswers("a1"))
            assertEquals(intentBefore, db.lessonAttemptDao().findAttemptIntent("a1"))
            assertEquals(queuedBefore, db.outboxDao().findByMutationId(row().mutationId))
            // То, что после рестарта показывает экран урока, читается тем же observeByLesson.
            assertEquals(listOf(attemptBefore), db.lessonAttemptDao().observeByLesson("uid-A", "lesson-1").first())
            // Исход (проценты, момент завершения) виден в earnings-проекции без сети.
            val earning = db.lessonAttemptDao().observeEarningsSince("uid-A", 0).first().single()
            assertEquals(100, earning.percentScore)
            assertEquals(1000L, earning.completedAt)
            // Сетевой гейт отсутствует: результат читаем, пока ряд очереди не отправлен и
            // серверной квитанции не существует.
            assertEquals("WAITING", db.outboxDao().findByMutationId(row().mutationId)!!.state)
            assertNull(db.mutationReceiptDao().find("uid-A", row().mutationId))
        }

    /** После рестарта тот же исход не задваивается и не переписывается. */
    @Test
    fun afterRestartIdenticalResaveAddsNoRowAndChangedOutcomeConflicts() =
        runTest {
            save()
            db.close()
            db = open()

            save() // тот же терминальный запрос после рестарта
            val queued = db.outboxDao().findByMutationId(row().mutationId)!!
            assertEquals("WAITING", queued.state)
            assertEquals(0, queued.attemptCount)
            assertEquals(1, db.outboxDao().countPending("uid-A"))

            assertTrue(runCatching { save(row("ABORTED")) }.exceptionOrNull() is AttemptIntentConflict)
            assertEquals(attempt, db.lessonAttemptDao().findAttempt("a1"))
        }
}
