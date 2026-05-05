package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignment
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitReviewActionCommand
import kotlinx.coroutines.flow.Flow

interface ReviewAssignmentRepository {
    fun observeAssignments(ownerUid: String): Flow<List<ReviewAssignment>>

    suspend fun submitReviewAction(command: SubmitReviewActionCommand): Result<Unit>
}
