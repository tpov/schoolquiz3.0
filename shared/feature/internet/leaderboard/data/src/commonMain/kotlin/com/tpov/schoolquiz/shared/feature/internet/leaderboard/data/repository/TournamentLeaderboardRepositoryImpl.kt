package com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.repository

import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.mapper.toDomain
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.remote.TournamentLeaderboardRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.model.TournamentOverview
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.repository.TournamentLeaderboardRepository
import kotlinx.coroutines.CancellationException

class TournamentLeaderboardRepositoryImpl(
    private val remote: TournamentLeaderboardRemoteDataSource,
) : TournamentLeaderboardRepository {
    override suspend fun fetchOverview(
        tournamentId: String,
        limit: Int,
    ): Result<TournamentOverview> =
        try {
            Result.success(remote.fetchOverview(tournamentId, limit).toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}
