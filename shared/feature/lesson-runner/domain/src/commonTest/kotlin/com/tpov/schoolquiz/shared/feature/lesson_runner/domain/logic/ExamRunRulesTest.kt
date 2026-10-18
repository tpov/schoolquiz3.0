package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamAck
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamResultProjection
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerDuration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Зеркала экзаменационного прогона: таймер по all-HARD правилу, линейные звёзды, вердикт ACK'а
 * и красацированный-единственный разбор payload'а.
 *
 * Протокол (случай 14): 100-символьные EASY и HARD позиции темы получают 24 секунды; пол —
 * 5 секунд; examFactor 0.75 не существует нигде.
 */
class ExamRunRulesTest {

    @Test
    fun `given 100 chars then the exam allowance is 24 seconds`() {
        assertEquals(24, examAnswerSecondsFor(100))
        assertEquals(24, examAnswerSeconds("HARD", 100, hasImage = false))
        assertEquals(24, examAnswerSeconds("EASY", 100, hasImage = false))
    }

    @Test
    fun `given 10 chars then the allowance floors at 5 seconds`() {
        assertEquals(5, examAnswerSecondsFor(10))
        assertEquals(5, examAnswerSecondsFor(0))
        assertEquals(5, examAnswerSecondsFor(12))
    }

    @Test
    fun `given half-up boundary then 100 chars rounds 24 not 25`() {
        // 100 × 0.24 = 24 ровно; 105 × 0.24 = 25.2 → 25; 104 × 0.24 = 24.96 → 25 (половина вверх).
        assertEquals(24, examAnswerSecondsFor(100))
        assertEquals(25, examAnswerSecondsFor(104))
        assertEquals(25, examAnswerSecondsFor(105))
    }

    @Test
    fun `given the shown difficulty then the coefficient never changes`() {
        // Случай 14: пять EASY позиций темы не превращаются в HARD-цены и не получают 0.75.
        val easy = examAnswerSeconds("EASY", 100, hasImage = false)
        val hard = examAnswerSeconds("HARD", 100, hasImage = false)
        assertEquals(hard, easy)
        assertEquals(24, easy)
    }

    @Test
    fun `given an image then it counts as 100 chars`() {
        assertEquals(24, examAnswerSeconds("HARD", 0, hasImage = true))
        assertEquals(examAnswerSecondsFor(126), examAnswerSeconds("HARD", 26, hasImage = true))
    }

    @Test
    fun `given a wrong shown difficulty label then the mirror refuses`() {
        assertFailsWith<IllegalArgumentException> {
            examAnswerSeconds("MEDIUM", 100, hasImage = false)
        }
    }

    @Test
    fun `given a negative char count then the mirror refuses`() {
        assertFailsWith<IllegalArgumentException> { examAnswerSecondsFor(-1) }
    }

    @Test
    fun `given the linear star mirror then the protocol endpoints hold`() {
        assertEquals(0, themeStarsTenthsMirror(0))
        assertEquals(15, themeStarsTenthsMirror(50))
        assertEquals(30, themeStarsTenthsMirror(100))
        assertEquals(5, themeStarsTenthsMirror(15), "15% → 0.5 звезды")
    }

    @Test
    fun `given a captured 60 mark then 59 and 60 both display 1_8 stars`() {
        assertEquals(18, themeStarsTenthsMirror(59))
        assertEquals(18, themeStarsTenthsMirror(60))
        // Но проход решает сервер по сырому проценту: звёзды — только отображение.
    }

    @Test
    fun `given an out-of-range percent then the star mirror refuses`() {
        assertFailsWith<IllegalArgumentException> { themeStarsTenthsMirror(101) }
        assertFailsWith<IllegalArgumentException> { themeStarsTenthsMirror(-1) }
    }

    @Test
    fun `given an established ack then a duplicate returns it and never extends`() {
        val established = ExamAck(ackedAtMs = 1_000, answerDeadlineMs = 25_000)
        val decision = examAckVerdict(
            established = established,
            nowMs = 60_000,
            boundForQuestion = 61_000,
            answerSeconds = 24,
        )
        assertIs<ExamAckDecision.Duplicate>(decision)
        assertEquals(established, decision.ack)
    }

    @Test
    fun `given a first ack within the window then the deadline fixes from the ack moment`() {
        val decision = examAckVerdict(
            established = null,
            nowMs = 10_000,
            boundForQuestion = 70_000,
            answerSeconds = 24,
        )
        assertIs<ExamAckDecision.Establish>(decision)
        assertEquals(10_000, decision.ack.ackedAtMs)
        assertEquals(34_000, decision.ack.answerDeadlineMs)
    }

    @Test
    fun `given an ack in the last second of the window then the deadline counts from the ack`() {
        // Окно ограничивает опоздание ACK'а, но не удлиняет ответные секунды: закреплённый
        // дедлайн — от момента подтверждения рендера.
        val decision = examAckVerdict(
            established = null,
            nowMs = 69_000,
            boundForQuestion = 70_000,
            answerSeconds = 24,
        )
        assertIs<ExamAckDecision.Establish>(decision)
        assertEquals(93_000, decision.ack.answerDeadlineMs)
    }

    @Test
    fun `given an expired window then the ack is refused and the server closes the question`() {
        val decision = examAckVerdict(
            established = null,
            nowMs = 70_001,
            boundForQuestion = 70_000,
            answerSeconds = 24,
        )
        assertIs<ExamAckDecision.Expired>(decision)
    }

    @Test
    fun `given a server result then rendering never recalculates`() {
        val result = ExamResultProjection(
            sittingId = "s1",
            mode = ExamMode.THEME_TEST,
            assessmentId = "th0",
            resultVersion = 1,
            percent = 59,
            passed = false,
            starsTenths = 18,
            advice = listOf("DEVELOPING", "BACK_TO_LESSONS", "THEME_RESULT"),
            closedAtMs = 100L,
            nextAvailableAtMs = 100L + 24 * 3600 * 1000,
        )
        assertEquals(result, renderResult(result))
        assertEquals(1.8, result.starsDisplay)
        assertTrue(!result.passed, "проход — серверное значение, не производная от звёзд")
    }

    @Test
    fun `given a result out of range then the projection refuses`() {
        assertFailsWith<IllegalArgumentException> {
            ExamResultProjection(
                sittingId = "s", mode = ExamMode.FINAL_EXAM, assessmentId = "c",
                resultVersion = 1, percent = 101, passed = false, starsTenths = null,
                advice = listOf("a", "b", "c"), closedAtMs = 1L,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ExamResultProjection(
                sittingId = "s", mode = ExamMode.THEME_TEST, assessmentId = "t",
                resultVersion = 1, percent = 50, passed = true, starsTenths = 31,
                advice = listOf("a", "b", "c"), closedAtMs = 1L,
            )
        }
    }

    @Test
    fun `given a redacted payload then only the redacted hierarchy can decode it`() {
        // Публичная половина SingleChoice: правильный вариант структурно отсутствует.
        val payload = """
            {"type":"SingleChoiceRedacted","id":"e0","difficulty":"EASY","text":"q?",
             "imageUrl":null,
             "options":[{"id":"ri-0","text":"a"},{"id":"ri-1","text":"b"}]}
        """.trimIndent()
        val content = parseRedactedExamPayload(payload)
        assertIs<com.tpov.schoolquiz.shared.core.question_schema.RedactedQuestionContent.SingleChoice>(content)
        // Никакого правильного идентификатора в публичной половине нет и быть не может.
        val encoded = kotlinx.serialization.json.Json.encodeToString(
            com.tpov.schoolquiz.shared.core.question_schema.RedactedQuestionContent.serializer(),
            content,
        )
        assertTrue(!encoded.contains("correctOptionId"), "правильный вариант не может вернуться")
    }

    @Test
    fun `given a full-content payload then the exam parse refuses it structurally`() {
        // Полный вопрос (с ответом) не разбирается экзаменационным клиентом вовсе: в
        // красацированной иерархии нет подтипа SingleChoice, и ни игнорирование неизвестных
        // ключей, ни nullable-поля не превращают её в полный вопрос. Это и есть гарантия
        // «редакция — другой тип, не ободранный».
        val full = """
            {"type":"SingleChoice","id":"e0","difficulty":"EASY","text":"q?",
             "imageUrl":null,
             "options":[{"id":"a","text":"x"}],"correctOptionId":"a"}
        """.trimIndent()
        assertFailsWith<Exception> { parseRedactedExamPayload(full) }
    }

    @Test
    fun `given the display timer then it mirrors the all-hard rule`() {
        assertEquals(TimerDuration(24), examDisplayTimer(charsCount = 100, hasImage = false))
        assertEquals(TimerDuration(5), examDisplayTimer(charsCount = 10, hasImage = false))
    }
}
