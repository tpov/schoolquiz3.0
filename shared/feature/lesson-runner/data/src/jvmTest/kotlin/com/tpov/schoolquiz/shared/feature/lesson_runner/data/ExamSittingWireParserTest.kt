package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamSittingWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamRefusal
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamStartOutcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Строгий разбор проводных форм сидинга: приватный ключ в ответе — испорченная форма, опциональные
 * ключи — часть контракта, число вне безопасного диапазона — «неизвестно», не ноль.
 */
class ExamSittingWireParserTest {
    private val parser = ExamSittingWireParser()

    @Test
    fun `a refused start parses into the typed refusal`() {
        val wire = """{"refused":true,"kind":"Cooldown","availableAtMs":1760086400000}"""
        val outcome = parser.parseStartOutcome(wire)
        val refused = outcome as ExamStartOutcome.Refused
        assertEquals(1_760_086_400_000L, (refused.refusal as ExamRefusal.Cooldown).availableAtMs)
    }

    @Test
    fun `a refusal without optional keys still parses`() {
        val wire = """{"refused":true,"kind":"InsufficientCharge"}"""
        val outcome = parser.parseStartOutcome(wire)
        assertEquals(ExamRefusal.InsufficientCharge, (outcome as ExamStartOutcome.Refused).refusal)
    }

    @Test
    fun `a private key inside a public reply is malformed wire, not extra information`() {
        val wire = """
            {"recovered":false,"debited":100,"lifePoints":9900,"schemaVersion":1,
             "sittingId":"s1","mode":"ThemeTest","assessmentId":"th0","state":"OPEN",
             "currentIndex":0,"totalQuestions":20,
             "rules":{"nextQuestionWindowSeconds":60,"firstAckGapSeconds":60,
                      "timerRule":"ALL_HARD_ORDINARY","timerRuleVersion":1},
             "nextQuestion":{"index":0,"questionId":"e0","revision":2,"shownDifficulty":"EASY",
                "answerSeconds":24,"publicPayload":"{}",
                "ack":{"ackedAtMs":1,"answerDeadlineMs":2},"serverNowMs":3},
             "serverNowMs":3,"canonicalContent":{"correctOptionId":"a"}}
        """.trimIndent()
        try {
            parser.parseStartOutcome(wire)
            throw AssertionError("canonical content must not be accepted in a public reply")
        } catch (expected: ExamSittingWireParser.MalformedExamWire) {
            assertTrue(true)
        }
    }

    @Test
    fun `a missing required key is malformed, not defaulted`() {
        val wire = """{"recovered":false,"debited":100,"schemaVersion":1,"sittingId":"s1"}"""
        try {
            parser.parseStartOutcome(wire)
            throw AssertionError("missing keys must not be defaulted to zero")
        } catch (expected: ExamSittingWireParser.MalformedExamWire) {
            assertTrue(true)
        }
    }

    @Test
    fun `a number outside the safe server range is unknown, not a plausible zero`() {
        val reply = JsonObject(
            mapOf(
                "sittingId" to JsonPrimitive("s1"),
                "state" to JsonPrimitive("OPEN"),
                "result" to JsonPrimitive(0),
                "open" to JsonPrimitive(true),
                "closedAtMs" to JsonPrimitive("9300000000000000000"),
            ),
        )
        try {
            parser.parseFetchOutcome(reply.toString())
            throw AssertionError("out-of-range number must be unknown")
        } catch (expected: ExamSittingWireParser.MalformedExamWire) {
            assertTrue(true)
        }
    }

    @Test
    fun `the terminal projection parses verbatim with nullable stars`() {
        val wire = """
            {"sittingId":"s1","state":"CLOSED","open":false,"result":{
              "schemaVersion":1,"sittingId":"s1","mode":"FinalExam","assessmentId":"c",
              "resultVersion":1,"percent":100,"passed":true,"starsTenths":null,
              "advice":["MASTERED","CERTIFICATE","FINAL_RESULT"],"closedAtMs":5,
              "nextAvailableAtMs":null}}
        """.trimIndent()
        val outcome = parser.parseFetchOutcome(wire)
        val terminal = outcome as com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamFetchOutcome.Terminal
        assertEquals("финальные звёзды — Q-EX-7, вне объёма", null, terminal.result.starsTenths)
        assertEquals(100, terminal.result.percent)
    }

    @Test
    fun `a refused start round-trips through the json canon`() {
        // Firebase привозит числа как Integer/Long/Double; форма ответа при этом одна.
        val wire = """{"refused":true,"kind":"PoolTooSmall"}"""
        val outcome = parser.parseStartOutcome(wire)
        assertEquals(
            ExamRefusal.PoolTooSmall,
            (outcome as ExamStartOutcome.Refused).refusal,
        )
    }
}
