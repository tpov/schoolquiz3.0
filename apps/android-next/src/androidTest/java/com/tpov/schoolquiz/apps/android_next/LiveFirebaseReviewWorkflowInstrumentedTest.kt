package com.tpov.schoolquiz.apps.android_next

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.quest_authoring.FirebaseQuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.quest_authoring.FirebaseReviewAssignmentRemoteDataSource
import com.tpov.schoolquiz.shared.core.persistence.AppDatabase
import com.tpov.schoolquiz.shared.core.persistence.RoomSyncStateRepository
import com.tpov.schoolquiz.shared.core.persistence.StringSetConverter
import com.tpov.schoolquiz.shared.core.persistence.TopParticipantListConverter
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.ReviewAssignmentLocalDataSourceImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaDraftDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaLessonDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaQuestionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaReviewDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaSectionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaThemeDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRequest
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewSegmentResultDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.SubmitReviewActionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync.ReviewAssignmentSync
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync.reviewAssignmentSyncCursorId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveFirebaseReviewWorkflowInstrumentedTest {
    private val args = InstrumentationRegistry.getArguments()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val submissionRemote = FirebaseQuestArenaSubmissionRemoteDataSource(firestore)
    private val reviewRemote =
        FirebaseReviewAssignmentRemoteDataSource(FirebaseFunctions.getInstance(REGION))

    @Test
    fun pixelRunsFullReviewWorkflowAgainstProductionFirebase() =
        runBlocking {
            val ids = LiveIds(requiredArg(ARG_PREFIX))
            val tokens = LiveTokens.fromArgs(args)
            val assignmentId = "${ids.submissionId}_${ids.lessonId}"

            // AppApplication starts anonymous auth for normal runtime; let that settle before
            // switching this test through deterministic custom-token users.
            delay(AUTH_BOOTSTRAP_DELAY_MS)

            signIn(tokens.owner, ids.ownerUid)
            submissionRemote.submit(request(ids))

            waitForProcessed(ids)
            assertTrue(serverDoc(privateQuestPath(ids)).exists())
            assertTrue(serverDoc(privateSyncChangePath(ids)).exists())

            signIn(tokens.testerStrong, ids.testerStrongUid)
            val changes = reviewRemote.fetchAssignmentChangesSince(0L)
            val assignmentChange = changes.firstOrNull { it.id == assignmentId }
            assertNotNull("review assignment change must include $assignmentId", assignmentChange)
            assertTrue(
                "cursor should not re-download the same admin/private sync change",
                reviewRemote.fetchAssignmentChangesSince(assignmentChange!!.changedAtMs).none { it.id == assignmentId },
            )
            val testerAssignment = reviewRemote.fetchByIds(setOf(assignmentId)).single()
            assertEquals(setOf(TESTING), testerAssignment.taskKinds)
            assertEquals(1, testerAssignment.questions.size)
            assertLocalReviewSyncDownloadsOnlyVisibleAssignments(
                uid = ids.testerStrongUid,
                expectedAssignmentId = assignmentId,
                expectedTaskKind = TESTING,
                expectedQuestionId = ids.questionId,
            )

            signIn(tokens.testerWeak, ids.testerWeakUid)
            reviewRemote.submitReviewAction(
                SubmitReviewActionDto(
                    assignmentId = assignmentId,
                    lessonId = ids.lessonId,
                    kind = TESTING,
                    score = 1,
                ),
            )

            signIn(tokens.testerStrong, ids.testerStrongUid)
            reviewRemote.submitReviewAction(
                SubmitReviewActionDto(
                    assignmentId = assignmentId,
                    lessonId = ids.lessonId,
                    kind = TESTING,
                    score = 3,
                ),
            )
            waitForAdminLesson(ids) { it.doubleValue("testingScore") == 3.0 }

            signIn(tokens.admin, ids.adminUid)
            val adminAssignment = reviewRemote.fetchByIds(setOf(assignmentId)).single()
            assertEquals(setOf(LOGIC), adminAssignment.taskKinds)
            reviewRemote.submitReviewAction(
                SubmitReviewActionDto(
                    assignmentId = assignmentId,
                    lessonId = ids.lessonId,
                    kind = LOGIC,
                    score = 2,
                ),
            )
            waitForAdminLesson(ids) { it.doubleValue("logicScore") == 2.0 }

            signIn(tokens.translatorWeak, ids.translatorWeakUid)
            assertTrue(reviewRemote.fetchByIds(setOf(assignmentId)).isEmpty())
            assertLocalReviewSyncHasNoVisibleAssignments(ids.translatorWeakUid)

            signIn(tokens.translator, ids.translatorUid)
            val translationAssignment = reviewRemote.fetchByIds(setOf(assignmentId)).single()
            assertEquals(setOf(TRANSLATION), translationAssignment.taskKinds)
            assertEquals(setOf("ru"), translationAssignment.sourceLanguages)
            assertEquals(setOf("en"), translationAssignment.newTranslationLanguages)
            reviewRemote.submitReviewAction(
                SubmitReviewActionDto(
                    assignmentId = assignmentId,
                    lessonId = ids.lessonId,
                    kind = TRANSLATION,
                    language = "en",
                    translatedQuestions = listOf(translatedQuestion(ids)),
                ),
            )
            waitForAdminLesson(ids) { snapshot ->
                val translatedLanguages = snapshot.get("translatedLanguages") as? Map<*, *>
                (translatedLanguages?.get("en") as? Number)?.toInt() == TRANSLATOR_LEVEL
            }
            assertTrue(serverDoc(adminTranslatedQuestionPath(ids)).exists())

            signIn(tokens.translationReviewer, ids.translationReviewerUid)
            val reviewAssignment = reviewRemote.fetchByIds(setOf(assignmentId)).single()
            assertEquals(setOf(TRANSLATION_REVIEW), reviewAssignment.taskKinds)
            assertEquals(setOf("en"), reviewAssignment.reviewLanguages)
            reviewRemote.submitReviewAction(
                SubmitReviewActionDto(
                    assignmentId = assignmentId,
                    lessonId = ids.lessonId,
                    kind = TRANSLATION_REVIEW,
                    language = "en",
                    segmentResults =
                        listOf(
                            ReviewSegmentResultDto(ids.questionId, "text", accepted = true),
                            ReviewSegmentResultDto(ids.questionId, "option:A", accepted = false),
                        ),
                ),
            )

            waitForAdminLesson(ids) { it.getLong("translationScore") == 50L }
            assertEquals(4L, serverDoc(profilePath(ids.translatorUid)).getLong("reviewReputation"))
            assertEquals(-3L, serverDoc(profilePath(ids.testerWeakUid)).getLong("reviewReputation"))
        }

    private suspend fun assertLocalReviewSyncDownloadsOnlyVisibleAssignments(
        uid: String,
        expectedAssignmentId: String,
        expectedTaskKind: String,
        expectedQuestionId: String,
    ) {
        val database = inMemoryDatabase()
        try {
            val local = ReviewAssignmentLocalDataSourceImpl(database.reviewAssignmentDao())
            val syncState = RoomSyncStateRepository(database.syncStateDao())
            val sync = ReviewAssignmentSync(local, reviewRemote, syncState) { auth.currentUser?.uid }

            val result = sync.sync()

            assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
            val localAssignments = local.findAssignmentDetails(uid)
            assertEquals(listOf(expectedAssignmentId), localAssignments.map { it.assignment.id })
            val localAssignment = localAssignments.single()
            assertEquals(setOf(expectedTaskKind), localAssignment.assignment.taskKinds.toSet())
            assertEquals(listOf(expectedQuestionId), localAssignment.questions.map { it.questionId })
            assertTrue(syncState.getCursor(reviewAssignmentSyncCursorId(uid)) > 0L)

            val secondResult = sync.sync()

            assertTrue(secondResult.exceptionOrNull()?.message.orEmpty(), secondResult.isSuccess)
            assertEquals(
                listOf(expectedAssignmentId),
                local.findAssignmentDetails(uid).map { it.assignment.id },
            )
        } finally {
            database.close()
        }
    }

    private suspend fun assertLocalReviewSyncHasNoVisibleAssignments(uid: String) {
        val database = inMemoryDatabase()
        try {
            val local = ReviewAssignmentLocalDataSourceImpl(database.reviewAssignmentDao())
            val syncState = RoomSyncStateRepository(database.syncStateDao())
            val sync = ReviewAssignmentSync(local, reviewRemote, syncState) { auth.currentUser?.uid }

            val result = sync.sync()

            assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
            assertTrue(local.findAssignmentDetails(uid).isEmpty())
            assertTrue(syncState.getCursor(reviewAssignmentSyncCursorId(uid)) > 0L)
        } finally {
            database.close()
        }
    }

    private fun inMemoryDatabase(): AppDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
    }

    private suspend fun signIn(
        token: String,
        expectedUid: String,
    ) {
        auth.signOut()
        auth.signInWithCustomToken(token).awaitValue()
        withTimeout(DEFAULT_TIMEOUT_MS) {
            while (auth.currentUser?.uid != expectedUid) {
                delay(POLL_INTERVAL_MS)
            }
        }
        assertEquals(expectedUid, auth.currentUser?.uid)
    }

    private suspend fun waitForProcessed(ids: LiveIds) {
        withTimeout(PROCESSING_TIMEOUT_MS) {
            while (serverDoc(reviewRequestPath(ids)).getBoolean("processed") != true) {
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun waitForAdminLesson(
        ids: LiveIds,
        predicate: (DocumentSnapshot) -> Boolean,
    ) {
        withTimeout(DEFAULT_TIMEOUT_MS) {
            while (!predicate(serverDoc(adminLessonPath(ids)))) {
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun serverDoc(path: String): DocumentSnapshot =
        firestore.document(path).get(Source.SERVER).awaitValue()

    private fun <T> Task<T>.awaitValue(): T = Tasks.await(this, DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)

    private fun requiredArg(name: String): String =
        requireNotNull(args.getString(name)?.takeIf { it.isNotBlank() }) {
            "Missing instrumentation argument: $name"
        }

    private fun request(ids: LiveIds): QuestArenaSubmissionRequest =
        QuestArenaSubmissionRequest(
            submissionId = ids.submissionId,
            draftId = ids.questId,
            ownerUid = ids.ownerUid,
            localRevision = 1L,
            requestedAtMs = System.currentTimeMillis(),
            draft =
                ArenaDraftDto(
                    id = ids.questId,
                    catalogId = ids.catalogId,
                    title = "Pixel live review e2e",
                    description = "Temporary production Firebase fixture",
                    defaultLanguage = "ru",
                    defaultDifficulty = "EASY",
                    publicQuestId = null,
                    createdAtMs = 1L,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            sections = listOf(ArenaSectionDto(ids.sectionId, ids.questId, "Pixel section", 0)),
            themes = listOf(ArenaThemeDto(ids.themeId, ids.questId, ids.sectionId, "Pixel theme", 0)),
            lessons = listOf(ArenaLessonDto(ids.lessonId, ids.questId, ids.themeId, "Pixel lesson", 0)),
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
            updatedAtMs = System.currentTimeMillis(),
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

    private data class LiveIds(
        private val prefix: String,
    ) {
        val ownerUid = "${prefix}_owner"
        val testerWeakUid = "${prefix}_tester_weak"
        val testerStrongUid = "${prefix}_tester_strong"
        val adminUid = "${prefix}_admin"
        val translatorWeakUid = "${prefix}_translator_weak"
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

    private data class LiveTokens(
        val owner: String,
        val testerWeak: String,
        val testerStrong: String,
        val admin: String,
        val translatorWeak: String,
        val translator: String,
        val translationReviewer: String,
    ) {
        companion object {
            fun fromArgs(args: android.os.Bundle): LiveTokens =
                LiveTokens(
                    owner = args.required(ARG_OWNER_TOKEN),
                    testerWeak = args.required(ARG_TESTER_WEAK_TOKEN),
                    testerStrong = args.required(ARG_TESTER_STRONG_TOKEN),
                    admin = args.required(ARG_ADMIN_TOKEN),
                    translatorWeak = args.required(ARG_TRANSLATOR_WEAK_TOKEN),
                    translator = args.required(ARG_TRANSLATOR_TOKEN),
                    translationReviewer = args.required(ARG_TRANSLATION_REVIEWER_TOKEN),
                )

            private fun android.os.Bundle.required(name: String): String =
                requireNotNull(getString(name)?.takeIf { it.isNotBlank() }) {
                    "Missing instrumentation argument: $name"
                }
        }
    }

    private fun reviewRequestPath(ids: LiveIds): String = "quest_review_requests/${ids.submissionId}"

    private fun privateQuestPath(ids: LiveIds): String =
        "private/${ids.ownerUid}/catalogs/${ids.catalogId}/quests/${ids.questId}"

    private fun privateSyncChangePath(ids: LiveIds): String =
        "private/${ids.ownerUid}/sync_changes/${ids.catalogId}_${ids.questId}"

    private fun adminLessonPath(ids: LiveIds): String = "admin/review/lessons/${ids.lessonId}"

    private fun adminTranslatedQuestionPath(ids: LiveIds): String =
        "admin/review/lessons/${ids.lessonId}/quests/${ids.questId}/questions/${ids.questionId}__en"

    private fun profilePath(uid: String): String = "profiles/$uid"

    private fun DocumentSnapshot.doubleValue(field: String): Double? =
        getDouble(field) ?: getLong(field)?.toDouble()

    private companion object {
        const val REGION = "us-central1"
        const val ARG_PREFIX = "reviewE2ePrefix"
        const val ARG_OWNER_TOKEN = "reviewE2eOwnerToken"
        const val ARG_TESTER_WEAK_TOKEN = "reviewE2eTesterWeakToken"
        const val ARG_TESTER_STRONG_TOKEN = "reviewE2eTesterStrongToken"
        const val ARG_ADMIN_TOKEN = "reviewE2eAdminToken"
        const val ARG_TRANSLATOR_WEAK_TOKEN = "reviewE2eTranslatorWeakToken"
        const val ARG_TRANSLATOR_TOKEN = "reviewE2eTranslatorToken"
        const val ARG_TRANSLATION_REVIEWER_TOKEN = "reviewE2eTranslationReviewerToken"
        const val TESTING = "TESTING"
        const val LOGIC = "LOGIC"
        const val TRANSLATION = "TRANSLATION"
        const val TRANSLATION_REVIEW = "TRANSLATION_REVIEW"
        const val TRANSLATOR_LEVEL = 180
        const val AUTH_BOOTSTRAP_DELAY_MS = 1_500L
        const val POLL_INTERVAL_MS = 1_000L
        const val DEFAULT_TIMEOUT_MS = 60_000L
        const val PROCESSING_TIMEOUT_MS = 180_000L
    }
}
