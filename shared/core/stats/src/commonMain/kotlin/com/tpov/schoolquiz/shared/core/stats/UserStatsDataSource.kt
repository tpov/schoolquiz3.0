package com.tpov.schoolquiz.shared.core.stats

import kotlinx.coroutines.flow.Flow

interface UserStatsDataSource {
    fun observeRaw(): Flow<RawUserStats>

    suspend fun fetchRaw(): RawUserStats
}
