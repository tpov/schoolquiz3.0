package com.tpov.schoolquiz.server.functions.review

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaQuestionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaReviewDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentChangeDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentDto

class FetchReviewAssignmentChangesHandler(
    private val service: ReviewAssignmentService,
) {
    suspend fun handle(
        authUid: String?,
        cursorMs: Long,
    ): Map<String, Any?> {
        val uid = requireNotNull(authUid?.takeIf { it.isNotBlank() }) {
            "Authenticated uid is required"
        }
        val changes = service.fetchChangesForTrustedUser(uid, cursorMs).getOrThrow()
        return mapOf("changes" to changes.map { it.toCallableMap() })
    }
}

class FetchReviewAssignmentsHandler(
    private val service: ReviewAssignmentService,
) {
    suspend fun handle(
        authUid: String?,
        ids: Set<String>,
    ): Map<String, Any?> {
        val uid = requireNotNull(authUid?.takeIf { it.isNotBlank() }) {
            "Authenticated uid is required"
        }
        val assignments = service.fetchAvailableForTrustedUser(uid, ids).getOrThrow()
        return mapOf("assignments" to assignments.map { it.toCallableMap() })
    }
}

class SubmitReviewActionHandler(
    private val processor: SubmitReviewActionProcessor,
) {
    suspend fun handle(
        authUid: String?,
        action: SubmitReviewAction,
    ): Map<String, Any?> {
        val uid = requireNotNull(authUid?.takeIf { it.isNotBlank() }) {
            "Authenticated uid is required"
        }
        val result = processor.submit(uid, action).getOrThrow()
        return mapOf(
            "recordId" to result.record.id,
            "aggregate" to result.aggregate.toArenaReviewDto().toCallableMap(),
            "reviewerDeltas" to result.reviewerDeltas.map { it.toCallableMap() },
        )
    }
}

private fun ReviewAssignmentChangeDto.toCallableMap(): Map<String, Any?> =
    mapOf(
        "id" to id,
        "changedAtMs" to changedAtMs,
    )

private fun ReviewAssignmentDto.toCallableMap(): Map<String, Any?> =
    mapOf(
        "id" to id,
        "submissionId" to submissionId,
        "ownerUid" to ownerUid,
        "catalogId" to catalogId,
        "draftId" to draftId,
        "questId" to questId,
        "lessonId" to lessonId,
        "title" to title,
        "createdAtMs" to createdAtMs,
        "taskKinds" to taskKinds.sorted(),
        "sourceLanguages" to sourceLanguages.sorted(),
        "newTranslationLanguages" to newTranslationLanguages.sorted(),
        "reviewLanguages" to reviewLanguages.sorted(),
        "checks" to checks.toCallableMap(),
        "questions" to questions.map { it.toCallableMap() },
    )

private fun ArenaReviewDto.toCallableMap(): Map<String, Any?> =
    mapOf(
        "isTested" to isTested,
        "testingScore" to testingScore,
        "isLogicReviewed" to isLogicReviewed,
        "logicScore" to logicScore,
        "isTranslationReviewed" to isTranslationReviewed,
        "translationScore" to translationScore,
        "translatedLanguages" to translatedLanguages,
    )

private fun ArenaQuestionDto.toCallableMap(): Map<String, Any?> =
    mapOf(
        "id" to id,
        "draftId" to draftId,
        "lessonId" to lessonId,
        "type" to type,
        "language" to language,
        "languageLevel" to languageLevel,
        "difficulty" to difficulty,
        "order" to order,
        "text" to text,
        "imagePath" to imagePath,
        "payload" to payload,
        "updatedAtMs" to updatedAtMs,
    )

private fun ReviewerReputationDelta.toCallableMap(): Map<String, Any?> =
    mapOf(
        "reviewerUid" to reviewerUid,
        "points" to points,
    )
