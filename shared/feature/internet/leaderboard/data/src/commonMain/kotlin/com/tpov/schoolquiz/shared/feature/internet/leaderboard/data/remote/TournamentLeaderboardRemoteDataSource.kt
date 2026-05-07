package com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.remote

data class TournamentOverviewDto(
    val tournament: TournamentSummaryDto,
    val metadata: TournamentMetadataDto?,
    val leaderboard: List<TournamentStandingDto>,
    val participants: List<TournamentParticipantDto>,
    val currentUserEntry: TournamentStandingDto?,
    val currentUserParticipant: TournamentParticipantDto?,
)

data class TournamentSummaryDto(
    val id: String,
    val sourceShelf: String,
    val title: String,
    val stageLabel: String,
    val updatedAtMs: Long,
    val leaderboardUpdatedAtMs: Long,
)

data class TournamentMetadataDto(
    val algorithmVersion: String,
    val playersCount: Int,
    val groupsCount: Int,
    val countedGroups: Int,
    val comparisonCount: Int,
    val isFullyConnected: Boolean,
    val updatedAtMs: Long,
)

data class TournamentStandingDto(
    val userId: String,
    val nickname: String,
    val avatarUrl: String?,
    val place: Int,
    val ratingPercent: Double,
    val ratingPoints: Int,
    val averagePercent: Double,
    val groupsPlayed: Int,
    val comparisons: Int,
    val uniqueOpponents: Int,
    val componentId: Int,
    val componentSize: Int,
    val confidence: Double,
    val updatedAtMs: Long,
)

data class TournamentParticipantDto(
    val userId: String,
    val nickname: String,
    val avatarUrl: String?,
    val attemptCount: Int,
    val lastAttemptAtMs: Long,
    val lastPercent: Int,
    val status: String,
    val updatedAtMs: Long,
)

interface TournamentLeaderboardRemoteDataSource {
    suspend fun fetchOverview(
        tournamentId: String,
        limit: Int,
    ): TournamentOverviewDto
}
