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
 * Tests for Journey 10: "Retry after partial sync fail".
 *
 * [DefaultMyQuestsComponent] observes QuestRepository via flatMapLatest.
 * The component has no explicit refresh/retry trigger — it reacts to Room Flow emissions.
 * Network failures are simulated by NOT emitting to the store (cache stays unchanged).
 * WorkManager retry results appear as new store emissions.
 *
 * Key invariant: cache-first — whatever is in the Room-backed store is shown immediately,
 * without a loading spinner, regardless of network state.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md Journey 10
 * Plan: docs/features/home-and-my-quests/plan/phase-05/tests.md §4
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PartialFailRetryTest {

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

    // ── Journey 10: empty store → no spinner ──────────────────────────────────

    @Test
    fun `when store is empty then state is empty and not loading`() = runTest {
        val questRepo = FakeQuestRepository()
        val component = buildComponent(questRepo = questRepo)

        val state = component.state.value
        assertTrue(state.quests.isEmpty(), "empty store → empty quests (no spinner)")
        assertFalse(state.isLoading, "must not be stuck in loading on empty cache")
    }

    // ── Journey 10: cached data visible before sync ───────────────────────────

    @Test
    fun `when store has cached data then state shows it without loading`() = runTest {
        val questRepo = FakeQuestRepository()
        questRepo.emit(listOf(buildQuest(id = "q1", authorUid = "user1")))

        val component = buildComponent(questRepo = questRepo)

        val state = component.state.value
        assertEquals(1, state.quests.size, "cached quest must be visible immediately")
        assertFalse(state.isLoading, "must not show spinner for cache hit")
    }

    // ── Journey 10: reactive update when WorkManager retries ─────────────────

    @Test
    fun `when store updated reactively after initial empty then state updates`() = runTest {
        val questRepo = FakeQuestRepository()
        val component = buildComponent(questRepo = questRepo)

        assertTrue(component.state.value.quests.isEmpty(), "initial empty")

        // WorkManager retries → upserts to Room → Room Flow emits → component updates
        questRepo.emit(listOf(
            buildQuest(id = "q1", authorUid = "user1"),
            buildQuest(id = "q2", authorUid = "user1"),
        ))

        val state = component.state.value
        assertEquals(2, state.quests.size, "state must update on retry emission")
        assertFalse(state.isLoading)
    }

    // ── Journey 10: network fail → cache preserved ────────────────────────────

    @Test
    fun `when network fails and store not mutated then cache is preserved`() = runTest {
        val questRepo = FakeQuestRepository()
        questRepo.emit(listOf(buildQuest(id = "q1", authorUid = "user1")))

        val component = buildComponent(questRepo = questRepo)

        assertEquals(1, component.state.value.quests.size, "baseline cache check")

        // Simulate network failure: no new emit → store unchanged → state unchanged
        // (WorkManager will retry; until then cache shown as-is)

        val state = component.state.value
        assertEquals(1, state.quests.size, "cache must be preserved on network fail")
        assertFalse(state.isLoading, "no spinner on preserved cache")
    }
}
