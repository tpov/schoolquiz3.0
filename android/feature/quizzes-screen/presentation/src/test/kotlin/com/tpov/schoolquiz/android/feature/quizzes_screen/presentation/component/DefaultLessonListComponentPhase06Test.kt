package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeAuthRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeStackNavigation
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase-06 JVM unit tests for [DefaultLessonListComponent] extended with
 * bestStars / hardUnlocked computation from [LessonAttemptRepository].
 *
 * Spec: docs/features/lesson-runner/plan/phase-06/tests.md §Scenario Group A
 * AC: AC-21, AC-22, AC-23, AC-47, AC-48, AC-49
 *
 * COMPILATION NOTE: These tests depend on Gradle deps not yet added to
 * android/feature/quizzes-screen/presentation/build.gradle.kts:
 *   testImplementation(project(":shared:feature:lesson-runner:domain"))
 *   testImplementation(project(":shared:feature:app-shell:domain"))
 * Tests compile and run once backend-dev adds these deps (scaffold change request sent to lead).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLessonListComponentPhase06Test {

    private val testScheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(testScheduler)
    private lateinit var lifecycle: LifecycleRegistry
    private val fakeLessonRepo = FakeLessonRepository()
    private val fakeAttemptRepo = FakeLessonAttemptRepository()
    private val fakeAuthRepo = FakeAuthRepository(initialUid = "user1")
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
        breadcrumbs: List<BreadcrumbRoot> =
            listOf("Math", "Quest 1", "Section A", "Theme A").map { BreadcrumbRoot.Dynamic(it) },
    ): DefaultLessonListComponent {
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val ctx = DefaultComponentContext(lifecycle = lifecycle)
        return DefaultLessonListComponent(
            componentContext = ctx,
            config = QuizzesConfig.LessonList(themeId = themeId, breadcrumbs = breadcrumbs),
            lessonRepository = fakeLessonRepo,
            attemptRepository = fakeAttemptRepo,
            authRepository = fakeAuthRepo,
            navigation = fakeNavigation,
            coroutineContext = dispatcher,
        )
    }

    private fun lessonFixture(
        id: String = "l-1",
        themeId: String = "t-1",
        title: String = "Урок 1",
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

    private fun attemptFixture(
        userId: String = "user1",
        lessonId: String = "l-1",
        mode: Difficulty = Difficulty.EASY,
        codeAnswer: String = "9",
        percentScore: Int = 100,
    ) = Attempt(
        id = AttemptId("attempt-$userId-$lessonId"),
        userId = userId,
        lessonId = LessonId(lessonId),
        lessonVersion = 1L,
        mode = mode,
        completedAt = 1_000_000L,
        codeAnswer = CodeAnswer(codeAnswer),
        percentScore = PercentScore(percentScore),
    )

    private fun loadedItems(component: DefaultLessonListComponent) =
        (component.uiState.value as LessonListUiState.Loaded).items

    // ── PT-15 ─────────────────────────────────────────────────────────────────

    /**
     * PT-15: GIVEN no attempts for lesson WHEN lessonItems emitted
     * THEN bestStarsRawTenths == 0 AND hardUnlocked == false.
     * Spec AC-21.
     */
    @Test
    fun `pt15 no attempts defaults to zero stars and hardUnlocked false`() = runTest(testScheduler) {
        val component = buildComponent(themeId = "t-1")

        fakeLessonRepo.emit(listOf(lessonFixture(id = "l-1", themeId = "t-1")))
        // No attempts emitted — fakeAttemptRepo stores empty list
        advanceUntilIdle()

        val items = loadedItems(component)
        assertEquals(1, items.size)
        assertEquals(0, items[0].bestStarsRawTenths, "no attempts → Stars(0) → rawTenths=0")
        assertFalse(items[0].hardUnlocked, "no attempts → hardUnlocked=false")
    }

    // ── PT-16 ─────────────────────────────────────────────────────────────────

    /**
     * PT-16: GIVEN one EASY attempt with allShownAnswersAre9=true WHEN lessonItems emitted
     * THEN hardUnlocked == true.
     * codeAnswer "9999" → all chars '9' AND at least one '9' → allShownAnswersAre9=true.
     * Spec AC-22.
     */
    @Test
    fun `pt16 easy perfect attempt unlocks hard mode`() = runTest(testScheduler) {
        val component = buildComponent(themeId = "t-1")

        fakeLessonRepo.emit(listOf(lessonFixture(id = "l-1", themeId = "t-1")))
        fakeAttemptRepo.emit(
            listOf(
                attemptFixture(
                    userId = "user1",
                    lessonId = "l-1",
                    mode = Difficulty.EASY,
                    codeAnswer = "9999",
                    percentScore = 100,
                ),
            ),
        )
        advanceUntilIdle()

        val items = loadedItems(component)
        assertEquals(1, items.size)
        assertTrue(items[0].hardUnlocked, "EASY perfect attempt (allShownAnswersAre9=true) → hardUnlocked=true")
    }

    // ── PT-17 ─────────────────────────────────────────────────────────────────

    /**
     * PT-17: GIVEN EASY attempt with allShownAnswersAre9=false WHEN lessonItems emitted
     * THEN hardUnlocked == false, even if rawTenths == 20.
     * codeAnswer "8999" → first digit '8' ≠ '9' → allShownAnswersAre9=false.
     * Spec AC-23.
     */
    @Test
    fun `pt17 easy imperfect attempt does not unlock hard even at high percent`() = runTest(testScheduler) {
        val component = buildComponent(themeId = "t-1")

        fakeLessonRepo.emit(listOf(lessonFixture(id = "l-1", themeId = "t-1")))
        fakeAttemptRepo.emit(
            listOf(
                attemptFixture(
                    userId = "user1",
                    lessonId = "l-1",
                    mode = Difficulty.EASY,
                    codeAnswer = "8999", // first digit '8' → allShownAnswersAre9=false
                    percentScore = 95,
                ),
            ),
        )
        advanceUntilIdle()

        val items = loadedItems(component)
        assertEquals(1, items.size)
        assertFalse(items[0].hardUnlocked, "EASY attempt with '8' digit → hardUnlocked=false")
    }

    // ── PT-34 ─────────────────────────────────────────────────────────────────

    /**
     * PT-34: GIVEN EASY attempt with percentScore=75 WHEN lessonItems emitted
     * THEN bestStarsRawTenths == 15.
     * Formula: EASY rawTenths = (75*20+50)/100 = 1550/100 = 15.
     * Spec AC-47.
     */
    @Test
    fun `pt34 best stars rawTenths 15 for percentScore 75 easy`() = runTest(testScheduler) {
        val component = buildComponent(themeId = "t-1")

        fakeLessonRepo.emit(listOf(lessonFixture(id = "l-1", themeId = "t-1")))
        fakeAttemptRepo.emit(
            listOf(
                attemptFixture(
                    userId = "user1",
                    lessonId = "l-1",
                    mode = Difficulty.EASY,
                    codeAnswer = "777",
                    percentScore = 75,
                ),
            ),
        )
        advanceUntilIdle()

        val items = loadedItems(component)
        assertEquals(1, items.size)
        assertEquals(15, items[0].bestStarsRawTenths, "EASY 75% → Stars rawTenths=15")
    }

    // ── PT-35 ─────────────────────────────────────────────────────────────────

    /**
     * PT-35: GIVEN hardUnlocked=false WHEN onHardCheckToggled called
     * THEN isHardChecked remains false (toggle ignored when not unlocked).
     * Spec AC-48.
     */
    @Test
    fun `pt35 hard not unlocked ignores toggle`() = runTest(testScheduler) {
        val component = buildComponent(themeId = "t-1")

        fakeLessonRepo.emit(listOf(lessonFixture(id = "l-1", themeId = "t-1")))
        // No attempts → hardUnlocked=false
        advanceUntilIdle()

        component.onHardCheckToggled("l-1")
        advanceUntilIdle()

        val items = loadedItems(component)
        assertEquals(1, items.size)
        assertFalse(items[0].isHardChecked, "toggle ignored when hardUnlocked=false → isHardChecked stays false")
    }

    // ── PT-36 ─────────────────────────────────────────────────────────────────

    /**
     * PT-36: GIVEN hardUnlocked=true AND isHardChecked=false WHEN onHardCheckToggled called
     * THEN isHardChecked == true.
     * Spec AC-49.
     */
    @Test
    fun `pt36 hard unlocked allows toggle to true`() = runTest(testScheduler) {
        val component = buildComponent(themeId = "t-1")

        fakeLessonRepo.emit(listOf(lessonFixture(id = "l-1", themeId = "t-1")))
        fakeAttemptRepo.emit(
            listOf(
                attemptFixture(
                    userId = "user1",
                    lessonId = "l-1",
                    mode = Difficulty.EASY,
                    codeAnswer = "9999",
                    percentScore = 100,
                ),
            ),
        )
        advanceUntilIdle()

        assertTrue(loadedItems(component)[0].hardUnlocked, "precondition: hardUnlocked=true")
        assertFalse(loadedItems(component)[0].isHardChecked, "precondition: isHardChecked starts false")

        component.onHardCheckToggled("l-1")
        advanceUntilIdle()

        assertTrue(
            loadedItems(component)[0].isHardChecked,
            "toggle when hardUnlocked=true → isHardChecked=true",
        )
    }
}
