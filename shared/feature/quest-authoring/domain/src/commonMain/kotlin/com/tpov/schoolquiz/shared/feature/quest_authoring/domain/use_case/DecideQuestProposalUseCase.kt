package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewDecisionsRepository

/**
 * Решение автора по предложению (история 4.25, UX-22): тот же приёмник `decideLessonProposal`
 * (4.21), что нажимают из «Предложений». Право — сервер; клиент прячет кнопки на устаревшем и
 * чужом, но не пытается решать сам.
 */
class DecideQuestProposalUseCase(
    private val remote: ProposalDecisionTransport,
) {
    suspend operator fun invoke(
        proposalId: String,
        approve: Boolean,
        comment: String,
    ): Result<Unit> {
        if (proposalId.isBlank()) return Result.failure(IllegalArgumentException("proposalId is required"))
        return remote.decideProposal(
            proposalId = proposalId,
            decision = if (approve) DECISION_APPROVE else DECISION_REJECT,
            comment = comment.trim(),
        )
    }

    companion object {
        const val DECISION_APPROVE = "APPROVE"
        const val DECISION_REJECT = "REJECT"
    }
}

/** Транспорт решения: ровно тело приёмника 4.21 — proposalId, decision, comment. */
interface ProposalDecisionTransport {
    suspend fun decideProposal(
        proposalId: String,
        decision: String,
        comment: String,
    ): Result<Unit>
}
