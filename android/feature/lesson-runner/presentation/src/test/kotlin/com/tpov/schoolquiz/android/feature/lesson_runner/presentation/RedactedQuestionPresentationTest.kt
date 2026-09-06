package com.tpov.schoolquiz.android.feature.lesson_runner.presentation

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toQuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toUserAnswer
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.buildHintDraft
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.isHintAvailable
import com.tpov.schoolquiz.shared.core.question_schema.BlankId
import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.question_schema.RedactedQuestionContent
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class RedactedQuestionPresentationTest {
    private fun rows(prefix: String) = (0..2).map { RedactedQuestionContent.Row("$prefix-$it", "Text $it") }

    @Test fun publicChoiceOptionsRenderWithoutCorrectAnswerOrHint() {
        val single = RedactedQuestionContent.SingleChoice(difficulty = "HARD", text = "Pick", imageUrl = null, options = rows("o"))
        val multiple = RedactedQuestionContent.MultipleChoice(difficulty = "HARD", text = "Pick several", imageUrl = null, options = rows("o"))
        listOf(single, multiple).forEach { content ->
            val ui = content.toQuestionUiState(shuffleSeed = 33)
            assertFalse(ui.hasLocalAnswerKey)
            assertNull(buildHintDraft(ui))
            assertFalse(isHintAvailable(ui, isHard = false, charges = 10, feedbackShown = false))
        }
        assertNull(assertIs<QuestionUiState.SingleChoice>(single.toQuestionUiState()).correctOptionId)
    }

    @Test fun publicOrderingRestoresDraftByOpaqueIdsNeverTreatsDisplayOrderAsCorrect() {
        val content = RedactedQuestionContent.Ordering(difficulty = "HARD", text = "Order", imageUrl = null, items = rows("ri"))
        val draft = UserAnswerDraft.OrderingDraft(listOf(OptionId("ri-2"), OptionId("ri-0"), OptionId("ri-1")))
        val ui = assertIs<QuestionUiState.Ordering>(content.toQuestionUiState(draft, 44))
        assertEquals(draft.order.map { it.raw }, ui.items.map { it.id })
        assertFalse(ui.hasLocalAnswerKey)
        assertEquals(emptyList(), ui.correctOrderIds)
        assertNull(buildHintDraft(ui))
        assertEquals(UserAnswer.OrderingAnswer(draft.order), draft.toUserAnswer())
    }

    @Test fun publicFillBlankKeepsBareBlankIdsAndCandidateIdsWithoutInventingKey() {
        val content = RedactedQuestionContent.FillBlank(difficulty = "HARD", text = "A ___ B ___", imageUrl = null,
            blanks = listOf("blank-a", "blank-b"), candidates = rows("rc"))
        val draft = UserAnswerDraft.FillBlankDraft(mapOf(BlankId("blank-b") to CandidateId("rc-2")))
        val ui = assertIs<QuestionUiState.FillBlank>(content.toQuestionUiState(draft, 55))
        assertEquals(listOf("blank-a", "blank-b"), ui.templateParts.filterIsInstance<TemplatePart.Blank>().map { it.blankId })
        assertEquals(mapOf(1 to "Text 2"), ui.filledValues)
        assertFalse(ui.hasLocalAnswerKey)
        assertEquals(emptyMap(), ui.correctCandidateIdsByBlankIndex)
        assertNull(buildHintDraft(ui))
        assertEquals(UserAnswer.FillBlankAnswer(draft.filled), draft.toUserAnswer())
    }
}
