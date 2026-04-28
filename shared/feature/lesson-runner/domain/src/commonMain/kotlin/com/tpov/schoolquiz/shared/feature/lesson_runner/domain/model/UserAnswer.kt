package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.core.question_schema.BlankId
import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
import com.tpov.schoolquiz.shared.core.question_schema.OptionId

/**
 * Submitted answer for a question. Null values represent timeout / empty input.
 */
sealed interface UserAnswer {
    data class SingleChoiceAnswer(val selected: OptionId?) : UserAnswer
    data class MultipleChoiceAnswer(val selected: Set<OptionId>) : UserAnswer
    data class OrderingAnswer(val order: List<OptionId>) : UserAnswer
    data class FillBlankAnswer(val filled: Map<BlankId, CandidateId?>) : UserAnswer
}
