package com.tpov.schoolquiz.domain.repository

import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.fierbase.ProfileRemote
import kotlinx.coroutines.flow.Flow

interface RepositoryProfile {
    suspend fun getProfileFlow(): Flow<ProfileEntity?>?

    suspend fun fetchProfile(tpovId: Int): ProfileRemote?

    suspend fun pushProfile(profileRemote: ProfileRemote)

    suspend fun getProfile(): ProfileEntity

    suspend fun insertProfile(profile: ProfileEntity)

    suspend fun updateProfile(profile: ProfileEntity)
}
