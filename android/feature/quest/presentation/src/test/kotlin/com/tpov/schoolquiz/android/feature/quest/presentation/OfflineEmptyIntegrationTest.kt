package com.tpov.schoolquiz.android.feature.quest.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quest.presentation.DefaultMyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeAuthRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeCatalogRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeNavigator
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeQuestAuthoringRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeQuestRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.buildQuest
import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.ObserveCatalogsUseCase
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.ObserveMyQuestsUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveQuestDraftSummariesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for Journey 9: "Offline + empty cache".
 *
 * Device is offline; local DB may be empty or contain cached data.
 * The component observes Room Flow directly — no network calls happen in the component layer.
 * Network sync is WorkManager's responsibility (data layer).
 *
 * Key invariant: component never shows infinite loading spinner.
 * It shows whatever is in the store (empty state or cached data).
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md Journey 9
 * Plan: docs/features/home-and-my-quests/plan/phase-05/tests.md §5
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineEmptyIntegrationTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private lateinit var lifecycle: LifecycleRegistry

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        if (::lifecycle.isInitialized) {
            lifecycle.stop()
            lifecycle.destroy()
        }
        Dispatchers.resetMain()
    }

    private fun testCtx(): DefaultComponentContext {
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
        return DefaultComponentContext(lifecycle)
    }

    private fun buildComponent(
        questRepo: FakeQuestRepository,
        authRepo: FakeAuthRepository = FakeAuthRepository(initialUid = "user1"),
    ) = DefaultMyQuestsComponent(
        componentContext = testCtx(),
        authRepo = authRepo,
        observeMyQuests = ObserveMyQuestsUseCase(questRepo),
        observeDraftSummaries = ObserveQuestDraftSummariesUseCase(FakeQuestAuthoringRepository()),
        observeCatalogs = ObserveCatalogsUseCase(FakeCatalogRepository()),
        navigator = FakeNavigator(),
        mainContext = testDispatcher,
    )

    // ── Journey 9: offline + empty cache → empty state, no spinner ────────────

    @Test
    fun `when offline and cache empty then state is empty and not loading`() = runTest {
        val questRepo = FakeQuestRepository()
        // No emit → empty store (simulates offline + empty DB)
        val component = buildComponent(questRepo = questRepo)

        val state = component.state.value
        assertTrue(state.quests.isEmpty(), "offline + empty cache → empty quest list")
        assertFalse(state.isLoading, "must not show infinite loading spinner")
    }

    // ── Journey 9 variant: offline + cached data → show cache ────────────────

    @Test
    fun `when offline but cache has data then cached data is shown`() = runTest {
        val questRepo = FakeQuestRepository()
        questRepo.emit(listOf(
            buildQuest(id = "q1", authorUid = "user1"),
            buildQuest(id = "q2", authorUid = "user1"),
        ))
        // nextRefreshResult failure simulates network unavailable
        questRepo.nextRefreshResult = Result.failure(RuntimeException("Network unavailable"))

        val component = buildComponent(questRepo = questRepo)

        val state = component.state.value
        assertEquals(2, state.quests.size, "cached quests must be shown despite offline state")
        assertFalse(state.isLoading, "no spinner when cache contains data")
    }

    // ── Component never triggers refreshFromRemote on its own ─────────────────

    @Test
    fun `when component created then it does not trigger refreshFromRemote`() = runTest {
        val questRepo = FakeQuestRepository()
        buildComponent(questRepo = questRepo)

        assertEquals(0, questRepo.refreshFromRemoteCallCount,
            "component must not initiate network refresh (that is WorkManager's job)")
    }
}
