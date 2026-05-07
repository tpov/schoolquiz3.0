package com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.mapper

import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.remote.TournamentMetadataDto
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.remote.TournamentOverviewDto
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.remote.TournamentParticipantDto
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.remote.TournamentStandingDto
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.remote.TournamentSummaryDto
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.model.TournamentMetadata
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.model.TournamentOverview
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.model.TournamentParticipant
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.model.TournamentStanding
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.model.TournamentSummary

fun TournamentOverviewDto.toDomain(): TournamentOverview =
    TournamentOverview(
        tournament = tournament.toDomain(),
        metadata = metadata?.toDomain(),
        leaderboard = leaderboard.map { it.toDomain() },
        participants = participants.map { it.toDomain() },
        currentUserEntry = currentUserEntry?.toDomain(),
        currentUserParticipant = currentUserParticipant?.toDomain(),
    )

private fun TournamentSummaryDto.toDomain(): TournamentSummary =
    TournamentSummary(
        id = id,
        sourceShelf = sourceShelf,
        title = title,
        stageLabel = stageLabel,
        updatedAtMs = updatedAtMs,
        leaderboardUpdatedAtMs = leaderboardUpdatedAtMs,
    )

private fun TournamentMetadataDto.toDomain(): TournamentMetadata =
    TournamentMetadata(
        algorithmVersion = algorithmVersion,
        playersCount = playersCount,
        groupsCount = groupsCount,
        countedGroups = countedGroups,
        comparisonCount = comparisonCount,
        isFullyConnected = isFullyConnected,
        updatedAtMs = updatedAtMs,
    )

private fun TournamentStandingDto.toDomain(): TournamentStanding =
    TournamentStanding(
        userId = userId,
        nickname = nickname,
        avatarUrl = avatarUrl,
        place = place,
        ratingPercent = ratingPercent,
        ratingPoints = ratingPoints,
        averagePercent = averagePercent,
        groupsPlayed = groupsPlayed,
        comparisons = comparisons,
        uniqueOpponents = uniqueOpponents,
        componentId = componentId,
        componentSize = componentSize,
        confidence = confidence,
        updatedAtMs = updatedAtMs,
    )

private fun TournamentParticipantDto.toDomain(): TournamentParticipant =
    TournamentParticipant(
        userId = userId,
        nickname = nickname,
        avatarUrl = avatarUrl,
        attemptCount = attemptCount,
        lastAttemptAtMs = lastAttemptAtMs,
        lastPercent = lastPercent,
        status = status,
        updatedAtMs = updatedAtMs,
    )
