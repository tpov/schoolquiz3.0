package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeAuthRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeEconomyRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeStackNavigation
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
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

/**
 * JVM unit tests for [DefaultLessonListComponent].
 *
 * Spec: docs/features/quizzes-screen/04-testing.md §6
 * Phase: 06 (updated)
 *
 * Coverage: LL-U-01..04
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLessonListComponentTest {

    private val testScheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(testScheduler)
    private lateinit var lifecycle: LifecycleRegistry
    private val fakeRepo = FakeLessonRepository()
    private val fakeAttemptRepo = FakeLessonAttemptRepository()
    private val fakeAuthRepo = FakeAuthRepository(initialUid = "user-1")
    private val fakeEconomyRepo = FakeEconomyRepository()
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
        themeId: String = "t-1",
        breadcrumbs: List<BreadcrumbRoot> = dynamicCrumbs("Math", "Quest 1", "Section A", "Theme A"),
    ): DefaultLessonListComponent {
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val ctx = DefaultComponentContext(lifecycle = lifecycle)
        return DefaultLessonListComponent(
            componentContext = ctx,
            navigation = fakeNavigation,
            lessonRepository = fakeRepo,
            attemptRepository = fakeAttemptRepo,
            authRepository = fakeAuthRepo,
            economyRepository = fakeEconomyRepo,
            config = QuizzesConfig.LessonList(themeId = themeId, breadcrumbs = breadcrumbs),
            coroutineContext = dispatcher,
        )
    }

    private fun lessonFixture(
        id: String = "l-1",
        themeId: String = "t-1",
        title: String = "Lesson A",
        order: Int = 1,
        averageRating: Float? = null,
        ratingCount: Int = 0,
    ) = Lesson(
        id = LessonId(id),
        themeId = ThemeId(themeId),
        title = title,
        order = order,
        version = 1L,
        lastModifiedAt = 0L,
        averageRating = averageRating,
        ratingCount = ratingCount,
    )

    private fun lessonItemFixture(
        id: String = "l-1",
        title: String = "Lesson A",
        hardUnlocked: Boolean = false,
        isHardChecked: Boolean = false,
    ) = LessonItemUi(id = id, title = title, hardUnlocked = hardUnlocked, isHardChecked = isHardChecked)

    // ── LL-U-01 ──────────────────────────────────────────────────────────────

    /**
     * Spec: LL-U-01 — initial state is Loading.
     */
    @Test
    fun `initial state is Loading`() = runTest(testScheduler) {
        val component = buildComponent()
        assertIs<LessonListUiState.Loading>(component.uiState.value)
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
        assertIs<LessonListUiState.Loaded>(component.uiState.value)
    }

    @Test
    fun `lessons are ordered by list order and map average rating`() = runTest(testScheduler) {
        val component = buildComponent(themeId = "t-1")
        fakeRepo.emit(
            listOf(
                lessonFixture(
                    id = "third",
                    themeId = "t-1",
                    title = "Third",
                    order = 2,
                    averageRating = 1.2f,
                    ratingCount = 4,
                ),
                lessonFixture(
                    id = "first",
                    themeId = "t-1",
                    title = "First",
                    order = 0,
                    averageRating = 2.8f,
                    ratingCount = 20,
                ),
                lessonFixture(
                    id = "second",
                    themeId = "t-1",
                    title = "Second",
                    order = 1,
                    averageRating = 1.9f,
                    ratingCount = 9,
                ),
            ),
        )
        advanceUntilIdle()

        val state = component.uiState.value
        assertIs<LessonListUiState.Loaded>(state)
        assertEquals(listOf("first", "second", "third"), state.items.map { it.id })
        assertEquals(2.8f, state.items.first().averageRating)
        assertEquals(20, state.items.first().ratingCount)
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
        assertIs<LessonListUiState.Empty>(component.uiState.value)
    }

    // ── LL-U-04 ──────────────────────────────────────────────────────────────

    /**
     * Spec: LL-U-04 — onLessonClick with hardUnlocked=false pushes LessonRunner with EASY mode.
     * Breadcrumbs appended with the dynamic lesson title.
     */
    @Test
    fun `onLessonClick pushes LessonRunner with EASY mode when hardUnlocked is false`() = runTest(testScheduler) {
        val component = buildComponent(themeId = "t-1")
        val lessonItem = lessonItemFixture(id = "l-1", title = "Lesson A", hardUnlocked = false, isHardChecked = false)

        component.onLessonClick(lessonItem)
        advanceUntilIdle()

        val pushed = fakeNavigation.pushedConfigs.last()
        assertIs<QuizzesConfig.LessonRunner>(pushed)
        assertEquals("l-1", pushed.lessonId)
        assertEquals(Difficulty.EASY, pushed.mode, "mode must be EASY when hardUnlocked=false")
        assertEquals(true, BreadcrumbRoot.Dynamic("Lesson A") in pushed.breadcrumbs, "breadcrumbs must include lesson title")
    }

    /**
     * Spec: LL-U-04 edge case — onLessonClick with hardUnlocked=true + isHardChecked=true pushes HARD mode.
     */
    @Test
    fun `onLessonClick pushes LessonRunner with HARD mode when hardUnlocked and isHardChecked`() =
        runTest(testScheduler) {
        val component = buildComponent()
        val lessonItem = lessonItemFixture(id = "l-1", title = "Lesson A", hardUnlocked = true, isHardChecked = true)

        component.onLessonClick(lessonItem)
        advanceUntilIdle()

        val pushed = fakeNavigation.pushedConfigs.last() as QuizzesConfig.LessonRunner
        assertEquals(Difficulty.HARD, pushed.mode, "mode must be HARD when hardUnlocked=true and isHardChecked=true")
    }
}
