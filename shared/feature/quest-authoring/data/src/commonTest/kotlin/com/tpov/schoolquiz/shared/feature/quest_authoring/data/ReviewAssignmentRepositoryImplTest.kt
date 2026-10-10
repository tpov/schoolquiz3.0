package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.MutationTransport
import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.outbox.OutboxDecision
import com.tpov.schoolquiz.shared.core.outbox.OutboxEngine
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxPolicy
import com.tpov.schoolquiz.shared.core.outbox.OutboxQuarantineRouter
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.core.outbox.QuarantineListener
import com.tpov.schoolquiz.shared.core.persistence.ReviewAssignmentEntity
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox.ReviewActionQuarantine
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox.SubmitReviewOutboxWriter
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewActionSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignmentKind
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignmentSyncState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewQuestionScore
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewSegmentDecision
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewSegmentDecisionKind
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitReviewActionCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Решение ревьюера — намерение без локальной половины (AD-25): в очередь ложится одна запись,
 * строка задания не трогается, callable не зовётся. Карантин — пометка по `entity_ref`, не откат.
 */
class ReviewAssignmentRepositoryImplTest {
    @Test
    fun offline_submitLeavesOneQueueRowAndTouchesNothingElse() =
        runTest {
            val local = FakeLocal(listOf(assignmentEntity()))
            val outbox = FakeOutboxStore()
            val repository = ReviewAssignmentRepositoryImpl(local, outbox)
            val before = repository.observeAssignments(OWNER).first()

            val result = repository.queueReviewAction(submission(score = 2))

            assertEquals(Result.success(Unit), result)
            val row = outbox.all.single()
            assertEquals(OutboxOperations.SUBMIT_REVIEW, row.operation)
            assertEquals("quest_authoring.SUBMIT_REVIEW", row.operation)
            assertEquals("quest_authoring.ASSIGNMENT:assignment-1", row.entityRef)
            assertEquals("quest_authoring-SUBMIT_REVIEW-review-action-1", row.mutationId)
            assertEquals(OutboxState.WAITING, row.state)
            assertNull(row.expectedVersion, "задание клиентом не версионируется: предусловие проверяет сервер (AD-27)")
            assertEquals(REQUESTED_AT_MS, row.createdAtMs)

            // Локальная строка задания не менялась — ни записью, ни содержимым.
            assertEquals(0, local.writes, "локальная половина пуста (AD-25)")
            assertEquals(before, repository.observeAssignments(OWNER).first())
            assertEquals(
                ReviewAssignmentSyncState.PENDING,
                repository.observeAssignmentSync(OWNER, "assignment-1").first(),
            )
        }

    @Test
    fun payload_isTheSerialisedCommandWithAFormatVersion() =
        runTest {
            val outbox = FakeOutboxStore()
            val repository = ReviewAssignmentRepositoryImpl(FakeLocal(), outbox)

            repository.queueReviewAction(submission(score = 3))

            val payload = Json.parseToJsonElement(outbox.all.single().payload).jsonObject
            assertEquals(
                SubmitReviewOutboxWriter.FORMAT_VERSION,
                payload.getValue("formatVersion").jsonPrimitive.content.toInt(),
            )
            assertEquals("review-action-1", payload.getValue("actionId").jsonPrimitive.content)
            assertEquals("assignment-1", payload.getValue("assignmentId").jsonPrimitive.content)
            assertEquals("lesson-1", payload.getValue("lessonId").jsonPrimitive.content)
            assertEquals("TESTING", payload.getValue("kind").jsonPrimitive.content)
            assertEquals(3, payload.getValue("score").jsonPrimitive.content.toInt())
            assertEquals(JsonNull, payload.getValue("language"))
            assertEquals(JsonNull, payload.getValue("targetReviewId"))
            assertEquals(0, payload.getValue("translatedQuestions").jsonArray.size)
            assertEquals(0, payload.getValue("segmentResults").jsonArray.size)
            assertEquals(0, payload.getValue("questionScores").jsonArray.size)
        }

    @Test
    fun payloadCarriesPerQuestionScores_andExplicitSegmentDecisions() =
        runTest {
            val outbox = FakeOutboxStore()
            val repository = ReviewAssignmentRepositoryImpl(FakeLocal(), outbox)

            repository.queueReviewAction(
                submission(score = 3).copy(
                    command =
                        submission(score = 3).command.copy(
                            questionScores =
                                listOf(
                                    ReviewQuestionScore("q1", 3),
                                    ReviewQuestionScore("q2", 1),
                                ),
                            segmentResults =
                                listOf(
                                    ReviewSegmentDecision("q1", "text", ReviewSegmentDecisionKind.CORRECT),
                                    ReviewSegmentDecision("q1", "option:A", ReviewSegmentDecisionKind.ERROR),
                                ),
                        ),
                ),
            )

            val payload = Json.parseToJsonElement(outbox.all.single().payload).jsonObject
            assertEquals(
                listOf("q1" to 3, "q2" to 1),
                payload.getValue("questionScores").jsonArray.map {
                    val entry = it.jsonObject
                    entry.getValue("questionId").jsonPrimitive.content to
                        entry.getValue("score").jsonPrimitive.content.toInt()
                },
            )
            assertEquals(
                listOf("correct", "error"),
                payload.getValue("segmentResults").jsonArray.map {
                    it.jsonObject.getValue("decision").jsonPrimitive.content
                },
            )
            // Молчаливого одобрения в проводе больше нет: решение — единственный носитель смысла.
            assertEquals(null, payload.getValue("segmentResults").jsonArray[0].jsonObject["accepted"])
        }

    @Test
    fun perQuestionScoreOutsideTheScaleIsRefusedBeforeTheQueue() {
        assertFailsWith<IllegalArgumentException> { ReviewQuestionScore("q1", 0) }
        assertFailsWith<IllegalArgumentException> { ReviewQuestionScore("q1", 4) }
        assertFailsWith<IllegalArgumentException> { ReviewQuestionScore(" ", 2) }
    }

    @Test
    fun theSameIntentAlwaysGetsTheSameKey_andAnotherIntentAnotherOne() {
        val first = SubmitReviewOutboxWriter.record(submission(score = 2))
        val again = SubmitReviewOutboxWriter.record(submission(score = 2))
        val other = SubmitReviewOutboxWriter.record(submission(id = "review-action-2", score = 3))

        // Ключ выведен из намерения, а не сгенерирован при постановке (AD-2).
        assertEquals(first.mutationId, again.mutationId)
        assertNotEquals(first.mutationId, other.mutationId)
    }

    @Test
    fun nothingQueued_answersNoneNotNull() =
        runTest {
            val repository = ReviewAssignmentRepositoryImpl(FakeLocal(), FakeOutboxStore())

            assertEquals(
                ReviewAssignmentSyncState.NONE,
                repository.observeAssignmentSync(OWNER, "assignment-1").first(),
            )
        }

    @Test
    fun serverRefusal_quarantinesTheRowAndMarksTheAssignment_withoutTouchingItsRow() =
        runTest {
            val local = FakeLocal(listOf(assignmentEntity()))
            val outbox = FakeOutboxStore()
            val repository = ReviewAssignmentRepositoryImpl(local, outbox)
            repository.queueReviewAction(submission(score = 2))
            val refusing = MutationTransport { Result.failure(SyncFailure(SyncError.Refused("assignment reassigned"))) }
            val engine =
                OutboxEngine(
                    store = outbox,
                    receipts = FakeMutationReceiptStore(),
                    sessions = { OutboxSession(OWNER, 0) },
                    transport = refusing,
                    clock = { REQUESTED_AT_MS + 1 },
                    onQuarantined =
                        OutboxQuarantineRouter(
                            handlers = mapOf(OutboxOperations.SUBMIT_REVIEW to ReviewActionQuarantine()),
                            onUnhandled =
                                QuarantineListener {
                                    fail(
                                        "обработчик фичи должен быть найден по operation",
                                    )
                                },
                        ),
                )

            val summary = engine.drain(OWNER)

            assertEquals(1, summary.quarantined)
            // Запрос по entity_ref отвечает «карантин» (история 4.4) …
            assertEquals(
                ReviewAssignmentSyncState.QUARANTINED,
                repository.observeAssignmentSync(OWNER, "assignment-1").first(),
            )
            // … счётчик карантинных растёт на единицу (история 4.5) …
            assertEquals(1, outbox.observeCounts(OWNER).first().quarantined)
            // … а строка задания как лежала, так и лежит: откатывать нечего (AD-25).
            assertEquals(0, local.writes)
            assertEquals(listOf(assignmentEntity()), local.assignments.value)
        }

    @Test
    fun otherAccountsRow_isNotThisAccountsMark() =
        runTest {
            val outbox = FakeOutboxStore()
            val repository = ReviewAssignmentRepositoryImpl(FakeLocal(), outbox)
            repository.queueReviewAction(submission(score = 2))

            assertEquals(
                ReviewAssignmentSyncState.NONE,
                repository.observeAssignmentSync("someone-else", "assignment-1").first(),
            )
        }

    private fun submission(
        id: String = "review-action-1",
        score: Int,
    ) = ReviewActionSubmission(
        id = id,
        ownerUid = OWNER,
        requestedAtMs = REQUESTED_AT_MS,
        command =
            SubmitReviewActionCommand(
                assignmentId = "assignment-1",
                lessonId = "lesson-1",
                kind = ReviewAssignmentKind.TESTING,
                score = score,
            ),
    )

    private fun assignmentEntity() =
        ReviewAssignmentEntity(
            id = "assignment-1",
            submissionId = "submission-1",
            ownerUid = OWNER,
            catalogId = "catalog-1",
            draftId = "draft-1",
            questId = "quest-1",
            lessonId = "lesson-1",
            title = "Lesson for review",
            createdAtMs = 1L,
            taskKinds = listOf("TESTING"),
            sourceLanguages = listOf("uk"),
            newTranslationLanguages = emptyList(),
            reviewLanguages = emptyList(),
            isTested = false,
            testingScore = null,
            isLogicReviewed = false,
            logicScore = null,
            isTranslationReviewed = false,
            translationScore = null,
            translatedLanguages = emptyList(),
        )

    /** Таблица заданий в памяти, считающая записи, — их обязано быть ноль. */
    private class FakeLocal(
        initial: List<ReviewAssignmentEntity> = emptyList(),
    ) : ReviewAssignmentLocalDataSource {
        val assignments = MutableStateFlow(initial)
        var writes = 0

        override fun observeAssignments(ownerUid: String): Flow<List<ReviewAssignmentEntity>> =
            assignments.map { list -> list.filter { it.ownerUid == ownerUid } }

        override suspend fun findAssignments(ownerUid: String): List<ReviewAssignmentEntity> =
            assignments.value.filter { it.ownerUid == ownerUid }

        override suspend fun replaceAssignments(
            ownerUid: String,
            assignments: List<ReviewAssignmentEntity>,
        ) {
            writes++
            this.assignments.value = assignments
        }

        override suspend fun applyAssignmentChanges(
            ownerUid: String,
            changedIds: Set<String>,
            assignments: List<ReviewAssignmentEntity>,
        ) {
            writes++
            this.assignments.value = assignments
        }
    }

    /** Очередь в памяти: то же правило выбора записи по сущности, что и в запросе Room. */
    private class FakeOutboxStore : OutboxStore {
        private val records = MutableStateFlow<List<OutboxRecord>>(emptyList())
        private var nextId = 1L
        val all: List<OutboxRecord> get() = records.value

        override suspend fun enqueue(record: OutboxRecord): OutboxRecord {
            records.value.firstOrNull { it.mutationId == record.mutationId }?.let { return it }
            val stored = record.copy(id = nextId++)
            records.value = records.value + stored
            return stored
        }

        override suspend fun dueRecords(
            ownerUid: String,
            nowMs: Long,
            limit: Int,
        ): List<OutboxRecord> =
            records.value.filter { it.ownerUid == ownerUid && OutboxPolicy().isDue(it, nowMs) }.take(limit)

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

        override fun observeCounts(ownerUid: String): Flow<OutboxCounts> =
            records.map { list ->
                val mine = list.filter { it.ownerUid == ownerUid }
                OutboxCounts(
                    waiting = mine.count { it.state == OutboxState.WAITING },
                    waitingPrecondition = mine.count { it.state == OutboxState.WAITING_PRECONDITION },
                    conflicted = mine.count { it.state == OutboxState.CONFLICT },
                    quarantined = mine.count { it.state == OutboxState.QUARANTINED },
                )
            }

        override fun observeEntity(
            ownerUid: String,
            entityRef: String,
        ): Flow<OutboxEntitySync> =
            records.map { list ->
                list.pick(ownerUid, entityRef)
                    ?.let { OutboxEntitySync.Queued(it.id, it.state, it.expectedVersion, it.serverVersion) }
                    ?: OutboxEntitySync.None
            }

        override suspend fun findByEntityRef(
            ownerUid: String,
            entityRef: String,
        ): OutboxRecord? = records.value.pick(ownerUid, entityRef)

        private fun List<OutboxRecord>.pick(
            ownerUid: String,
            entityRef: String,
        ): OutboxRecord? =
            filter { it.ownerUid == ownerUid && it.entityRef == entityRef }
                .sortedWith(
                    compareByDescending<OutboxRecord> { it.state == OutboxState.CONFLICT }
                        .thenByDescending { it.createdAtMs },
                ).firstOrNull()

        override suspend fun quarantined(ownerUid: String): List<OutboxRecord> =
            records.value.filter { it.ownerUid == ownerUid && it.state == OutboxState.QUARANTINED }

        override suspend fun countPending(ownerUid: String): Int =
            records.value.count { it.ownerUid == ownerUid && it.state.isPending }

        /** Вторая операция AD-8: после согласия на смену аккаунта записи прежнего владельца снимаются. */
        override suspend fun forget(ownerUid: String) {
            records.value = records.value.filterNot { it.ownerUid == ownerUid }
        }
    }

    private companion object {
        const val OWNER = "reviewer-1"
        const val REQUESTED_AT_MS = 5_000L
    }
}
