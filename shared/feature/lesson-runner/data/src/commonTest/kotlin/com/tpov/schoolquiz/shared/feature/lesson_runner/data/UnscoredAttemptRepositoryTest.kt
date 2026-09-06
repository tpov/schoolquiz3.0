package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.question_schema.BlankId
import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake.FakeContentHierarchy
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake.FakeLessonAttemptDao
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.LessonResultOutboxWriter
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonAttemptRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptCompletionKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RawAnsweredQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServedQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UnscoredTerminalIntent
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnscoredAttemptRepositoryTest {
    private val rawAnswers = listOf(
        UserAnswer.SingleChoiceAnswer(OptionId("option-b")),
        UserAnswer.MultipleChoiceAnswer(setOf(OptionId("x"), OptionId("y"))),
        UserAnswer.OrderingAnswer(listOf(OptionId("ri-2"), OptionId("ri-0"), OptionId("ri-1"))),
        UserAnswer.FillBlankAnswer(mapOf(BlankId("blank-a") to CandidateId("rc-3"))),
    )

    private fun intent(kind: AttemptCompletionKind = AttemptCompletionKind.COMPLETED) = UnscoredTerminalIntent(
        AttemptIdentity(AttemptId("raw-1"), "uid-1", LessonId(FakeContentHierarchy.LESSON_ID), 3, Difficulty.HARD),
        kind, 4000,
        rawAnswers.mapIndexed { index, answer -> RawAnsweredQuestion(QuestionId("q-$index"), index, answer, 2000 + index.toLong(), 100, false) },
        (0..4).map { ServedQuestion(QuestionId("q-$it"), it) },
        ChargeClaimMask.none(5),
    )

    @Test fun writerOmitsAllScoresAndPreservesActualAnswersAndUnansweredServedPosition() = runTest {
        val row = FakeContentHierarchy.roomWriter().buildUnscoredAttemptRow(intent())
        val body = Json.parseToJsonElement(row.payload).jsonObject
        assertFalse("codeAnswer" in body)
        assertFalse("percentScore" in body)
        assertFalse("chargeClaims" in body)
        assertEquals("COMPLETED", body.getValue("completionKind").jsonPrimitive.content)
        assertEquals(5, body.getValue("served").jsonArray.size)
        val encodedAnswers = body.getValue("answers").jsonArray
        assertEquals(4, encodedAnswers.size)
        encodedAnswers.forEachIndexed { index, encoded ->
            assertFalse("score" in encoded.jsonObject)
            val decoded = Json.decodeFromString(UserAnswer.serializer(), encoded.jsonObject.getValue("answerPayload").jsonPrimitive.content)
            assertEquals(rawAnswers[index], decoded)
        }
        assertEquals("uid-1", row.ownerUid)
        assertEquals(OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, "raw-1"), row.mutationId)
    }

    @Test fun frozenTerminalTimestampMakesRebuildIndependentOfCurrentClock() = runTest {
        val early = FakeContentHierarchy.roomWriter(nowMs = 1).buildUnscoredAttemptRow(intent())
        val late = FakeContentHierarchy.roomWriter(nowMs = 999999).buildUnscoredAttemptRow(intent())
        assertEquals(early, late)
        assertEquals(4000L, early.createdAtMs)
    }

    @Test fun realWriterAndRepositorySaveOnlyIntentAndQueueNeverScoredRowsOrRepetitions() = runTest {
        val dao = FakeLessonAttemptDao()
        val repository = LessonAttemptRepositoryImpl(dao, FakeContentHierarchy.roomWriter())
        assertTrue(repository.saveUnscored(intent()).isSuccess)
        assertTrue(dao.attempts.isEmpty() && dao.answers.isEmpty() && dao.repetitions.isEmpty())
        assertEquals(0, dao.upsertCallCount)
        assertEquals(1, dao.intents.size)
        assertEquals(1, dao.outboxRows.size)
        assertTrue(repository.saveUnscored(intent()).isSuccess)
        assertEquals(1, dao.outboxRows.size)
    }

    @Test fun firstRawCompletionSurvivesAckAndConflictingAbort() = runTest {
        val dao = FakeLessonAttemptDao()
        val repository = LessonAttemptRepositoryImpl(dao, FakeContentHierarchy.roomWriter())
        repository.saveUnscored(intent()).getOrThrow()
        val marker = dao.intents.getValue("raw-1")
        dao.outboxRows.clear() // transport ACK, not an instruction to resubmit
        assertTrue(repository.saveUnscored(intent()).isSuccess)
        assertTrue(repository.saveUnscored(intent(AttemptCompletionKind.ABORTED)).isFailure)
        assertEquals(marker, dao.intents.getValue("raw-1"))
        assertTrue(dao.outboxRows.isEmpty() && dao.attempts.isEmpty())
    }

    @Test fun noOpWriterFailsClosedAndDoesNotCreateMarker() = runTest {
        val dao = FakeLessonAttemptDao()
        assertTrue(LessonAttemptRepositoryImpl(dao).saveUnscored(intent()).isFailure)
        assertTrue(dao.intents.isEmpty() && dao.outboxRows.isEmpty() && dao.attempts.isEmpty())
    }

    @Test fun ordinaryRawWriterRefusesEasyAndUnbackedPlasmaClaims() = runTest {
        val writer = FakeContentHierarchy.roomWriter()
        assertFailsWith<IllegalArgumentException> {
            writer.buildUnscoredAttemptRow(intent().let { it.copy(identity = it.identity.copy(mode = Difficulty.EASY)) })
        }
        assertFailsWith<IllegalArgumentException> {
            writer.buildUnscoredAttemptRow(intent().copy(claims = ChargeClaimMask("P....")))
        }
    }

    @Test fun cancellationDoesNotBecomeSaveFailureOrFakeResult() = runTest {
        val dao = FakeLessonAttemptDao()
        val writer = object : LessonResultOutboxWriter {
            override suspend fun buildUnscoredAttemptRow(intent: UnscoredTerminalIntent): com.tpov.schoolquiz.shared.core.persistence.OutboxEntity? {
                throw CancellationException("cancelled")
            }
        }
        assertFailsWith<CancellationException> { LessonAttemptRepositoryImpl(dao, writer).saveUnscored(intent()) }
        assertTrue(dao.intents.isEmpty())
    }
}
