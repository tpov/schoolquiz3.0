package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultQuizzesComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeAuthRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestionRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeSectionRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.StubCatalogRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeThemeRepository
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.SetPublicQuestShelfUseCase
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * JVM unit tests for [DefaultQuizzesComponent] StateKeeper save/restore contract.
 *
 * Simulates process death restoration using [StateKeeperDispatcher]:
 *   1. Build component and navigate to a multi-level stack.
 *   2. Save state: stateHolder.save()
 *   3. Restore: new DefaultComponentContext(stateKeeper = StateKeeperDispatcher(savedState))
 *   4. Assert stack is identical to original.
 *
 * NOTE: This is NOT a real process death test.
 * Real process death (adb shell am kill + cold start) requires UIAutomator (excluded from MVP).
 * StateKeeperDispatcher serializes/deserializes via ListSerializer(QuizzesConfig.serializer())
 * which is the same mechanism used by Decompose on real process death.
 *
 * Spec: docs/features/quizzes-screen/0-spec.md — AC#21
 * Design: docs/features/quizzes-screen/04-testing.md §12
 * Phase: 03
 *
 * PD-01..05 — stack restore, titles preserved, active config, backCallback, Idle anchor.
 *
 * No Dispatchers.setMain: DefaultQuizzesComponent has SupervisorJob but no CoroutineScope;
 * all Decompose navigation and Value.subscribe calls are synchronous on the calling thread.
 */
class QuizzesStateKeeperRestoreTest {

    private val lifecycles = mutableListOf<LifecycleRegistry>()

    @After
    fun tearDown() {
        lifecycles.forEach { lc ->
            lc.stop()
            lc.destroy()
        }
        lifecycles.clear()
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private fun buildComponent(
        stateKeeper: StateKeeperDispatcher,
        backDispatcher: BackDispatcher = BackDispatcher(),
    ): DefaultQuizzesComponent {
        val lifecycle = LifecycleRegistry().also { lifecycles += it }
        lifecycle.resume()
        val ctx = DefaultComponentContext(
            lifecycle = lifecycle,
            stateKeeper = stateKeeper,
            backHandler = backDispatcher,
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
            lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _, _ -> error("Not expected") },
            mainContext = Dispatchers.Unconfined,
        )
    }

    /** Total items in Decompose ChildStack (backStack + active). */
    private fun DefaultQuizzesComponent.stackSize(): Int =
        childStack.value.backStack.size + 1

    // ── PD-01 — Stack size preserved ─────────────────────────────────────────

    /**
     * Spec: PD-01 — stack [Idle, QuestList, SectionList] saved and restored.
     * After restoration, childStack has 3 items.
     */
    @Test
    fun `stack Idle QuestList SectionList saved and restored via StateKeeper`() {
        val stateHolder = StateKeeperDispatcher(null)
        val original = buildComponent(stateHolder)

        original.openQuestList(CatalogId("cat-1"), "Math")
        original.openSectionList(QuestId("q-1"), listOf("Math", "Quest 1"))
        assertEquals(3, original.stackSize(), "stack must be 3 before save")

        val savedState = stateHolder.save()

        val stateHolder2 = StateKeeperDispatcher(savedState)
        val restored = buildComponent(stateHolder2)

        assertEquals(3, restored.stackSize(), "restored stack must have 3 items")
    }

    // ── PD-02 — Titles preserved ──────────────────────────────────────────────

    /**
     * Spec: PD-02 — titles from SectionList config are preserved after restoration.
     * Breadcrumb data must survive process death exactly as originally set.
     */
    @Test
    fun `titles preserved after restoration`() {
        val originalTitles = listOf("Math", "Quest 1")
        val stateHolder = StateKeeperDispatcher(null)
        val original = buildComponent(stateHolder)

        original.openQuestList(CatalogId("cat-1"), "Math")
        original.openSectionList(QuestId("q-1"), originalTitles)

        val savedState = stateHolder.save()
        val restored = buildComponent(StateKeeperDispatcher(savedState))

        val activeConfig = restored.childStack.value.active.configuration
        assertIs<QuizzesConfig.SectionList>(activeConfig)
        assertEquals(originalTitles, activeConfig.titles, "titles must be preserved after restore")
    }

    // ── PD-03 — Active config is SectionList ─────────────────────────────────

    /**
     * Spec: PD-03 — restored active config is SectionList, not QuestList or Idle.
     * Verifies that Decompose restores the correct top-of-stack config.
     */
    @Test
    fun `restored active config is SectionList`() {
        val stateHolder = StateKeeperDispatcher(null)
        val original = buildComponent(stateHolder)

        original.openQuestList(CatalogId("cat-1"), "Math")
        original.openSectionList(QuestId("q-1"), listOf("Math", "Quest 1"))

        val savedState = stateHolder.save()
        val restored = buildComponent(StateKeeperDispatcher(savedState))

        assertIs<QuizzesConfig.SectionList>(
            restored.childStack.value.active.configuration,
            "active config must be SectionList after restore",
        )
    }

    // ── PD-04 — BackCallback enabled after restoration ────────────────────────

    /**
     * Spec: PD-04 — backCallback is enabled after restoration because backStack is non-empty.
     * backDispatcher.isEnabled reflects the registered backCallback's enabled state.
     */
    @Test
    fun `backCallback enabled after restoration`() {
        val stateHolder = StateKeeperDispatcher(null)
        val original = buildComponent(stateHolder)
        original.openQuestList(CatalogId("cat-1"), "Math")
        original.openSectionList(QuestId("q-1"), listOf("Math", "Quest 1"))

        val savedState = stateHolder.save()
        val backDispatcher = BackDispatcher()
        val restored = buildComponent(StateKeeperDispatcher(savedState), backDispatcher)

        assertTrue(
            backDispatcher.isEnabled,
            "backCallback must be enabled after restore (backStack is non-empty)",
        )
    }

    // ── PD-05 — Idle anchor at stack[0] ──────────────────────────────────────

    /**
     * Spec: PD-05 — Idle anchor is always at stack[0] after restoration.
     * backStack[0] must be QuizzesConfig.Idle regardless of how deep the stack goes.
     */
    @Test
    fun `Idle anchor always at stack index 0 after restoration`() {
        val stateHolder = StateKeeperDispatcher(null)
        val original = buildComponent(stateHolder)
        original.openQuestList(CatalogId("cat-1"), "Math")
        original.openSectionList(QuestId("q-1"), listOf("Math", "Quest 1"))

        val savedState = stateHolder.save()
        val restored = buildComponent(StateKeeperDispatcher(savedState))

        val backStack = restored.childStack.value.backStack
        assertTrue(backStack.isNotEmpty(), "backStack must have items (Idle + QuestList)")
        assertIs<QuizzesConfig.Idle>(
            backStack.first().configuration,
            "first backStack entry must be QuizzesConfig.Idle anchor",
        )
    }
}
