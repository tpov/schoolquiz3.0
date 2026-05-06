package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuestListMode
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestionRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeSectionRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeStackNavigation
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeThemeRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.SetPublicQuestShelfUseCase
import com.tpov.schoolquiz.shared.feature.question.domain.model.Question
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.section.domain.model.Section
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * JVM unit tests for [DefaultQuestListComponent].
 *
 * Spec: docs/features/quizzes-screen/04-testing.md §3
 * Phase: 04 (TDD — written parallel to production implementation)
 *
 * Coverage: QL-U-01..07, QL-U-10, QL-U-stub-01
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultQuestListComponentTest {

    private val testScheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(testScheduler)
    private lateinit var lifecycle: LifecycleRegistry
    private val fakeRepo = FakeQuestRepository()
    private val fakeSectionRepo = FakeSectionRepository()
    private val fakeThemeRepo = FakeThemeRepository()
    private val fakeLessonRepo = FakeLessonRepository()
    private val fakeQuestionRepo = FakeQuestionRepository()
    private val fakeNavigation = FakeStackNavigation()
    private var syncedQuestIds = emptyList<QuestId>()

    @After
    fun tearDown() {
        if (::lifecycle.isInitialized) {
            lifecycle.stop()
            lifecycle.destroy()
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun buildComponent(
        catalogId: String = "cat-1",
        titles: List<String> = listOf("Mathematics"),
        shelf: String = "home",
        mode: QuestListMode = QuestListMode.Home,
        selectionTargetShelf: String? = null,
    ): DefaultQuestListComponent {
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val ctx = DefaultComponentContext(lifecycle = lifecycle)
        return DefaultQuestListComponent(
            componentContext = ctx,
            navigation = fakeNavigation,
            questRepository = fakeRepo,
            sectionRepository = fakeSectionRepo,
            themeRepository = fakeThemeRepo,
            lessonRepository = fakeLessonRepo,
            questionRepository = fakeQuestionRepo,
            setPublicQuestShelf = SetPublicQuestShelfUseCase(fakeRepo),
            questContentSync = { questId ->
                syncedQuestIds = syncedQuestIds + questId
                Result.success(Unit)
            },
            config =
                QuizzesConfig.QuestList(
                    catalogId = catalogId,
                    titles = titles,
                    shelf = shelf,
                    mode = mode,
                    selectionTargetShelf = selectionTargetShelf,
                ),
            coroutineContext = dispatcher,
        )
    }

    private fun questFixture(
        id: String = "q-1",
        catalogId: String = "cat-1",
        title: String = "Quest A",
        lastModifiedAt: Long = 0L,
        visibleOn: Set<String> = setOf("home"),
        averageRating: Float? = null,
        averageRatingCount: Int = 0,
        archived: Boolean = false,
    ) = Quest(
        id = QuestId(id),
        catalogId = CatalogId(catalogId),
        authorUid = "author-uid",
        title = title,
        picturePath = null,
        visibleOn = visibleOn,
        averageRating = averageRating,
        averageRatingCount = averageRatingCount,
        version = 1L,
        contentsVersion = 0L,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
    )

    private fun questDisplayItemFixture(
        id: String = "q-1",
        catalogId: String = "cat-1",
        title: String = "Quest A",
    ) = QuestDisplayItem(
        id = QuestId(id),
        catalogId = CatalogId(catalogId),
        title = title,
        pictureUrl = null,
        averageRating = null,
    )

    private fun seedHierarchyForQuest(
        questId: String,
        lessonId: String = "lesson-$questId",
    ): LessonId {
        val sectionId = SectionId("section-$questId")
        val themeId = ThemeId("theme-$questId")
        val lesson = LessonId(lessonId)
        fakeSectionRepo.emit(
            listOf(
                Section(
                    id = sectionId,
                    questId = QuestId(questId),
                    title = "Section",
                    order = 0,
                    version = 1,
                    contentsVersion = 1,
                    lastModifiedAt = 1,
                ),
            ),
        )
        fakeThemeRepo.emit(
            listOf(
                Theme(
                    id = ThemeId("theme-$questId"),
                    sectionId = sectionId,
                    title = "Theme",
                    order = 0,
                    version = 1,
                    contentsVersion = 1,
                    lastModifiedAt = 1,
                ),
            ),
        )
        fakeLessonRepo.emit(
            listOf(
                Lesson(lesson, themeId, "Lesson", order = 0, version = 1, contentsVersion = 1, lastModifiedAt = 1),
            ),
        )
        return lesson
    }

    private fun questionFixture(lessonId: LessonId) =
        Question(
            id = QuestionId("question-${lessonId.value}"),
            lessonId = lessonId,
            text = "Question",
            payload = "{}",
            language = "ru",
            order = 0,
            version = 1,
            lastModifiedAt = 1,
        )

    // ── QL-U-01 ──────────────────────────────────────────────────────────────

    /**
     * Spec: QL-U-01 — before any coroutine advancement, initial state is Loading.
     * StandardTestDispatcher holds the collection coroutine until advanceUntilIdle().
     */
    @Test
    fun `initial state is Loading`() = runTest(testScheduler) {
        val component = buildComponent()
        assertIs<QuestListUiState.Loading>(component.uiState.value)
    }

    // ── QL-U-02 ──────────────────────────────────────────────────────────────

    /**
     * Spec: QL-U-02 — FakeQuestRepository emits 2 quests → state transitions to Loaded.
     * Quests must match catalogId and shelf "home" per FakeQuestRepository.observeByCatalog filter.
     */
    @Test
    fun `when FakeQuestRepository emits list then state is Loaded with correct count`() = runTest(testScheduler) {
        val component = buildComponent(catalogId = "cat-1")
        fakeRepo.emit(
            listOf(
                questFixture(id = "q-1", catalogId = "cat-1"),
                questFixture(id = "q-2", catalogId = "cat-1", title = "Quest B"),
            ),
        )
        advanceUntilIdle()
        val state = component.uiState.value
        assertIs<QuestListUiState.Loaded>(state)
        assertEquals(2, state.quests.size)
    }

    // ── QL-U-03 ──────────────────────────────────────────────────────────────

    /**
     * Spec: QL-U-03 — empty emission → state is Empty.
     * FakeQuestRepository emits empty list (matching catalogId filter returns nothing).
     */
    @Test
    fun `when repository emits empty list then state is Empty`() = runTest(testScheduler) {
        val component = buildComponent()
        fakeRepo.emit(emptyList())
        advanceUntilIdle()
        assertIs<QuestListUiState.Empty>(component.uiState.value)
    }

    // ── QL-U-05 ──────────────────────────────────────────────────────────────

    /**
     * Spec: QL-U-05 — onQuestClick pushes SectionList with correct questId.
     * Breadcrumb titles must include quest.title appended at the end.
     */
    @Test
    fun `onQuestClick pushes SectionList with correct config`() = runTest(testScheduler) {
        val component = buildComponent(catalogId = "cat-1", titles = listOf("Mathematics"))
        val questItem = questDisplayItemFixture(id = "q-1", title = "Quest A")

        component.onQuestClick(questItem)

        val pushed = fakeNavigation.pushedConfigs.last()
        assertIs<QuizzesConfig.SectionList>(pushed)
        assertEquals("q-1", pushed.questId)
        assertTrue("Quest A" in pushed.titles, "titles must include quest.title")
    }

    @Test
    fun `onQuestClick in selection mode sets target shelf instead of opening sections`() = runTest(testScheduler) {
        val component = buildComponent(shelf = "arena", mode = QuestListMode.Arena, selectionTargetShelf = "home")
        val questItem = questDisplayItemFixture(id = "q-1", title = "Quest A")

        component.onQuestClick(questItem)
        advanceUntilIdle()

        assertEquals(listOf(QuestId("q-1") to "home"), fakeRepo.shelfSetRequests)
        assertTrue(fakeNavigation.pushedConfigs.isEmpty(), "selection mode must not push SectionList")
    }

    // ── QL-U-06 ──────────────────────────────────────────────────────────────

    /**
     * Spec: QL-U-06 — onQuestClick breadcrumb titles include quest.title as last element.
     * Component was created with titles=["Math"]; click appends quest.title.
     */
    @Test
    fun `onQuestClick breadcrumb titles include quest title as last element`() = runTest(testScheduler) {
        val component = buildComponent(titles = listOf("Math"))
        val quest = questDisplayItemFixture(title = "Quest B")

        component.onQuestClick(quest)

        val pushed = fakeNavigation.pushedConfigs.last() as QuizzesConfig.SectionList
        assertEquals("Quest B", pushed.titles.last())
    }

    // ── QL-U-07 ──────────────────────────────────────────────────────────────

    /**
     * Spec: QL-U-07 — breadcrumb titles[0] in pushed SectionList config equals original catalogName.
     * titles[0] must be the catalog name passed at component creation time.
     */
    @Test
    fun `onQuestClick breadcrumb titles index 0 equals original catalogName`() = runTest(testScheduler) {
        val component = buildComponent(titles = listOf("Mathematics"))

        component.onQuestClick(questDisplayItemFixture())

        val pushed = fakeNavigation.pushedConfigs.last() as QuizzesConfig.SectionList
        assertEquals("Mathematics", pushed.titles[0])
    }

    // ── QL-U-10 ──────────────────────────────────────────────────────────────

    /**
     * Spec: QL-U-10 — ADR-QS-10 frozen titles. Repository renaming a quest does NOT change
     * component.titles which was frozen at component creation time.
     */
    @Test
    fun `titles in QuestList config unchanged after repository emits renamed quest`() = runTest(testScheduler) {
        val component = buildComponent(titles = listOf("Original Name"))

        fakeRepo.emit(listOf(questFixture(title = "Renamed Quest")))
        advanceUntilIdle()

        assertEquals(
            listOf("Original Name"),
            component.titles,
            "titles must remain frozen at component creation time",
        )
    }

    // ── QL-U-stub-01 ─────────────────────────────────────────────────────────

    /**
     * Spec: QL-U-stub-01 — onShareClick stub does not throw.
     * Phase-04: onShareClick is a stub (Phase-06 will wire the share intent).
     */
    @Test
    fun `onShareClick does not throw`() = runTest(testScheduler) {
        val component = buildComponent()
        component.onShareClick(questDisplayItemFixture())
    }

    // ── QL-U-archived ─────────────────────────────────────────────────────────

    /**
     * Spec: AC#1 — public quests appear in QuestList; archived marks on-demand content.
     * GIVEN: repo emits mix of archived and non-archived quests with same catalogId
     * WHEN: advanceUntilIdle()
     * THEN: both quests appear in Loaded.quests
     */
    @Test
    fun `archived quests are included in Loaded state`() = runTest(testScheduler) {
        val component = buildComponent(catalogId = "cat-1")
        fakeRepo.emit(
            listOf(
                questFixture(id = "q-visible", catalogId = "cat-1", title = "Visible"),
                questFixture(id = "q-archived", catalogId = "cat-1", title = "Archived").copy(archived = true),
            ),
        )
        advanceUntilIdle()
        val state = component.uiState.value
        assertIs<QuestListUiState.Loaded>(state)
        assertEquals(2, state.quests.size, "archived quest must remain visible")
        assertEquals(setOf("q-visible", "q-archived"), state.quests.map { it.id.value }.toSet())
        assertEquals(
            true,
            state.quests.first { it.id.value == "q-archived" }.isDownloadable,
            "archived root quest should expose an explicit download action",
        )
    }

    @Test
    fun `courses home is empty even when archived course is downloaded`() = runTest(testScheduler) {
        val lessonId = seedHierarchyForQuest("q-course")
        val component = buildComponent(catalogId = "courses")
        fakeRepo.emit(
            listOf(
                questFixture(
                    id = "q-course",
                    catalogId = "courses",
                    title = "Course",
                    visibleOn = setOf("archive"),
                    archived = true,
                ),
            ),
        )
        advanceUntilIdle()
        assertIs<QuestListUiState.Empty>(component.uiState.value)

        fakeQuestionRepo.emit(listOf(questionFixture(lessonId)))
        advanceUntilIdle()

        assertIs<QuestListUiState.Empty>(component.uiState.value)
    }

    @Test
    fun `course archive shows downloaded courses with persistent complete mark`() = runTest(testScheduler) {
        val lessonId = seedHierarchyForQuest("q-course")
        val component = buildComponent(
            catalogId = "courses",
            titles = listOf("Архив", "Курсы"),
            shelf = "archive",
            mode = QuestListMode.Archive,
        )
        fakeQuestionRepo.emit(listOf(questionFixture(lessonId)))
        fakeRepo.emit(
            listOf(
                questFixture(
                    id = "q-course",
                    catalogId = "courses",
                    title = "Course",
                    visibleOn = setOf("archive"),
                    archived = true,
                ),
            ),
        )
        advanceUntilIdle()

        val state = component.uiState.value
        assertIs<QuestListUiState.Loaded>(state)
        assertEquals(false, state.quests.first().isDownloadable)
        assertEquals(true, state.quests.first().isDownloadComplete)
    }

    @Test
    fun `course archive click on not downloaded course opens hierarchy without download`() = runTest(testScheduler) {
        seedHierarchyForQuest("q-course")
        val component = buildComponent(
            catalogId = "courses",
            titles = listOf("Архив", "Курсы"),
            shelf = "archive",
            mode = QuestListMode.Archive,
        )
        fakeRepo.emit(
            listOf(
                questFixture(
                    id = "q-course",
                    catalogId = "courses",
                    title = "Course",
                    visibleOn = setOf("archive"),
                    archived = true,
                ),
            ),
        )
        advanceUntilIdle()
        val state = component.uiState.value
        assertIs<QuestListUiState.Loaded>(state)

        component.onQuestClick(state.quests.first())
        advanceUntilIdle()

        assertTrue(syncedQuestIds.isEmpty())
        val pushed = fakeNavigation.pushedConfigs.last()
        assertIs<QuizzesConfig.SectionList>(pushed)
        assertEquals("q-course", pushed.questId)
    }

    @Test
    fun `course archive download icon starts download`() = runTest(testScheduler) {
        seedHierarchyForQuest("q-course")
        val component = buildComponent(
            catalogId = "courses",
            titles = listOf("Архив", "Курсы"),
            shelf = "archive",
            mode = QuestListMode.Archive,
        )
        fakeRepo.emit(
            listOf(
                questFixture(
                    id = "q-course",
                    catalogId = "courses",
                    title = "Course",
                    visibleOn = setOf("archive"),
                    archived = true,
                ),
            ),
        )
        advanceUntilIdle()
        val state = component.uiState.value
        assertIs<QuestListUiState.Loaded>(state)

        component.onQuestDownloadClick(state.quests.first())
        advanceUntilIdle()

        assertEquals(listOf(QuestId("q-course")), syncedQuestIds)
        assertTrue(fakeNavigation.pushedConfigs.isEmpty())
    }

    @Test
    fun `course archive quests are sorted by quest rating descending`() = runTest(testScheduler) {
        val component = buildComponent(
            catalogId = "courses",
            titles = listOf("Архив", "Курсы"),
            shelf = "archive",
            mode = QuestListMode.Archive,
        )
        fakeRepo.emit(
            listOf(
                questFixture(
                    id = "low",
                    catalogId = "courses",
                    title = "Low",
                    visibleOn = setOf("archive"),
                    averageRating = 1.7f,
                    averageRatingCount = 80,
                    lastModifiedAt = 300,
                    archived = true,
                ),
                questFixture(
                    id = "top-newer-low-count",
                    catalogId = "courses",
                    title = "Top Newer",
                    visibleOn = setOf("archive"),
                    averageRating = 2.8f,
                    averageRatingCount = 5,
                    lastModifiedAt = 400,
                    archived = true,
                ),
                questFixture(
                    id = "top-older-high-count",
                    catalogId = "courses",
                    title = "Top Older",
                    visibleOn = setOf("archive"),
                    averageRating = 2.8f,
                    averageRatingCount = 900,
                    lastModifiedAt = 100,
                    archived = true,
                ),
                questFixture(
                    id = "unrated",
                    catalogId = "courses",
                    title = "Unrated",
                    visibleOn = setOf("archive"),
                    averageRating = null,
                    averageRatingCount = 0,
                    lastModifiedAt = 500,
                    archived = true,
                ),
            ),
        )
        advanceUntilIdle()

        val state = component.uiState.value
        assertIs<QuestListUiState.Loaded>(state)
        assertEquals(
            listOf("top-newer-low-count", "top-older-high-count", "low", "unrated"),
            state.quests.map { it.id.value },
        )
    }

    @Test
    fun `arena quests are sorted by quest rating descending`() = runTest(testScheduler) {
        val component = buildComponent(
            catalogId = "courses",
            titles = listOf("Арена", "Курсы"),
            shelf = "arena",
            mode = QuestListMode.Arena,
        )
        fakeRepo.emit(
            listOf(
                questFixture(
                    id = "low",
                    catalogId = "courses",
                    title = "Low",
                    visibleOn = setOf("arena"),
                    averageRating = 1.7f,
                    averageRatingCount = 80,
                    lastModifiedAt = 300,
                ),
                questFixture(
                    id = "top-newer-low-count",
                    catalogId = "courses",
                    title = "Top Newer",
                    visibleOn = setOf("arena"),
                    averageRating = 2.8f,
                    averageRatingCount = 5,
                    lastModifiedAt = 400,
                ),
                questFixture(
                    id = "top-older-high-count",
                    catalogId = "courses",
                    title = "Top Older",
                    visibleOn = setOf("arena"),
                    averageRating = 2.8f,
                    averageRatingCount = 900,
                    lastModifiedAt = 100,
                ),
                questFixture(
                    id = "unrated",
                    catalogId = "courses",
                    title = "Unrated",
                    visibleOn = setOf("arena"),
                    averageRating = null,
                    averageRatingCount = 0,
                    lastModifiedAt = 500,
                ),
            ),
        )
        advanceUntilIdle()

        val state = component.uiState.value
        assertIs<QuestListUiState.Loaded>(state)
        assertEquals(
            listOf("top-newer-low-count", "top-older-high-count", "low", "unrated"),
            state.quests.map { it.id.value },
        )
    }

    @Test
    fun `arena random quest click opens a local arena quest`() = runTest(testScheduler) {
        val component = buildComponent(
            catalogId = "courses",
            titles = listOf("Арена", "Курсы"),
            shelf = "arena",
            mode = QuestListMode.Arena,
        )
        fakeRepo.emit(
            listOf(
                questFixture(
                    id = "arena-quest",
                    catalogId = "courses",
                    title = "Arena Quest",
                    visibleOn = setOf("arena"),
                    averageRating = 2.2f,
                ),
            ),
        )
        advanceUntilIdle()

        component.onRandomQuestClick()

        val pushed = fakeNavigation.pushedConfigs.last()
        assertIs<QuizzesConfig.SectionList>(pushed)
        assertEquals("arena-quest", pushed.questId)
    }
}
