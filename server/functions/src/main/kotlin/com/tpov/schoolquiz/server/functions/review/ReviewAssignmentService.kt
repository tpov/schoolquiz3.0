package com.tpov.schoolquiz.server.functions.review

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentChangeDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentDto

class ReviewAssignmentService(
    private val store: ReviewAssignmentStore,
) {
    suspend fun fetchChangesForTrustedUser(
        uid: String,
        cursorMs: Long,
    ): Result<List<ReviewAssignmentChangeDto>> =
        runCatching {
            requireNotNull(store.readTrustedProfile(uid)) {
                "Trusted profile $uid not found"
            }
            store.readAssignmentChangesSince(cursorMs)
                .map {
                    ReviewAssignmentChangeDto(
                        id = it.assignmentId,
                        changedAtMs = it.changedAtMs,
                    )
                }
                .sortedBy { it.changedAtMs }
        }

    suspend fun fetchAvailableForTrustedUser(
        uid: String,
        ids: Set<String>,
    ): Result<List<ReviewAssignmentDto>> =
        runCatching {
            if (ids.isEmpty()) return@runCatching emptyList()
            val profile = requireNotNull(store.readTrustedProfile(uid)) {
                "Trusted profile $uid not found"
            }
            val config = store.readArenaReviewConfig()
            store.readAdminReviewLessonTasksByIds(ids)
                .mapNotNull { task -> task.toAssignmentDto(profile, config) }
        }

    private fun AdminReviewLessonTask.toAssignmentDto(
        profile: TrustedProfile,
        config: ArenaReviewConfig?,
    ): ReviewAssignmentDto? {
        val taskKinds = ReviewAssignmentPolicy.availableTasks(profile, this, config)
        if (taskKinds.isEmpty()) return null
        val targets = ReviewAssignmentPolicy.translationTargets(profile, this, config)
        return ReviewAssignmentDto(
            id = id,
            submissionId = submissionId,
            ownerUid = ownerUid,
            catalogId = catalogId,
            draftId = draftId,
            questId = questId,
            lessonId = lessonId,
            title = title,
            createdAtMs = createdAtMs,
            taskKinds = taskKinds.map { it.name }.toSet(),
            sourceLanguages = targets.sourceLanguages,
            newTranslationLanguages = targets.newTranslationLanguages,
            reviewLanguages = targets.reviewLanguages,
            checks = checks.toArenaReviewDto(),
            questions = questions,
        )
    }
}

interface ReviewAssignmentStore {
    suspend fun readTrustedProfile(uid: String): TrustedProfile?

    suspend fun readArenaReviewConfig(): ArenaReviewConfig?

    suspend fun readAssignmentChangesSince(cursorMs: Long): List<ReviewAssignmentChange>

    suspend fun readAdminReviewLessonTasks(): List<AdminReviewLessonTask>

    suspend fun readAdminReviewLessonTasksByIds(ids: Set<String>): List<AdminReviewLessonTask> =
        readAdminReviewLessonTasks().filter { it.id in ids }
}
