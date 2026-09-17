package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewActionSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitReviewActionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewAssignmentRepository
import kotlinx.coroutines.CancellationException

/**
 * Решение ревьюера уходит в очередь, а не в callable (AD-3): результат детерминированно выводится из
 * задания, которое уже лежит на устройстве, и ждать сети незачем.
 *
 * Идентификатор намерения назначается здесь, в момент нажатия, и больше не меняется (AD-2).
 */
class SubmitReviewActionUseCase(
    private val repository: ReviewAssignmentRepository,
    private val idProvider: QuestAuthoringIdProvider,
    private val timestampProvider: QuestAuthoringTimestampProvider,
) {
    suspend operator fun invoke(
        ownerUid: String,
        command: SubmitReviewActionCommand,
    ): Result<Unit> {
        return try {
            require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
            require(command.assignmentId.isNotBlank()) { "assignmentId must not be blank" }
            require(command.lessonId.isNotBlank()) { "lessonId must not be blank" }
            val submission =
                ReviewActionSubmission(
                    id = idProvider.nextId("review-action"),
                    ownerUid = ownerUid,
                    requestedAtMs = timestampProvider.nowMs(),
                    command = command,
                )
            repository.queueReviewAction(submission)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }
}
