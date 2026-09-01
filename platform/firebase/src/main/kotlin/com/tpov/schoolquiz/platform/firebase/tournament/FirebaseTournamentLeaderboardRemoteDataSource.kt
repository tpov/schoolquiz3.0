package com.tpov.schoolquiz.platform.firebase.tournament

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.remote.TournamentLeaderboardRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.remote.TournamentMetadataDto
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.remote.TournamentOverviewDto
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.remote.TournamentParticipantDto
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.remote.TournamentStandingDto
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.remote.TournamentSummaryDto
import kotlinx.coroutines.tasks.await

class FirebaseTournamentLeaderboardRemoteDataSource(
    private val functions: FirebaseFunctions,
) : TournamentLeaderboardRemoteDataSource {
    override suspend fun fetchOverview(
        tournamentId: String,
        limit: Int,
    ): TournamentOverviewDto {
        val data =
            functions
                .getHttpsCallable(FETCH_TOURNAMENT_OVERVIEW)
                .withAppTimeout()
                .call(
                    mapOf(
                        TOURNAMENT_ID to tournamentId,
                        LIMIT to limit,
                    ),
                )
                .await()
                .data as? Map<*, *>
        return data.toTournamentOverview()
    }

    private fun Map<*, *>?.toTournamentOverview(): TournamentOverviewDto {
        val leaderboard = mapList(LEADERBOARD).map { it.toStanding() }
        val participants = mapList(PARTICIPANTS).map { it.toParticipant() }
        return TournamentOverviewDto(
            tournament = map(TOURNAMENT).toSummary(),
            metadata = mapOrNull(METADATA)?.toMetadata(),
            leaderboard = leaderboard,
            participants = participants,
            currentUserEntry = mapOrNull(CURRENT_USER_ENTRY)?.toStanding(),
            currentUserParticipant = mapOrNull(CURRENT_USER_PARTICIPANT)?.toParticipant(),
        )
    }

    private fun Map<*, *>.toSummary(): TournamentSummaryDto =
        TournamentSummaryDto(
            id = string(ID) ?: "",
            sourceShelf = string(SOURCE_SHELF) ?: "",
            title = string(TITLE) ?: "",
            stageLabel = string(STAGE_LABEL) ?: "",
            updatedAtMs = long(UPDATED_AT_MS),
            leaderboardUpdatedAtMs = long(LEADERBOARD_UPDATED_AT_MS),
        )

    private fun Map<*, *>.toMetadata(): TournamentMetadataDto =
        TournamentMetadataDto(
            algorithmVersion = string(ALGORITHM_VERSION) ?: "",
            playersCount = int(PLAYERS_COUNT),
            groupsCount = int(GROUPS_COUNT),
            countedGroups = int(COUNTED_GROUPS),
            comparisonCount = int(COMPARISON_COUNT),
            isFullyConnected = boolean(IS_FULLY_CONNECTED),
            updatedAtMs = long(UPDATED_AT_MS),
        )

    private fun Map<*, *>.toStanding(): TournamentStandingDto =
        TournamentStandingDto(
            userId = string(USER_ID) ?: "",
            nickname = string(NICKNAME) ?: "User",
            avatarUrl = string(AVATAR_URL),
            place = int(PLACE),
            ratingPercent = double(RATING_PERCENT),
            ratingPoints = int(RATING_POINTS),
            averagePercent = double(AVERAGE_PERCENT),
            groupsPlayed = int(GROUPS_PLAYED),
            comparisons = int(COMPARISONS),
            uniqueOpponents = int(UNIQUE_OPPONENTS),
            componentId = int(COMPONENT_ID),
            componentSize = int(COMPONENT_SIZE),
            confidence = double(CONFIDENCE),
            updatedAtMs = long(UPDATED_AT_MS),
        )

    private fun Map<*, *>.toParticipant(): TournamentParticipantDto =
        TournamentParticipantDto(
            userId = string(USER_ID) ?: "",
            nickname = string(NICKNAME) ?: "User",
            avatarUrl = string(AVATAR_URL),
            attemptCount = int(ATTEMPT_COUNT),
            lastAttemptAtMs = long(LAST_ATTEMPT_AT_MS),
            lastPercent = int(LAST_PERCENT),
            status = string(STATUS) ?: "active",
            updatedAtMs = long(UPDATED_AT_MS),
        )

    private fun Map<*, *>?.map(field: String): Map<*, *> = mapOrNull(field) ?: emptyMap<Any, Any>()

    private fun Map<*, *>?.mapOrNull(field: String): Map<*, *>? = this?.get(field) as? Map<*, *>

    private fun Map<*, *>?.mapList(field: String): List<Map<*, *>> =
        (this?.get(field) as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }

    private fun Map<*, *>?.string(field: String): String? = this?.get(field)?.toString()?.takeIf { it.isNotBlank() }

    private fun Map<*, *>?.boolean(field: String): Boolean = this?.get(field) as? Boolean ?: false

    private fun Map<*, *>?.int(field: String): Int = long(field).toInt()

    private fun Map<*, *>?.long(field: String): Long =
        when (val value = this?.get(field)) {
            is Long -> value
            is Int -> value.toLong()
            is Number -> value.toLong()
            else -> 0L
        }

    private fun Map<*, *>?.double(field: String): Double =
        when (val value = this?.get(field)) {
            is Double -> value
            is Float -> value.toDouble()
            is Number -> value.toDouble()
            else -> 0.0
        }

    private companion object {
        const val FETCH_TOURNAMENT_OVERVIEW = "fetchTournamentOverview"
        const val TOURNAMENT_ID = "tournamentId"
        const val LIMIT = "limit"
        const val TOURNAMENT = "tournament"
        const val METADATA = "metadata"
        const val LEADERBOARD = "leaderboard"
        const val PARTICIPANTS = "participants"
        const val CURRENT_USER_ENTRY = "currentUserEntry"
        const val CURRENT_USER_PARTICIPANT = "currentUserParticipant"
        const val ID = "id"
        const val SOURCE_SHELF = "sourceShelf"
        const val TITLE = "title"
        const val STAGE_LABEL = "stageLabel"
        const val UPDATED_AT_MS = "updatedAtMs"
        const val LEADERBOARD_UPDATED_AT_MS = "leaderboardUpdatedAtMs"
        const val ALGORITHM_VERSION = "algorithmVersion"
        const val PLAYERS_COUNT = "playersCount"
        const val GROUPS_COUNT = "groupsCount"
        const val COUNTED_GROUPS = "countedGroups"
        const val COMPARISON_COUNT = "comparisonCount"
        const val IS_FULLY_CONNECTED = "isFullyConnected"
        const val USER_ID = "userId"
        const val NICKNAME = "nickname"
        const val AVATAR_URL = "avatarUrl"
        const val PLACE = "place"
        const val RATING_PERCENT = "ratingPercent"
        const val RATING_POINTS = "ratingPoints"
        const val AVERAGE_PERCENT = "averagePercent"
        const val GROUPS_PLAYED = "groupsPlayed"
        const val COMPARISONS = "comparisons"
        const val UNIQUE_OPPONENTS = "uniqueOpponents"
        const val COMPONENT_ID = "componentId"
        const val COMPONENT_SIZE = "componentSize"
        const val CONFIDENCE = "confidence"
        const val ATTEMPT_COUNT = "attemptCount"
        const val LAST_ATTEMPT_AT_MS = "lastAttemptAtMs"
        const val LAST_PERCENT = "lastPercent"
        const val STATUS = "status"
    }
}
