package com.tpov.schoolquiz.android.feature.quest.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.quest.presentation.DefaultMyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeAuthRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeCatalogRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeNavigator
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeQuestRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.buildCatalog
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.buildQuest
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.ObserveCatalogsUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.ObserveMyQuestsUseCase
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for [DefaultMyQuestsComponent].
 *
 * Tests are traced to spec ACs: AC#45 (guest/auth state), AC#25 (catalog filter),
 * AC#29 (create quest), AC#47 (archived exclusion).
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 * Plan: docs/features/home-and-my-quests/plan/phase-05/tests.md §1
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultMyQuestsComponentTest {

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
        authRepo: FakeAuthRepository = FakeAuthRepository(),
        questRepo: FakeQuestRepository = FakeQuestRepository(),
        catalogRepo: FakeCatalogRepository = FakeCatalogRepository(),
        navigator: FakeNavigator = FakeNavigator(),
        onQuestDrillDown: (QuestDisplayItem) -> Unit = { },
    ) = DefaultMyQuestsComponent(
        componentContext = testCtx(),
        authRepo = authRepo,
        observeMyQuests = ObserveMyQuestsUseCase(questRepo),
        observeCatalogs = ObserveCatalogsUseCase(catalogRepo),
        navigator = navigator,
        onQuestDrillDown = onQuestDrillDown,
        mainContext = testDispatcher,
    )

    // ── AC#45a — guest path: uid=null → isGuest=true, no DB query ─────────────

    @Test
    fun `when uid is null then state is empty and isGuest is true and repo not called`() = runTest {
        val questRepo = FakeQuestRepository()
        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = null),
            questRepo = questRepo,
        )

        val state = component.state.value

        assertTrue(state.quests.isEmpty(), "quests must be empty for guest")
        assertTrue(state.isGuest, "isGuest must be true when uid=null")
        assertEquals(0, questRepo.observeMyQuestsCallCount, "repository must NOT be queried for guest")
    }

    // ── AC#45b — mid-session login: uid changes → switches to quest list ───────

    @Test
    fun `when uid changes from null to user then state switches to quest list`() = runTest {
        val authRepo = FakeAuthRepository(initialUid = null)
        val questRepo = FakeQuestRepository()
        questRepo.emit(listOf(
            buildQuest(id = "q1", authorUid = "user1"),
            buildQuest(id = "q2", authorUid = "user1"),
        ))

        val component = buildComponent(authRepo = authRepo, questRepo = questRepo)

        assertTrue(component.state.value.isGuest, "initial state must be guest")

        authRepo.setUid("user1")

        val state = component.state.value
        assertFalse(state.isGuest, "isGuest must be false after sign-in")
        assertEquals(2, state.quests.size, "must show user quests after sign-in")
    }

    // ── AC#45c — sign-out: uid→null → returns to guest state ─────────────────

    @Test
    fun `when uid changes from user to null then state returns to guest`() = runTest {
        val authRepo = FakeAuthRepository(initialUid = "user1")
        val questRepo = FakeQuestRepository()
        questRepo.emit(listOf(buildQuest(id = "q1", authorUid = "user1")))

        val component = buildComponent(authRepo = authRepo, questRepo = questRepo)

        assertEquals(1, component.state.value.quests.size, "must have quest before sign-out")

        authRepo.setUid(null)

        val state = component.state.value
        assertTrue(state.isGuest, "isGuest must be true after sign-out")
        assertTrue(state.quests.isEmpty(), "quest list must be empty after sign-out")
    }

    // ── AC#25 / Journey 7 — catalog spinner filter ────────────────────────────

    @Test
    fun `when catalog selected then state shows only quests for that catalog`() = runTest {
        val questRepo = FakeQuestRepository()
        questRepo.emit(listOf(
            buildQuest(id = "q1", authorUid = "user1", catalogId = "cat1"),
            buildQuest(id = "q2", authorUid = "user1", catalogId = "cat2"),
        ))

        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = "user1"),
            questRepo = questRepo,
        )

        assertEquals(2, component.state.value.quests.size, "must show all quests initially")

        component.onCatalogSelected(CatalogId("cat1"))

        val filtered = component.state.value.quests
        assertEquals(1, filtered.size, "must filter to catalog1 only")
        assertEquals("q1", filtered.first().id.value, "filtered quest must match catalogId cat1")
    }

    // ── AC#29 / Journey 8 — create quest FAB ─────────────────────────────────

    @Test
    fun `when onCreateQuestClick then navigator receives OpenQuestCreate`() = runTest {
        val navigator = FakeNavigator()
        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = "user1"),
            navigator = navigator,
        )

        component.onCreateQuestClick()

        assertEquals(1, navigator.destinations.size, "exactly one navigation event expected")
        assertEquals(Destination.OpenQuestCreate, navigator.destinations.first())
    }

    // ── AC#47 — archived quests excluded ──────────────────────────────────────

    @Test
    fun `when store has archived quest then archived quest is not in state`() = runTest {
        val questRepo = FakeQuestRepository()
        questRepo.emit(listOf(
            buildQuest(id = "active", authorUid = "user1", archived = false),
            buildQuest(id = "archived", authorUid = "user1", archived = true),
        ))

        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = "user1"),
            questRepo = questRepo,
        )

        val quests = component.state.value.quests
        assertEquals(1, quests.size, "only non-archived quest must appear")
        assertEquals("active", quests.first().id.value, "active quest must be present")
    }

    // ── selectedCatalogId reset on sign-out ───────────────────────────────────

    @Test
    fun `when sign-out after catalog selection then selectedCatalogId is reset`() = runTest {
        val authRepo = FakeAuthRepository(initialUid = "user1")
        val questRepo = FakeQuestRepository()
        questRepo.emit(listOf(buildQuest(id = "q1", authorUid = "user1", catalogId = "cat1")))

        val component = buildComponent(authRepo = authRepo, questRepo = questRepo)
        component.onCatalogSelected(CatalogId("cat1"))

        assertEquals(CatalogId("cat1"), component.state.value.selectedCatalogId)

        authRepo.setUid(null)

        val state = component.state.value
        assertNull(state.selectedCatalogId, "selectedCatalogId must be null after sign-out")
        assertTrue(state.isGuest)
    }

    // ── AC#46 / Journey 11 — guest clicks FAB ────────────────────────────────

    @Test
    fun `when guest clicks FAB then navigator receives OpenQuestCreate`() = runTest {
        val navigator = FakeNavigator()
        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = null),
            navigator = navigator,
        )

        assertTrue(component.state.value.isGuest, "precondition: must be guest")

        component.onCreateQuestClick()

        assertEquals(Destination.OpenQuestCreate, navigator.lastDestination(),
            "guest FAB must navigate to OpenQuestCreate")
    }

    // ── catalog list populated from catalogRepo ───────────────────────────────

    @Test
    fun `when catalogs emitted then state catalogs reflects non-archived catalogs`() = runTest {
        val catalogRepo = FakeCatalogRepository()
        catalogRepo.emit(listOf(
            buildCatalog(id = "cat1", archived = false),
            buildCatalog(id = "cat2", archived = true),
        ))

        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = "user1"),
            catalogRepo = catalogRepo,
        )

        val catalogs = component.state.value.catalogs
        assertEquals(1, catalogs.size, "archived catalogs must not appear in state")
        assertEquals(CatalogId("cat1"), catalogs.first().id)
    }

    // ── MC-U-01 — onQuestClick invokes onQuestDrillDown lambda with quest ──────
    // Spec: docs/features/quizzes-screen/plan/phase-07/tests.md MC-U-01
    // GIVEN onQuestDrillDown lambda recorder; quest = QuestDisplayItem(id="q-1", catalogId="cat-1")
    // WHEN component.onQuestClick(quest)
    // THEN capturedQuest == quest

    @Test
    fun `when onQuestClick called then onQuestDrillDown lambda invoked with quest`() = runTest {
        var capturedQuest: QuestDisplayItem? = null
        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = "user1"),
            onQuestDrillDown = { quest -> capturedQuest = quest },
        )
        val quest = QuestDisplayItem(
            id = QuestId("q-1"),
            catalogId = CatalogId("cat-1"),
            title = "Quest A",
            pictureUrl = null,
            averageRating = null,
        )

        component.onQuestClick(quest)

        assertEquals(quest, capturedQuest, "lambda must receive the exact quest instance")
    }

    // ── MC-U-02 — lambda receives entire QuestDisplayItem including catalogId ──
    // Spec: docs/features/quizzes-screen/plan/phase-07/tests.md MC-U-02
    // GIVEN quest with catalogId = CatalogId("cat-2")
    // WHEN component.onQuestClick(quest)
    // THEN capturedQuest?.catalogId == CatalogId("cat-2")

    @Test
    fun `when onQuestClick called then catalogId is preserved in captured quest`() = runTest {
        var capturedQuest: QuestDisplayItem? = null
        val component = buildComponent(
            authRepo = FakeAuthRepository(initialUid = "user1"),
            onQuestDrillDown = { quest -> capturedQuest = quest },
        )
        val quest = QuestDisplayItem(
            id = QuestId("q-2"),
            catalogId = CatalogId("cat-2"),
            title = "Quest B",
            pictureUrl = null,
            averageRating = null,
        )

        component.onQuestClick(quest)

        assertEquals(CatalogId("cat-2"), capturedQuest?.catalogId,
            "lambda must preserve catalogId for breadcrumb resolution in DefaultRootComponent")
    }
}
