package com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.model

data class TournamentOverview(
    val tournament: TournamentSummary,
    val metadata: TournamentMetadata?,
    val leaderboard: List<TournamentStanding>,
    val participants: List<TournamentParticipant>,
    val currentUserEntry: TournamentStanding?,
    val currentUserParticipant: TournamentParticipant?,
)

data class TournamentSummary(
    val id: String,
    val sourceShelf: String,
    val title: String,
    val stageLabel: String,
    val updatedAtMs: Long,
    val leaderboardUpdatedAtMs: Long,
)

data class TournamentMetadata(
    val algorithmVersion: String,
    val playersCount: Int,
    val groupsCount: Int,
    val countedGroups: Int,
    val comparisonCount: Int,
    val isFullyConnected: Boolean,
    val updatedAtMs: Long,
)

data class TournamentStanding(
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

data class TournamentParticipant(
    val userId: String,
    val nickname: String,
    val avatarUrl: String?,
    val attemptCount: Int,
    val lastAttemptAtMs: Long,
    val lastPercent: Int,
    val status: String,
    val updatedAtMs: Long,
)
