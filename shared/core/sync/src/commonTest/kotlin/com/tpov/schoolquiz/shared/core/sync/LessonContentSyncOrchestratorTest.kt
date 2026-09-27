package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.sync.fake.FakeCatalogRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeLessonRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeQuestRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeQuestionRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeSectionRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeSyncStateRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeThemeRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.section.domain.model.Section
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class LessonContentSyncOrchestratorTest {

    private lateinit var catalogRepo: FakeCatalogRepository
    private lateinit var questRepo: FakeQuestRepository
    private lateinit var sectionRepo: FakeSectionRepository
    private lateinit var themeRepo: FakeThemeRepository
    private lateinit var lessonRepo: FakeLessonRepository
    private lateinit var questionRepo: FakeQuestionRepository
    private lateinit var syncState: FakeSyncStateRepository
    private lateinit var catalogChanges: FakeCatalogJournal
    private lateinit var lessonChanges: FakeLessonContentSyncChangeRemoteDataSource
    private lateinit var status: RecordingSyncStatus
    private lateinit var syncGate: SyncGate
    private lateinit var orchestrator: LessonContentSyncOrchestrator

    @BeforeTest
    fun setUp() {
        catalogRepo = FakeCatalogRepository()
        questRepo = FakeQuestRepository()
        sectionRepo = FakeSectionRepository()
        themeRepo = FakeThemeRepository()
        lessonRepo = FakeLessonRepository()
        questionRepo = FakeQuestionRepository()
        syncState = FakeSyncStateRepository()
        catalogChanges = FakeCatalogJournal()
        lessonChanges = FakeLessonContentSyncChangeRemoteDataSource()
        status = RecordingSyncStatus()
        syncGate = SyncGate()
        val catalogSync = CatalogSyncListOrchestrator(
            catalogRepo = catalogRepo,
            questRepo = questRepo,
            sectionRepo = sectionRepo,
            themeRepo = themeRepo,
            lessonRepo = lessonRepo,
            questionRepo = questionRepo,
            syncStateRepo = syncState,
            syncChangeRemote = catalogChanges,
            gate = syncGate,
        )
        orchestrator = LessonContentSyncOrchestrator(
            catalogSync = catalogSync,
            lessonRepo = lessonRepo,
            themeRepo = themeRepo,
            sectionRepo = sectionRepo,
            questRepo = questRepo,
            questionRepo = questionRepo,
            syncStateRepo = syncState,
            syncChangeRemote = lessonChanges,
            status = status,
            gate = syncGate,
        )
    }

    @Test
    fun `given the lesson journal held records nobody could read then the run says how many`() = runTest {
        // Число выбрасывалось `.map { }`: изменение вопроса не применялось, и об этом нигде не
        // говорилось — ровно то «выглядит как изменений нет», которое 7.6 требует убрать (AD-11).
        val lessonId = LessonId("lesson-1")
        lessonChanges.changesByLesson = mapOf(
            lessonId to listOf(
                LessonContentSyncChange(lessonId, CatalogSyncNodeType.Question, "", 10L),
                LessonContentSyncChange(lessonId, CatalogSyncNodeType.Lesson, "not-a-question", 11L),
            ),
        )

        orchestrator.syncLessonContent(lessonId)

        assertEquals(2, status.byJournal[SyncJournals.LESSON_CONTENT])
    }

    @Test
    fun `given a clean lesson journal then zero is said out loud`() = runTest {
        orchestrator.syncLessonContent(LessonId("lesson-1"))

        assertEquals(0, status.byJournal[SyncJournals.LESSON_CONTENT], "иначе прошлое число живёт вечно")
    }

    @Test
    fun `on-demand lesson sync holds the shared gate through content apply and cursor advance`() = runTest {
        val catalogId = CatalogId("cat-1")
        val questId = QuestId("quest-1")
        val sectionId = SectionId("section-1")
        val themeId = ThemeId("theme-1")
        val lessonId = LessonId("lesson-1")
        seedPath(catalogId, questId, sectionId, themeId, lessonId)
        lessonChanges.changesByLesson = mapOf(
            lessonId to listOf(lessonChange(lessonId, "question-1", 200L)),
        )
        val applyStarted = CompletableDeferred<Unit>()
        val releaseApply = CompletableDeferred<Unit>()
        questionRepo.beforeRefreshByIds = {
            applyStarted.complete(Unit)
            releaseApply.await()
        }

        val contentSync = async { orchestrator.syncLessonContent(lessonId) }
        applyStarted.await()
        var competingPassEntered = false
        var cursorSeenByCompetingPass: SyncCursor? = null
        val competingPass =
            async {
                syncGate.withPass {
                    competingPassEntered = true
                    cursorSeenByCompetingPass = syncState.getCursor(lessonContentSyncCursorId(lessonId))
                }
            }
        runCurrent()

        assertFalse(competingPassEntered, "another sync pass must wait while lesson content is being applied")
        releaseApply.complete(Unit)
        assertTrue(contentSync.await().isSuccess)
        competingPass.await()
        assertTrue(competingPassEntered)
        assertEquals(SyncCursor(200L, "question-1"), cursorSeenByCompetingPass)
    }

    @Test
    fun `sync lesson content refreshes containing structure then question ids`() = runTest {
        val catalogId = CatalogId("cat-1")
        val questId = QuestId("quest-1")
        val sectionId = SectionId("section-1")
        val themeId = ThemeId("theme-1")
        val lessonId = LessonId("lesson-1")
        seedPath(catalogId, questId, sectionId, themeId, lessonId)
        catalogChanges.docs = mapOf(
            catalogId to listOf(
                catalogChange(catalogId, CatalogSyncNodeType.Lesson, lessonId.value, 100L),
                catalogChange(catalogId, CatalogSyncNodeType.Question, "ignored-question", 110L),
            ),
        )
        lessonChanges.changesByLesson = mapOf(
            lessonId to listOf(
                lessonChange(lessonId, "question-1", 200L),
                lessonChange(lessonId, "question-2", 210L),
            ),
        )

        val result = orchestrator.syncLessonContent(lessonId)

        assertTrue(result.isSuccess)
        assertEquals(listOf(catalogId to SyncCursor.BEGINNING), catalogChanges.requests)
        assertEquals(setOf(QuestionId("question-1"), QuestionId("question-2")), questionRepo.lastRefreshByIds)
        assertEquals(SyncCursor(110L, "question_ignored-question"), syncState.getCursor(catalogSyncCursorId(catalogId)))
        assertEquals(SyncCursor(210L, "question-2"), syncState.getCursor(lessonContentSyncCursorId(lessonId)))
    }

    @Test
    fun `sync lesson content does not advance cursor when question refresh fails`() = runTest {
        val lessonId = LessonId("lesson-1")
        lessonChanges.changesByLesson = mapOf(
            lessonId to listOf(lessonChange(lessonId, "question-1", 200L)),
        )
        syncState.setCursor(lessonContentSyncCursorId(lessonId), SyncCursor(50L, "question-0"))
        questionRepo.setNextRefreshByIdsFailure(IllegalStateException("boom"))

        val result = orchestrator.syncLessonContent(lessonId)

        assertTrue(result.isFailure)
        assertEquals(SyncCursor(50L, "question-0"), syncState.getCursor(lessonContentSyncCursorId(lessonId)))
    }

    @Test
    fun `sync lesson content reads two questions written in the same millisecond, second run reads neither`() = runTest {
        val lessonId = LessonId("lesson-1")
        lessonChanges.changesByLesson = mapOf(
            lessonId to listOf(
                lessonChange(lessonId, "question-a", 200L),
                lessonChange(lessonId, "question-b", 200L),
            ),
        )

        orchestrator.syncLessonContent(lessonId)
        assertEquals(setOf(QuestionId("question-a"), QuestionId("question-b")), questionRepo.lastRefreshByIds)
        assertEquals(SyncCursor(200L, "question-b"), syncState.getCursor(lessonContentSyncCursorId(lessonId)))
        val callsAfterFirstRun = questionRepo.refreshByIdsCallCount

        orchestrator.syncLessonContent(lessonId)

        assertEquals(callsAfterFirstRun, questionRepo.refreshByIdsCallCount)
        assertEquals(listOf(SyncCursor.BEGINNING, SyncCursor(200L, "question-b")), lessonChanges.requests)
    }

    @Test
    fun `sync lesson content continues from the first of two same-millisecond questions`() = runTest {
        val lessonId = LessonId("lesson-1")
        lessonChanges.changesByLesson = mapOf(
            lessonId to listOf(
                lessonChange(lessonId, "question-a", 200L),
                lessonChange(lessonId, "question-b", 200L),
            ),
        )
        syncState.setCursor(lessonContentSyncCursorId(lessonId), SyncCursor(200L, "question-a"))

        orchestrator.syncLessonContent(lessonId)

        assertEquals(setOf(QuestionId("question-b")), questionRepo.lastRefreshByIds)
    }

    @Test
    fun `sync lesson content reads the journal page by page`() = runTest {
        val lessonId = LessonId("lesson-1")
        lessonChanges.pageSize = 2
        lessonChanges.changesByLesson = mapOf(
            lessonId to listOf(
                lessonChange(lessonId, "question-1", 200L),
                lessonChange(lessonId, "question-2", 210L),
                lessonChange(lessonId, "question-3", 220L),
            ),
        )

        orchestrator.syncLessonContent(lessonId)

        assertEquals(listOf(SyncCursor.BEGINNING, SyncCursor(210L, "question-2")), lessonChanges.requests)
        assertEquals(2, questionRepo.refreshByIdsCallCount)
        assertEquals(SyncCursor(220L, "question-3"), syncState.getCursor(lessonContentSyncCursorId(lessonId)))
    }

    private fun seedPath(
        catalogId: CatalogId,
        questId: QuestId,
        sectionId: SectionId,
        themeId: ThemeId,
        lessonId: LessonId,
    ) {
        questRepo.seed(
            listOf(
                Quest(
                    id = questId,
                    catalogId = catalogId,
                    authorUid = "author",
                    title = "Quest",
                    picturePath = null,
                    visibleOn = setOf("home"),
                    version = 1L,
                    lastModifiedAt = 1L,
                    archived = true,
                ),
            ),
        )
        sectionRepo.seed(
            listOf(
                Section(sectionId, questId, "Section", 0, 1L, 1L),
            ),
        )
        themeRepo.seed(
            listOf(
                Theme(themeId, sectionId, "Theme", 0, 1L, 1L),
            ),
        )
        lessonRepo.seed(
            listOf(
                Lesson(lessonId, themeId, "Lesson", 0, 1L, 1L),
            ),
        )
    }

    private fun catalogChange(
        catalogId: CatalogId,
        type: CatalogSyncNodeType,
        nodeId: String,
        changedAtMs: Long,
    ): JournalDoc =
        JournalDoc(
            cursor = SyncCursor(changedAtMs, "${type.name.lowercase()}_$nodeId"),
            change = CatalogSyncChange(catalogId, type, nodeId, changedAtMs),
        )

    private fun lessonChange(
        lessonId: LessonId,
        nodeId: String,
        changedAtMs: Long,
    ): LessonContentSyncChange =
        LessonContentSyncChange(lessonId, CatalogSyncNodeType.Question, nodeId, changedAtMs)
}

/** Журнал урока в памяти: id документа — id вопроса, как пишет сервер; страница после пары. */
private class FakeLessonContentSyncChangeRemoteDataSource : LessonContentSyncChangeRemoteDataSource {
    var changesByLesson: Map<LessonId, List<LessonContentSyncChange>> = emptyMap()
    var pageSize: Int = SYNC_PAGE_SIZE
    val requests = mutableListOf<SyncCursor>()

    override suspend fun fetchPage(
        lessonId: LessonId,
        cursor: SyncCursor,
        limit: Int,
    ): SyncChangePage<LessonContentSyncChange> {
        requests += cursor
        val size = minOf(limit, pageSize)
        val slice = changesByLesson[lessonId].orEmpty()
            .sortedBy { SyncCursor(it.changedAtMs, it.nodeId) }
            .filter { SyncCursor(it.changedAtMs, it.nodeId) > cursor }
            .take(size)
        return journalPage(slice, size, { SyncCursor(it.changedAtMs, it.nodeId) }, { it })
    }
}
