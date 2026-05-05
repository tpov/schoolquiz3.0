package com.tpov.schoolquiz.server.functions.review

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaDraftDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaLessonDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaQuestionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaReviewDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaSectionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaThemeDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRequest

class FirestoreQuestReviewStore(
    private val firestore: Firestore,
) : QuestReviewRequestStore,
    ReviewActionStore {

    override suspend fun readPendingSubmissions(limit: Int): List<QuestArenaSubmissionRequest> =
        firestore.collection(ReviewFirestorePaths.REVIEW_REQUESTS_COLLECTION)
            .whereEqualTo(PROCESSED, false)
            .limit(limit)
            .get()
            .get()
            .documents
            .map { it.toArenaSubmissionRequest() }

    override suspend fun readTrustedProfile(uid: String): TrustedProfile? {
        val snapshot = firestore.collection(ReviewFirestorePaths.PROFILES_COLLECTION)
            .document(uid)
            .get()
            .get()
        if (!snapshot.exists()) return null
        val data = snapshot.data.orEmpty()
        return TrustedProfile(
            uid = uid,
            testerLevel = data.int(TESTER_LEVEL),
            adminLevel = data.int(ADMIN_LEVEL),
            translatorLevel = data.int(TRANSLATOR_LEVEL),
            developerLevel = data.int(DEVELOPER_LEVEL),
            knownLanguages = data.stringSet(KNOWN_LANGUAGES),
        )
    }

    override suspend fun writePrivateHierarchy(request: QuestArenaSubmissionRequest) {
        writeDocuments(ReviewDocumentBuilder.privateDocuments(request))
    }

    override suspend fun writeAdminReviewLessonTasks(tasks: List<AdminReviewLessonTask>) {
        writeDocuments(ReviewDocumentBuilder.adminDocuments(tasks))
    }

    override suspend fun readArenaReviewConfig(): ArenaReviewConfig? {
        val snapshot = firestore.document(ReviewFirestorePaths.ARENA_REVIEW_CONFIG)
            .get()
            .get()
        if (!snapshot.exists()) return null
        val data = snapshot.data.orEmpty()
        return ArenaReviewConfig(
            requiredLanguages = data.stringSet(REQUIRED_LANGUAGES),
            updatedAtMs = data.long(UPDATED_AT_MS),
        )
    }

    override suspend fun markSubmissionProcessed(
        submissionId: String,
        processedAtMs: Long,
    ) {
        firestore.collection(ReviewFirestorePaths.REVIEW_REQUESTS_COLLECTION)
            .document(submissionId)
            .update(
                mapOf(
                    PROCESSED to true,
                    PROCESSED_AT_MS to processedAtMs,
                ),
            )
            .get()
    }

    override suspend fun readAdminReviewLessonTasks(): List<AdminReviewLessonTask> =
        firestore.collection(ADMIN_COLLECTION)
            .document(REVIEW_DOCUMENT)
            .collection(LESSONS_COLLECTION)
            .get()
            .get()
            .documents
            .map { it.toAdminReviewLessonTask() }

    override suspend fun readAdminReviewLessonTasksByIds(ids: Set<String>): List<AdminReviewLessonTask> {
        if (ids.isEmpty()) return emptyList()
        return ids.mapNotNull { assignmentId ->
            val change = firestore.document(ReviewFirestorePaths.adminSyncChange(assignmentId)).get().get()
            val lessonId = change.data.orEmpty().stringOrNull(LESSON_ID) ?: return@mapNotNull null
            val lesson = firestore.document(ReviewFirestorePaths.adminLesson(lessonId)).get().get()
            if (lesson.exists()) lesson.toAdminReviewLessonTask() else null
        }
    }

    override suspend fun readAdminReviewLessonTask(assignmentId: String): AdminReviewLessonTask? =
        readAdminReviewLessonTasksByIds(setOf(assignmentId)).firstOrNull()

    override suspend fun readAssignmentChangesSince(cursorMs: Long): List<ReviewAssignmentChange> =
        firestore.collection(ADMIN_COLLECTION)
            .document(REVIEW_DOCUMENT)
            .collection(SYNC_CHANGES_COLLECTION)
            .whereGreaterThan(CHANGED_AT_MS, cursorMs)
            .get()
            .get()
            .documents
            .mapNotNull { snapshot ->
                val data = snapshot.data.orEmpty()
                val assignmentId = data.stringOrNull(ASSIGNMENT_ID) ?: data.stringOrNull(ID) ?: return@mapNotNull null
                val lessonId = data.stringOrNull(LESSON_ID) ?: return@mapNotNull null
                ReviewAssignmentChange(
                    assignmentId = assignmentId,
                    lessonId = lessonId,
                    changedAtMs = data.long(CHANGED_AT_MS),
                )
            }

    override suspend fun readReviewRecords(lessonId: String): List<ReviewRecord> =
        firestore.document(ReviewFirestorePaths.adminLesson(lessonId))
            .collection(REVIEWS_COLLECTION)
            .get()
            .get()
            .documents
            .map { it.toReviewRecord(lessonId) }

    override suspend fun writeReviewRecord(record: ReviewRecord) {
        firestore.document(ReviewFirestorePaths.adminReviewRecord(record.lessonId, record.id))
            .set(record.toDocument(), SetOptions.merge())
            .get()
    }

    override suspend fun addReviewerReputation(
        uid: String,
        points: Int,
    ) {
        if (points == 0) return
        val ref = firestore.collection(ReviewFirestorePaths.PROFILES_COLLECTION).document(uid)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(ref).get()
            val current = snapshot.data.orEmpty().long(REVIEW_REPUTATION)
            transaction.set(
                ref,
                mapOf(REVIEW_REPUTATION to current + points),
                SetOptions.merge(),
            )
        }.get()
    }

    private fun writeDocuments(documents: Map<String, Map<String, Any?>>) {
        if (documents.isEmpty()) return
        val batch = firestore.batch()
        documents.forEach { (path, data) ->
            batch.set(firestore.document(path), data, SetOptions.merge())
        }
        batch.commit().get()
    }

    private fun DocumentSnapshot.toArenaSubmissionRequest(): QuestArenaSubmissionRequest {
        val data = data.orEmpty()
        return QuestArenaSubmissionRequest(
            submissionId = data.string(SUBMISSION_ID, fallback = id),
            draftId = data.string(DRAFT_ID),
            ownerUid = data.string(OWNER_UID),
            localRevision = data.long(LOCAL_REVISION),
            requestedAtMs = data.long(REQUESTED_AT_MS),
            draft = data.map(DRAFT).toDraftDto(),
            sections = data.list(SECTIONS).map { it.toSectionDto() },
            themes = data.list(THEMES).map { it.toThemeDto() },
            lessons = data.list(LESSONS).map { it.toLessonDto() },
            questions = data.list(QUESTIONS).map { it.toQuestionDto() },
            review = data.map(REVIEW).toArenaReviewDto(),
        )
    }

    private fun DocumentSnapshot.toAdminReviewLessonTask(): AdminReviewLessonTask {
        val data = data.orEmpty()
        val questId = data.string(QUEST_ID)
        val questions = reference.collection(QUESTS_COLLECTION)
            .document(questId)
            .collection(QUESTIONS_COLLECTION)
            .get()
            .get()
            .documents
            .map { it.data.orEmpty().toQuestionDto() }
        return AdminReviewLessonTask(
            id = "${data.string(SUBMISSION_ID)}_$id",
            submissionId = data.string(SUBMISSION_ID),
            ownerUid = data.string(OWNER_UID),
            catalogId = data.string(CATALOG_ID),
            draftId = data.string(DRAFT_ID),
            questId = questId,
            lessonId = id,
            title = data.string(TITLE),
            createdAtMs = data.long(CREATED_AT_MS),
            changedAtMs = data.long(CHANGED_AT_MS, fallback = data.long(CREATED_AT_MS)),
            checks = data.map(CHECKS).toChecks(),
            questions = questions,
            sourceLanguages = data.stringSet(SOURCE_LANGUAGES).ifEmpty {
                questions.mapTo(linkedSetOf()) { it.language.trim().lowercase() }
            },
        )
    }

    private fun DocumentSnapshot.toReviewRecord(lessonId: String): ReviewRecord {
        val data = data.orEmpty()
        return ReviewRecord(
            id = data.string(ID, fallback = id),
            lessonId = data.string(LESSON_ID, fallback = lessonId),
            kind = ReviewTaskKind.valueOf(data.string(KIND)),
            reviewerUid = data.string(REVIEWER_UID),
            reviewerLevelAtSubmit = data.int(REVIEWER_LEVEL_AT_SUBMIT),
            score = data.longOrNull(SCORE)?.toInt(),
            language = data.stringOrNull(LANGUAGE),
            targetReviewId = data.stringOrNull(TARGET_REVIEW_ID),
            createdAtMs = data.long(CREATED_AT_MS),
            acceptedByServer = data.boolean(ACCEPTED_BY_SERVER),
            segmentResults = data.list(SEGMENT_RESULTS).map { it.toSegmentResult() },
            translatedQuestions = data.list(TRANSLATED_QUESTIONS).map { it.toQuestionDto() },
        )
    }

    private fun Map<String, Any?>.toDraftDto(): ArenaDraftDto =
        ArenaDraftDto(
            id = string(ID),
            catalogId = string(CATALOG_ID),
            title = string(TITLE),
            description = stringOrNull(DESCRIPTION),
            defaultLanguage = string(DEFAULT_LANGUAGE),
            defaultDifficulty = string(DEFAULT_DIFFICULTY),
            publicQuestId = stringOrNull(PUBLIC_QUEST_ID),
            createdAtMs = long(CREATED_AT_MS),
            updatedAtMs = long(UPDATED_AT_MS),
        )

    private fun Map<String, Any?>.toSectionDto(): ArenaSectionDto =
        ArenaSectionDto(
            id = string(ID),
            draftId = string(DRAFT_ID),
            title = string(TITLE),
            order = int(ORDER),
        )

    private fun Map<String, Any?>.toThemeDto(): ArenaThemeDto =
        ArenaThemeDto(
            id = string(ID),
            draftId = string(DRAFT_ID),
            sectionId = string(SECTION_ID),
            title = string(TITLE),
            order = int(ORDER),
        )

    private fun Map<String, Any?>.toLessonDto(): ArenaLessonDto =
        ArenaLessonDto(
            id = string(ID),
            draftId = string(DRAFT_ID),
            themeId = string(THEME_ID),
            title = string(TITLE),
            order = int(ORDER),
        )

    private fun Map<String, Any?>.toQuestionDto(): ArenaQuestionDto =
        ArenaQuestionDto(
            id = string(ID),
            draftId = string(DRAFT_ID),
            lessonId = string(LESSON_ID),
            type = string(TYPE),
            language = string(LANGUAGE),
            languageLevel = int(LANGUAGE_LEVEL),
            difficulty = string(DIFFICULTY),
            order = int(ORDER),
            text = string(TEXT),
            imagePath = stringOrNull(IMAGE_PATH),
            payload = string(PAYLOAD),
            updatedAtMs = long(UPDATED_AT_MS),
        )

    private fun Map<String, Any?>.toArenaReviewDto(): ArenaReviewDto =
        ArenaReviewDto(
            isTested = boolean(IS_TESTED),
            testingScore = doubleOrNull(TESTING_SCORE),
            isLogicReviewed = boolean(IS_LOGIC_REVIEWED),
            logicScore = doubleOrNull(LOGIC_SCORE),
            isTranslationReviewed = boolean(IS_TRANSLATION_REVIEWED),
            translationScore = longOrNull(TRANSLATION_SCORE)?.toInt(),
            translatedLanguages = languageLevels(TRANSLATED_LANGUAGES),
        )

    private fun Map<String, Any?>.toChecks(): ReviewChecks =
        toArenaReviewDto().toChecks()

    private fun Map<String, Any?>.string(
        field: String,
        fallback: String = "",
    ): String = stringOrNull(field) ?: fallback

    private fun Map<String, Any?>.stringOrNull(field: String): String? =
        this[field]?.toString()?.takeIf { it.isNotBlank() }

    private fun Map<String, Any?>.int(field: String): Int =
        long(field).toInt()

    private fun Map<String, Any?>.long(
        field: String,
        fallback: Long = 0L,
    ): Long = longOrNull(field) ?: fallback

    private fun Map<String, Any?>.longOrNull(field: String): Long? =
        when (val value = this[field]) {
            is Long -> value
            is Int -> value.toLong()
            is Number -> value.toLong()
            else -> null
        }

    private fun Map<String, Any?>.doubleOrNull(field: String): Double? =
        when (val value = this[field]) {
            is Double -> value
            is Float -> value.toDouble()
            is Number -> value.toDouble()
            else -> null
        }

    private fun Map<String, Any?>.boolean(field: String): Boolean =
        this[field] as? Boolean ?: false

    private fun Map<String, Any?>.map(field: String): Map<String, Any?> =
        (this[field] as? Map<*, *>).orEmpty().mapKeys { it.key.toString() }

    private fun Map<String, Any?>.list(field: String): List<Map<String, Any?>> =
        (this[field] as? List<*>).orEmpty()
            .mapNotNull { it as? Map<*, *> }
            .map { item -> item.mapKeys { it.key.toString() } }

    private fun Map<String, Any?>.stringSet(field: String): Set<String> =
        (this[field] as? List<*>).orEmpty()
            .mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
            .toSet()

    private fun Map<String, Any?>.languageLevels(field: String): Map<String, Int> =
        (this[field] as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
            val language = key?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val level = (value as? Number)?.toInt() ?: return@mapNotNull null
            language to level
        }.toMap()

    private fun Map<String, Any?>.toSegmentResult(): ReviewSegmentResult =
        ReviewSegmentResult(
            questionId = string(QUESTION_ID),
            segmentKey = string(SEGMENT_KEY),
            accepted = boolean(ACCEPTED),
        )

    private fun ReviewRecord.toDocument(): Map<String, Any?> =
        mapOf(
            ID to id,
            LESSON_ID to lessonId,
            KIND to kind.name,
            REVIEWER_UID to reviewerUid,
            REVIEWER_LEVEL_AT_SUBMIT to reviewerLevelAtSubmit,
            SCORE to score,
            LANGUAGE to language,
            TARGET_REVIEW_ID to targetReviewId,
            CREATED_AT_MS to createdAtMs,
            ACCEPTED_BY_SERVER to acceptedByServer,
            SEGMENT_RESULTS to segmentResults.map { it.toDocument() },
            TRANSLATED_QUESTIONS to translatedQuestions.map { it.toDocument() },
        )

    private fun ReviewSegmentResult.toDocument(): Map<String, Any?> =
        mapOf(
            QUESTION_ID to questionId,
            SEGMENT_KEY to segmentKey,
            ACCEPTED to accepted,
        )

    private fun ArenaQuestionDto.toDocument(): Map<String, Any?> =
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

    private companion object {
        const val ADMIN_COLLECTION = "admin"
        const val REVIEW_DOCUMENT = "review"
        const val LESSONS_COLLECTION = "lessons"
        const val QUESTS_COLLECTION = "quests"
        const val QUESTIONS_COLLECTION = "questions"
        const val SYNC_CHANGES_COLLECTION = "sync_changes"
        const val REVIEWS_COLLECTION = "reviews"
        const val PROCESSED = "processed"
        const val PROCESSED_AT_MS = "processedAtMs"
        const val ID = "id"
        const val SUBMISSION_ID = "submissionId"
        const val DRAFT_ID = "draftId"
        const val OWNER_UID = "ownerUid"
        const val CATALOG_ID = "catalogId"
        const val QUEST_ID = "questId"
        const val LESSON_ID = "lessonId"
        const val LOCAL_REVISION = "localRevision"
        const val REQUESTED_AT_MS = "requestedAtMs"
        const val DRAFT = "draft"
        const val SECTIONS = "sections"
        const val THEMES = "themes"
        const val LESSONS = "lessons"
        const val QUESTIONS = "questions"
        const val CHECKS = "checks"
        const val REVIEW = "review"
        const val TITLE = "title"
        const val DESCRIPTION = "description"
        const val DEFAULT_LANGUAGE = "defaultLanguage"
        const val DEFAULT_DIFFICULTY = "defaultDifficulty"
        const val PUBLIC_QUEST_ID = "publicQuestId"
        const val CREATED_AT_MS = "createdAtMs"
        const val CHANGED_AT_MS = "changedAtMs"
        const val UPDATED_AT_MS = "updatedAtMs"
        const val SECTION_ID = "sectionId"
        const val THEME_ID = "themeId"
        const val TYPE = "type"
        const val LANGUAGE = "language"
        const val LANGUAGE_LEVEL = "languageLevel"
        const val DIFFICULTY = "difficulty"
        const val ORDER = "order"
        const val TEXT = "text"
        const val IMAGE_PATH = "imagePath"
        const val PAYLOAD = "payload"
        const val TESTER_LEVEL = "testerLevel"
        const val ADMIN_LEVEL = "adminLevel"
        const val TRANSLATOR_LEVEL = "translatorLevel"
        const val DEVELOPER_LEVEL = "developerLevel"
        const val KNOWN_LANGUAGES = "knownLanguages"
        const val REQUIRED_LANGUAGES = "requiredLanguages"
        const val IS_TESTED = "isTested"
        const val TESTING_SCORE = "testingScore"
        const val IS_LOGIC_REVIEWED = "isLogicReviewed"
        const val LOGIC_SCORE = "logicScore"
        const val IS_TRANSLATION_REVIEWED = "isTranslationReviewed"
        const val TRANSLATION_SCORE = "translationScore"
        const val TRANSLATED_LANGUAGES = "translatedLanguages"
        const val ASSIGNMENT_ID = "assignmentId"
        const val KIND = "kind"
        const val REVIEWER_UID = "reviewerUid"
        const val REVIEWER_LEVEL_AT_SUBMIT = "reviewerLevelAtSubmit"
        const val SCORE = "score"
        const val TARGET_REVIEW_ID = "targetReviewId"
        const val ACCEPTED_BY_SERVER = "acceptedByServer"
        const val SEGMENT_RESULTS = "segmentResults"
        const val TRANSLATED_QUESTIONS = "translatedQuestions"
        const val QUESTION_ID = "questionId"
        const val SEGMENT_KEY = "segmentKey"
        const val ACCEPTED = "accepted"
        const val SOURCE_LANGUAGES = "sourceLanguages"
        const val REVIEW_REPUTATION = "reviewReputation"
    }
}
