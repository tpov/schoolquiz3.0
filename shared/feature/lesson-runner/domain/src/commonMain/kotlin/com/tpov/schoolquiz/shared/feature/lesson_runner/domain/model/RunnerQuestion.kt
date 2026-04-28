package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId

/**
 * Domain wrapper distinguishing questions with parseable vs unparseable payloads.
 * Only [Valid] items enter the play pool; [Invalid] are filtered out at init.
 */
sealed interface RunnerQuestion {

    val sourceId: QuestionId
    val order: Int
    val codeAnswerIndex: Int

    data class Valid(
        override val sourceId: QuestionId,
        override val order: Int,
        override val codeAnswerIndex: Int,
        val content: QuestionContent,
    ) : RunnerQuestion

    data class Invalid(
        override val sourceId: QuestionId,
        override val order: Int,
        override val codeAnswerIndex: Int,
        val parseError: String,
    ) : RunnerQuestion
}
