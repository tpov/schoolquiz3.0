package com.tpov.schoolquiz.domain.repository

import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.fierbase.Profile
import kotlinx.coroutines.flow.Flow

interface RepositoryProfile {
    suspend fun getProfileFlow(): Flow<ProfileEntity?>?

    suspend fun fetchProfile(tpovId: Int): Profile?

    suspend fun pushProfile(profile: Profile)

    suspend fun getProfile(): ProfileEntity?

    suspend fun insertProfile(profile: ProfileEntity)

    suspend fun updateProfile(profile: ProfileEntity)
}
