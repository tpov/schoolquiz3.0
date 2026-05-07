package com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.use_case

import com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.model.TournamentOverview
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.repository.TournamentLeaderboardRepository

class FetchTournamentOverviewUseCase(
    private val repository: TournamentLeaderboardRepository,
) {
    suspend operator fun invoke(
        tournamentId: String,
        limit: Int = TournamentLeaderboardRepository.DEFAULT_LIMIT,
    ): Result<TournamentOverview> = repository.fetchOverview(tournamentId, limit)
}
