@file:Suppress("MaxLineLength")

package com.tpov.schoolquiz.android.feature.quest.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quest.presentation.DefaultHomeQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeAuthRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeCatalogRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeLessonRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeQuestRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeSectionRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeThemeRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.buildCatalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.model.QuestType
import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.ObserveCatalogsUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxOpening
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxReward
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftBoxRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.OpenGiftBoxUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.ObserveCurrentProfileUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Integration tests for [DefaultHomeQuestsComponent].
 *
 * Tests are traced to AC#21 (catalog list in home state) and AC#22 (archived excluded).
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 * Plan: docs/features/home-and-my-quests/plan/phase-05/tests.md §2
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultHomeQuestsComponentTest {

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
        catalogRepo: FakeCatalogRepository = FakeCatalogRepository(),
        profileRepo: FakeProfileRepository = FakeProfileRepository(),
        giftBoxRepo: FakeGiftBoxRepository = FakeGiftBoxRepository(),
        onCatalogDrillDown: (CatalogId, String) -> Unit = { _, _ -> },
        onResumeLesson: (LessonId) -> Unit = {},
    ) = DefaultHomeQuestsComponent(
        componentContext = testCtx(),
        observeCatalogs = ObserveCatalogsUseCase(catalogRepo),
        observeProfile = ObserveCurrentProfileUseCase(profileRepo),
        openGiftBoxUseCase = OpenGiftBoxUseCase(giftBoxRepo),
        onCatalogDrillDown = onCatalogDrillDown,
        onResumeLesson = onResumeLesson,
        attemptRepository = FakeLessonAttemptRepository(),
        lessonRepository = FakeLessonRepository(),
        themeRepository = FakeThemeRepository(),
        sectionRepository = FakeSectionRepository(),
        questRepository = FakeQuestRepository(),
        authRepository = FakeAuthRepository(),
        mainContext = testDispatcher,
    )

    // ── AC#21 — catalogs from repository appear in state ─────────────────────

    @Test
    fun `when observeCatalogs emits then state catalogs updated`() = runTest {
        val catalogRepo = FakeCatalogRepository()
        catalogRepo.emit(listOf(
            buildCatalog(id = "cat1", name = "Catalog 1"),
            buildCatalog(id = "cat2", name = "Catalog 2"),
        ))

        val component = buildComponent(catalogRepo = catalogRepo)

        val catalogs = component.state.value.catalogs
        assertEquals(2, catalogs.size, "both catalogs must appear in state")
        assertTrue(catalogs.any { it.id == CatalogId("cat1") })
        assertTrue(catalogs.any { it.id == CatalogId("cat2") })
    }

    // ── AC#22 — archived catalog not in state ────────────────────────────────

    @Test
    fun `when catalog is archived then it is not in state`() = runTest {
        val catalogRepo = FakeCatalogRepository()
        catalogRepo.emit(listOf(
            buildCatalog(id = "active", archived = false),
            buildCatalog(id = "archived", archived = true),
        ))

        val component = buildComponent(catalogRepo = catalogRepo)

        val catalogs = component.state.value.catalogs
        assertEquals(1, catalogs.size, "archived catalog must be excluded")
        assertEquals(CatalogId("active"), catalogs.first().id)
    }

    @Test
    fun `course catalogs are not shown in home`() = runTest {
        val catalogRepo = FakeCatalogRepository()
        catalogRepo.emit(listOf(
            buildCatalog(id = "school", name = "Школа"),
            buildCatalog(id = "courses", name = "Курсы", questType = QuestType.COURSE),
        ))

        val component = buildComponent(catalogRepo = catalogRepo)

        val catalogs = component.state.value.catalogs
        assertEquals(1, catalogs.size, "courses catalog is opened from archive, not home")
        assertEquals(CatalogId("school"), catalogs.first().id)
    }

    // ── Late catalog emission updates state reactively ────────────────────────

    @Test
    fun `when catalog emitted after construction then state updates`() = runTest {
        val catalogRepo = FakeCatalogRepository()
        val component = buildComponent(catalogRepo = catalogRepo)

        assertTrue(component.state.value.catalogs.isEmpty(), "initial state must be empty")

        catalogRepo.emit(listOf(buildCatalog(id = "cat1")))

        assertEquals(1, component.state.value.catalogs.size, "state must update on late emission")
        assertEquals(CatalogId("cat1"), component.state.value.catalogs.first().id)
    }

    // ── Empty catalog list → not stuck in loading ─────────────────────────────

    @Test
    fun `when catalog list is empty then state is empty and not loading`() = runTest {
        val component = buildComponent(catalogRepo = FakeCatalogRepository())

        val state = component.state.value
        assertTrue(state.catalogs.isEmpty(), "empty store → empty catalog list")
        assertFalse(state.isLoading, "must not be stuck in loading state on empty data")
    }

    // ── HC-U-01 — onCatalogClick invokes onCatalogDrillDown lambda ─────────────
    // Spec: docs/features/quizzes-screen/plan/phase-07/tests.md HC-U-01
    // GIVEN onCatalogClick(CatalogId("cat-1"), "Mathematics")
    // THEN lambda invoked with (CatalogId("cat-1"), "Mathematics")

    @Test
    fun `when onCatalogClick called then onCatalogDrillDown lambda invoked with correct catalogId and catalogName`() = runTest {
        var capturedId: CatalogId? = null
        var capturedName: String? = null

        val component = buildComponent(
            onCatalogDrillDown = { id, name ->
                capturedId = id
                capturedName = name
            },
        )

        component.onCatalogClick(CatalogId("cat-1"), "Mathematics")

        assertEquals(CatalogId("cat-1"), capturedId, "lambda must receive the clicked catalog id")
        assertEquals("Mathematics", capturedName, "lambda must receive the name passed from UI")
    }

    // ── HC-U-02 — blank name is forwarded as-is ────────────────────────────────
    // Spec: docs/features/quizzes-screen/plan/phase-07/tests.md HC-U-02
    // GIVEN onCatalogClick with blank name
    // THEN lambda invoked; capturedName == "" (screen resolves blank to localized fallback)

    @Test
    fun `when onCatalogClick called with blank name then catalogName is forwarded unchanged`() = runTest {
        var capturedId: CatalogId? = null
        var capturedName: String? = null

        val component = buildComponent(
            onCatalogDrillDown = { id, name ->
                capturedId = id
                capturedName = name
            },
        )

        component.onCatalogClick(CatalogId("cat-1"), "")

        assertEquals(CatalogId("cat-1"), capturedId, "lambda must receive the clicked id even if name is blank")
        assertEquals("", capturedName, "blank name must be forwarded; the screen resolves the fallback label")
    }

    // ── HC-U-03 — lambda is only side-effect, no double invocation ────────────
    // Spec: docs/features/quizzes-screen/plan/phase-07/tests.md HC-U-03
    // GIVEN component
    // WHEN onCatalogClick called once
    // THEN lambda invoked exactly once

    @Test
    fun `when onCatalogClick called once then lambda invoked exactly once`() = runTest {
        var invocationCount = 0

        val component = buildComponent(
            onCatalogDrillDown = { _, _ -> invocationCount++ },
        )

        component.onCatalogClick(CatalogId("cat-1"), "Mathematics")

        assertEquals(1, invocationCount, "onCatalogDrillDown must be invoked exactly once per click")
    }

    @Test
    fun `when profile has boxes then gift box count appears in state`() = runTest {
        val profileRepo = FakeProfileRepository(UserProfile.offline().copy(boxCount = 2, boxStreakDays = 7))
        val component = buildComponent(profileRepo = profileRepo)

        assertEquals(2, component.state.value.giftBoxCount)
        assertEquals(7, component.state.value.giftBoxStreakDays)
    }

    @Test
    fun `when gift box opened then state shows reward and remaining count`() = runTest {
        val profileRepo = FakeProfileRepository(UserProfile.offline().copy(boxCount = 1))
        val giftBoxRepo =
            FakeGiftBoxRepository(
                Result.success(
                    GiftBoxOpening(
                        reward = GiftBoxReward.Nolics(500),
                        profileSynced = true,
                        remainingBoxCount = 0,
                    ),
                ),
            )
        val component = buildComponent(profileRepo = profileRepo, giftBoxRepo = giftBoxRepo)

        component.onGiftBoxFabClick()

        val opening = component.state.value.giftBoxOpening as HomeGiftBoxOpeningState.Opened
        assertEquals(GiftBoxReward.Nolics(500), opening.reward)
        assertEquals(0, opening.remainingBoxCount)
    }
}

private class FakeProfileRepository(
    initialProfile: UserProfile = UserProfile.offline(),
) : ProfileRepository {
    private val state = MutableStateFlow(initialProfile)

    override fun observeCurrentProfile(): Flow<UserProfile> = state

    override suspend fun currentProfile(): UserProfile = state.value

    override suspend fun ensureCurrentProfile(): Result<UserProfile> = Result.success(state.value)

    override suspend fun updateNickname(nickname: String): Result<UserProfile> =
        Result.success(state.value.copy(nickname = nickname))
}

private class FakeGiftBoxRepository(
    private val result: Result<GiftBoxOpening> =
        Result.failure(IllegalStateException("No gift boxes available")),
) : GiftBoxRepository {
    override suspend fun openGiftBox(): Result<GiftBoxOpening> = result
}
