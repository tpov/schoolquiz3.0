package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spec scenarios 36-39b: Timer formula.
 * Tests `computeTimer(content, mode, coefficients): TimerDuration`.
 *
 * Formula (Business Rule 21):
 *   charsCount = chars(text) + sum(option/item/candidate text chars) + (if hasImage then 100 else 0)
 *   seconds = max(5, round(charsCount × k))
 */
class TimerComputeTest {

    private val coeffs = TimerCoefficients.Default  // kEasy=0.36, kHard=0.24

    // text "Question text" = 13 chars; 4 options "Option A/B/C/D" each 8 = 32 chars → total = 45... we need different
    // To get totalChars=165: text=100 chars + options total=65 chars

    private fun longText(chars: Int): String = "x".repeat(chars)

    private fun contentWithChars(textChars: Int, optionsTotalChars: Int, hasImage: Boolean = false) =
        singleChoiceContent(
            correctId = "A",
            optionIds = listOf("A", "B", "C", "D"),
            text = longText(textChars),
            imageUrl = if (hasImage) "http://example.com/img.png" else null,
        ).let { base ->
            // Override options to control char count
            com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.SingleChoice(
                id = base.id,
                difficulty = base.difficulty,
                text = longText(textChars),
                imageUrl = if (hasImage) "http://example.com/img.png" else null,
                options = listOf(
                    com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(
                        optId("A"),
                        longText(optionsTotalChars),
                    ),
                    com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("B"), "B"),
                    com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("C"), "C"),
                    com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("D"), "D"),
                ),
                correctOptionId = optId("A"),
            )
        }

    @Test
    fun `given totalChars 165 mode EASY then timer equals 59`() {
        // Spec scenario #36: round(165 × 0.36) = round(59.4) = 59
        // text=100, options_total=65 → 100+65 = 165 (options: 65+1+1+1=68? Let's adjust)
        // To get totalChars exactly 165: text=100, one option=62, others each=1 → total=100+62+1+1+1=165
        val content = com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.SingleChoice(
            id = "q1",
            difficulty = Difficulty.EASY,
            text = longText(100),
            imageUrl = null,
            options = listOf(
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("A"), longText(62)),
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("B"), "B"),
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("C"), "C"),
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("D"), "D"),
            ),
            correctOptionId = optId("A"),
        )
        val timer = computeTimer(content, Difficulty.EASY, coeffs)
        assertEquals(59, timer.seconds, "Spec scenario #36: round(165 × 0.36) = 59")
    }

    @Test
    fun `given totalChars 165 mode HARD then timer equals 40`() {
        // Spec scenario #37: round(165 × 0.24) = round(39.6) = 40
        val content = com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.SingleChoice(
            id = "q1",
            difficulty = Difficulty.HARD,
            text = longText(100),
            imageUrl = null,
            options = listOf(
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("A"), longText(62)),
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("B"), "B"),
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("C"), "C"),
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("D"), "D"),
            ),
            correctOptionId = optId("A"),
        )
        val timer = computeTimer(content, Difficulty.HARD, coeffs)
        assertEquals(40, timer.seconds, "Spec scenario #37: round(165 × 0.24) = 40")
    }

    @Test
    fun `given same question HARD timer less than EASY timer`() {
        // Spec scenario #38: sanity check k_hard < k_easy
        val content = com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.SingleChoice(
            id = "q1",
            difficulty = Difficulty.EASY,
            text = longText(100),
            imageUrl = null,
            options = listOf(
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("A"), longText(62)),
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("B"), "B"),
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("C"), "C"),
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("D"), "D"),
            ),
            correctOptionId = optId("A"),
        )
        val easyTimer = computeTimer(content, Difficulty.EASY, coeffs)
        val hardTimer = computeTimer(content, Difficulty.HARD, coeffs)
        assertTrue(hardTimer.seconds < easyTimer.seconds, "Spec scenario #38: HARD timer < EASY timer")
    }

    @Test
    fun `given short question totalChars 10 mode EASY then timer equals 5 min floor`() {
        // Spec scenario #39: max(5, round(10 × 0.36)) = max(5, round(3.6)) = max(5, 4) = 5
        val content = com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.SingleChoice(
            id = "q1",
            difficulty = Difficulty.EASY,
            text = "Short",
            imageUrl = null,
            options = listOf(
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("A"), "A"),
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("B"), "B"),
            ),
            correctOptionId = optId("A"),
        )
        val timer = computeTimer(content, Difficulty.EASY, coeffs)
        assertEquals(5, timer.seconds, "Spec scenario #39: min floor = 5")
    }

    @Test
    fun `given question with image totalChars 200 mode EASY then timer includes 100 image bonus`() {
        // Spec scenario #39a: round((200+100) × 0.36) = round(108) = 108
        val content = com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.SingleChoice(
            id = "q1",
            difficulty = Difficulty.EASY,
            text = longText(100),
            imageUrl = "http://example.com/img.png",
            options = listOf(
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("A"), longText(97)),
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("B"), "B"),
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("C"), "C"),
            ),
            correctOptionId = optId("A"),
        )
        val timer = computeTimer(content, Difficulty.EASY, coeffs)
        assertEquals(108, timer.seconds, "Spec scenario #39a: with image +100 chars bonus → 108 sec")
    }

    @Test
    fun `given question without image totalChars 200 mode EASY then timer 72`() {
        // Spec scenario #39b: round(200 × 0.36) = round(72) = 72
        val content = com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.SingleChoice(
            id = "q1",
            difficulty = Difficulty.EASY,
            text = longText(100),
            imageUrl = null,
            options = listOf(
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("A"), longText(97)),
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("B"), "B"),
                com.tpov.schoolquiz.shared.core.question_schema.QuestionContent.Option(optId("C"), "C"),
            ),
            correctOptionId = optId("A"),
        )
        val timer = computeTimer(content, Difficulty.EASY, coeffs)
        assertEquals(72, timer.seconds, "Spec scenario #39b: no image → 72 sec")
    }
}
