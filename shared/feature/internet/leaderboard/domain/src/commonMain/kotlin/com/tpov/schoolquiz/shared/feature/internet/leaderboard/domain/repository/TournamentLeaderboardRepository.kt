package com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.repository

import com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.model.TournamentOverview

interface TournamentLeaderboardRepository {
    suspend fun fetchOverview(
        tournamentId: String,
        limit: Int = DEFAULT_LIMIT,
    ): Result<TournamentOverview>

    companion object {
        const val DEFAULT_LIMIT = 50
    }
}
