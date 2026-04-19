package com.tpov.schoolquiz.android.feature.app_shell.presentation.fake

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeUserStatsRepository(
    initialStats: UserStats = UserStats.guest(),
) : UserStatsRepository {

    private val _stats = MutableStateFlow(initialStats)

    override fun observeStats(): Flow<UserStats> = _stats.asStateFlow()

    override suspend fun currentStats(): UserStats = _stats.value

    fun emit(stats: UserStats) {
        _stats.value = stats
    }
}
