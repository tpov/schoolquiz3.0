package com.tpov.schoolquiz.shared.feature.app_shell.domain.fake

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake implementation of [UserStatsRepository] for use case testing.
 *
 * Backed by [MutableStateFlow] — tests can push new [UserStats] values and verify
 * that the [ObserveAppShellStateUseCase] emits the corresponding updated states.
 *
 * This is a full fake (not a mock): it implements the interface contract completely
 * without any test framework dependencies.
 */
class FakeUserStatsRepository(
    initialStats: UserStats = UserStats.guest(),
) : UserStatsRepository {

    private val _stats = MutableStateFlow(initialStats)

    override fun observeStats(): Flow<UserStats> = _stats.asStateFlow()

    override suspend fun currentStats(): UserStats = _stats.value

    // ----- Test helpers (not part of the repository interface) -----

    /**
     * Pushes a new [UserStats] value into the backing [MutableStateFlow].
     * All active collectors of [observeStats()] will receive the new value.
     */
    fun emit(stats: UserStats) {
        _stats.value = stats
    }

    /**
     * Returns the currently stored [UserStats] snapshot.
     * Useful for asserting the final state without collecting the flow.
     */
    fun currentEmitted(): UserStats = _stats.value
}
