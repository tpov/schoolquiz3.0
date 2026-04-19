package com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case

import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState

/**
 * Initialises the AppShellState for cold-start.
 *
 * Fetches the current [UserStats] snapshot from [userStatsRepository] and builds
 * the initial [AppShellState] using [AppShellState.default].
 *
 * Business Rule #19: default section per tab is determined by visibility.
 * The [AppShellState.default] factory encodes the current resolution (see OQ-1 open question).
 *
 * Use case is thin: fetch stats → build default state. All business rules live in domain core.
 */
class InitializeAppShellUseCase(
    private val userStatsRepository: UserStatsRepository,
) {
    suspend operator fun invoke(): AppShellState {
        val stats = userStatsRepository.currentStats()
        return AppShellState.default(stats)
    }
}
