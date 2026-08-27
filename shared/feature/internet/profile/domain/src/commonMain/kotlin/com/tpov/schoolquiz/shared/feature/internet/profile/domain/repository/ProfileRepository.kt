package com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.LeagueStanding
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeCurrentProfile(): Flow<UserProfile>

    suspend fun currentProfile(): UserProfile

    suspend fun ensureCurrentProfile(): Result<UserProfile>

    /**
     * This account's place among all players, or null when it cannot be worked out.
     *
     * Null rather than a zero standing: "we could not ask" and "you are last" are different
     * answers, and a screen that shows the second when it means the first is lying.
     */
    suspend fun leagueStanding(): LeagueStanding? = null

    suspend fun updateNickname(nickname: String): Result<UserProfile>
}
