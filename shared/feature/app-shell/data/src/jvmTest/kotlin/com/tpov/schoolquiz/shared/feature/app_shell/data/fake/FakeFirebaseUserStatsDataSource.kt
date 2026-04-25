package com.tpov.schoolquiz.shared.feature.app_shell.data.fake

import com.tpov.schoolquiz.shared.core.stats.RawUserStats
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

// Standalone fake matching 04-testing.md §4.4.
// Implements UserStatsDataSource so it can be passed as remoteDataSource constructor param.
// fetchCalls tracks invocations of fetchRaw() (the interface method used by refreshProfile()).
// NOTE: if backend-dev adds fetchOnce(uid: String) to UserStatsDataSource, rename
// the override below from fetchRaw() to fetchOnce(uid: String) and update tests accordingly.
class FakeFirebaseUserStatsDataSource : UserStatsDataSource {
    var result: Result<RawUserStats> = Result.success(RawUserStats())
    var fetchCalls: Int = 0

    override fun observeRaw(): Flow<RawUserStats> = emptyFlow()

    override suspend fun fetchRaw(): RawUserStats {
        fetchCalls++
        return result.getOrThrow()
    }
}
