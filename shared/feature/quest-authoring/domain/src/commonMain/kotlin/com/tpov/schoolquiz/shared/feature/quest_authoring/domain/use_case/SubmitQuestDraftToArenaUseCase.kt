package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.logic.validateArenaReadiness
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionValidationState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QUEST_ARCHIVE_TARGET_SHELF
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QUEST_ARENA_TARGET_SHELF
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmissionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmissionRejection
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SUBMITTABLE_DRAFT_STATUSES
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
            val bundle = submittableBundle(draftId, targetShelf)
            validateTargetLessons(bundle, lessonIds)

            val submissionId = QuestArenaSubmissionId(idProvider.nextId("arena-submission"))
            val submission =
                QuestArenaSubmission(
                    id = submissionId,
                    draftId = draftId,
                    ownerUid = bundle.draft.ownerUid,
                    // Версию для сверки берём ту, что назвал сервер, а не счётчик локальных правок
                    // (AD-12): расхождение — это когда квест уехал под нами, а не когда автор много
                    // раз нажал «сохранить».
                    expectedVersion = bundle.draft.serverRevision,
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

    /**
     * Черновик, годный к подаче, — либо отказ с названной причиной.
     *
     * Собрано в одну функцию, чтобы отказ читался списком условий, а не был размазан по телу
     * сценария: каждая причина названа своим типом, и экран переводит её в понятную автору подпись.
     */
    private suspend fun submittableBundle(
        draftId: QuestDraftId,
        targetShelf: String,
    ): QuestAuthoringBundle {
        val bundle =
            repository.getDraft(draftId)
                ?: throw QuestArenaSubmissionRejection.DraftNotFound(draftId)
        submissionRejection(bundle, targetShelf)?.let { throw it }
        return bundle
    }

    /** Причина, по которой сам черновик подавать нельзя, — или `null`, если можно. */
    private fun submissionRejection(
        bundle: QuestAuthoringBundle,
        targetShelf: String,
    ): QuestArenaSubmissionRejection? =
        when {
            bundle.draft.status !in SUBMITTABLE_DRAFT_STATUSES ->
                QuestArenaSubmissionRejection.DraftNotSubmittable(bundle.draft.id, bundle.draft.status)

            targetShelf != QUEST_ARENA_TARGET_SHELF && targetShelf != QUEST_ARCHIVE_TARGET_SHELF ->
                QuestArenaSubmissionRejection.UnsupportedTargetShelf(targetShelf)

            else -> null
        }

    /** Причина, по которой не годится сам выбор уроков, — или `null`, если выбор годен. */
    private fun lessonSelectionRejection(
        bundle: QuestAuthoringBundle,
        lessonIds: Set<DraftLessonId>,
    ): QuestArenaSubmissionRejection? {
        val existingLessonIds = bundle.lessons.mapTo(linkedSetOf()) { it.id }
        return when {
            lessonIds.isEmpty() -> QuestArenaSubmissionRejection.NoLessonsChosen()

            !existingLessonIds.containsAll(lessonIds) ->
                QuestArenaSubmissionRejection.LessonOutsideDraft(
                    draftId = bundle.draft.id,
                    lessonIds = lessonIds - existingLessonIds,
                )

            else -> null
        }
    }

    private fun validateTargetLessons(
        bundle: QuestAuthoringBundle,
        lessonIds: Set<DraftLessonId>,
    ) {
        lessonSelectionRejection(bundle, lessonIds)?.let { throw it }
        val notReady = lessonIds.firstOrNull { !isLessonReady(bundle, it) }
        if (notReady != null) throw QuestArenaSubmissionRejection.LessonNotReady(notReady)
    }

    /** Урок готов, когда в нём есть сохранённые вопросы обеих сложностей и нет испорченных. */
    private fun isLessonReady(
        bundle: QuestAuthoringBundle,
        lessonId: DraftLessonId,
    ): Boolean {
        val lessonQuestions = bundle.questions.filter { it.lessonId == lessonId }
        val hasInvalidQuestion =
            lessonQuestions.any {
                it.validationState != DraftQuestionValidationState.SAVED || it.payload == null
            }
        val lessonBundle =
            bundle.copy(
                lessons = bundle.lessons.filter { it.id == lessonId },
                questions = lessonQuestions,
            )
        return validateArenaReadiness(lessonBundle).canSend && !hasInvalidQuestion
    }
}
