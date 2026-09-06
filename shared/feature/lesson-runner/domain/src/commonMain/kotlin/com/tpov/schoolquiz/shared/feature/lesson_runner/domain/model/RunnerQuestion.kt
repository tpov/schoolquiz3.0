package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.QuestionDisplay
import com.tpov.schoolquiz.shared.core.question_schema.RedactedQuestionContent
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId

/**
 * Domain wrapper distinguishing questions with parseable vs unparseable payloads.
 * [Playable] includes public-only questions; an answer key is never fabricated for them.
 */
sealed interface RunnerQuestion {
    val sourceId: QuestionId
    val order: Int
    val codeAnswerIndex: Int

    sealed interface Playable : RunnerQuestion {
        val content: QuestionDisplay

        fun withCodeAnswerIndex(index: Int): Playable
    }

    data class Valid(
        override val sourceId: QuestionId,
        override val order: Int,
        override val codeAnswerIndex: Int,
        override val content: QuestionContent,
    ) : Playable {
        override fun withCodeAnswerIndex(index: Int) = copy(codeAnswerIndex = index)
    }

    data class Redacted(
        override val sourceId: QuestionId,
        override val order: Int,
        override val codeAnswerIndex: Int,
        override val content: RedactedQuestionContent,
    ) : Playable {
        override fun withCodeAnswerIndex(index: Int) = copy(codeAnswerIndex = index)
    }

    data class Invalid(
        override val sourceId: QuestionId,
        override val order: Int,
        override val codeAnswerIndex: Int,
        val parseError: String,
    ) : RunnerQuestion
}
