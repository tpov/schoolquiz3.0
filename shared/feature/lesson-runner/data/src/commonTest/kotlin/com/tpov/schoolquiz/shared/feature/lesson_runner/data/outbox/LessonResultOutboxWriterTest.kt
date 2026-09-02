package com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox

import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestionAnswerEntity
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake.FakeContentHierarchy
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServedQuestion
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Тело строки очереди для прохождения. Проверяется та часть, что появилась вместе со списком
 * показанных вопросов: сам список, его форма, что остальное тело от него не меняется, и что тело,
 * не сходящееся с цифрами рядом, здесь и отвергается.
 */
class LessonResultOutboxWriterTest {

    private val writer = FakeContentHierarchy.roomWriter()

    @Test
    fun servedIsWrittenBesideAnswersWithTheAnswerRowsFieldNames() = runTest {
        val attempt = attempt(codeAnswer = "9010")
        val answers = listOf(answerRow(attempt, questionId = "q-0", codeAnswerIndex = 0))
        val served = listOf(ServedQuestion(QuestionId("q-0"), 0), ServedQuestion(QuestionId("q-2"), 2))

        val body = writer.buildAttemptRow(attempt, answers, served).body()

        val servedJson = body.getValue("served").jsonArray
        assertEquals(2, servedJson.size)
        servedJson.forEach { entry ->
            assertEquals(setOf("questionId", "codeAnswerIndex"), entry.jsonObject.keys)
        }
        assertEquals("q-0", servedJson[0].jsonObject.getValue("questionId").jsonPrimitive.content)
        assertEquals(0, servedJson[0].jsonObject.getValue("codeAnswerIndex").jsonPrimitive.int)
        assertEquals("q-2", servedJson[1].jsonObject.getValue("questionId").jsonPrimitive.content)
        assertEquals(2, servedJson[1].jsonObject.getValue("codeAnswerIndex").jsonPrimitive.int)

        // The answer row for the same question reads identically on the two fields they share.
        val answerJson = body.getValue("answers").jsonArray.single().jsonObject
        assertEquals(answerJson.getValue("questionId"), servedJson[0].jsonObject.getValue("questionId"))
        assertEquals(answerJson.getValue("codeAnswerIndex"), servedJson[0].jsonObject.getValue("codeAnswerIndex"))
    }

    @Test
    fun servedIsOmittedWhenNoListWasSupplied() = runTest {
        // Absent and empty are different answers to the server: absent is "unknown", empty is "none".
        // The two fake-only save forms supply no list, and must not claim that nothing was shown.
        val attempt = attempt(codeAnswer = "9010")
        val answers = listOf(answerRow(attempt, questionId = "q-0", codeAnswerIndex = 0))

        assertFalse("served" in writer.buildAttemptRow(attempt).body())
        assertFalse("served" in writer.buildAttemptRow(attempt, answers).body())
    }

    @Test
    fun servedIsAnEmptyArrayWhenTheSuppliedListIsEmpty() = runTest {
        // A list that was supplied and is empty really does mean "nothing was shown".
        val body = writer.buildAttemptRow(attempt(codeAnswer = "0"), served = emptyList()).body()

        assertEquals(JsonArray(emptyList()), body.getValue("served"))
    }

    @Test
    fun theRestOfTheRowDoesNotChangeWithServed() = runTest {
        val attempt = attempt(codeAnswer = "9010")
        val answers = listOf(answerRow(attempt, questionId = "q-0", codeAnswerIndex = 0))
        val served = listOf(ServedQuestion(QuestionId("q-0"), 0), ServedQuestion(QuestionId("q-2"), 2))

        val without = writer.buildAttemptRow(attempt, answers)
        val with = writer.buildAttemptRow(attempt, answers, served)

        assertEquals(without.body() - "served", with.body() - "served")
        assertEquals(without.copy(payload = ""), with.copy(payload = ""))
    }

    @Test
    fun servedPositionOutsideTheCodeAnswerIsRefused() = runTest {
        val attempt = attempt(codeAnswer = "9010") // positions 0..3
        val outside = listOf(ServedQuestion(QuestionId("q-4"), 4))

        assertFailsWith<IllegalArgumentException> { writer.buildAttemptRow(attempt, emptyList(), outside) }
    }

    @Test
    fun answerRowNotInServedIsRefused() = runTest {
        val attempt = attempt(codeAnswer = "9010")
        val answers = listOf(answerRow(attempt, questionId = "q-0", codeAnswerIndex = 0))

        val withoutTheQuestion = listOf(ServedQuestion(QuestionId("q-2"), 2))
        assertFailsWith<IllegalArgumentException> { writer.buildAttemptRow(attempt, answers, withoutTheQuestion) }

        // Same question claimed at another position is a different claim, and refused too.
        val atAnotherPosition = listOf(ServedQuestion(QuestionId("q-0"), 2))
        assertFailsWith<IllegalArgumentException> { writer.buildAttemptRow(attempt, answers, atAnotherPosition) }
    }

    private fun OutboxEntity.body(): JsonObject = Json.parseToJsonElement(payload).jsonObject

    private fun attempt(codeAnswer: String = "9") = Attempt(
        id = AttemptId("attempt-1"),
        userId = "user-1",
        lessonId = LessonId(FakeContentHierarchy.LESSON_ID),
        lessonVersion = 3L,
        mode = Difficulty.EASY,
        completedAt = 4_000L,
        codeAnswer = CodeAnswer(codeAnswer),
        percentScore = PercentScore(100),
    )

    private fun answerRow(attempt: Attempt, questionId: String, codeAnswerIndex: Int) = QuestionAnswerEntity(
        attemptId = attempt.id.value,
        questionId = questionId,
        userId = attempt.userId,
        lessonId = attempt.lessonId.value,
        lessonVersion = attempt.lessonVersion,
        isHard = 0,
        codeAnswerIndex = codeAnswerIndex,
        score = 9,
        answerPayload = "{}",
        answeredAtMs = 1L,
        durationMs = 1L,
        wasTimeout = 0,
    )
}
