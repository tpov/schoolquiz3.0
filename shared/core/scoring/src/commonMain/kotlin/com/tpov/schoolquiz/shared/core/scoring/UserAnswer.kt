package com.tpov.schoolquiz.shared.core.scoring

import com.tpov.schoolquiz.shared.core.question_schema.BlankId
import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Submitted answer for a question. Null values represent timeout / empty input.
 *
 * Serializable so the answer can be stored verbatim next to the attempt: the score digit alone
 * cannot tell which option a player picked, which is what survey distributions and gap analysis
 * are built from. Names are pinned with [SerialName] so stored rows survive class renames.
 */
@Serializable
sealed interface UserAnswer {
    @Serializable
    @SerialName("single-choice")
    data class SingleChoiceAnswer(val selected: OptionId?) : UserAnswer

    @Serializable
    @SerialName("multiple-choice")
    data class MultipleChoiceAnswer(val selected: Set<OptionId>) : UserAnswer

    @Serializable
    @SerialName("ordering")
    data class OrderingAnswer(val order: List<OptionId>) : UserAnswer

    @Serializable
    @SerialName("fill-blank")
    data class FillBlankAnswer(val filled: Map<BlankId, CandidateId?>) : UserAnswer

    /** Survey response: whatever was picked. Empty means the question was skipped. */
    @Serializable
    @SerialName("survey")
    data class SurveyAnswer(val selected: Set<OptionId>) : UserAnswer
}
