package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitReviewActionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewAssignmentRepository

class SubmitReviewActionUseCase(
    private val repository: ReviewAssignmentRepository,
) {
    suspend operator fun invoke(command: SubmitReviewActionCommand): Result<Unit> {
        require(command.assignmentId.isNotBlank()) { "assignmentId must not be blank" }
        require(command.lessonId.isNotBlank()) { "lessonId must not be blank" }
        return repository.submitReviewAction(command)
    }
}
