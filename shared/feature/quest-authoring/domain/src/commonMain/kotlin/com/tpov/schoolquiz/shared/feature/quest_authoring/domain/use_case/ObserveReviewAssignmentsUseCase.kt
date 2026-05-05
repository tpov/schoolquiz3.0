package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignment
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewAssignmentRepository
import kotlinx.coroutines.flow.Flow

class ObserveReviewAssignmentsUseCase(
    private val repository: ReviewAssignmentRepository,
) {
    operator fun invoke(ownerUid: String): Flow<List<ReviewAssignment>> {
        require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
        return repository.observeAssignments(ownerUid)
    }
}
