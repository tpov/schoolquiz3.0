package com.tpov.schoolquiz.platform.firebase.quest_authoring

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaDraftDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaLessonDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaQuestionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaReviewDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaSectionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaThemeDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRequest
import kotlinx.coroutines.tasks.await

class FirebaseQuestArenaSubmissionRemoteDataSource(
    private val firestore: FirebaseFirestore,
) : QuestArenaSubmissionRemoteDataSource {
    override suspend fun submit(request: QuestArenaSubmissionRequest) {
        firestore.collection(REVIEW_REQUESTS_COLLECTION)
            .document(request.submissionId)
            .set(request.toFirestoreMap())
            .await()
    }

    private companion object {
        const val REVIEW_REQUESTS_COLLECTION = "quest_review_requests"
        const val REQUEST_TYPE = "SUBMIT_TO_ARENA"
    }

    private fun QuestArenaSubmissionRequest.toFirestoreMap(): Map<String, Any?> =
        mapOf(
            "type" to REQUEST_TYPE,
            "processed" to false,
            "submissionId" to submissionId,
            "draftId" to draftId,
            "ownerUid" to ownerUid,
            "localRevision" to localRevision,
            "requestedAtMs" to requestedAtMs,
            "createdAt" to FieldValue.serverTimestamp(),
            "draft" to draft.toFirestoreMap(),
            "sections" to sections.map { it.toFirestoreMap() },
            "themes" to themes.map { it.toFirestoreMap() },
            "lessons" to lessons.map { it.toFirestoreMap() },
            "questions" to questions.map { it.toFirestoreMap() },
            "review" to review.toFirestoreMap(),
        )

    private fun ArenaReviewDto.toFirestoreMap(): Map<String, Any?> =
        mapOf(
            "isTested" to isTested,
            "testingScore" to testingScore,
            "isLogicReviewed" to isLogicReviewed,
            "logicScore" to logicScore,
            "isTranslationReviewed" to isTranslationReviewed,
            "translationScore" to translationScore,
            "translatedLanguages" to translatedLanguages,
        )

    private fun ArenaDraftDto.toFirestoreMap(): Map<String, Any?> =
        mapOf(
            "id" to id,
            "catalogId" to catalogId,
            "title" to title,
            "description" to description,
            "defaultLanguage" to defaultLanguage,
            "defaultDifficulty" to defaultDifficulty,
            "publicQuestId" to publicQuestId,
            "createdAtMs" to createdAtMs,
            "updatedAtMs" to updatedAtMs,
        )

    private fun ArenaSectionDto.toFirestoreMap(): Map<String, Any?> =
        mapOf(
            "id" to id,
            "draftId" to draftId,
            "title" to title,
            "order" to order,
        )

    private fun ArenaThemeDto.toFirestoreMap(): Map<String, Any?> =
        mapOf(
            "id" to id,
            "draftId" to draftId,
            "sectionId" to sectionId,
            "title" to title,
            "order" to order,
        )

    private fun ArenaLessonDto.toFirestoreMap(): Map<String, Any?> =
        mapOf(
            "id" to id,
            "draftId" to draftId,
            "themeId" to themeId,
            "title" to title,
            "order" to order,
        )

    private fun ArenaQuestionDto.toFirestoreMap(): Map<String, Any?> =
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
}
