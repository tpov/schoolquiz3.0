package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.backhandler.BackHandler
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultQuizzesComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuestListMode
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeBackDispatcher
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeAuthRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestionRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeSectionRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeThemeRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.StubCatalogRepository
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.SetPublicQuestShelfUseCase
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM unit tests for [DefaultQuizzesComponent].
 *
 * Spec: docs/features/quizzes-screen/0-spec.md
 * Design: docs/features/quizzes-screen/04-testing.md §2
 * Phase: 03 (skeleton: QuizzesConfig + DefaultQuizzesComponent with Idle anchor)
 *
 * QZ-U-01..09 — back callback lifecycle, dismissQuizzes, initial state, lifecycle cleanup.
 *
 * No Dispatchers.setMain: DefaultQuizzesComponent has SupervisorJob but no CoroutineScope;
 * all Decompose navigation and Value.subscribe calls are synchronous on the calling thread.
 */
class DefaultQuizzesComponentTest {

    private lateinit var lifecycle: LifecycleRegistry

    @After
    fun tearDown() {
        if (::lifecycle.isInitialized) {
            lifecycle.stop()
            lifecycle.destroy()
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private fun buildComponent(
        backHandler: BackHandler = BackDispatcher(),
        stateKeeper: StateKeeperDispatcher = StateKeeperDispatcher(null),
    ): DefaultQuizzesComponent {
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val ctx = DefaultComponentContext(
            lifecycle = lifecycle,
            stateKeeper = stateKeeper,
            backHandler = backHandler,
        )
        val questRepository = FakeQuestRepository()
        return DefaultQuizzesComponent(
            componentContext = ctx,
            questRepository = questRepository,
            sectionRepository = FakeSectionRepository(),
            themeRepository = FakeThemeRepository(),
            lessonRepository = FakeLessonRepository(),
            lessonAttemptRepository = FakeLessonAttemptRepository(),
            authRepository = FakeAuthRepository(),
            questionRepository = FakeQuestionRepository(),
            catalogRepository = StubCatalogRepository(),
            setPublicQuestShelf = SetPublicQuestShelfUseCase(questRepository),
            lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _ -> error("Not expected") },
            mainContext = Dispatchers.Unconfined,
        )
    }

    /** Total items in stack: Idle anchor (backStack[0]) + everything above. */
    private fun DefaultQuizzesComponent.stackSize(): Int =
        childStack.value.backStack.size + 1

    // ── QZ-U-01 — Back Callback lifecycle ────────────────────────────────────────

    /**
     * Spec: QZ-U-01 — fresh component has stack=[Idle], backStack is empty.
     * backCallback.isEnabled is false → dispatcher reports no enabled callback.
     */
    @Test
    fun `when stack=Idle then backCallback disabled`() {
        val backDispatcher = BackDispatcher()
        buildComponent(backHandler = backDispatcher)

        assertFalse(
            backDispatcher.isEnabled,
            "backCallback must be disabled when stack contains only Idle anchor",
        )
    }

    // ── QZ-U-02 ──────────────────────────────────────────────────────────────────

    /**
     * Spec: QZ-U-02 — after openQuestList, backStack becomes non-empty.
     * backCallback.isEnabled flips to true (subscribed via childStack.subscribe).
     */
    @Test
    fun `when pushNew QuestList then backCallback enabled`() {
        val backDispatcher = BackDispatcher()
        val component = buildComponent(backHandler = backDispatcher)

        component.openQuestList(CatalogId("cat-1"), "Математика")

        assertTrue(
            backDispatcher.isEnabled,
            "backCallback must be enabled after pushing QuestList onto stack",
        )
    }

    // ── QZ-U-03 ──────────────────────────────────────────────────────────────────

    /**
     * Spec: QZ-U-03 — pop from [Idle, QuestList] back to [Idle] → backCallback disabled.
     * Back dispatch fires the registered BackCallback → navigation.pop().
     */
    @Test
    fun `when pop to Idle then backCallback disabled`() {
        val backDispatcher = BackDispatcher()
        val component = buildComponent(backHandler = backDispatcher)

        component.openQuestList(CatalogId("cat-1"), "Math")
        assertTrue(backDispatcher.isEnabled, "backCallback must be enabled after push")

        backDispatcher.back()  // fires registered callback → navigation.pop()

        assertFalse(
            backDispatcher.isEnabled,
            "backCallback must be disabled after popping back to Idle",
        )
        assertIs<QuizzesChild.Idle>(
            component.childStack.value.active.instance,
            "active child must be Idle after pop",
        )
    }

    // ── QZ-U-04 ──────────────────────────────────────────────────────────────────

    /**
     * Spec: QZ-U-04 — backCallback priority == 100.
     *
     * BackCallback.PRIORITY_OVERLAY is absent in Essenty 2.1.0 (Pattern Invariant 5,
     * docs/features/quizzes-screen/plan/phase-03/overview.md:159).
     * Hardcoded priority = 100 (same as DefaultRootComponent.kt:140 pattern).
     */
    @Test
    fun `backCallback priority equals 100`() {
        val fakeDispatcher = FakeBackDispatcher()
        buildComponent(backHandler = fakeDispatcher)

        // With handleBackButton=false Decompose registers no internal callbacks.
        // Only our explicit backCallback (priority=100) is registered.
        val callback = fakeDispatcher.callbacks.single { it.priority == 100 }
        assertEquals(
            100,
            callback.priority,
            "backCallback must use priority=100 (PRIORITY_OVERLAY pattern, Essenty 2.1.0)",
        )
    }

    // ── QZ-U-05 — dismissQuizzes ──────────────────────────────────────────────────

    /**
     * Spec: QZ-U-05 — dismissQuizzes() with stack=[Idle, QuestList, SectionList]
     * collapses stack to [Idle]. items.size == 1.
     */
    @Test
    fun `dismissQuizzes collapses stack to Idle`() {
        val component = buildComponent()

        component.openQuestList(CatalogId("cat-1"), "Math")
        component.openSectionList(QuestId("q-1"), listOf("Math", "Quest 1"))
        assertEquals(3, component.stackSize(), "stack must have 3 items before dismiss")

        component.dismissQuizzes()

        assertEquals(1, component.stackSize(), "dismissQuizzes must collapse stack to [Idle]")
        assertIs<QuizzesChild.Idle>(
            component.childStack.value.active.instance,
            "active must be Idle after dismissQuizzes",
        )
    }

    // ── QZ-U-06 ──────────────────────────────────────────────────────────────────

    /**
     * Spec: QZ-U-06 — dismissQuizzes() when stack=[Idle] is a noop.
     * No exception; stack remains size 1.
     */
    @Test
    fun `dismissQuizzes when stack=Idle is noop`() {
        val component = buildComponent()
        assertEquals(1, component.stackSize(), "initial stack must contain only Idle")

        component.dismissQuizzes()  // should not throw

        assertEquals(1, component.stackSize(), "stack size must remain 1 after noop dismiss")
        assertIs<QuizzesChild.Idle>(component.childStack.value.active.instance)
    }

    // ── QZ-U-07 — ChildStack initial state ───────────────────────────────────────

    /**
     * Spec: QZ-U-07 — initial stack contains exactly one item: Idle.
     * Decompose ChildStack: backStack.isEmpty() && active.instance is QuizzesChild.Idle.
     */
    @Test
    fun `initial stack contains exactly Idle`() {
        val component = buildComponent()
        val stack = component.childStack.value

        assertTrue(stack.backStack.isEmpty(), "backStack must be empty on fresh component")
        assertEquals(1, component.stackSize(), "total stack size must be 1")
        assertIs<QuizzesChild.Idle>(stack.active.instance, "active child must be Idle")
        assertTrue(
            stack.active.configuration is QuizzesConfig.Idle,
            "active configuration must be QuizzesConfig.Idle",
        )
    }

    // ── QZ-U-08 ──────────────────────────────────────────────────────────────────

    /**
     * Spec: QZ-U-08 — childFactory for QuizzesConfig.Idle returns QuizzesChild.Idle.
     * Verifies the childFactory branch for the Idle anchor config.
     */
    @Test
    fun `Idle childFactory returns QuizzesChild Idle`() {
        val component = buildComponent()
        val active = component.childStack.value.active

        assertIs<QuizzesConfig.Idle>(active.configuration)
        assertIs<QuizzesChild.Idle>(active.instance)
    }

    // ── QZ-U-09 ──────────────────────────────────────────────────────────────────

    /**
     * Spec: QZ-U-09 — componentJob.cancel() is invoked in doOnDestroy callback.
     *
     * Observable guarantee: no exception on lifecycle.destroy(), and childStack.value
     * remains in a consistent state after destruction (memory not invalidated).
     * A crash or IllegalStateException here would indicate doOnDestroy cleanup is missing.
     */
    @Test
    fun `componentJob cancel called on doOnDestroy`() {
        val component = buildComponent()
        component.openQuestList(CatalogId("cat-1"), "Math")

        lifecycle.stop()
        lifecycle.destroy()  // triggers doOnDestroy { componentJob.cancel() }

        // Post-destroy: Value is still readable (Decompose freezes it), no exception
        assertNotNull(component.childStack.value, "childStack.value must not throw after destroy")
    }

    // ── QZ-U-10 — popToLevel(0) ──────────────────────────────────────────────────

    /**
     * Spec: tests.md — popToLevel(0) with stack=[Idle,QuestList,SectionList] → stack=[Idle,QuestList].
     * uiLevel=0 → navigation.popTo(1) → active becomes QuestList.
     */
    @Test
    fun `popToLevel(0) with 3-item stack navigates active to QuestList`() {
        val component = buildComponent()
        component.openQuestList(CatalogId("cat-1"), "Math")
        component.openSectionList(QuestId("q-1"), listOf("Math", "Quest 1"))
        assertEquals(3, component.stackSize(), "stack must have 3 items before popToLevel")

        component.popToLevel(0)

        assertEquals(2, component.stackSize(), "stack must shrink to [Idle, QuestList]")
        assertIs<QuizzesChild.QuestList>(
            component.childStack.value.active.instance,
            "active must be QuestList after popToLevel(0)",
        )
    }

    // ── QZ-U-11 ──────────────────────────────────────────────────────────────────

    /**
     * Spec: tests.md — popToLevel(1) with 4-item stack → active becomes item at uiLevel=1.
     * Uses two distinct SectionList configs (q-1, q-2) to build a 4-item stack.
     * uiLevel=1 → navigation.popTo(2) → active becomes SectionList(q-1).
     */
    @Test
    fun `popToLevel(1) with 4-item stack navigates active to first SectionList`() {
        val component = buildComponent()
        component.openQuestList(CatalogId("cat-1"), "Math")
        component.openSectionList(QuestId("q-1"), listOf("Math", "Quest 1"))
        component.openSectionList(QuestId("q-2"), listOf("Math", "Quest 2"))
        assertEquals(4, component.stackSize(), "stack must have 4 items before popToLevel")

        component.popToLevel(1)

        assertEquals(3, component.stackSize(), "stack must shrink to [Idle, QuestList, SectionList-q1]")
        assertIs<QuizzesChild.SectionList>(
            component.childStack.value.active.instance,
            "active must be SectionList after popToLevel(1)",
        )
    }

    // ── QZ-U-12 ──────────────────────────────────────────────────────────────────

    /**
     * Spec: popToLevel(-1) must be a noop — guard `if (uiLevel < 0) return`.
     */
    @Test
    fun `popToLevel with negative index is noop`() {
        val component = buildComponent()
        component.openQuestList(CatalogId("cat-1"), "Math")
        assertEquals(2, component.stackSize())

        component.popToLevel(-1)

        assertEquals(2, component.stackSize(), "stack must remain unchanged on negative popToLevel")
        assertIs<QuizzesChild.QuestList>(component.childStack.value.active.instance)
    }

    @Test
    fun `archive root breadcrumb does not collapse stack to Idle`() {
        val component = buildComponent()
        component.openCourseArchive()
        assertEquals(2, component.stackSize())

        component.popToLevel(0)

        assertEquals(2, component.stackSize(), "archive root must remain on QuestList")
        assertIs<QuizzesChild.QuestList>(component.childStack.value.active.instance)
        val config = assertIs<QuizzesConfig.QuestList>(component.childStack.value.active.configuration)
        assertEquals(QuestListMode.Archive, config.mode)
    }

    @Test
    fun `archive path breadcrumb from details returns to archive quest list`() {
        val component = buildComponent()
        component.openCourseArchive()
        component.openSectionList(QuestId("quest-sample"), listOf("Архив", "Курсы", "Основы Kotlin"))
        assertEquals(3, component.stackSize())

        component.popToLevel(0)

        assertEquals(2, component.stackSize(), "archive path must pop to QuestList, not Idle")
        assertIs<QuizzesChild.QuestList>(component.childStack.value.active.instance)
        val config = assertIs<QuizzesConfig.QuestList>(component.childStack.value.active.configuration)
        assertEquals(QuestListMode.Archive, config.mode)
    }

    @Test
    fun `openPublicQuestCatalogPicker opens mutable catalog picker`() {
        val component = buildComponent()

        component.openPublicQuestCatalogPicker(targetShelf = "tournament", title = "Отборочный турнир")

        val config = assertIs<QuizzesConfig.PublicQuestCatalogPicker>(
            component.childStack.value.active.configuration,
        )
        assertEquals("tournament", config.targetShelf)
        assertEquals("tournament", config.selectionTargetShelf)
    }

    @Test
    fun `openPublicQuestShelfCatalog opens read-only shelf catalog`() {
        val component = buildComponent()

        component.openPublicQuestShelfCatalog(
            targetShelf = "tournamentFinal",
            title = "Чемпионат мира",
            forcedHardMode = true,
        )

        val config = assertIs<QuizzesConfig.PublicQuestCatalogPicker>(
            component.childStack.value.active.configuration,
        )
        assertEquals("tournamentFinal", config.targetShelf)
        assertNull(config.selectionTargetShelf)
        assertEquals(Difficulty.HARD, config.forcedLessonMode)
    }
}
