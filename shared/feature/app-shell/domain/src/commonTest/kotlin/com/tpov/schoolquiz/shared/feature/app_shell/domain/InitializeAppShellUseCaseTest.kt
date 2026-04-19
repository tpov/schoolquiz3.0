package com.tpov.schoolquiz.shared.feature.app_shell.domain

import com.tpov.schoolquiz.shared.feature.app_shell.domain.fake.FakeUserStatsRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Qualification
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.InitializeAppShellUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * Tests for [InitializeAppShellUseCase].
 *
 * Coverage:
 * - Journey 1 (cold start) through use case
 * - Domain Test Scenario 1 via use case
 * - Stats are correctly embedded in the initial state
 */
class InitializeAppShellUseCaseTest {

    // -----------------------------------------------------------------------
    // Cold start — guest stats
    // -----------------------------------------------------------------------

    @Test
    fun `initialize with guest stats produces default LOCAL active tab`() = runTest {
        val fake = FakeUserStatsRepository(UserStats.guest())
        val useCase = InitializeAppShellUseCase(fake)

        val state = useCase()

        assertEquals(Tab.LOCAL, state.activeTab)
    }

    @Test
    fun `initialize with guest stats produces MyQuests as local active section`() = runTest {
        val fake = FakeUserStatsRepository(UserStats.guest())
        val useCase = InitializeAppShellUseCase(fake)

        val state = useCase()

        assertEquals(DrawerSection.LocalSection.MyQuests, state.localState.activeSection)
    }

    @Test
    fun `initialize with guest stats produces empty backStack for local tab`() = runTest {
        val fake = FakeUserStatsRepository(UserStats.guest())
        val useCase = InitializeAppShellUseCase(fake)

        val state = useCase()

        assertFalse(state.isDrawerOpen)
        assertEquals(emptyList(), state.localState.stack.backStack)
    }

    @Test
    fun `initialize embeds the stats from repository into the state`() = runTest {
        val customStats = UserStats.guest().copy(currentSkill = 5_000)
        val fake = FakeUserStatsRepository(customStats)
        val useCase = InitializeAppShellUseCase(fake)

        val state = useCase()

        assertEquals(5_000, state.userStats.currentSkill)
    }

    // -----------------------------------------------------------------------
    // Stats with elevated skill — no change to navigation defaults
    // -----------------------------------------------------------------------

    @Test
    fun `initialize with elevated skill still defaults to LOCAL tab`() = runTest {
        val statsWithSkill = UserStats.guest().copy(currentSkill = 50_000)
        val fake = FakeUserStatsRepository(statsWithSkill)
        val useCase = InitializeAppShellUseCase(fake)

        val state = useCase()

        assertEquals(Tab.LOCAL, state.activeTab)
        assertNull(state.shopState.activeSection)
    }

    // -----------------------------------------------------------------------
    // Journey 1 verification — scenario 1 through use case
    // -----------------------------------------------------------------------

    @Test
    fun `journey 1 cold start via use case produces scenario 1 state`() = runTest {
        val fake = FakeUserStatsRepository(UserStats.guest())
        val useCase = InitializeAppShellUseCase(fake)

        val state = useCase()

        // Scenario 1: activeTab=LOCAL, localSection=MyQuests, stack root, drawer closed
        assertEquals(Tab.LOCAL, state.activeTab)
        assertEquals(DrawerSection.LocalSection.MyQuests, state.localState.activeSection)
        assertFalse(state.isDrawerOpen)
        assertEquals(emptyList(), state.localState.stack.backStack)
    }

    // -----------------------------------------------------------------------
    // Repository is called exactly once (currentStats)
    // -----------------------------------------------------------------------

    @Test
    fun `initialize reads currentStats from repository`() = runTest {
        val uniqueStats = UserStats.guest().copy(
            nickname = "TestUser",
            qualification = Qualification(
                tester = 42,
                moderator = 0,
                sponsor = 0,
                translator = 0,
                admin = 0,
                developer = 0,
            ),
        )
        val fake = FakeUserStatsRepository(uniqueStats)
        val useCase = InitializeAppShellUseCase(fake)

        val state = useCase()

        assertEquals("TestUser", state.userStats.nickname)
        assertEquals(42, state.userStats.qualification.tester)
    }
}
