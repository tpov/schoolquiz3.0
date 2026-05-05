package com.tpov.schoolquiz.server.functions.review

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.firestore.SetOptions
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaDraftDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaLessonDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaQuestionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaReviewDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaSectionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaThemeDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRequest
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LiveFirebaseReviewWorkflowE2eTest {
    @Test
    fun liveFirestoreReviewWorkflowTestingLogicTranslationAndReview() = runTest {
        if (System.getenv(RUN_LIVE_FIREBASE_E2E) != "true") return@runTest

        val serviceAccount = serviceAccountFile()
        val firestore = liveFirestore(serviceAccount)
        val prefix = "codex_review_e2e_${System.currentTimeMillis()}"
        val ids = LiveIds(prefix)
        val originalArenaReviewConfig =
            firestore.document(ReviewFirestorePaths.ARENA_REVIEW_CONFIG).get().get().data
        val runtime =
            QuestReviewRuntime.fromFirestore(
                firestore = firestore,
                clock = IncrementingClock(1_000_000L),
            )
        val request = request(ids)
        val assignmentId = "${ids.submissionId}_${ids.lessonId}"

        try {
            seedLiveConfigAndProfiles(firestore, ids)
            writeReviewRequest(firestore, request)

            val processed =
                QuestArenaSubmissionProcessor(
                    store = FirestoreQuestReviewStore(firestore),
                    clock = IncrementingClock(2_000_000L),
                ).process(request).getOrThrow()

            assertEquals(ids.submissionId, processed.submissionId)
            assertTrue(processed.adminLessonTaskPaths.contains(ReviewFirestorePaths.adminLesson(ids.lessonId)))

            val emptyAssignments = runtime.fetchReviewAssignments(ids.testerStrongUid, emptySet())
            assertEquals(emptyList<Any>(), emptyAssignments["assignments"])

            val testerAssignments = runtime.fetchReviewAssignments(ids.testerStrongUid, setOf(assignmentId))
            val testerAssignment = testerAssignments.singleAssignment()
            assertEquals(listOf("TESTING"), testerAssignment["taskKinds"])

            runtime.submitReviewAction(
                ids.testerWeakUid,
                SubmitReviewAction(
                    assignmentId = assignmentId,
                    lessonId = ids.lessonId,
                    kind = ReviewTaskKind.TESTING,
                    score = 1,
                ),
            )
            runtime.submitReviewAction(
                ids.testerStrongUid,
                SubmitReviewAction(
                    assignmentId = assignmentId,
                    lessonId = ids.lessonId,
                    kind = ReviewTaskKind.TESTING,
                    score = 3,
                ),
            )
            assertEquals(3.0, firestore.adminLesson(ids.lessonId).get().get().doubleValue("testingScore"))
            assertEquals(-3L, firestore.profile(ids.testerWeakUid).get().get().getLong("reviewReputation"))

            runtime.submitReviewAction(
                ids.adminUid,
                SubmitReviewAction(
                    assignmentId = assignmentId,
                    lessonId = ids.lessonId,
                    kind = ReviewTaskKind.LOGIC,
                    score = 2,
                ),
            )
            assertEquals(2.0, firestore.adminLesson(ids.lessonId).get().get().doubleValue("logicScore"))

            val translatorAssignments = runtime.fetchReviewAssignments(ids.translatorUid, setOf(assignmentId))
            val translatorAssignment = translatorAssignments.singleAssignment()
            assertEquals(listOf("TRANSLATION"), translatorAssignment["taskKinds"])
            assertEquals(listOf("en"), translatorAssignment["newTranslationLanguages"])

            runtime.submitReviewAction(
                ids.translatorUid,
                SubmitReviewAction(
                    assignmentId = assignmentId,
                    lessonId = ids.lessonId,
                    kind = ReviewTaskKind.TRANSLATION,
                    language = "en",
                    translatedQuestions = listOf(translatedQuestion(ids)),
                ),
            )
            val translatedQuestion =
                firestore
                    .document(ReviewFirestorePaths.adminQuestion(ids.lessonId, ids.questId, "${ids.questionId}__en"))
                    .get()
                    .get()
            assertTrue(translatedQuestion.exists())

            val reviewerAssignments = runtime.fetchReviewAssignments(ids.translationReviewerUid, setOf(assignmentId))
            val reviewerAssignment = reviewerAssignments.singleAssignment()
            assertEquals(listOf("TRANSLATION_REVIEW"), reviewerAssignment["taskKinds"])
            assertEquals(listOf("en"), reviewerAssignment["reviewLanguages"])

            runtime.submitReviewAction(
                ids.translationReviewerUid,
                SubmitReviewAction(
                    assignmentId = assignmentId,
                    lessonId = ids.lessonId,
                    kind = ReviewTaskKind.TRANSLATION_REVIEW,
                    language = "en",
                    segmentResults =
                        listOf(
                            ReviewSegmentResult(ids.questionId, "text", accepted = true),
                            ReviewSegmentResult(ids.questionId, "option:A", accepted = false),
                        ),
                ),
            )

            val aggregate = firestore.adminLesson(ids.lessonId).get().get()
            assertEquals(50L, aggregate.getLong("translationScore"))
            assertEquals(4L, firestore.profile(ids.translatorUid).get().get().getLong("reviewReputation"))
        } finally {
            cleanupLiveDocuments(firestore, ids, originalArenaReviewConfig)
            firestore.close()
        }
    }

    private fun serviceAccountFile(): File {
        val configured = System.getenv(SERVICE_ACCOUNT_ENV)?.takeIf { it.isNotBlank() }
        return File(configured ?: DEFAULT_SERVICE_ACCOUNT_PATH)
    }

    private fun liveFirestore(serviceAccount: File): Firestore {
        require(serviceAccount.isFile) {
            "Service account file not found: ${serviceAccount.absolutePath}"
        }
        val credentials = FileInputStream(serviceAccount).use { GoogleCredentials.fromStream(it) }
        return FirestoreOptions
            .newBuilder()
            .setProjectId(PROJECT_ID)
            .setCredentials(credentials)
            .build()
            .service
    }

    private fun seedLiveConfigAndProfiles(
        firestore: Firestore,
        ids: LiveIds,
    ) {
        firestore.document(ReviewFirestorePaths.ARENA_REVIEW_CONFIG)
            .set(
                mapOf(
                    "requiredLanguages" to listOf("ru", "en"),
                    "updatedAtMs" to 1L,
                ),
                SetOptions.merge(),
            )
            .get()
        mapOf(
            ids.ownerUid to profile(),
            ids.testerWeakUid to profile(testerLevel = 100),
            ids.testerStrongUid to profile(testerLevel = 250),
            ids.adminUid to profile(adminLevel = 250),
            ids.translatorUid to profile(translatorLevel = 180, languages = listOf("ru", "en")),
            ids.translationReviewerUid to profile(translatorLevel = 300, languages = listOf("ru", "en")),
        ).forEach { (uid, data) ->
            firestore.profile(uid).set(data, SetOptions.merge()).get()
        }
    }

    private fun writeReviewRequest(
        firestore: Firestore,
        request: QuestArenaSubmissionRequest,
    ) {
        firestore.collection(ReviewFirestorePaths.REVIEW_REQUESTS_COLLECTION)
            .document(request.submissionId)
            .set(request.toDocument(), SetOptions.merge())
            .get()
    }

    private fun cleanupLiveDocuments(
        firestore: Firestore,
        ids: LiveIds,
        originalArenaReviewConfig: Map<String, Any?>?,
    ) {
        firestore.deleteIfExists(ReviewFirestorePaths.adminSyncChange("${ids.submissionId}_${ids.lessonId}"))
        firestore.deleteCollection(ReviewFirestorePaths.adminQuest(ids.lessonId, ids.questId), "questions")
        firestore.deleteIfExists(ReviewFirestorePaths.adminQuest(ids.lessonId, ids.questId))
        firestore.deleteCollection(ReviewFirestorePaths.adminLesson(ids.lessonId), "reviews")
        firestore.deleteCollection(ReviewFirestorePaths.adminLesson(ids.lessonId), "quests")
        firestore.deleteIfExists(ReviewFirestorePaths.adminLesson(ids.lessonId))
        firestore.deleteIfExists("${ReviewFirestorePaths.REVIEW_REQUESTS_COLLECTION}/${ids.submissionId}")

        firestore.deleteCollection(
            ReviewFirestorePaths.privateLesson(
                ownerUid = ids.ownerUid,
                catalogId = ids.catalogId,
                questId = ids.questId,
                sectionId = ids.sectionId,
                themeId = ids.themeId,
                lessonId = ids.lessonId,
            ),
            "questions",
        )
        firestore.deleteIfExists(
            ReviewFirestorePaths.privateLesson(
                ownerUid = ids.ownerUid,
                catalogId = ids.catalogId,
                questId = ids.questId,
                sectionId = ids.sectionId,
                themeId = ids.themeId,
                lessonId = ids.lessonId,
            ),
        )
        firestore.deleteIfExists(
            ReviewFirestorePaths.privateTheme(
                ownerUid = ids.ownerUid,
                catalogId = ids.catalogId,
                questId = ids.questId,
                sectionId = ids.sectionId,
                themeId = ids.themeId,
            ),
        )
        firestore.deleteIfExists(
            ReviewFirestorePaths.privateSection(
                ownerUid = ids.ownerUid,
                catalogId = ids.catalogId,
                questId = ids.questId,
                sectionId = ids.sectionId,
            ),
        )
        firestore.deleteIfExists(ReviewFirestorePaths.privateSyncChange(ids.ownerUid, ids.catalogId, ids.questId))
        firestore.deleteIfExists(ReviewFirestorePaths.privateQuest(ids.ownerUid, ids.catalogId, ids.questId))
        firestore.deleteIfExists(ReviewFirestorePaths.privateCatalog(ids.ownerUid, ids.catalogId))

        listOf(
            ids.ownerUid,
            ids.testerWeakUid,
            ids.testerStrongUid,
            ids.adminUid,
            ids.translatorUid,
            ids.translationReviewerUid,
        ).forEach { uid -> firestore.deleteIfExists("profiles/$uid") }

        val configRef = firestore.document(ReviewFirestorePaths.ARENA_REVIEW_CONFIG)
        if (originalArenaReviewConfig == null) {
            configRef.delete().get()
        } else {
            configRef.set(originalArenaReviewConfig).get()
        }
    }

    private fun Firestore.deleteCollection(
        parentPath: String,
        collectionId: String,
    ) {
        document(parentPath)
            .collection(collectionId)
            .listDocuments()
            .forEach { it.delete().get() }
    }

    private fun Firestore.deleteIfExists(path: String) {
        document(path).delete().get()
    }

    private fun Firestore.adminLesson(lessonId: String): DocumentReference =
        document(ReviewFirestorePaths.adminLesson(lessonId))

    private fun Firestore.profile(uid: String): DocumentReference =
        collection(ReviewFirestorePaths.PROFILES_COLLECTION).document(uid)

    private fun Map<String, Any?>.singleAssignment(): Map<*, *> {
        val assignments = this["assignments"] as? List<*>
        assertNotNull(assignments)
        return assignments.single() as Map<*, *>
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.doubleValue(field: String): Double? =
        getDouble(field) ?: getLong(field)?.toDouble()

    private fun profile(
        testerLevel: Int = 0,
        adminLevel: Int = 0,
        translatorLevel: Int = 0,
        developerLevel: Int = 0,
        languages: List<String> = emptyList(),
    ): Map<String, Any?> =
        mapOf(
            "testerLevel" to testerLevel,
            "adminLevel" to adminLevel,
            "translatorLevel" to translatorLevel,
            "developerLevel" to developerLevel,
            "knownLanguages" to languages,
            "reviewReputation" to 0L,
        )

    private fun request(ids: LiveIds): QuestArenaSubmissionRequest =
        QuestArenaSubmissionRequest(
            submissionId = ids.submissionId,
            draftId = ids.questId,
            ownerUid = ids.ownerUid,
            localRevision = 1L,
            requestedAtMs = 10L,
            draft =
                ArenaDraftDto(
                    id = ids.questId,
                    catalogId = ids.catalogId,
                    title = "Codex live review e2e",
                    description = "Temporary live Firestore e2e fixture",
                    defaultLanguage = "ru",
                    defaultDifficulty = "EASY",
                    publicQuestId = null,
                    createdAtMs = 1L,
                    updatedAtMs = 10L,
                ),
            sections = listOf(ArenaSectionDto(ids.sectionId, ids.questId, "Section", 0)),
            themes = listOf(ArenaThemeDto(ids.themeId, ids.questId, ids.sectionId, "Theme", 0)),
            lessons = listOf(ArenaLessonDto(ids.lessonId, ids.questId, ids.themeId, "Lesson", 0)),
            questions = listOf(sourceQuestion(ids)),
            review = ArenaReviewDto(translatedLanguages = mapOf("ru" to 125)),
        )

    private fun sourceQuestion(ids: LiveIds): ArenaQuestionDto =
        ArenaQuestionDto(
            id = ids.questionId,
            draftId = ids.questId,
            lessonId = ids.lessonId,
            type = "SINGLE_CHOICE",
            language = "ru",
            languageLevel = 125,
            difficulty = "EASY",
            order = 0,
            text = "Сколько будет два плюс два?",
            imagePath = null,
            payload =
                """
                {
                  "type":"SingleChoice",
                  "id":"${ids.questionId}",
                  "difficulty":"EASY",
                  "text":"Сколько будет два плюс два?",
                  "imageUrl":null,
                  "options":[{"id":"A","text":"Четыре"},{"id":"B","text":"Пять"}],
                  "correctOptionId":"A",
                  "info":"Проверочный вопрос"
                }
                """.trimIndent(),
            updatedAtMs = 10L,
        )

    private fun translatedQuestion(ids: LiveIds): ArenaQuestionDto =
        sourceQuestion(ids).copy(
            language = "en",
            text = "What is two plus two?",
            payload =
                """
                {
                  "type":"SingleChoice",
                  "id":"${ids.questionId}",
                  "difficulty":"EASY",
                  "text":"What is two plus two?",
                  "imageUrl":null,
                  "options":[{"id":"A","text":"Four"},{"id":"B","text":"Five"}],
                  "correctOptionId":"A",
                  "info":"Verification question"
                }
                """.trimIndent(),
        )

    private fun QuestArenaSubmissionRequest.toDocument(): Map<String, Any?> =
        mapOf(
            "type" to "SUBMIT_TO_ARENA",
            "processed" to false,
            "submissionId" to submissionId,
            "draftId" to draftId,
            "ownerUid" to ownerUid,
            "localRevision" to localRevision,
            "requestedAtMs" to requestedAtMs,
            "draft" to draft.toDocument(),
            "sections" to sections.map { it.toDocument() },
            "themes" to themes.map { it.toDocument() },
            "lessons" to lessons.map { it.toDocument() },
            "questions" to questions.map { it.toDocument() },
            "review" to review.toDocument(),
        )

    private fun ArenaDraftDto.toDocument(): Map<String, Any?> =
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

    private fun ArenaSectionDto.toDocument(): Map<String, Any?> =
        mapOf(
            "id" to id,
            "draftId" to draftId,
            "title" to title,
            "order" to order,
        )

    private fun ArenaThemeDto.toDocument(): Map<String, Any?> =
        mapOf(
            "id" to id,
            "draftId" to draftId,
            "sectionId" to sectionId,
            "title" to title,
            "order" to order,
        )

    private fun ArenaLessonDto.toDocument(): Map<String, Any?> =
        mapOf(
            "id" to id,
            "draftId" to draftId,
            "themeId" to themeId,
            "title" to title,
            "order" to order,
        )

    private fun ArenaQuestionDto.toDocument(): Map<String, Any?> =
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

    private fun ArenaReviewDto.toDocument(): Map<String, Any?> =
        mapOf(
            "isTested" to isTested,
            "testingScore" to testingScore,
            "isLogicReviewed" to isLogicReviewed,
            "logicScore" to logicScore,
            "isTranslationReviewed" to isTranslationReviewed,
            "translationScore" to translationScore,
            "translatedLanguages" to translatedLanguages,
        )

    private class IncrementingClock(
        private var value: Long,
    ) : ReviewClock {
        override fun nowMs(): Long = value++
    }

    private data class LiveIds(
        private val prefix: String,
    ) {
        val ownerUid = "${prefix}_owner"
        val testerWeakUid = "${prefix}_tester_weak"
        val testerStrongUid = "${prefix}_tester_strong"
        val adminUid = "${prefix}_admin"
        val translatorUid = "${prefix}_translator"
        val translationReviewerUid = "${prefix}_translation_reviewer"
        val catalogId = "${prefix}_catalog"
        val questId = "${prefix}_quest"
        val sectionId = "${prefix}_section"
        val themeId = "${prefix}_theme"
        val lessonId = "${prefix}_lesson"
        val questionId = "${prefix}_question"
        val submissionId = "${prefix}_submission"
    }

    private companion object {
        const val RUN_LIVE_FIREBASE_E2E = "RUN_LIVE_FIREBASE_E2E"
        const val SERVICE_ACCOUNT_ENV = "SCHOOLQUIZ_FIREBASE_SERVICE_ACCOUNT"
        const val DEFAULT_SERVICE_ACCOUNT_PATH =
            "/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json"
        const val PROJECT_ID = "school-quiz-89336951"
    }
}
