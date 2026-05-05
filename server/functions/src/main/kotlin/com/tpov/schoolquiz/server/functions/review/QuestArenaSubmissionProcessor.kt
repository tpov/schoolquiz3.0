package com.tpov.schoolquiz.server.functions.review

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRequest

class QuestArenaSubmissionProcessor(
    private val store: QuestReviewStore,
    private val clock: ReviewClock,
) {
    suspend fun process(request: QuestArenaSubmissionRequest): Result<ReviewSubmissionResult> =
        runCatching {
            require(request.ownerUid.isNotBlank()) { "request.ownerUid must not be blank" }
            require(request.questions.isNotEmpty()) { "request.questions must not be empty" }
            requireNotNull(store.readTrustedProfile(request.ownerUid)) {
                "Trusted profile ${request.ownerUid} not found"
            }

            val now = clock.nowMs()
            val adminTasks = request.toAdminLessonTasks(now)
            store.writePrivateHierarchy(request)
            store.writeAdminReviewLessonTasks(adminTasks)
            store.markSubmissionProcessed(request.submissionId, now)

            ReviewSubmissionResult(
                submissionId = request.submissionId,
                privateQuestPath =
                    ReviewFirestorePaths.privateQuest(
                        ownerUid = request.ownerUid,
                        catalogId = request.draft.catalogId,
                        questId = request.draft.id,
                    ),
                adminLessonTaskPaths = adminTasks.map { ReviewFirestorePaths.adminLesson(it.lessonId) },
            )
        }
}

fun interface ReviewClock {
    fun nowMs(): Long
}

interface QuestReviewStore {
    suspend fun readTrustedProfile(uid: String): TrustedProfile?

    suspend fun writePrivateHierarchy(request: QuestArenaSubmissionRequest)

    suspend fun writeAdminReviewLessonTasks(tasks: List<AdminReviewLessonTask>)

    suspend fun markSubmissionProcessed(
        submissionId: String,
        processedAtMs: Long,
    )
}

interface QuestReviewRequestStore : QuestReviewStore {
    suspend fun readPendingSubmissions(limit: Int): List<com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRequest>
}
