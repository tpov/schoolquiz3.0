package com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case

import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Reactively observes [AppShellState] updates driven by [UserStats] changes.
 *
 * For each new [UserStats] emission from [UserStatsRepository.observeStats], produces
 * an updated [AppShellState] with only [AppShellState.userStats] replaced.
 *
 * Navigation state (activeTab, activeSection, child stacks, isDrawerOpen) is preserved
 * across emissions — Business Rule #8 (navigation state preserved on stats update).
 *
 * Visibility re-check (which drawer sections are unlocked) happens automatically in the
 * UI/presentation layer by calling `visibleSections(tab, newStats)` on each emission.
 */
class ObserveAppShellStateUseCase(
    private val userStatsRepository: UserStatsRepository,
) {
    /**
     * Returns a cold [Flow] that emits an updated [AppShellState] for every new [UserStats]
     * emission. Navigation state is read from [currentStateProvider] at each emission
     * to prevent stale closure (ADR-LEAD-02, ADR-COMP-01).
     *
     * @param currentStateProvider Lambda returning the current navigation state at call time.
     *        Typically `{ _state.value }` from DefaultRootComponent.
     */
    operator fun invoke(currentStateProvider: () -> AppShellState): Flow<AppShellState> =
        userStatsRepository.observeStats()
            .map { stats -> currentStateProvider().copy(userStats = stats) }
}
