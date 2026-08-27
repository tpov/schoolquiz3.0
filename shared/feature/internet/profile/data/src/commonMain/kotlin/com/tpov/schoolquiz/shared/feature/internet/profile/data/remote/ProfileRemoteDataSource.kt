package com.tpov.schoolquiz.shared.feature.internet.profile.data.remote

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.LeagueStanding
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile

interface ProfileRemoteDataSource {
    suspend fun ensureProfile(request: ProfileBootstrapRequest): UserProfile

    suspend fun updateNickname(
        nickname: String,
        knownLanguages: List<String>,
    ): UserProfile

    suspend fun leagueStanding(): LeagueStanding
}
