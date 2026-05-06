package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.logic.validateArenaReadiness
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionValidationState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QUEST_ARENA_TARGET_SHELF
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QUEST_ARCHIVE_TARGET_SHELF
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmissionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import kotlinx.coroutines.CancellationException

class SubmitQuestDraftToArenaUseCase(
    private val repository: QuestAuthoringRepository,
    private val idProvider: QuestAuthoringIdProvider,
    private val timestampProvider: QuestAuthoringTimestampProvider,
) {
    suspend operator fun invoke(draftId: QuestDraftId): Result<QuestArenaSubmissionId> {
        val bundle = repository.getDraft(draftId)
        val lessonIds = bundle?.lessons?.mapTo(linkedSetOf()) { it.id }.orEmpty()
        return invoke(
            draftId = draftId,
            lessonIds = lessonIds,
            targetShelf = QUEST_ARENA_TARGET_SHELF,
        )
    }

    suspend operator fun invoke(
        draftId: QuestDraftId,
        lessonIds: Set<DraftLessonId>,
        targetShelf: String,
    ): Result<QuestArenaSubmissionId> {
        return try {
            val bundle = requireNotNull(repository.getDraft(draftId)) {
                "Draft ${draftId.value} not found"
            }
            require(bundle.draft.status in SUBMITTABLE_STATUSES) {
                "Draft ${draftId.value} is already submitted or not editable"
            }
            require(targetShelf == QUEST_ARENA_TARGET_SHELF || targetShelf == QUEST_ARCHIVE_TARGET_SHELF) {
                "Target shelf must be arena or archive"
            }
            validateTargetLessons(bundle, lessonIds)

            val submissionId = QuestArenaSubmissionId(idProvider.nextId("arena-submission"))
            val submission = QuestArenaSubmission(
                id = submissionId,
                draftId = draftId,
                ownerUid = bundle.draft.ownerUid,
                localRevision = bundle.draft.localRevision,
                requestedAtMs = timestampProvider.nowMs(),
                lessonIds = lessonIds,
                targetShelf = targetShelf,
            )
            repository.queueArenaSubmission(submission).getOrThrow()
            Result.success(submissionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun validateTargetLessons(
        bundle: QuestAuthoringBundle,
        lessonIds: Set<DraftLessonId>,
    ) {
        require(lessonIds.isNotEmpty()) { "Choose at least one lesson for arena submission" }
        val existingLessonIds = bundle.lessons.mapTo(linkedSetOf()) { it.id }
        require(existingLessonIds.containsAll(lessonIds)) {
            "Selected lessons must belong to draft ${bundle.draft.id.value}"
        }
        lessonIds.forEach { lessonId ->
            val lessonQuestions = bundle.questions.filter { it.lessonId == lessonId }
            val invalidQuestions = lessonQuestions.filter {
                it.validationState != DraftQuestionValidationState.SAVED || it.payload == null
            }
            val lessonBundle = bundle.copy(
                lessons = bundle.lessons.filter { it.id == lessonId },
                questions = lessonQuestions,
            )
            val readiness = validateArenaReadiness(lessonBundle)
            require(readiness.canSend && invalidQuestions.isEmpty()) {
                "Lesson ${lessonId.value} is not ready for arena submission"
            }
        }
    }

    private companion object {
        val SUBMITTABLE_STATUSES =
            setOf(
                QuestDraftStatus.SAVED,
                QuestDraftStatus.SYNC_PENDING,
                QuestDraftStatus.SYNCED,
            )
    }
}
