package com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote

import com.tpov.schoolquiz.shared.core.sync.SYNC_PAGE_SIZE
import com.tpov.schoolquiz.shared.core.sync.SyncChangePage
import com.tpov.schoolquiz.shared.core.sync.SyncCursor

data class ReviewAssignmentDto(
    val id: String,
    val submissionId: String,
    val ownerUid: String,
    val catalogId: String,
    val draftId: String,
    val questId: String,
    val lessonId: String,
    val title: String,
    val createdAtMs: Long,
    val taskKinds: Set<String>,
    val sourceLanguages: Set<String>,
    val newTranslationLanguages: Set<String>,
    val reviewLanguages: Set<String>,
    val checks: ArenaReviewDto,
    val questions: List<ArenaQuestionDto>,
) {
    init {
        require(id.isNotBlank()) { "ReviewAssignmentDto.id must not be blank" }
        require(submissionId.isNotBlank()) { "ReviewAssignmentDto.submissionId must not be blank" }
        require(ownerUid.isNotBlank()) { "ReviewAssignmentDto.ownerUid must not be blank" }
        require(catalogId.isNotBlank()) { "ReviewAssignmentDto.catalogId must not be blank" }
        require(draftId.isNotBlank()) { "ReviewAssignmentDto.draftId must not be blank" }
        require(questId.isNotBlank()) { "ReviewAssignmentDto.questId must not be blank" }
        require(lessonId.isNotBlank()) { "ReviewAssignmentDto.lessonId must not be blank" }
        require(title.isNotBlank()) { "ReviewAssignmentDto.title must not be blank" }
        require(createdAtMs >= 0L) { "ReviewAssignmentDto.createdAtMs must be >= 0" }
        require(taskKinds.none { it.isBlank() }) { "ReviewAssignmentDto.taskKinds must not contain blanks" }
    }
}

data class ReviewAssignmentChangeDto(
    val id: String,
    val changedAtMs: Long,
) {
    init {
        require(id.isNotBlank()) { "ReviewAssignmentChangeDto.id must not be blank" }
        require(changedAtMs >= 0L) { "ReviewAssignmentChangeDto.changedAtMs must be >= 0" }
    }
}

data class ReviewSegmentResultDto(
    val questionId: String,
    val segmentKey: String,
    val accepted: Boolean,
)

data class SubmitReviewActionDto(
    val assignmentId: String,
    val lessonId: String,
    val kind: String,
    val score: Int? = null,
    val language: String? = null,
    val targetReviewId: String? = null,
    val translatedQuestions: List<ArenaQuestionDto> = emptyList(),
    val segmentResults: List<ReviewSegmentResultDto> = emptyList(),
)

interface ReviewAssignmentRemoteDataSource {
    /**
     * Одна страница журнала `admin/review/sync_changes`, начиная сразу после [cursor] (AD-31).
     *
     * Журнал ревью читается через callable, а не напрямую, но правило то же: продолжение по паре
     * «время, id документа», размер страницы — [limit], непонятое считается, а не пропадает.
     */
    suspend fun fetchAssignmentChangePage(
        cursor: SyncCursor,
        limit: Int = SYNC_PAGE_SIZE,
    ): SyncChangePage<ReviewAssignmentChangeDto>

    suspend fun fetchByIds(ids: Set<String>): List<ReviewAssignmentDto>

    suspend fun submitReviewAction(action: SubmitReviewActionDto)
}
