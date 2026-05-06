package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

const val QUEST_ARENA_TARGET_SHELF = "arena"
const val QUEST_ARCHIVE_TARGET_SHELF = "archive"

data class QuestArenaSubmission(
    val id: QuestArenaSubmissionId,
    val draftId: QuestDraftId,
    val ownerUid: String,
    val localRevision: Long,
    val requestedAtMs: Long,
    val lessonIds: Set<DraftLessonId>,
    val targetShelf: String,
    val attemptCount: Int = 0,
    val lastError: String? = null,
) {
    init {
        require(ownerUid.isNotBlank()) { "QuestArenaSubmission.ownerUid must not be blank" }
        require(localRevision >= 1) { "QuestArenaSubmission.localRevision must be >= 1, got $localRevision" }
        require(requestedAtMs >= 0) { "QuestArenaSubmission.requestedAtMs must be >= 0, got $requestedAtMs" }
        require(lessonIds.isNotEmpty()) { "QuestArenaSubmission.lessonIds must not be empty" }
        require(targetShelf == QUEST_ARENA_TARGET_SHELF || targetShelf == QUEST_ARCHIVE_TARGET_SHELF) {
            "QuestArenaSubmission.targetShelf must be arena or archive"
        }
        require(attemptCount >= 0) { "QuestArenaSubmission.attemptCount must be >= 0, got $attemptCount" }
        require(lastError == null || lastError.isNotBlank()) {
            "QuestArenaSubmission.lastError must be null or non-blank"
        }
    }
}
