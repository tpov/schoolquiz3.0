package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.sync.fake.FakeCatalogRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeLessonRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeQuestRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeQuestionRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeSectionRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeSyncStateRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeThemeRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogSyncListOrchestratorTest {

    private lateinit var catalogRepo: FakeCatalogRepository
    private lateinit var questRepo: FakeQuestRepository
    private lateinit var sectionRepo: FakeSectionRepository
    private lateinit var themeRepo: FakeThemeRepository
    private lateinit var lessonRepo: FakeLessonRepository
    private lateinit var questionRepo: FakeQuestionRepository
    private lateinit var syncState: FakeSyncStateRepository
    private lateinit var syncChanges: FakeCatalogSyncChangeRemoteDataSource
    private lateinit var orchestrator: CatalogSyncListOrchestrator

    @BeforeTest
    fun setUp() {
        catalogRepo = FakeCatalogRepository()
        questRepo = FakeQuestRepository()
        sectionRepo = FakeSectionRepository()
        themeRepo = FakeThemeRepository()
        lessonRepo = FakeLessonRepository()
        questionRepo = FakeQuestionRepository()
        syncState = FakeSyncStateRepository()
        syncChanges = FakeCatalogSyncChangeRemoteDataSource()
        orchestrator = CatalogSyncListOrchestrator(
            catalogRepo = catalogRepo,
            questRepo = questRepo,
            sectionRepo = sectionRepo,
            themeRepo = themeRepo,
            lessonRepo = lessonRepo,
            questionRepo = questionRepo,
            syncStateRepo = syncState,
            syncChangeRemote = syncChanges,
        )
    }

    @Test
    fun `sync fetches structure nodes from catalog change list and advances catalog cursor`() = runTest {
        val catalogId = CatalogId("cat-1")
        catalogRepo.nextChangedIds = setOf(catalogId)
        syncState.setCursor(catalogSyncCursorId(catalogId), 50L)
        syncChanges.changesByCatalog = mapOf(
            catalogId to listOf(
                change(catalogId, CatalogSyncNodeType.Quest, "quest-1", 100L),
                change(catalogId, CatalogSyncNodeType.Quest, "quest-1", 110L),
                change(catalogId, CatalogSyncNodeType.Section, "section-1", 120L),
                change(catalogId, CatalogSyncNodeType.Theme, "theme-1", 130L),
                change(catalogId, CatalogSyncNodeType.Lesson, "lesson-1", 140L),
                change(catalogId, CatalogSyncNodeType.Question, "question-1", 150L),
                change(catalogId, CatalogSyncNodeType.Catalog, "cat-1", 160L),
                change(catalogId, CatalogSyncNodeType.Question, "", 170L),
            ),
        )

        val result = orchestrator.sync()

        assertTrue(result.isSuccess)
        assertEquals(listOf(catalogId to 50L, CatalogId("courses") to 0L), syncChanges.requests)
        assertTrue(setOf(catalogId) in catalogRepo.refreshByIdsRequests)
        assertEquals(setOf(QuestId("quest-1")), questRepo.lastRefreshByIds)
        assertEquals(setOf(SectionId("section-1")), sectionRepo.lastRefreshByIds)
        assertEquals(setOf(ThemeId("theme-1")), themeRepo.lastRefreshByIds)
        assertEquals(setOf(LessonId("lesson-1")), lessonRepo.lastRefreshByIds)
        assertEquals(1, questionRepo.refreshByIdsCallCount)
        assertEquals(setOf(QuestionId("question-1")), questionRepo.lastRefreshByIds)
        // 170, а не 160: запись с пустым id прочитана и применить её нельзя никогда, поэтому
        // курсор проходит и её. Оставаться перед ней значит перечитывать её каждый проход.
        assertEquals(170L, syncState.getCursor(catalogSyncCursorId(catalogId)))
        assertEquals(1_000L, syncState.getCursor(CATALOG_LIST_CURSOR_ID))
    }

    @Test
    fun `sync does not advance catalog change cursor when concrete node refresh fails`() = runTest {
        val catalogId = CatalogId("cat-1")
        val cursorId = catalogSyncCursorId(catalogId)
        catalogRepo.nextChangedIds = setOf(catalogId)
        syncState.setCursor(cursorId, 50L)
        syncChanges.changesByCatalog = mapOf(
            catalogId to listOf(
                change(catalogId, CatalogSyncNodeType.Lesson, "lesson-1", 150L),
            ),
        )
        lessonRepo.setNextRefreshByIdsFailure(IllegalStateException("lesson refresh failed"))

        val result = orchestrator.sync()

        assertTrue(result.isFailure)
        assertEquals(50L, syncState.getCursor(cursorId))
    }

    @Test
    fun `sync scans every catalog returned by catalog refresh`() = runTest {
        val firstCatalog = CatalogId("cat-1")
        val secondCatalog = CatalogId("cat-2")
        catalogRepo.nextChangedIds = setOf(firstCatalog, secondCatalog)
        syncChanges.changesByCatalog = mapOf(
            firstCatalog to listOf(change(firstCatalog, CatalogSyncNodeType.Question, "question-1", 100L)),
            secondCatalog to listOf(change(secondCatalog, CatalogSyncNodeType.Question, "question-2", 200L)),
        )

        val result = orchestrator.sync()

        assertTrue(result.isSuccess)
        assertEquals(
            listOf(firstCatalog to 0L, secondCatalog to 0L, CatalogId("courses") to 0L),
            syncChanges.requests,
        )
        assertEquals(2, questionRepo.refreshByIdsCallCount)
        assertEquals(100L, syncState.getCursor(catalogSyncCursorId(firstCatalog)))
        assertEquals(200L, syncState.getCursor(catalogSyncCursorId(secondCatalog)))
    }

    @Test
    fun `sync skips questions for courses catalog`() = runTest {
        val catalogId = CatalogId("courses")
        catalogRepo.nextChangedIds = setOf(catalogId)
        syncChanges.changesByCatalog = mapOf(
            catalogId to listOf(change(catalogId, CatalogSyncNodeType.Question, "question-1", 100L)),
        )

        val result = orchestrator.sync()

        assertTrue(result.isSuccess)
        assertEquals(0, questionRepo.refreshByIdsCallCount)
        assertEquals(100L, syncState.getCursor(catalogSyncCursorId(catalogId)))
    }

    @Test
    fun `sync scans courses even when local catalog cache is empty`() = runTest {
        val courses = CatalogId("courses")
        catalogRepo.nextChangedIds = emptySet()
        syncChanges.changesByCatalog = mapOf(
            courses to listOf(
                change(courses, CatalogSyncNodeType.Catalog, "courses", 90L),
                change(courses, CatalogSyncNodeType.Quest, "quest-course", 100L),
            ),
        )

        val result = orchestrator.sync()

        assertTrue(result.isSuccess)
        assertEquals(listOf(courses to 0L), syncChanges.requests)
        assertEquals(setOf(QuestId("quest-course")), questRepo.lastRefreshByIds)
        assertEquals(100L, syncState.getCursor(catalogSyncCursorId(courses)))
    }

    @Test
    fun `sync uses full courses cursor when local archive quests are empty`() = runTest {
        val courses = CatalogId("courses")
        catalogRepo.nextChangedIds = setOf(courses)
        syncState.setCursor(catalogSyncCursorId(courses), 500L)
        syncChanges.changesByCatalog = mapOf(
            courses to listOf(change(courses, CatalogSyncNodeType.Quest, "quest-course", 100L)),
        )

        val result = orchestrator.sync()

        assertTrue(result.isSuccess)
        assertEquals(listOf(courses to 0L), syncChanges.requests)
        assertEquals(setOf(QuestId("quest-course")), questRepo.lastRefreshByIds)
    }

    @Test
    fun `sync catalog structure applies hierarchy changes but skips question refresh`() = runTest {
        val catalogId = CatalogId("cat-1")
        syncChanges.changesByCatalog = mapOf(
            catalogId to listOf(
                change(catalogId, CatalogSyncNodeType.Lesson, "lesson-1", 100L),
                change(catalogId, CatalogSyncNodeType.Question, "question-1", 200L),
            ),
        )

        val result = orchestrator.syncCatalogStructure(catalogId)

        assertTrue(result.isSuccess)
        assertEquals(setOf(LessonId("lesson-1")), lessonRepo.lastRefreshByIds)
        assertEquals(0, questionRepo.refreshByIdsCallCount)
        assertEquals(200L, syncState.getCursor(catalogSyncCursorId(catalogId)))
    }

    private fun change(
        catalogId: CatalogId,
        type: CatalogSyncNodeType,
        nodeId: String,
        changedAtMs: Long,
    ): CatalogSyncChange =
        CatalogSyncChange(
            catalogId = catalogId,
            type = type,
            nodeId = nodeId,
            changedAtMs = changedAtMs,
        )
}

private class FakeCatalogSyncChangeRemoteDataSource : CatalogSyncChangeRemoteDataSource {
    var changesByCatalog: Map<CatalogId, List<CatalogSyncChange>> = emptyMap()
    val requests = mutableListOf<Pair<CatalogId, Long>>()

    override suspend fun fetchChangedSince(
        catalogId: CatalogId,
        cursorMs: Long,
    ): List<CatalogSyncChange> {
        requests.add(catalogId to cursorMs)
        return changesByCatalog[catalogId].orEmpty()
    }
}
