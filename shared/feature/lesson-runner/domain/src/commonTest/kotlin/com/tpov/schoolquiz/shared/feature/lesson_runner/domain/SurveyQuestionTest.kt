package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.evaluateAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.generateTimeoutAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SurveyQuestionTest {

    private fun survey(allowMultiple: Boolean = false) = QuestionContent.Survey(
        id = "s1",
        difficulty = Difficulty.EASY,
        text = "Как вам новый экран?",
        imageUrl = null,
        options =
            listOf(
                QuestionContent.Option(optId("A"), "Нравится"),
                QuestionContent.Option(optId("B"), "Не нравится"),
            ),
        allowMultiple = allowMultiple,
    )

    @Test
    fun `answering a survey counts as full marks`() {
        // There is no right answer, so scoring anything lower would drag down percentScore,
        // stars and the hard-mode unlock for a question that was never a test.
        val score = evaluateAnswer(survey(), UserAnswer.SurveyAnswer(setOf(optId("B"))))

        assertEquals(9, score.raw)
    }

    @Test
    fun `skipping a survey does not`() {
        val score = evaluateAnswer(survey(), UserAnswer.SurveyAnswer(emptySet()))

        assertEquals(1, score.raw)
    }

    @Test
    fun `an option that is not on the list does not count as a response`() {
        val score = evaluateAnswer(survey(), UserAnswer.SurveyAnswer(setOf(optId("Z"))))

        assertEquals(1, score.raw)
    }

    @Test
    fun `a timeout keeps what was picked and never invents an opinion`() {
        // Filling in a random answer would poison the very distribution a survey exists to collect.
        val withDraft = generateTimeoutAnswer(
            content = survey(),
            draft = UserAnswerDraft.SurveyDraft(setOf(optId("A"))),
            seed = 42L,
        )
        val withoutDraft = generateTimeoutAnswer(content = survey(), draft = null, seed = 42L)

        assertEquals(UserAnswer.SurveyAnswer(setOf(optId("A"))), withDraft)
        assertEquals(UserAnswer.SurveyAnswer(emptySet()), withoutDraft)
    }

    @Test
    fun `a survey needs at least two options`() {
        val error = runCatching {
            QuestionContent.Survey(
                id = "s1",
                difficulty = Difficulty.EASY,
                text = "Один вариант?",
                imageUrl = null,
                options = listOf(QuestionContent.Option(optId("A"), "Единственный")),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException, "expected an invariant failure, got $error")
    }
}
