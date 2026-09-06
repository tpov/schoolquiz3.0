package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignmentSyncState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewAssignmentRepository
import kotlinx.coroutines.flow.Flow

/**
 * Что с решением по этому заданию прямо сейчас (AD-14, AD-28).
 *
 * Поток живёт столько же, сколько карточка: пометка «ожидает» снимается, когда запись уехала, а
 * «карантин» — единственное, что фича показывает вместо отката, которого здесь нет (AD-25).
 */
class ObserveReviewAssignmentSyncUseCase(
    private val repository: ReviewAssignmentRepository,
) {
    operator fun invoke(
        ownerUid: String,
        assignmentId: String,
    ): Flow<ReviewAssignmentSyncState> = repository.observeAssignmentSync(ownerUid, assignmentId)
}
