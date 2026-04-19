package com.tpov.schoolquiz.shared.feature.app_shell.data.fake

import com.tpov.schoolquiz.shared.core.stats.RawUserStats
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/**
 * In-memory fake for [UserStatsDataSource].
 * Used in data-layer JVM tests.
 * NOT the same as FakeUserStatsRepository (domain layer fake in domain/commonTest/fake/).
 */
class FakeUserStatsDataSource(
    initialRaw: RawUserStats = RawUserStats(),
) : UserStatsDataSource {

    private val _raw = MutableStateFlow(initialRaw)

    var fetchRawResult: RawUserStats = initialRaw
    var fetchRawCallCount = 0

    /** When set, [observeRaw] returns a flow that throws this exception on collection. */
    var observeRawShouldThrow: Exception? = null

    /** When set, [fetchRaw] throws this exception instead of returning [fetchRawResult]. */
    var fetchRawShouldThrow: Exception? = null

    override fun observeRaw(): Flow<RawUserStats> {
        observeRawShouldThrow?.let { ex ->
            return flow { throw ex }
        }
        return _raw
    }

    override suspend fun fetchRaw(): RawUserStats {
        fetchRawCallCount++
        fetchRawShouldThrow?.let { throw it }
        return fetchRawResult
    }

    fun emit(raw: RawUserStats) {
        _raw.value = raw
    }
}
