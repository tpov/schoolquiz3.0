package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.arkivanov.essenty.statekeeper.SerializableContainer
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaCatalogNotice
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaLessonCatalogComponentFactory
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultArenaLessonCatalogComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultQuizzesComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuestListMode
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeAuthRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeEconomyRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonUnlockQueue
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestionRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeSectionRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeThemeRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.StubCatalogRepository
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.model.QuestType
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaCatalogRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.RetirePublicQuestUseCase
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.SetPublicQuestShelfUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.builtins.ListSerializer
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
 * PD-01..05 — stack restore, breadcrumbs preserved, active config, backCallback, Idle anchor.
 *
 * No Dispatchers.setMain: DefaultQuizzesComponent has SupervisorJob but no CoroutineScope;
 * all Decompose navigation and Value.subscribe calls are synchronous on the calling thread.
 */
class QuizzesStateKeeperRestoreTest {

    private val lifecycles = mutableListOf<LifecycleRegistry>()
    private var arenaReads = 0

    @Test
    fun `new Arena route restores without cards cursor or automatic read`() {
        val original = buildComponent(StateKeeperDispatcher(null))
        original.openCourseArena()
        val restored = buildComponent(savedStack(listOf(QuizzesConfig.Idle, QuizzesConfig.ArenaLessons)))
        val child = assertIs<QuizzesChild.ArenaLessons>(restored.childStack.value.active.instance)
        assertEquals(ArenaCatalogNotice.NOT_LOADED, child.component.state.value.notice)
        assertEquals(null, child.component.state.value.totalLessons)
        assertTrue(child.component.state.value.lessons.isEmpty())
        assertEquals(0, arenaReads)
    }

    @Test
    fun `exact old course Arena and its known drill-down tail restore only the preview`() {
        val root = listOf(BreadcrumbRoot.Arena, BreadcrumbRoot.Courses)
        val old =
            listOf(
                QuizzesConfig.Idle,
                legacyArena(),
                QuizzesConfig.SectionList(
                    "course",
                    root + BreadcrumbRoot.Dynamic("Course"),
                    questType = QuestType.COURSE,
                ),
                QuizzesConfig.ThemeList(
                    "section",
                    root + BreadcrumbRoot.Dynamic("Section"),
                    questType = QuestType.COURSE,
                ),
                QuizzesConfig.LessonList("theme", root + BreadcrumbRoot.Dynamic("Theme"), questType = QuestType.COURSE),
                QuizzesConfig.LessonRunner(
                    "lesson",
                    com.tpov.schoolquiz.shared.core.question_schema.Difficulty.EASY,
                    root + BreadcrumbRoot.Dynamic("Lesson"),
                ),
            )
        val restored = buildComponent(savedStack(old))
        assertIs<QuizzesChild.ArenaLessons>(restored.childStack.value.active.instance)
        assertEquals(2, restored.stackSize())
        assertIs<QuizzesConfig.Idle>(restored.childStack.value.backStack.single().configuration)
        assertEquals(0, arenaReads)
    }

    @Test
    fun `unrecognized tail under exact legacy Arena falls to safe Idle`() {
        val unknown =
            QuizzesConfig.PublicQuestCatalogPicker(
                targetShelf = "home",
                breadcrumbs = listOf(BreadcrumbRoot.HomeQuests, BreadcrumbRoot.Catalogs),
            )
        val restored = buildComponent(savedStack(listOf(QuizzesConfig.Idle, legacyArena(), unknown)))
        assertIs<QuizzesChild.Idle>(restored.childStack.value.active.instance)
        assertEquals(1, restored.stackSize())
        assertEquals(0, arenaReads)
    }

    @Test
    fun `curator selection is not mistaken for the old player Arena entry`() {
        val curator = legacyArena().copy(selectionTargetShelf = "home")
        val restored = buildComponent(savedStack(listOf(QuizzesConfig.Idle, curator)))
        assertEquals(curator, restored.childStack.value.active.configuration)
        assertIs<QuizzesChild.QuestList>(restored.childStack.value.active.instance)
        assertEquals(0, arenaReads)
    }

    @Test
    fun `other Arena catalog and course archive restoration remain untouched`() {
        val other = legacyArena().copy(catalogId = "school", questType = QuestType.REGULAR)
        val archive =
            legacyArena().copy(
                shelf = "archive",
                mode = QuestListMode.Archive,
                breadcrumbs = listOf(BreadcrumbRoot.Archive, BreadcrumbRoot.Courses),
            )
        listOf(other, archive).forEach { config ->
            val restored = buildComponent(savedStack(listOf(QuizzesConfig.Idle, config)))
            assertEquals(config, restored.childStack.value.active.configuration)
            assertIs<QuizzesChild.QuestList>(restored.childStack.value.active.instance)
        }
        assertEquals(0, arenaReads)
    }

    private fun legacyArena() =
        QuizzesConfig.QuestList(
            catalogId = "courses",
            breadcrumbs = listOf(BreadcrumbRoot.Arena, BreadcrumbRoot.Courses),
            shelf = "arena",
            mode = QuestListMode.Arena,
            questType = QuestType.COURSE,
        )

    /** Produce the old router's real StateKeeper envelope, without constructing a playable child. */
    private fun savedStack(stack: List<QuizzesConfig>): StateKeeperDispatcher {
        val stateKeeper = StateKeeperDispatcher(null)
        val lifecycle = LifecycleRegistry().also { lifecycles += it }
        lifecycle.resume()
        val context = DefaultComponentContext(lifecycle, stateKeeper = stateKeeper)
        context.childStack(
            source = StackNavigation<QuizzesConfig>(),
            initialStack = { stack },
            saveStack = { SerializableContainer(value = it, strategy = ListSerializer(QuizzesConfig.serializer())) },
            restoreStack = { it.consume(ListSerializer(QuizzesConfig.serializer())) },
            handleBackButton = false,
            childFactory = { config, _ -> config },
        )
        return StateKeeperDispatcher(stateKeeper.save())
    }


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
            economyRepository = FakeEconomyRepository(),
            queueLessonUnlock = FakeLessonUnlockQueue(),
            questionRepository = FakeQuestionRepository(),
            catalogRepository = StubCatalogRepository(),
            setPublicQuestShelf = SetPublicQuestShelfUseCase(questRepository),
            retirePublicQuest = RetirePublicQuestUseCase(questRepository),
            lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _, _ -> error("Not expected") },
            arenaCatalogFactory =
                ArenaLessonCatalogComponentFactory { child ->
                    DefaultArenaLessonCatalogComponent(
                        child,
                        ArenaCatalogRepository {
                            arenaReads++
                            Result.failure(IllegalStateException("No read expected during restore"))
                        },
                        ArenaSessionProvider { ArenaSessionStamp("test-user", 1) },
                        FakeAuthRepository(),
                        Dispatchers.Unconfined,
                    )
                },
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
        original.openSectionList(
            QuestId("q-1"),
            listOf(BreadcrumbRoot.Catalogs, BreadcrumbRoot.Dynamic("Quest 1")),
        )
        assertEquals(3, original.stackSize(), "stack must be 3 before save")

        val savedState = stateHolder.save()

        val stateHolder2 = StateKeeperDispatcher(savedState)
        val restored = buildComponent(stateHolder2)

        assertEquals(3, restored.stackSize(), "restored stack must have 3 items")
    }

    // ── PD-02 — Breadcrumbs preserved ─────────────────────────────────────────

    /**
     * Spec: PD-02 — breadcrumbs from SectionList config are preserved after restoration.
     * Breadcrumb data must survive process death exactly as originally set.
     */
    @Test
    fun `breadcrumbs preserved after restoration`() {
        val originalBreadcrumbs =
            listOf(BreadcrumbRoot.Catalogs, BreadcrumbRoot.Dynamic("Quest 1"))
        val stateHolder = StateKeeperDispatcher(null)
        val original = buildComponent(stateHolder)

        original.openQuestList(CatalogId("cat-1"), "Math")
        original.openSectionList(QuestId("q-1"), originalBreadcrumbs)

        val savedState = stateHolder.save()
        val restored = buildComponent(StateKeeperDispatcher(savedState))

        val activeConfig = restored.childStack.value.active.configuration
        assertIs<QuizzesConfig.SectionList>(activeConfig)
        assertEquals(originalBreadcrumbs, activeConfig.breadcrumbs, "breadcrumbs must be preserved after restore")
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
        original.openSectionList(
            QuestId("q-1"),
            listOf(BreadcrumbRoot.Catalogs, BreadcrumbRoot.Dynamic("Quest 1")),
        )

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
        original.openSectionList(
            QuestId("q-1"),
            listOf(BreadcrumbRoot.Catalogs, BreadcrumbRoot.Dynamic("Quest 1")),
        )

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
        original.openSectionList(
            QuestId("q-1"),
            listOf(BreadcrumbRoot.Catalogs, BreadcrumbRoot.Dynamic("Quest 1")),
        )

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
