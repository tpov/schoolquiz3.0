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
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LessonContentSyncOrchestratorTest {

    private lateinit var catalogRepo: FakeCatalogRepository
    private lateinit var questRepo: FakeQuestRepository
    private lateinit var sectionRepo: FakeSectionRepository
    private lateinit var themeRepo: FakeThemeRepository
    private lateinit var lessonRepo: FakeLessonRepository
    private lateinit var questionRepo: FakeQuestionRepository
    private lateinit var syncState: FakeSyncStateRepository
    private lateinit var catalogChanges: FakeCatalogSyncChangeRemoteDataSourceForLessonContent
    private lateinit var lessonChanges: FakeLessonContentSyncChangeRemoteDataSource
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
        catalogChanges = FakeCatalogSyncChangeRemoteDataSourceForLessonContent()
        lessonChanges = FakeLessonContentSyncChangeRemoteDataSource()
        val catalogSync = CatalogSyncListOrchestrator(
            catalogRepo = catalogRepo,
            questRepo = questRepo,
            sectionRepo = sectionRepo,
            themeRepo = themeRepo,
            lessonRepo = lessonRepo,
            questionRepo = questionRepo,
            syncStateRepo = syncState,
            syncChangeRemote = catalogChanges,
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
        )
    }

    @Test
    fun `sync lesson content refreshes containing structure then question ids`() = runTest {
        val catalogId = CatalogId("cat-1")
        val questId = QuestId("quest-1")
        val sectionId = SectionId("section-1")
        val themeId = ThemeId("theme-1")
        val lessonId = LessonId("lesson-1")
        seedPath(catalogId, questId, sectionId, themeId, lessonId)
        catalogChanges.changesByCatalog = mapOf(
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
        assertEquals(listOf(catalogId to 0L), catalogChanges.requests)
        assertEquals(setOf(QuestionId("question-1"), QuestionId("question-2")), questionRepo.lastRefreshByIds)
        assertEquals(110L, syncState.getCursor(catalogSyncCursorId(catalogId)))
        assertEquals(210L, syncState.getCursor(lessonContentSyncCursorId(lessonId)))
    }

    @Test
    fun `sync lesson content does not advance cursor when question refresh fails`() = runTest {
        val lessonId = LessonId("lesson-1")
        lessonChanges.changesByLesson = mapOf(
            lessonId to listOf(lessonChange(lessonId, "question-1", 200L)),
        )
        syncState.setCursor(lessonContentSyncCursorId(lessonId), 50L)
        questionRepo.setNextRefreshByIdsFailure(IllegalStateException("boom"))

        val result = orchestrator.syncLessonContent(lessonId)

        assertTrue(result.isFailure)
        assertEquals(50L, syncState.getCursor(lessonContentSyncCursorId(lessonId)))
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
                    contentsVersion = 1L,
                    lastModifiedAt = 1L,
                    archived = true,
                ),
            ),
        )
        sectionRepo.seed(
            listOf(
                Section(sectionId, questId, "Section", 0, 1L, 1L, 1L),
            ),
        )
        themeRepo.seed(
            listOf(
                Theme(themeId, sectionId, "Theme", 0, 1L, 1L, 1L),
            ),
        )
        lessonRepo.seed(
            listOf(
                Lesson(lessonId, themeId, "Lesson", 0, 1L, 1L, 1L),
            ),
        )
    }

    private fun catalogChange(
        catalogId: CatalogId,
        type: CatalogSyncNodeType,
        nodeId: String,
        changedAtMs: Long,
    ): CatalogSyncChange =
        CatalogSyncChange(catalogId, type, nodeId, changedAtMs)

    private fun lessonChange(
        lessonId: LessonId,
        nodeId: String,
        changedAtMs: Long,
    ): LessonContentSyncChange =
        LessonContentSyncChange(lessonId, CatalogSyncNodeType.Question, nodeId, changedAtMs)
}

private class FakeLessonContentSyncChangeRemoteDataSource : LessonContentSyncChangeRemoteDataSource {
    var changesByLesson: Map<LessonId, List<LessonContentSyncChange>> = emptyMap()

    override suspend fun fetchChangedSince(
        lessonId: LessonId,
        cursorMs: Long,
    ): List<LessonContentSyncChange> =
        changesByLesson[lessonId].orEmpty().filter { it.changedAtMs > cursorMs }
}

private class FakeCatalogSyncChangeRemoteDataSourceForLessonContent : CatalogSyncChangeRemoteDataSource {
    var changesByCatalog: Map<CatalogId, List<CatalogSyncChange>> = emptyMap()
    val requests = mutableListOf<Pair<CatalogId, Long>>()

    override suspend fun fetchChangedSince(
        catalogId: CatalogId,
        cursorMs: Long,
    ): List<CatalogSyncChange> {
        requests.add(catalogId to cursorMs)
        return changesByCatalog[catalogId].orEmpty().filter { it.changedAtMs > cursorMs }
    }
}
