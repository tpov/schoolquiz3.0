package com.tpov.schoolquiz.server.functions.review

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.persistence.DraftLessonEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftQuestionEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftSectionEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftThemeEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestArenaSubmissionEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftSummaryEntity
import com.tpov.schoolquiz.shared.core.persistence.ReviewAssignmentEntity
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.KotlinxSerializationQuestionContentParser
import com.tpov.schoolquiz.shared.core.sync.InMemorySyncStateRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringEntityBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringRepositoryImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.ReviewAssignmentLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.ReviewAssignmentRepositoryImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestAuthoringMapper.toEntityBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaQuestionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSnapshot
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSyncChange
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRequest
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestPrivateRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentChangeDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.SubmitReviewActionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync.QuestArenaSubmissionSync
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync.QuestPrivateSync
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync.ReviewAssignmentSync
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.command.CreateQuestDraftCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.command.SaveDraftQuestionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionType
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewAssignmentKind
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewSegmentDecision
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitReviewActionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.CreateQuestDraftUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SaveDraftQuestionUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitQuestDraftToArenaUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitReviewActionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArenaReviewWorkflowEndToEndTest {
    @Test
    fun codeDrivenCreateSubmitSyncProcessAndDownloadWithoutUi() = runTest {
        val server = InMemoryReviewServer()
        server.profiles +=
            mapOf(
                "owner-1" to TrustedProfile(uid = "owner-1"),
                "tester" to TrustedProfile(uid = "tester", testerLevel = 100),
                "translator" to
                    TrustedProfile(
                        uid = "translator",
                        translatorLevel = 200,
                        knownLanguages = setOf("ru", "en"),
                    ),
            )
        val local = FakeQuestLocal(draft = null, pending = mutableListOf())
        val repository = QuestAuthoringRepositoryImpl(local)
        val ids = CountingIdProvider()
        val clock = MutableClock(1_000L)

        val draftId =
            CreateQuestDraftUseCase(repository, ids, clock)(
                CreateQuestDraftCommand(
                    ownerUid = "owner-1",
                    catalogId = CatalogId("math"),
                    sourceQuestId = null,
                    title = "Code Driven Quest",
                    description = "Created by test code",
                    defaultLanguage = "ru",
                    defaultDifficulty = Difficulty.EASY,
                    sectionTitle = "Algebra",
                    themeTitle = "Linear equations",
                    lessonTitle = "Basics",
                ),
            ).getOrThrow()
        val lessonId = requireNotNull(repository.getDraft(draftId)).lessons.single().id
        val save = SaveDraftQuestionUseCase(repository, KotlinxSerializationQuestionContentParser(), ids, clock)

        clock.value = 2_000L
        saveCodeDrivenQuestion(save, draftId, lessonId, "easy", Difficulty.EASY, order = 0)
        clock.value = 3_000L
        saveCodeDrivenQuestion(save, draftId, lessonId, "hard", Difficulty.HARD, order = 1)

        clock.value = 4_000L
        val submissionId = SubmitQuestDraftToArenaUseCase(repository, ids, clock)(draftId).getOrThrow()

        assertTrue(QuestArenaSubmissionSync(local, server, clock).sync().isSuccess)
        assertEquals(submissionId.value, server.pendingRequests.keys.single())

        val processed = server.handler.processPending().getOrThrow().single()

        assertEquals(
            ReviewFirestorePaths.privateQuest("owner-1", "math", draftId.value),
            processed.privateQuestPath,
        )
        assertTrue(ReviewFirestorePaths.adminLesson(lessonId.value) in server.adminDocuments)

        val privateSync = QuestPrivateSync(local, server, InMemorySyncStateRepository()) { "owner-1" }
        assertTrue(privateSync.sync().isSuccess)
        val synced = local.syncedPrivateDrafts.single()
        assertEquals("SYNCED_PRIVATE", synced.draft.status)
        assertEquals(listOf("Question easy?", "Question hard?"), synced.questions.map { it.text })
        assertEquals(listOf(125, 125), synced.questions.map { it.languageLevel })

        assertDownloadedKinds(server, "tester", listOf("TESTING"))
        assertDownloadedKinds(server, "translator", emptyList())

        server.replaceChecks(
            ReviewChecks(
                isTested = true,
                testingScore = 3.0,
                isLogicReviewed = true,
                logicScore = 3.0,
                translatedLanguages = mapOf("ru" to 125),
            ),
        )

        val translatorAssignments = syncAssignments(server, "translator")
        assertEquals(listOf("TRANSLATION"), translatorAssignments.single().taskKinds)
        assertEquals(listOf("ru"), translatorAssignments.single().sourceLanguages)
        assertEquals(listOf("en"), translatorAssignments.single().newTranslationLanguages)
    }

    @Test
    fun runtimeFacadeProcessesPendingRequestsAndReturnsCallableAssignments() = runTest {
        val server = InMemoryReviewServer()
        server.profiles +=
            mapOf(
                "owner-1" to TrustedProfile(uid = "owner-1"),
                "tester" to TrustedProfile(uid = "tester", testerLevel = 100),
            )
        val runtime =
            QuestReviewRuntime.fromStores(
                requestStore = server,
                assignmentStore = server,
                clock = ReviewClock { server.nowMs++ },
            )
        val local = FakeQuestLocal(draft = entityBundle(), pending = mutableListOf(submissionEntity()))

        QuestArenaSubmissionSync(local, server, FakeClock(20L)).sync().getOrThrow()
        val processed = runtime.processPendingArenaRequests().getOrThrow()

        assertEquals(1, processed.size)
        assertEquals("submission-1", processed.single().submissionId)

        server.currentViewerUid = "tester"
        val changes = runtime.fetchReviewAssignmentChanges("tester", cursorMs = 0L)
        val assignmentIds =
            (changes["changes"] as List<*>)
                .map { it as Map<*, *> }
                .map { it["id"].toString() }
                .toSet()
        val response = runtime.fetchReviewAssignments("tester", assignmentIds)
        val assignment = (response["assignments"] as List<*>).single() as Map<*, *>

        assertEquals("submission-1_lesson-1", assignment["id"])
        assertEquals(listOf("TESTING"), assignment["taskKinds"])
    }

    @Test
    fun pushBeforePrivateSyncCopiesToPrivateAdminAndDownloadsOwnerCopy() = runTest {
        val server = InMemoryReviewServer()
        server.profiles["owner-1"] = TrustedProfile(uid = "owner-1")
        val local = FakeQuestLocal(draft = entityBundle(), pending = mutableListOf(submissionEntity()))
        val arenaSync = QuestArenaSubmissionSync(local, server, FakeClock(20L))

        assertTrue(arenaSync.sync().isSuccess)
        assertEquals(1, server.pendingRequests.size)

        val processResult = server.handler.processPending().getOrThrow()

        assertEquals("private/owner-1/catalogs/catalog-1/quests/draft-1", processResult.single().privateQuestPath)
        assertTrue("admin/review/lessons/lesson-1" in server.adminDocuments)
        assertTrue("private/owner-1/catalogs/catalog-1/quests/draft-1" in server.privateDocuments)

        val privateSync = QuestPrivateSync(local, server, InMemorySyncStateRepository()) { "owner-1" }
        assertTrue(privateSync.sync().isSuccess)

        val synced = local.syncedPrivateDrafts.single()
        assertEquals("SYNCED_PRIVATE", synced.draft.status)
        assertEquals("Question?", synced.questions.single().text)
        assertEquals(7, synced.questions.single().languageLevel)
    }

    @Test
    fun privateSyncCanRunBeforeArenaPushAndStillDownloadAfterServerProcessing() = runTest {
        val server = InMemoryReviewServer()
        server.profiles["owner-1"] = TrustedProfile(uid = "owner-1")
        val local = FakeQuestLocal(draft = entityBundle(), pending = mutableListOf(submissionEntity()))
        val privateSync = QuestPrivateSync(local, server, InMemorySyncStateRepository()) { "owner-1" }

        assertTrue(privateSync.sync().isSuccess)
        assertEquals(emptyList(), local.syncedPrivateDrafts)

        assertTrue(QuestArenaSubmissionSync(local, server, FakeClock(20L)).sync().isSuccess)
        server.handler.processPending().getOrThrow()
        assertTrue(privateSync.sync().isSuccess)

        assertEquals(1, local.syncedPrivateDrafts.size)
        assertEquals("draft-1", local.syncedPrivateDrafts.single().draft.id)
    }

    @Test
    fun assignmentSyncDownloadsOnlyTasksAllowedByTrustedQualifications() = runTest {
        val server = processedServer()

        assertDownloadedKinds(server, "tester", listOf("TESTING"))
        assertDownloadedKinds(server, "admin", listOf("TESTING"))
        assertDownloadedKinds(server, "translator", emptyList())

        server.replaceChecks(ReviewChecks(isTested = true, testingScore = 2.5))
        assertDownloadedKinds(server, "tester", emptyList())
        assertDownloadedKinds(server, "admin", listOf("LOGIC"))
        assertDownloadedKinds(server, "translator", emptyList())

        server.replaceChecks(
            ReviewChecks(
                isTested = true,
                testingScore = 2.5,
                isLogicReviewed = true,
                logicScore = 2.7,
                translatedLanguages = mapOf("ru" to 25),
            ),
        )
        val translatorAssignments = syncAssignments(server, "translator")
        assertEquals(listOf("TRANSLATION"), translatorAssignments.single().taskKinds)
        assertEquals(listOf("ru"), translatorAssignments.single().sourceLanguages)
        assertEquals(listOf("en"), translatorAssignments.single().newTranslationLanguages)
        assertDownloadedKinds(server, "translator-low", emptyList())
        assertDownloadedKinds(server, "translator-no-source", emptyList())
        assertDownloadedKinds(server, "developer", listOf("TRANSLATION"))

        server.replaceChecks(
            ReviewChecks(
                isTested = true,
                testingScore = 2.5,
                isLogicReviewed = true,
                logicScore = 2.7,
                translatedLanguages = mapOf("ru" to 25, "en" to 25),
            ),
        )

        assertDownloadedKinds(server, "translator-reviewer", listOf("TRANSLATION_REVIEW"))
    }

    @Test
    fun submitAllowsMultipleTestingReviewsAndDoesNotRepeatWeakReviewerDelta() = runTest {
        val server = processedServer()
        server.profiles["tester-weak"] = TrustedProfile(uid = "tester-weak", testerLevel = 100)
        server.profiles["tester-strong"] = TrustedProfile(uid = "tester-strong", testerLevel = 250)
        val processor = SubmitReviewActionProcessor(server, ReviewClock { server.nowMs++ })
        val assignmentId = "submission-1_lesson-1"

        processor.submit(
            "tester-weak",
            SubmitReviewAction(
                assignmentId = assignmentId,
                lessonId = "lesson-1",
                kind = ReviewTaskKind.TESTING,
                score = 1,
            ),
        ).getOrThrow()
        processor.submit(
            "tester-strong",
            SubmitReviewAction(
                assignmentId = assignmentId,
                lessonId = "lesson-1",
                kind = ReviewTaskKind.TESTING,
                score = 3,
            ),
        ).getOrThrow()

        assertEquals(3.0, requireNotNull(server.readAdminReviewLessonTask(assignmentId)).checks.testingScore)
        assertEquals(-3, server.reviewerReputation["tester-weak"])

        processor.submit(
            "admin",
            SubmitReviewAction(
                assignmentId = assignmentId,
                lessonId = "lesson-1",
                kind = ReviewTaskKind.LOGIC,
                score = 2,
            ),
        ).getOrThrow()

        assertEquals(2.0, requireNotNull(server.readAdminReviewLessonTask(assignmentId)).checks.logicScore)
        assertEquals(-3, server.reviewerReputation["tester-weak"])
    }

    @Test
    fun fullReviewChainSubmitsAllStagesAndSyncsNextAllowedAssignments() = runTest {
        val server = processedServer()
        server.profiles["translation-reviewer-strong"] =
            TrustedProfile(
                uid = "translation-reviewer-strong",
                translatorLevel = 320,
                knownLanguages = setOf("ru", "en"),
            )
        val submit =
            SubmitReviewActionUseCase(
                ReviewAssignmentRepositoryImpl(
                    local = FakeAssignmentLocal(),
                    remote = server,
                ),
            )
        val assignmentId = "submission-1_lesson-1"
        val lessonId = "lesson-1"

        server.currentViewerUid = "tester"
        submit(
            SubmitReviewActionCommand(
                assignmentId = assignmentId,
                lessonId = lessonId,
                kind = ReviewAssignmentKind.TESTING,
                score = 3,
            ),
        ).getOrThrow()

        assertDownloadedKinds(server, "tester", emptyList())
        assertDownloadedKinds(server, "admin", listOf("LOGIC"))

        server.currentViewerUid = "admin"
        submit(
            SubmitReviewActionCommand(
                assignmentId = assignmentId,
                lessonId = lessonId,
                kind = ReviewAssignmentKind.LOGIC,
                score = 2,
            ),
        ).getOrThrow()

        val translatorAssignments = syncAssignments(server, "translator")
        assertEquals(listOf("TRANSLATION"), translatorAssignments.single().taskKinds)
        assertEquals(listOf("en"), translatorAssignments.single().newTranslationLanguages)

        val sourceQuestion = requireNotNull(server.readAdminReviewLessonTask(assignmentId)).questions.single()
        server.currentViewerUid = "translator"
        submit(
            SubmitReviewActionCommand(
                assignmentId = assignmentId,
                lessonId = lessonId,
                kind = ReviewAssignmentKind.TRANSLATION,
                language = "en",
                translatedQuestions = listOf(sourceQuestion.toReviewQuestion(language = "en")),
            ),
        ).getOrThrow()

        val translatedTask = requireNotNull(server.readAdminReviewLessonTask(assignmentId))
        assertEquals(200, translatedTask.checks.translatedLanguages["en"])
        assertTrue(translatedTask.questions.any { it.id == "question-1__en" })
        assertDownloadedKinds(server, "translator", emptyList())
        assertDownloadedKinds(server, "translation-reviewer-strong", listOf("TRANSLATION_REVIEW"))

        server.currentViewerUid = "translation-reviewer-strong"
        submit(
            SubmitReviewActionCommand(
                assignmentId = assignmentId,
                lessonId = lessonId,
                kind = ReviewAssignmentKind.TRANSLATION_REVIEW,
                language = "en",
                segmentResults =
                    listOf(
                        ReviewSegmentDecision(questionId = "question-1", segmentKey = "text", accepted = true),
                        ReviewSegmentDecision(questionId = "question-1", segmentKey = "option:A", accepted = false),
                    ),
            ),
        ).getOrThrow()

        val reviewedTask = requireNotNull(server.readAdminReviewLessonTask(assignmentId))
        assertEquals(50, reviewedTask.checks.translationScore)
        assertEquals(4, server.reviewerReputation["translator"])
    }

    private suspend fun processedServer(): InMemoryReviewServer {
        val server = InMemoryReviewServer()
        server.profiles +=
            mapOf(
                "owner-1" to TrustedProfile(uid = "owner-1"),
                "tester" to TrustedProfile(uid = "tester", testerLevel = 100),
                "admin" to TrustedProfile(uid = "admin", adminLevel = 100),
                "translator" to
                    TrustedProfile(
                        uid = "translator",
                        translatorLevel = 200,
                        knownLanguages = setOf("ru", "en"),
                    ),
                "translator-reviewer" to
                    TrustedProfile(
                        uid = "translator-reviewer",
                        translatorLevel = 125,
                        knownLanguages = setOf("ru", "en"),
                    ),
                "translator-low" to
                    TrustedProfile(
                        uid = "translator-low",
                        translatorLevel = 90,
                        knownLanguages = setOf("ru", "en"),
                    ),
                "translator-no-source" to
                    TrustedProfile(
                        uid = "translator-no-source",
                        translatorLevel = 200,
                        knownLanguages = setOf("de", "en"),
                    ),
                "developer" to TrustedProfile(uid = "developer", developerLevel = 101),
            )
        val local = FakeQuestLocal(draft = entityBundle(), pending = mutableListOf(submissionEntity()))
        QuestArenaSubmissionSync(local, server, FakeClock(20L)).sync().getOrThrow()
        server.handler.processPending().getOrThrow()
        return server
    }

    private suspend fun assertDownloadedKinds(
        server: InMemoryReviewServer,
        uid: String,
        expectedKinds: List<String>,
    ) {
        val assignments = syncAssignments(server, uid)
        assertEquals(expectedKinds, assignments.flatMap { it.taskKinds })
    }

    private suspend fun syncAssignments(
        server: InMemoryReviewServer,
        uid: String,
    ): List<ReviewAssignmentEntity> {
        val local = FakeAssignmentLocal()
        server.currentViewerUid = uid
        ReviewAssignmentSync(local, server, InMemorySyncStateRepository()) { uid }.sync().getOrThrow()
        return local.findAssignments(uid)
    }

    private class InMemoryReviewServer :
        QuestArenaSubmissionRemoteDataSource,
        QuestReviewRequestStore,
        ReviewActionStore,
        QuestPrivateRemoteDataSource,
        ReviewAssignmentRemoteDataSource {
        val profiles = linkedMapOf<String, TrustedProfile>()
        val pendingRequests = linkedMapOf<String, QuestArenaSubmissionRequest>()
        val processedRequests = mutableSetOf<String>()
        val privateDocuments = linkedMapOf<String, Map<String, Any?>>()
        val adminDocuments = linkedMapOf<String, Map<String, Any?>>()
        val reviewerReputation = linkedMapOf<String, Int>()
        var reviewConfig: ArenaReviewConfig? = ArenaReviewConfig(requiredLanguages = setOf("ru", "en"), updatedAtMs = 1L)
        private val privateSnapshots = mutableListOf<PrivateQuestSnapshot>()
        private val privateChanges = mutableListOf<PrivateQuestSyncChange>()
        private val adminChanges = mutableListOf<ReviewAssignmentChange>()
        private val adminTasks = linkedMapOf<String, AdminReviewLessonTask>()
        private val reviewRecords = linkedMapOf<String, MutableList<ReviewRecord>>()
        var currentViewerUid: String = ""
        var nowMs: Long = 100L
        val handler = QuestReviewRequestHandler(this, QuestArenaSubmissionProcessor(this, ReviewClock { nowMs++ }))

        override suspend fun submit(request: QuestArenaSubmissionRequest) {
            pendingRequests[request.submissionId] = request
        }

        override suspend fun readPendingSubmissions(limit: Int): List<QuestArenaSubmissionRequest> =
            pendingRequests.values.filter { it.submissionId !in processedRequests }.take(limit)

        override suspend fun readTrustedProfile(uid: String): TrustedProfile? = profiles[uid]

        override suspend fun writePrivateHierarchy(request: QuestArenaSubmissionRequest) {
            privateDocuments += ReviewDocumentBuilder.privateDocuments(request)
            privateChanges +=
                PrivateQuestSyncChange(
                    catalogId = request.draft.catalogId,
                    questId = request.draft.id,
                    changedAtMs = nowMs,
                )
            privateSnapshots +=
                PrivateQuestSnapshot(
                    serverRevision = request.localRevision,
                    changedAtMs = nowMs,
                    request = request,
                )
        }

        override suspend fun writeAdminReviewLessonTasks(tasks: List<AdminReviewLessonTask>) {
            tasks.forEach { adminTasks[it.id] = it }
            adminDocuments += ReviewDocumentBuilder.adminDocuments(tasks)
            tasks.forEach { task ->
                adminChanges +=
                    ReviewAssignmentChange(
                        assignmentId = task.id,
                        lessonId = task.lessonId,
                        changedAtMs = task.changedAtMs,
                    )
            }
        }

        override suspend fun markSubmissionProcessed(
            submissionId: String,
            processedAtMs: Long,
        ) {
            processedRequests += submissionId
        }

        override suspend fun readAdminReviewLessonTasks(): List<AdminReviewLessonTask> =
            adminTasks.values.toList()

        override suspend fun readAdminReviewLessonTasksByIds(ids: Set<String>): List<AdminReviewLessonTask> =
            ids.mapNotNull { adminTasks[it] }

        override suspend fun readAdminReviewLessonTask(assignmentId: String): AdminReviewLessonTask? =
            adminTasks[assignmentId]

        override suspend fun readArenaReviewConfig(): ArenaReviewConfig? = reviewConfig

        override suspend fun readAssignmentChangesSince(cursorMs: Long): List<ReviewAssignmentChange> =
            adminChanges.filter { it.changedAtMs > cursorMs }

        override suspend fun readReviewRecords(lessonId: String): List<ReviewRecord> =
            reviewRecords[lessonId].orEmpty()

        override suspend fun writeReviewRecord(record: ReviewRecord) {
            reviewRecords.getOrPut(record.lessonId) { mutableListOf() } += record
        }

        override suspend fun addReviewerReputation(
            uid: String,
            points: Int,
        ) {
            reviewerReputation[uid] = reviewerReputation.getOrDefault(uid, 0) + points
        }

        override suspend fun fetchChangedSince(cursorMs: Long): List<PrivateQuestSyncChange> =
            privateChanges.filter { it.changedAtMs > cursorMs }

        override suspend fun fetchSnapshots(changes: List<PrivateQuestSyncChange>): List<PrivateQuestSnapshot> {
            val ids = changes.map { it.catalogId to it.questId }.toSet()
            return privateSnapshots.filter { snapshot ->
                (snapshot.request.draft.catalogId to snapshot.request.draft.id) in ids
            }
        }

        override suspend fun fetchAssignmentChangesSince(cursorMs: Long): List<ReviewAssignmentChangeDto> =
            ReviewAssignmentService(this).fetchChangesForTrustedUser(currentViewerUid, cursorMs).getOrThrow()

        override suspend fun fetchByIds(ids: Set<String>): List<ReviewAssignmentDto> =
            ReviewAssignmentService(this).fetchAvailableForTrustedUser(currentViewerUid, ids).getOrThrow()

        override suspend fun submitReviewAction(action: SubmitReviewActionDto) {
            SubmitReviewActionProcessor(this, ReviewClock { nowMs++ })
                .submit(currentViewerUid, action.toServerAction())
                .getOrThrow()
        }

        private fun SubmitReviewActionDto.toServerAction(): SubmitReviewAction =
            SubmitReviewAction(
                assignmentId = assignmentId,
                lessonId = lessonId,
                kind = ReviewTaskKind.valueOf(kind),
                score = score,
                language = language,
                targetReviewId = targetReviewId,
                translatedQuestions = translatedQuestions,
                segmentResults =
                    segmentResults.map {
                        ReviewSegmentResult(
                            questionId = it.questionId,
                            segmentKey = it.segmentKey,
                            accepted = it.accepted,
                        )
                    },
            )

        fun replaceChecks(checks: ReviewChecks) {
            adminTasks.replaceAll { _, task ->
                task.copy(checks = checks, changedAtMs = nowMs++).also { changed ->
                    adminChanges +=
                        ReviewAssignmentChange(
                            assignmentId = changed.id,
                            lessonId = changed.lessonId,
                            changedAtMs = changed.changedAtMs,
                        )
                }
            }
        }
    }

    private class FakeQuestLocal(
        private var draft: QuestAuthoringEntityBundle?,
        private val pending: MutableList<QuestArenaSubmissionEntity>,
    ) : QuestAuthoringLocalDataSource {
        val syncedPrivateDrafts = mutableListOf<QuestAuthoringEntityBundle>()

        override fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummaryEntity>> =
            flowOf(emptyList())

        override fun observeDraft(draftId: String): Flow<QuestAuthoringEntityBundle?> =
            flowOf(draft)

        override suspend fun getDraft(draftId: String): QuestAuthoringEntityBundle? =
            draft?.takeIf { it.draft.id == draftId }

        override suspend fun getActiveDraft(ownerUid: String): QuestAuthoringEntityBundle? = draft

        override suspend fun saveDraft(bundle: QuestAuthoringEntityBundle) {
            draft = bundle
        }

        override suspend fun upsertQuestion(question: DraftQuestionEntity) {
            val bundle = requireNotNull(draft) {
                "Draft ${question.draftId} not found"
            }
            require(bundle.lessons.any { it.id == question.lessonId }) {
                "Lesson ${question.lessonId} not found in draft ${question.draftId}"
            }
            val questions =
                (bundle.questions.filterNot { it.id == question.id } + question)
                    .sortedWith(compareBy<DraftQuestionEntity> { it.lessonId }.thenBy { it.order }.thenBy { it.id })
            draft =
                bundle.copy(
                    draft =
                        bundle.draft.copy(
                            localRevision = bundle.draft.localRevision + 1,
                            updatedAtMs = maxOf(bundle.draft.updatedAtMs, question.updatedAtMs),
                            status = "DRAFT",
                        ),
                    questions = questions,
                )
        }

        override suspend fun queueArenaSubmission(submission: QuestArenaSubmissionEntity) {
            pending += submission
            updateDraftStatus(
                draftId = submission.draftId,
                status = "REVIEW_QUEUED",
                updatedAtMs = submission.requestedAtMs,
            )
        }

        override suspend fun findPendingArenaSubmissions(limit: Int): List<QuestArenaSubmissionEntity> =
            pending.take(limit)

        override suspend fun markArenaSubmissionFailure(
            submissionId: String,
            message: String?,
        ) = Unit

        override suspend fun markArenaSubmissionSent(
            submissionId: String,
            draftId: String,
            updatedAtMs: Long,
        ) {
            pending.removeAll { it.id == submissionId }
            updateDraftStatus(
                draftId = draftId,
                status = "REVIEW_SENT",
                updatedAtMs = updatedAtMs,
            )
        }

        override suspend fun upsertSyncedPrivateQuest(snapshot: PrivateQuestSnapshot) {
            val bundle = snapshot.toEntityBundle()
            syncedPrivateDrafts += bundle
            draft = bundle
        }

        override suspend fun setDraftStatus(
            draftId: String,
            status: String,
            updatedAtMs: Long,
        ) {
            updateDraftStatus(draftId, status, updatedAtMs)
        }

        private fun updateDraftStatus(
            draftId: String,
            status: String,
            updatedAtMs: Long,
        ) {
            val bundle = draft ?: return
            if (bundle.draft.id != draftId) return
            draft =
                bundle.copy(
                    draft =
                        bundle.draft.copy(
                            status = status,
                            updatedAtMs = maxOf(bundle.draft.updatedAtMs, updatedAtMs),
                        ),
                )
        }
    }

    private class FakeAssignmentLocal : ReviewAssignmentLocalDataSource {
        private val assignmentsByOwner = linkedMapOf<String, List<ReviewAssignmentEntity>>()

        override fun observeAssignments(ownerUid: String): Flow<List<ReviewAssignmentEntity>> =
            flowOf(assignmentsByOwner[ownerUid].orEmpty())

        override suspend fun findAssignments(ownerUid: String): List<ReviewAssignmentEntity> =
            assignmentsByOwner[ownerUid].orEmpty()

        override suspend fun replaceAssignments(
            ownerUid: String,
            assignments: List<ReviewAssignmentEntity>,
        ) {
            assignmentsByOwner[ownerUid] = assignments
        }

        override suspend fun applyAssignmentChanges(
            ownerUid: String,
            changedIds: Set<String>,
            assignments: List<ReviewAssignmentEntity>,
        ) {
            val current = assignmentsByOwner[ownerUid].orEmpty()
            assignmentsByOwner[ownerUid] =
                (current.filterNot { it.id in changedIds } + assignments)
                    .sortedBy { it.createdAtMs }
        }
    }

    private class FakeClock(
        private val now: Long,
    ) : QuestAuthoringTimestampProvider {
        override fun nowMs(): Long = now
    }

    private class MutableClock(
        var value: Long,
    ) : QuestAuthoringTimestampProvider {
        override fun nowMs(): Long = value
    }

    private class CountingIdProvider : QuestAuthoringIdProvider {
        private var counter = 0

        override fun nextId(prefix: String): String = "$prefix-${counter++}"
    }

    private suspend fun saveCodeDrivenQuestion(
        save: SaveDraftQuestionUseCase,
        draftId: QuestDraftId,
        lessonId: DraftLessonId,
        id: String,
        difficulty: Difficulty,
        order: Int,
    ) {
        val result =
            save(
                SaveDraftQuestionCommand(
                    draftId = draftId,
                    lessonId = lessonId,
                    questionId = null,
                    type = DraftQuestionType.SINGLE_CHOICE,
                    language = "ru",
                    difficulty = difficulty,
                    order = order,
                    imagePath = null,
                    payload = singleChoicePayload(id, difficulty),
                    languageLevel = 125,
                ),
            )
        assertTrue(result.isSuccess)
    }

    private fun singleChoicePayload(
        id: String,
        difficulty: Difficulty,
    ): String =
        """
        {
          "type":"SingleChoice",
          "id":"$id",
          "difficulty":"${difficulty.name}",
          "text":"Question $id?",
          "imageUrl":null,
          "options":[{"id":"A","text":"A"},{"id":"B","text":"B"}],
          "correctOptionId":"A"
        }
        """.trimIndent()

    private fun submissionEntity(): QuestArenaSubmissionEntity =
        QuestArenaSubmissionEntity(
            id = "submission-1",
            draftId = "draft-1",
            ownerUid = "owner-1",
            localRevision = 2L,
            requestedAtMs = 10L,
            attemptCount = 0,
            lastError = null,
        )

    private fun entityBundle(): QuestAuthoringEntityBundle =
        QuestAuthoringEntityBundle(
            draft =
                QuestDraftEntity(
                    id = "draft-1",
                    ownerUid = "owner-1",
                    catalogId = "catalog-1",
                    title = "Draft",
                    description = null,
                    defaultLanguage = "ru",
                    defaultDifficulty = "EASY",
                    status = "REVIEW_QUEUED",
                    localRevision = 2L,
                    serverRevision = null,
                    publicQuestId = null,
                    createdAtMs = 1L,
                    updatedAtMs = 2L,
                    isActive = true,
                ),
            sections = listOf(DraftSectionEntity("section-1", "draft-1", "Section", 0)),
            themes = listOf(DraftThemeEntity("theme-1", "draft-1", "section-1", "Theme", 0)),
            lessons = listOf(DraftLessonEntity("lesson-1", "draft-1", "theme-1", "Lesson", 0)),
            questions =
                listOf(
                    DraftQuestionEntity(
                        id = "question-1",
                        draftId = "draft-1",
                        lessonId = "lesson-1",
                        type = "SINGLE_CHOICE",
                        language = "ru",
                        difficulty = "EASY",
                        order = 0,
                        text = "Question?",
                        imagePath = null,
                        payload = """{"type":"single_choice"}""",
                        validationState = "SAVED",
                        updatedAtMs = 3L,
                        languageLevel = 7,
                    ),
                ),
        )

    private fun ArenaQuestionDto.toReviewQuestion(language: String = this.language): ReviewQuestion =
        ReviewQuestion(
            id = id,
            draftId = draftId,
            lessonId = lessonId,
            type = type,
            language = language,
            languageLevel = languageLevel,
            difficulty = difficulty,
            order = order,
            text = if (language == this.language) text else "Translated question?",
            imagePath = imagePath,
            payload = payload,
            updatedAtMs = updatedAtMs,
        )
}
