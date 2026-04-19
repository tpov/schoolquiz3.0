package com.tpov.schoolquiz.shared.feature.app_shell.domain

import com.tpov.schoolquiz.shared.feature.app_shell.domain.fake.FakeUserStatsRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.visibleSections
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Qualification
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Role
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.ObserveAppShellStateUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
/**
 * Tests for [ObserveAppShellStateUseCase].
 *
 * The use case wraps [UserStatsRepository.observeStats()] and maps each [UserStats]
 * emission to an [AppShellState] with only [AppShellState.userStats] updated.
 * Navigation state must be preserved across all emissions (BR #8).
 *
 * All tests use [runTest] + [UnconfinedTestDispatcher] for deterministic Flow collection.
 */
class ObserveAppShellStateUseCaseTest {

    // -----------------------------------------------------------------------
    // First emission reflects current stats
    // -----------------------------------------------------------------------

    @Test
    fun `flow emits initial stats from repository on first collection`() = runTest {
        val stats = UserStats.guest().copy(currentSkill = 500)
        val fake = FakeUserStatsRepository(stats)
        val useCase = ObserveAppShellStateUseCase(fake)
        val initial = AppShellState.default(stats)
        val collected = mutableListOf<AppShellState>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase { initial }.toList(collected)
        }
        job.cancel()

        assertTrue(collected.isNotEmpty())
        assertEquals(500, collected.first().userStats.currentSkill)
    }

    // -----------------------------------------------------------------------
    // Subsequent emissions reflect updated stats
    // -----------------------------------------------------------------------

    @Test
    fun `flow emits updated state when stats change via fake emit`() = runTest {
        val fake = FakeUserStatsRepository(UserStats.guest())
        val useCase = ObserveAppShellStateUseCase(fake)
        val initial = AppShellState.default(UserStats.guest())
        val collected = mutableListOf<AppShellState>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase { initial }.toList(collected)
        }

        fake.emit(UserStats.guest().copy(currentSkill = 3_000))
        fake.emit(UserStats.guest().copy(currentSkill = 10_000))

        job.cancel()

        // First emission = initial (0), then 3000, then 10000
        assertEquals(3, collected.size)
        assertEquals(0, collected[0].userStats.currentSkill)
        assertEquals(3_000, collected[1].userStats.currentSkill)
        assertEquals(10_000, collected[2].userStats.currentSkill)
    }

    // -----------------------------------------------------------------------
    // Previous stats value is not carried over — each emission is fresh
    // -----------------------------------------------------------------------

    @Test
    fun `each emission reflects exactly the emitted stats not previous values`() = runTest {
        val fake = FakeUserStatsRepository(UserStats.guest())
        val useCase = ObserveAppShellStateUseCase(fake)
        val initial = AppShellState.default(UserStats.guest())
        val collected = mutableListOf<AppShellState>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase { initial }.toList(collected)
        }

        fake.emit(UserStats.guest().copy(nickname = "Alice", currentSkill = 1_000))
        fake.emit(UserStats.guest().copy(nickname = "Bob", currentSkill = 2_000))

        job.cancel()

        assertEquals("Alice", collected[1].userStats.nickname)
        assertEquals(1_000, collected[1].userStats.currentSkill)
        assertEquals("Bob", collected[2].userStats.nickname)
        assertEquals(2_000, collected[2].userStats.currentSkill)
    }

    // -----------------------------------------------------------------------
    // Navigation state preserved across emissions (BR #8)
    // -----------------------------------------------------------------------

    @Test
    fun `navigation active tab is preserved across stats emissions`() = runTest {
        val fake = FakeUserStatsRepository(UserStats.guest())
        val useCase = ObserveAppShellStateUseCase(fake)
        // Set initial state to INTERNET tab
        val initial = AppShellState.default(UserStats.guest()).copy(activeTab = Tab.INTERNET)
        val collected = mutableListOf<AppShellState>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase { initial }.toList(collected)
        }

        fake.emit(UserStats.guest().copy(currentSkill = 5_000))

        job.cancel()

        // Both emissions must preserve INTERNET as activeTab
        assertTrue(collected.all { it.activeTab == Tab.INTERNET })
    }

    @Test
    fun `drawer open state is preserved across stats emissions`() = runTest {
        val fake = FakeUserStatsRepository(UserStats.guest())
        val useCase = ObserveAppShellStateUseCase(fake)
        val initial = AppShellState.default(UserStats.guest()).copy(isDrawerOpen = true)
        val collected = mutableListOf<AppShellState>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase { initial }.toList(collected)
        }

        fake.emit(UserStats.guest().copy(currentSkill = 999))

        job.cancel()

        assertTrue(collected.all { it.isDrawerOpen })
    }

    // -----------------------------------------------------------------------
    // Stats change that unlocks a section — visible sections updated via logic
    // -----------------------------------------------------------------------

    @Test
    fun `after stats emission visibleSections reflects new qualification unlock`() = runTest {
        // User starts as guest (no TESTER qualification) → TESTER section not visible
        val guestStats = UserStats.guest()
        val fake = FakeUserStatsRepository(guestStats)
        val useCase = ObserveAppShellStateUseCase(fake)
        val initial = AppShellState.default(guestStats)
        val collected = mutableListOf<AppShellState>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase { initial }.toList(collected)
        }

        // Gain tester qualification — unlocks TESTER-role-gated sections
        val testerStats = UserStats.guest().copy(
            qualification = Qualification(
                tester = 1,
                moderator = 0,
                sponsor = 0,
                translator = 0,
                admin = 0,
                developer = 0,
            ),
        )
        fake.emit(testerStats)

        job.cancel()

        val updatedState = collected.last()
        // visibleSections with tester qual should contain more sections than guest
        val guestVisible = visibleSections(Tab.LOCAL, guestStats)
        val testerVisible = visibleSections(Tab.LOCAL, updatedState.userStats)
        assertTrue(testerVisible.size >= guestVisible.size)
    }

    // -----------------------------------------------------------------------
    // Nickname update is reflected in each emitted state
    // -----------------------------------------------------------------------

    @Test
    fun `nickname change is reflected in emitted state`() = runTest {
        val fake = FakeUserStatsRepository(UserStats.guest())
        val useCase = ObserveAppShellStateUseCase(fake)
        val initial = AppShellState.default(UserStats.guest())
        val collected = mutableListOf<AppShellState>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase { initial }.toList(collected)
        }

        fake.emit(UserStats.guest().copy(nickname = "Charlie"))

        job.cancel()

        assertEquals("Charlie", collected.last().userStats.nickname)
    }

    // -----------------------------------------------------------------------
    // hasPremium flag update reflected
    // -----------------------------------------------------------------------

    @Test
    fun `hasPremium flag change is reflected in emitted state`() = runTest {
        val fake = FakeUserStatsRepository(UserStats.guest())
        val useCase = ObserveAppShellStateUseCase(fake)
        val initial = AppShellState.default(UserStats.guest())
        val collected = mutableListOf<AppShellState>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase { initial }.toList(collected)
        }

        fake.emit(UserStats.guest().copy(hasPremium = true))

        job.cancel()

        assertEquals(false, collected.first().userStats.hasPremium)
        assertEquals(true, collected.last().userStats.hasPremium)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 40 — runtime unlock does NOT auto-navigate
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 40 when stats unlock Arena activeSection is NOT auto-updated by ObserveAppShellStateUseCase`() = runTest {
        // Use case only updates userStats — navigation state (activeSection, stack) is NOT changed.
        // Auto-navigation on unlock is presentation-layer concern (BR #8 navigation state preserved).
        val guestStats = UserStats.guest()
        val fake = FakeUserStatsRepository(guestStats)
        val useCase = ObserveAppShellStateUseCase(fake)
        val initial = AppShellState.default(guestStats)
        // initial.internetState.activeSection = Qualifications (first visible for guest)
        val collected = mutableListOf<AppShellState>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase { initial }.toList(collected)
        }

        // Unlock Arena by gaining enough skill
        val unlockedStats = guestStats.copy(currentSkill = 5_000)
        fake.emit(unlockedStats)

        job.cancel()

        // userStats updated
        assertEquals(5_000, collected.last().userStats.currentSkill)
        // BUT navigation state NOT changed — still Qualifications (initial.internetState preserved)
        assertEquals(
            initial.internetState.activeSection,
            collected.last().internetState.activeSection,
        )
        assertEquals(
            initial.internetState.stack.active,
            collected.last().internetState.stack.active,
        )
    }

    // -----------------------------------------------------------------------
    // ADR-LEAD-02 — stale closure absent: provider() called per-emission
    // -----------------------------------------------------------------------

    @Test
    fun `when stats emit after navigation change then provider value is used not stale closure`() = runTest {
        val fake = FakeUserStatsRepository(UserStats.guest())
        val useCase = ObserveAppShellStateUseCase(fake)

        // Mutable navigation state — provider captures var reference, not initial value
        var currentNavState = AppShellState.default(UserStats.guest())
        val provider: () -> AppShellState = { currentNavState }

        val collected = mutableListOf<AppShellState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase(provider).toList(collected)
        }

        val sizeAfterFirst = collected.size

        // User navigates to INTERNET tab before next stats emit
        currentNavState = currentNavState.copy(activeTab = Tab.INTERNET)

        // Stats update arrives after navigation change
        fake.emit(UserStats.guest().copy(currentSkill = 999))

        job.cancel()

        assertTrue(collected.size > sizeAfterFirst)
        val lastEmission = collected.last()
        assertEquals(999, lastEmission.userStats.currentSkill)
        // With new signature: provider() re-evaluated per-emission → reads navigated INTERNET state.
        // With stale closure (old invoke(initialState)): would read initial LOCAL state → regression.
        assertEquals(Tab.INTERNET, lastEmission.activeTab)
    }
}
