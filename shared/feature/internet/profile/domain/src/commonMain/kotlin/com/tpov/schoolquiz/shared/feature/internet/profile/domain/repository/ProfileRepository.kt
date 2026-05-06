package com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeCurrentProfile(): Flow<UserProfile>

    suspend fun currentProfile(): UserProfile

    suspend fun ensureCurrentProfile(): Result<UserProfile>

    suspend fun updateNickname(nickname: String): Result<UserProfile>
}
