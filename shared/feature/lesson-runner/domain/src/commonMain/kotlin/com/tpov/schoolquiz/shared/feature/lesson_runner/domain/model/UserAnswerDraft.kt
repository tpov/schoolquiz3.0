package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.core.question_schema.BlankId
import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
import com.tpov.schoolquiz.shared.core.question_schema.OptionId

/**
 * In-progress partial input not yet submitted by the user.
 * Serialized via Decompose instanceKeeper for configuration change recovery.
 * RunnerState.Ready.currentDraftAnswer is nullable: null = no active draft.
 */
sealed interface UserAnswerDraft {
    data class SingleChoiceDraft(val selected: OptionId?) : UserAnswerDraft
    data class MultipleChoiceDraft(val selected: Set<OptionId>) : UserAnswerDraft
    data class OrderingDraft(val order: List<OptionId>) : UserAnswerDraft
    data class FillBlankDraft(val filled: Map<BlankId, CandidateId?>) : UserAnswerDraft
    data class SurveyDraft(val selected: Set<OptionId>) : UserAnswerDraft
}
