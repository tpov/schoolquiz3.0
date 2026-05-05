package com.tpov.schoolquiz.platform.firebase.quest_authoring

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaQuestionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaReviewDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentChangeDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewSegmentResultDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.SubmitReviewActionDto
import kotlinx.coroutines.tasks.await

class FirebaseReviewAssignmentRemoteDataSource(
    private val functions: FirebaseFunctions,
) : ReviewAssignmentRemoteDataSource {
    override suspend fun fetchAssignmentChangesSince(cursorMs: Long): List<ReviewAssignmentChangeDto> {
        val data =
            functions
                .getHttpsCallable(FETCH_REVIEW_ASSIGNMENT_CHANGES)
                .call(mapOf(CURSOR_MS to cursorMs))
                .await()
                .data
        val root = data as? Map<*, *> ?: return emptyList()
        return root.list(CHANGES).mapNotNull { it.toReviewAssignmentChangeDto() }
    }

    override suspend fun fetchByIds(ids: Set<String>): List<ReviewAssignmentDto> {
        if (ids.isEmpty()) return emptyList()
        val data =
            functions
                .getHttpsCallable(FETCH_REVIEW_ASSIGNMENTS)
                .call(mapOf(IDS to ids.sorted()))
                .await()
                .data
        val root = data as? Map<*, *> ?: return emptyList()
        return root.list(ASSIGNMENTS).mapNotNull { it.toReviewAssignmentDto() }
    }

    override suspend fun submitReviewAction(action: SubmitReviewActionDto) {
        functions
            .getHttpsCallable(SUBMIT_REVIEW_ACTION)
            .call(action.toCallableMap())
            .await()
    }

    private fun Map<*, *>.toReviewAssignmentChangeDto(): ReviewAssignmentChangeDto? {
        return ReviewAssignmentChangeDto(
            id = string(ID) ?: return null,
            changedAtMs = long(CHANGED_AT_MS),
        )
    }

    private fun Map<*, *>.toReviewAssignmentDto(): ReviewAssignmentDto? {
        val checks = map(CHECKS).toArenaReviewDto()
        return ReviewAssignmentDto(
            id = string(ID) ?: return null,
            submissionId = string(SUBMISSION_ID) ?: return null,
            ownerUid = string(OWNER_UID) ?: return null,
            catalogId = string(CATALOG_ID) ?: return null,
            draftId = string(DRAFT_ID) ?: return null,
            questId = string(QUEST_ID) ?: return null,
            lessonId = string(LESSON_ID) ?: return null,
            title = string(TITLE) ?: return null,
            createdAtMs = long(CREATED_AT_MS),
            taskKinds = stringSet(TASK_KINDS),
            sourceLanguages = stringSet(SOURCE_LANGUAGES),
            newTranslationLanguages = stringSet(NEW_TRANSLATION_LANGUAGES),
            reviewLanguages = stringSet(REVIEW_LANGUAGES),
            checks = checks,
            questions = list(QUESTIONS).mapNotNull { it.toArenaQuestionDto() },
        )
    }

    private fun Map<*, *>.toArenaQuestionDto(): ArenaQuestionDto? =
        ArenaQuestionDto(
            id = string(ID) ?: return null,
            draftId = string(DRAFT_ID) ?: return null,
            lessonId = string(LESSON_ID) ?: return null,
            type = string(TYPE) ?: return null,
            language = string(LANGUAGE) ?: return null,
            languageLevel = long(LANGUAGE_LEVEL).toInt(),
            difficulty = string(DIFFICULTY) ?: return null,
            order = long(ORDER).toInt(),
            text = string(TEXT) ?: return null,
            imagePath = string(IMAGE_PATH),
            payload = string(PAYLOAD) ?: return null,
            updatedAtMs = long(UPDATED_AT_MS),
        )

    private fun Map<*, *>.toArenaReviewDto(): ArenaReviewDto =
        ArenaReviewDto(
            isTested = boolean(IS_TESTED),
            testingScore = doubleOrNull(TESTING_SCORE),
            isLogicReviewed = boolean(IS_LOGIC_REVIEWED),
            logicScore = doubleOrNull(LOGIC_SCORE),
            isTranslationReviewed = boolean(IS_TRANSLATION_REVIEWED),
            translationScore = longOrNull(TRANSLATION_SCORE)?.toInt(),
            translatedLanguages = languageLevels(TRANSLATED_LANGUAGES),
        )

    private fun SubmitReviewActionDto.toCallableMap(): Map<String, Any?> =
        mapOf(
            ASSIGNMENT_ID to assignmentId,
            LESSON_ID to lessonId,
            KIND to kind,
            SCORE to score,
            LANGUAGE to language,
            TARGET_REVIEW_ID to targetReviewId,
            TRANSLATED_QUESTIONS to translatedQuestions.map { it.toCallableMap() },
            SEGMENT_RESULTS to segmentResults.map { it.toCallableMap() },
        )

    private fun ArenaQuestionDto.toCallableMap(): Map<String, Any?> =
        mapOf(
            ID to id,
            DRAFT_ID to draftId,
            LESSON_ID to lessonId,
            TYPE to type,
            LANGUAGE to language,
            LANGUAGE_LEVEL to languageLevel,
            DIFFICULTY to difficulty,
            ORDER to order,
            TEXT to text,
            IMAGE_PATH to imagePath,
            PAYLOAD to payload,
            UPDATED_AT_MS to updatedAtMs,
        )

    private fun ReviewSegmentResultDto.toCallableMap(): Map<String, Any?> =
        mapOf(
            QUESTION_ID to questionId,
            SEGMENT_KEY to segmentKey,
            ACCEPTED to accepted,
        )

    private fun Map<*, *>?.list(field: String): List<Map<*, *>> =
        (this?.get(field) as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }

    private fun Map<*, *>?.map(field: String): Map<*, *> = this?.get(field) as? Map<*, *> ?: emptyMap<Any, Any>()

    private fun Map<*, *>?.string(field: String): String? = this?.get(field)?.toString()?.takeIf { it.isNotBlank() }

    private fun Map<*, *>?.stringSet(field: String): Set<String> =
        (this?.get(field) as? List<*>).orEmpty()
            .mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
            .toSet()

    private fun Map<*, *>?.boolean(field: String): Boolean = this?.get(field) as? Boolean ?: false

    private fun Map<*, *>?.long(field: String): Long = longOrNull(field) ?: 0L

    private fun Map<*, *>?.longOrNull(field: String): Long? =
        when (val value = this?.get(field)) {
            is Long -> value
            is Int -> value.toLong()
            is Number -> value.toLong()
            else -> null
        }

    private fun Map<*, *>?.doubleOrNull(field: String): Double? =
        when (val value = this?.get(field)) {
            is Double -> value
            is Float -> value.toDouble()
            is Number -> value.toDouble()
            else -> null
        }

    private fun Map<*, *>?.languageLevels(field: String): Map<String, Int> =
        (this?.get(field) as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
            val language = key?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val level = (value as? Number)?.toInt() ?: return@mapNotNull null
            language to level
        }.toMap()

    private companion object {
        const val FETCH_REVIEW_ASSIGNMENT_CHANGES = "fetchReviewAssignmentChanges"
        const val FETCH_REVIEW_ASSIGNMENTS = "fetchReviewAssignments"
        const val SUBMIT_REVIEW_ACTION = "submitReviewAction"
        const val CURSOR_MS = "cursorMs"
        const val IDS = "ids"
        const val CHANGES = "changes"
        const val ASSIGNMENTS = "assignments"
        const val ID = "id"
        const val CHANGED_AT_MS = "changedAtMs"
        const val SUBMISSION_ID = "submissionId"
        const val OWNER_UID = "ownerUid"
        const val CATALOG_ID = "catalogId"
        const val DRAFT_ID = "draftId"
        const val QUEST_ID = "questId"
        const val LESSON_ID = "lessonId"
        const val TITLE = "title"
        const val CREATED_AT_MS = "createdAtMs"
        const val TASK_KINDS = "taskKinds"
        const val SOURCE_LANGUAGES = "sourceLanguages"
        const val NEW_TRANSLATION_LANGUAGES = "newTranslationLanguages"
        const val REVIEW_LANGUAGES = "reviewLanguages"
        const val CHECKS = "checks"
        const val QUESTIONS = "questions"
        const val TYPE = "type"
        const val LANGUAGE = "language"
        const val LANGUAGE_LEVEL = "languageLevel"
        const val DIFFICULTY = "difficulty"
        const val ORDER = "order"
        const val TEXT = "text"
        const val IMAGE_PATH = "imagePath"
        const val PAYLOAD = "payload"
        const val UPDATED_AT_MS = "updatedAtMs"
        const val IS_TESTED = "isTested"
        const val TESTING_SCORE = "testingScore"
        const val IS_LOGIC_REVIEWED = "isLogicReviewed"
        const val LOGIC_SCORE = "logicScore"
        const val IS_TRANSLATION_REVIEWED = "isTranslationReviewed"
        const val TRANSLATION_SCORE = "translationScore"
        const val TRANSLATED_LANGUAGES = "translatedLanguages"
        const val ASSIGNMENT_ID = "assignmentId"
        const val KIND = "kind"
        const val SCORE = "score"
        const val TARGET_REVIEW_ID = "targetReviewId"
        const val TRANSLATED_QUESTIONS = "translatedQuestions"
        const val SEGMENT_RESULTS = "segmentResults"
        const val QUESTION_ID = "questionId"
        const val SEGMENT_KEY = "segmentKey"
        const val ACCEPTED = "accepted"
    }
}
