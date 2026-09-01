package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeStackNavigation
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeThemeRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
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
 * JVM unit tests for [DefaultThemeListComponent].
 *
 * Spec: docs/features/quizzes-screen/04-testing.md §5
 * Phase: 04 (TDD)
 *
 * Coverage: TH-U-01..04
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultThemeListComponentTest {

    private val testScheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(testScheduler)
    private lateinit var lifecycle: LifecycleRegistry
    private val fakeRepo = FakeThemeRepository()
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
        sectionId: String = "s-1",
        breadcrumbs: List<BreadcrumbRoot> = dynamicCrumbs("Math", "Quest 1", "Section A"),
    ): DefaultThemeListComponent {
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val ctx = DefaultComponentContext(lifecycle = lifecycle)
        return DefaultThemeListComponent(
            componentContext = ctx,
            navigation = fakeNavigation,
            themeRepository = fakeRepo,
            config = QuizzesConfig.ThemeList(sectionId = sectionId, breadcrumbs = breadcrumbs),
            coroutineContext = dispatcher,
        )
    }

    private fun themeFixture(
        id: String = "t-1",
        sectionId: String = "s-1",
        title: String = "Theme A",
        order: Int = 1,
    ) = Theme(
        id = ThemeId(id),
        sectionId = SectionId(sectionId),
        title = title,
        order = order,
        version = 1L,
        lastModifiedAt = 0L,
    )

    private fun themeItemFixture(
        id: String = "t-1",
        title: String = "Theme A",
    ) = HierarchyItemUi(id = id, title = title)

    // ── TH-U-01 ──────────────────────────────────────────────────────────────

    /**
     * Spec: TH-U-01 — initial state is Loading (collection coroutine not yet advanced).
     */
    @Test
    fun `initial state is Loading`() = runTest(testScheduler) {
        val component = buildComponent()
        assertIs<HierarchyListUiState.Loading>(component.uiState.value)
    }

    // ── TH-U-02 ──────────────────────────────────────────────────────────────

    /**
     * Spec: TH-U-02 — FakeThemeRepository emits themes → state is Loaded with correct item count.
     * Themes must match sectionId filter in FakeThemeRepository.observeBySection.
     */
    @Test
    fun `FakeThemeRepository emits themes then state is Loaded`() = runTest(testScheduler) {
        val component = buildComponent(sectionId = "s-1")
        fakeRepo.emit(listOf(themeFixture(id = "t-1", sectionId = "s-1")))
        advanceUntilIdle()
        val state = component.uiState.value
        assertIs<HierarchyListUiState.Loaded>(state)
        assertEquals(1, state.items.size)
    }

    @Test
    fun `themes are ordered by list order`() = runTest(testScheduler) {
        val component = buildComponent(sectionId = "s-1")
        fakeRepo.emit(
            listOf(
                themeFixture(id = "third", sectionId = "s-1", title = "Third", order = 2),
                themeFixture(id = "first", sectionId = "s-1", title = "First", order = 0),
                themeFixture(id = "second", sectionId = "s-1", title = "Second", order = 1),
            ),
        )
        advanceUntilIdle()

        val state = component.uiState.value
        assertIs<HierarchyListUiState.Loaded>(state)
        assertEquals(listOf("first", "second", "third"), state.items.map { it.id })
    }

    // ── TH-U-03 ──────────────────────────────────────────────────────────────

    /**
     * Spec: TH-U-03 — empty list → Empty state.
     */
    @Test
    fun `empty list produces Empty state`() = runTest(testScheduler) {
        val component = buildComponent()
        fakeRepo.emit(emptyList())
        advanceUntilIdle()
        assertIs<HierarchyListUiState.Empty>(component.uiState.value)
    }

    // ── TH-U-04 ──────────────────────────────────────────────────────────────

    /**
     * Spec: TH-U-04 — onThemeClick pushes LessonList with correct themeId and accumulated crumbs.
     * Breadcrumbs: parentCrumbs + [theme.title].
     */
    @Test
    fun `onThemeClick pushes LessonList with correct themeId`() = runTest(testScheduler) {
        val component = buildComponent(sectionId = "s-1")
        val themeItem = themeItemFixture(id = "t-1", title = "Theme A")

        component.onThemeClick(themeItem)

        val pushed = fakeNavigation.pushedConfigs.last()
        assertIs<QuizzesConfig.LessonList>(pushed)
        assertEquals("t-1", pushed.themeId)
        assertTrue(
            BreadcrumbRoot.Dynamic("Theme A") in pushed.breadcrumbs,
            "breadcrumbs must include theme.title",
        )
        assertEquals(dynamicCrumbs("Math", "Quest 1", "Section A", "Theme A"), pushed.breadcrumbs)
    }
}
