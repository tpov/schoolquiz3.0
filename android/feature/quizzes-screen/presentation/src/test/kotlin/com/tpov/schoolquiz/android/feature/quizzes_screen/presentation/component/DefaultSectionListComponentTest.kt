package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeSectionRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeStackNavigation
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyLevel
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.section.domain.model.Section
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
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
 * JVM unit tests for [DefaultSectionListComponent].
 *
 * Spec: docs/features/quizzes-screen/04-testing.md §4
 * Phase: 04 (TDD)
 *
 * Coverage: SL-U-01..07
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSectionListComponentTest {

    private val testScheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(testScheduler)
    private lateinit var lifecycle: LifecycleRegistry
    private val fakeRepo = FakeSectionRepository()
    private val fakeNavigation = FakeStackNavigation()

    @After
    fun tearDown() {
        if (::lifecycle.isInitialized) {
            lifecycle.stop()
            lifecycle.destroy()
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun dynamicCrumbs(vararg titles: String): List<BreadcrumbRoot> =
        titles.map { BreadcrumbRoot.Dynamic(it) }

    private fun buildComponent(
        questId: String = "q-1",
        breadcrumbs: List<BreadcrumbRoot> = dynamicCrumbs("Math", "Quest 1"),
    ): DefaultSectionListComponent {
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val ctx = DefaultComponentContext(lifecycle = lifecycle)
        return DefaultSectionListComponent(
            componentContext = ctx,
            navigation = fakeNavigation,
            sectionRepository = fakeRepo,
            config = QuizzesConfig.SectionList(questId = questId, breadcrumbs = breadcrumbs),
            coroutineContext = dispatcher,
        )
    }

    private fun sectionFixture(
        id: String = "s-1",
        questId: String = "q-1",
        title: String = "Section A",
        order: Int = 1,
    ) = Section(
        id = SectionId(id),
        questId = QuestId(questId),
        title = title,
        order = order,
        version = 1L,
        lastModifiedAt = 0L,
    )

    private fun sectionItemFixture(
        id: String = "s-1",
        title: String = "Section A",
    ) = HierarchyItemUi(id = id, title = title)

    // ── SL-U-01 ──────────────────────────────────────────────────────────────

    /**
     * Spec: SL-U-01 — before any coroutine advancement, initial state is Loading.
     */
    @Test
    fun `initial state is Loading`() = runTest(testScheduler) {
        val component = buildComponent()
        assertIs<HierarchyListUiState.Loading>(component.uiState.value)
    }

    // ── SL-U-02 ──────────────────────────────────────────────────────────────

    /**
     * Spec: SL-U-02 — FakeSectionRepository emits 2 sections → state is Loaded with 2 items.
     * Sections must match questId filter in FakeSectionRepository.observeByQuest.
     */
    @Test
    fun `FakeSectionRepository emits sections then state is Loaded`() = runTest(testScheduler) {
        val component = buildComponent(questId = "q-1")
        fakeRepo.emit(
            listOf(
                sectionFixture(id = "s-1", questId = "q-1"),
                sectionFixture(id = "s-2", questId = "q-1", title = "Section B", order = 2),
            ),
        )
        advanceUntilIdle()
        val state = component.uiState.value
        assertIs<HierarchyListUiState.Loaded>(state)
        assertEquals(2, state.items.size)
    }

    @Test
    fun `sections are ordered by list order`() = runTest(testScheduler) {
        val component = buildComponent(questId = "q-1")
        fakeRepo.emit(
            listOf(
                sectionFixture(id = "third", questId = "q-1", title = "Third", order = 2),
                sectionFixture(id = "first", questId = "q-1", title = "First", order = 0),
                sectionFixture(id = "second", questId = "q-1", title = "Second", order = 1),
            ),
        )
        advanceUntilIdle()

        val state = component.uiState.value
        assertIs<HierarchyListUiState.Loaded>(state)
        assertEquals(listOf("first", "second", "third"), state.items.map { it.id })
    }

    // ── SL-U-03 ──────────────────────────────────────────────────────────────

    /**
     * Spec: SL-U-03 — empty list → Empty state carrying the SECTIONS level;
     * the screen resolves the level to localized copy.
     */
    @Test
    fun `empty list produces Empty state with sections level`() = runTest(testScheduler) {
        val component = buildComponent()
        fakeRepo.emit(emptyList())
        advanceUntilIdle()
        val state = component.uiState.value
        assertIs<HierarchyListUiState.Empty>(state)
        assertEquals(HierarchyLevel.SECTIONS, state.level, "Empty must carry the sections level")
    }

    // ── SL-U-04 ──────────────────────────────────────────────────────────────

    /**
     * Spec: SL-U-04 — onSectionClick pushes ThemeList with correct sectionId.
     * Breadcrumbs must include section.title as the last (dynamic) element.
     */
    @Test
    fun `onSectionClick pushes ThemeList with correct sectionId`() = runTest(testScheduler) {
        val component = buildComponent(questId = "q-1", breadcrumbs = dynamicCrumbs("Math", "Quest 1"))
        val sectionItem = sectionItemFixture(id = "s-1", title = "Section A")

        component.onSectionClick(sectionItem)

        val pushed = fakeNavigation.pushedConfigs.last()
        assertIs<QuizzesConfig.ThemeList>(pushed)
        assertEquals("s-1", pushed.sectionId)
        assertTrue(
            BreadcrumbRoot.Dynamic("Section A") in pushed.breadcrumbs,
            "breadcrumbs must include section.title",
        )
    }

    // ── SL-U-05 ──────────────────────────────────────────────────────────────

    /**
     * Spec: SL-U-05 — breadcrumb snapshot at push time includes all parent crumbs + section.title.
     */
    @Test
    fun `breadcrumb snapshot at push time`() = runTest(testScheduler) {
        val component = buildComponent(breadcrumbs = dynamicCrumbs("Math", "Quest 1"))
        val sectionItem = sectionItemFixture(title = "Section A")

        component.onSectionClick(sectionItem)

        val pushed = fakeNavigation.pushedConfigs.last() as QuizzesConfig.ThemeList
        assertEquals(dynamicCrumbs("Math", "Quest 1", "Section A"), pushed.breadcrumbs)
    }

    // ── SL-U-06 ──────────────────────────────────────────────────────────────

    /**
     * Spec: SL-U-06 — AC#17: sync adds new section → Flow re-emits → Loaded.items updates.
     */
    @Test
    fun `when sync adds new section Flow re-emits and Loaded items updates`() = runTest(testScheduler) {
        val component = buildComponent(questId = "q-1")
        fakeRepo.emit(listOf(sectionFixture(id = "s-1", questId = "q-1")))
        advanceUntilIdle()
        assertIs<HierarchyListUiState.Loaded>(component.uiState.value)
        assertEquals(1, (component.uiState.value as HierarchyListUiState.Loaded).items.size)

        fakeRepo.emit(
            listOf(
                sectionFixture(id = "s-1", questId = "q-1"),
                sectionFixture(id = "s-2", questId = "q-1", title = "Section B", order = 2),
            ),
        )
        advanceUntilIdle()

        assertEquals(2, (component.uiState.value as HierarchyListUiState.Loaded).items.size)
    }

    // ── SL-U-07 ──────────────────────────────────────────────────────────────

    /**
     * Spec: SL-U-07 — AC#23: sync renames section → already-pushed ThemeList config.breadcrumbs
     * remain frozen (ADR-QS-10). The navigation stack retains the snapshot taken at push time.
     */
    @Test
    fun `when sync renames section breadcrumbs remain frozen`() = runTest(testScheduler) {
        val component = buildComponent(questId = "q-1", breadcrumbs = dynamicCrumbs("Math", "Quest 1"))

        fakeRepo.emit(listOf(sectionFixture(id = "s-1", questId = "q-1", title = "Original Section")))
        advanceUntilIdle()

        component.onSectionClick(sectionItemFixture(id = "s-1", title = "Original Section"))
        val pushedAtClickTime = fakeNavigation.pushedConfigs.last() as QuizzesConfig.ThemeList
        assertEquals(
            dynamicCrumbs("Math", "Quest 1", "Original Section"),
            pushedAtClickTime.breadcrumbs,
        )

        fakeRepo.emit(listOf(sectionFixture(id = "s-1", questId = "q-1", title = "RENAMED Section")))
        advanceUntilIdle()

        // already-pushed config is immutable — breadcrumbs frozen at push time
        assertEquals(
            dynamicCrumbs("Math", "Quest 1", "Original Section"),
            pushedAtClickTime.breadcrumbs,
            "pushed config.breadcrumbs must remain frozen after repository re-emits renamed section",
        )
    }
}
