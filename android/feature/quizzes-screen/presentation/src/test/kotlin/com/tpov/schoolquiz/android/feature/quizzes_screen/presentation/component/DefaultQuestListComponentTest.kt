package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeStackNavigation
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
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
    private val fakeNavigation = FakeStackNavigation()

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
    ): DefaultQuestListComponent {
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val ctx = DefaultComponentContext(lifecycle = lifecycle)
        return DefaultQuestListComponent(
            componentContext = ctx,
            navigation = fakeNavigation,
            questRepository = fakeRepo,
            config = QuizzesConfig.QuestList(catalogId = catalogId, titles = titles),
            coroutineContext = dispatcher,
        )
    }

    private fun questFixture(
        id: String = "q-1",
        catalogId: String = "cat-1",
        title: String = "Quest A",
        lastModifiedAt: Long = 0L,
    ) = Quest(
        id = QuestId(id),
        catalogId = CatalogId(catalogId),
        authorUid = "author-uid",
        title = title,
        picturePath = null,
        visibleOn = setOf("home"),
        version = 1L,
        contentsVersion = 0L,
        lastModifiedAt = lastModifiedAt,
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
     * Spec: AC#1 — only public non-archived quests appear in QuestList.
     * FakeQuestRepository.observeByCatalog filters archived=true quests.
     * GIVEN: repo emits mix of archived and non-archived quests with same catalogId
     * WHEN: advanceUntilIdle()
     * THEN: only non-archived quests appear in Loaded.quests
     */
    @Test
    fun `archived quests are excluded from Loaded state`() = runTest(testScheduler) {
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
        assertEquals(1, state.quests.size, "archived quest must be excluded")
        assertEquals("q-visible", state.quests.first().id.value)
    }
}
