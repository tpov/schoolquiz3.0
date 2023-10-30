package com.tpov.schoolquiz.domain.repository

import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import kotlinx.coroutines.flow.Flow

interface RepositoryProfile {
    fun getProfileFlow(tpovId: Int): Flow<ProfileEntity>

    fun getProfile(tpovId: Int): ProfileEntity

    fun getProfileList(): List<ProfileEntity>

    fun insertProfile(profile: ProfileEntity)

    fun updateProfile(profile: ProfileEntity)

    fun unloadProfile()

    fun downloadProfile()
}