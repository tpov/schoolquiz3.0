package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

data class ArenaReadiness(
    val canSend: Boolean,
    val hasEasyQuestion: Boolean,
    val hasHardQuestion: Boolean,
    val invalidQuestionIds: Set<DraftQuestionId>,
) {
    init {
        require(canSend == (hasEasyQuestion && hasHardQuestion && invalidQuestionIds.isEmpty())) {
            "ArenaReadiness.canSend must match readiness fields"
        }
    }
}
