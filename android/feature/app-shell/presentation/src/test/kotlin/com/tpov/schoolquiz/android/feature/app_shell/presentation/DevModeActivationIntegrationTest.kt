@file:Suppress("MaxLineLength")

package com.tpov.schoolquiz.android.feature.app_shell.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.fake.FakeSyncScheduler
import com.tpov.schoolquiz.android.feature.app_shell.presentation.fake.FakeUserStatsRepository
import com.tpov.schoolquiz.android.feature.app_shell.presentation.fake.StubHomeQuestsComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.fake.StubMyQuestsComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.fake.StubQuizzesComponent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Qualification
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.InitializeAppShellUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.NavigateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.ObserveAppShellStateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.OnTabRetapUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for dev mode activation via 10-tap sequence in [DefaultRootComponent].
 *
 * Covers DM-phase7-01..04 from docs/features/menu-refactor/plan/phase-07/tests.md.
 * Spec: 0-spec-dev-mode.md § Dev Mode Activation / State Matrix.
 *
 * NOTE: These tests reference the Phase-07 expected constructor signature for
 * [DefaultRootComponent] (adds `userStatsRepository` and sync scheduling).
 * They will compile once backend-dev completes task 1 in phase-07/backend.md.
 *
 * DM-phase7-02 spec discrepancy: tests.md says "× 1" tap triggers AlreadyDev, but
 * domain logic (registerTap) only checks developer level when count==9 (10th tap).
 * Test written as "× 10" per correct domain contract. See Open Questions in RESULT report.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DevModeActivationIntegrationTest {

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
        fakeRepo: FakeUserStatsRepository = FakeUserStatsRepository(),
    ) = DefaultRootComponent(
        componentContext = testCtx(),
        initUseCase = InitializeAppShellUseCase(fakeRepo),
        navigateUseCase = NavigateUseCase(),
        observeUseCase = ObserveAppShellStateUseCase(fakeRepo),
        retapUseCase = OnTabRetapUseCase(),
        userStatsRepository = fakeRepo,
        syncScheduler = FakeSyncScheduler(),
        myQuestsFactory = { _, _, _ -> StubMyQuestsComponent },
        homeQuestsFactory = { _, _ -> StubHomeQuestsComponent },
        quizzesFactory = { _ -> StubQuizzesComponent },
    )

    // -----------------------------------------------------------------------
    // DM-phase7-01
    // GIVEN DefaultRootComponent with FakeUserStatsRepository (developer=0)
    // WHEN onVersionTap(nowMillis) × 10 with taps < 500ms apart
    // THEN setLocalDeveloperLevelCalls==1 + lastSetDeveloperLevel==100 + DevModeActivated emitted
    // -----------------------------------------------------------------------
    @Test
    fun `DM-phase7-01 ten rapid taps activate dev mode and emit DevModeActivated`() = runTest(testDispatcher) {
        val fakeRepo = FakeUserStatsRepository()
        val component = buildComponent(fakeRepo)
        val now = 1_000L
        val collectedEvents = mutableListOf<RootEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            component.events.toList(collectedEvents)
        }

        repeat(10) { i -> component.onVersionTap(now + i * 50L) }

        job.cancel()
        assertEquals(1, fakeRepo.setLocalDeveloperLevelCalls)
        assertEquals(100, fakeRepo.lastSetDeveloperLevel)
        assertTrue(collectedEvents.any { it == RootEvent.DevModeActivated })
    }

    // -----------------------------------------------------------------------
    // DM-phase7-02
    // GIVEN DefaultRootComponent with FakeUserStatsRepository(developer=100) (already dev)
    // WHEN onVersionTap() × 10 (count reaches 9, AlreadyDev guard fires)
    // THEN setLocalDeveloperLevelCalls==0 + DevModeAlreadyActive emitted (no repository write)
    // -----------------------------------------------------------------------
    @Test
    fun `DM-phase7-02 when developer already 100 ten taps emit DevModeAlreadyActive without write`() = runTest(testDispatcher) {
        val devStats = UserStats.guest().copy(
            qualification = Qualification.zero().copy(developer = 100),
        )
        val fakeRepo = FakeUserStatsRepository(initialStats = devStats)
        val component = buildComponent(fakeRepo)
        val now = 1_000L
        val collectedEvents = mutableListOf<RootEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            component.events.toList(collectedEvents)
        }

        repeat(10) { i -> component.onVersionTap(now + i * 50L) }

        job.cancel()
        assertEquals(0, fakeRepo.setLocalDeveloperLevelCalls)
        assertTrue(collectedEvents.any { it == RootEvent.DevModeAlreadyActive })
    }

    // -----------------------------------------------------------------------
    // DM-phase7-03
    // GIVEN DefaultRootComponent (developer=0)
    // WHEN onVersionTap() × 10 → Activated (state reset to TapProgress.initial)
    //      then onVersionTap() × 10 again (second sequence, still developer=0 in fake)
    // THEN setLocalDeveloperLevelCalls==2 — second sequence also activates (proves state reset)
    // -----------------------------------------------------------------------
    @Test
    fun `DM-phase7-03 tap progress resets after Activated enabling a second activation sequence`() = runTest(testDispatcher) {
        val fakeRepo = FakeUserStatsRepository()
        val component = buildComponent(fakeRepo)
        val now = 1_000L

        // First 10-tap sequence → Activated, _tapProgress resets to TapProgress(0, null)
        repeat(10) { i -> component.onVersionTap(now + i * 50L) }
        assertEquals(1, fakeRepo.setLocalDeveloperLevelCalls)

        // Second 10-tap sequence: fake doesn't update stats → developer still 0 → Activated again
        // First tap of second sequence: progress=(0,null) → isFirstTap=true → NoChange(count=1)
        val secondSequenceStart = now + 2_000L
        repeat(10) { i -> component.onVersionTap(secondSequenceStart + i * 50L) }

        assertEquals(2, fakeRepo.setLocalDeveloperLevelCalls)
    }

    // -----------------------------------------------------------------------
    // DM-phase7-04
    // GIVEN DefaultRootComponent (developer=0)
    // WHEN 9 fast taps (count→9), then 1 slow tap >500ms (Reset→count=1), then 9 fast taps (count→9)
    // THEN still NOT activated (total 19 taps with reset = only 9 from reset point)
    //      ONE more tap → Activated
    // -----------------------------------------------------------------------
    @Test
    fun `DM-phase7-04 tap after 500ms timeout resets progress count causing delayed activation`() = runTest(testDispatcher) {
        val fakeRepo = FakeUserStatsRepository()
        val component = buildComponent(fakeRepo)
        val now = 1_000L

        // 9 fast taps → count reaches 9 (not yet activated; count<9 → NoChange for 0..8, count=9 stops)
        repeat(9) { i -> component.onVersionTap(now + i * 50L) }
        assertEquals(0, fakeRepo.setLocalDeveloperLevelCalls)

        // Slow tap: elapsed = 1000ms > 500ms threshold → Reset(count=1)
        val lastFastTapTime = now + 8 * 50L
        val slowTapTime = lastFastTapTime + 1_000L
        component.onVersionTap(slowTapTime)
        assertEquals(0, fakeRepo.setLocalDeveloperLevelCalls)

        // 8 fast taps from reset point: count goes 1→2→...→9
        repeat(8) { i -> component.onVersionTap(slowTapTime + (i + 1) * 50L) }
        assertEquals(0, fakeRepo.setLocalDeveloperLevelCalls)

        // Final tap: count=9 → Activated
        component.onVersionTap(slowTapTime + 9 * 50L)
        assertEquals(1, fakeRepo.setLocalDeveloperLevelCalls)
        assertEquals(100, fakeRepo.lastSetDeveloperLevel)
    }
}
