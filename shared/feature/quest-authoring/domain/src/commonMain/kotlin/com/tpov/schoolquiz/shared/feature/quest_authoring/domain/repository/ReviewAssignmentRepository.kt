package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewActionSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignment
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignmentSyncState
import kotlinx.coroutines.flow.Flow

interface ReviewAssignmentRepository {
    fun observeAssignments(ownerUid: String): Flow<List<ReviewAssignment>>

    /**
     * Кладёт решение ревьюера в общую очередь (AD-5, AD-6).
     *
     * Строка задания при этом не меняется: всё, что действие меняет, пишет сервер (AD-25).
     */
    suspend fun queueReviewAction(submission: ReviewActionSubmission): Result<Unit>

    /** Состояние очереди по одному заданию — для пометки на его карточке (AD-14). */
    fun observeAssignmentSync(
        ownerUid: String,
        assignmentId: String,
    ): Flow<ReviewAssignmentSyncState>
}
