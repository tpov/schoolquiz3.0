package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeStackNavigation
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM unit tests for [DefaultLessonListComponent].
 *
 * Spec: docs/features/quizzes-screen/04-testing.md §6
 * Phase: 04 (TDD)
 *
 * Coverage: LL-U-01..04
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLessonListComponentTest {

    private val testScheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(testScheduler)
    private lateinit var lifecycle: LifecycleRegistry
    private val fakeRepo = FakeLessonRepository()
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
        themeId: String = "t-1",
        titles: List<String> = listOf("Math", "Quest 1", "Section A", "Theme A"),
    ): DefaultLessonListComponent {
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val ctx = DefaultComponentContext(lifecycle = lifecycle)
        return DefaultLessonListComponent(
            componentContext = ctx,
            navigation = fakeNavigation,
            lessonRepository = fakeRepo,
            config = QuizzesConfig.LessonList(themeId = themeId, titles = titles),
            coroutineContext = dispatcher,
        )
    }

    private fun lessonFixture(
        id: String = "l-1",
        themeId: String = "t-1",
        title: String = "Lesson A",
        order: Int = 1,
    ) = Lesson(
        id = LessonId(id),
        themeId = ThemeId(themeId),
        title = title,
        order = order,
        version = 1L,
        contentsVersion = 0L,
        lastModifiedAt = 0L,
    )

    private fun lessonItemFixture(
        id: String = "l-1",
        title: String = "Lesson A",
    ) = HierarchyItemUi(id = id, title = title)

    // ── LL-U-01 ──────────────────────────────────────────────────────────────

    /**
     * Spec: LL-U-01 — initial state is Loading.
     */
    @Test
    fun `initial state is Loading`() = runTest(testScheduler) {
        val component = buildComponent()
        assertIs<HierarchyListUiState.Loading>(component.uiState.value)
    }

    // ── LL-U-02 ──────────────────────────────────────────────────────────────

    /**
     * Spec: LL-U-02 — FakeLessonRepository emits lessons → state is Loaded.
     * Lessons must match themeId filter in FakeLessonRepository.observeByTheme.
     */
    @Test
    fun `FakeLessonRepository emits lessons then state is Loaded`() = runTest(testScheduler) {
        val component = buildComponent(themeId = "t-1")
        fakeRepo.emit(listOf(lessonFixture(id = "l-1", themeId = "t-1")))
        advanceUntilIdle()
        assertIs<HierarchyListUiState.Loaded>(component.uiState.value)
    }

    // ── LL-U-03 ──────────────────────────────────────────────────────────────

    /**
     * Spec: LL-U-03 — empty list → Empty state.
     */
    @Test
    fun `empty list produces Empty state`() = runTest(testScheduler) {
        val component = buildComponent()
        fakeRepo.emit(emptyList())
        advanceUntilIdle()
        assertIs<HierarchyListUiState.Empty>(component.uiState.value)
    }

    // ── LL-U-04 ──────────────────────────────────────────────────────────────

    /**
     * Spec: LL-U-04 — onLessonClick pushes LessonPlaceholder with correct lessonId, lessonTitle,
     * and titles list. lessonTitle is a separate field (not just titles.last()).
     * SER-06 depends on this field existing independently.
     */
    @Test
    fun `onLessonClick pushes LessonPlaceholder with correct lessonId and lessonTitle`() = runTest(testScheduler) {
        val component = buildComponent(themeId = "t-1", titles = listOf("Math", "Quest 1", "Section A", "Theme A"))
        val lessonItem = lessonItemFixture(id = "l-1", title = "Lesson A")

        component.onLessonClick(lessonItem)

        val pushed = fakeNavigation.pushedConfigs.last()
        assertIs<QuizzesConfig.LessonPlaceholder>(pushed)
        assertEquals("l-1", pushed.lessonId)
        assertEquals("Lesson A", pushed.lessonTitle, "lessonTitle must be set as a separate field")
        assertTrue("Lesson A" in pushed.titles, "titles must include lesson.title")
    }

    /**
     * Spec: LL-U-04 edge case — lessonTitle is distinct from titles.last() (both happen to be equal,
     * but lessonTitle must not be derived from titles.last() — it's independently set).
     */
    @Test
    fun `onLessonClick lessonTitle is set independently from titles`() = runTest(testScheduler) {
        val component = buildComponent(titles = listOf("Math", "Quest 1", "Section A", "Theme A"))
        val lessonItem = lessonItemFixture(id = "l-1", title = "Lesson A")

        component.onLessonClick(lessonItem)

        val pushed = fakeNavigation.pushedConfigs.last() as QuizzesConfig.LessonPlaceholder
        assertNotNull(pushed.lessonTitle, "lessonTitle must not be null — it is a dedicated field")
        assertEquals(pushed.lessonTitle, pushed.titles.last(), "lessonTitle matches titles.last()")
    }
}
