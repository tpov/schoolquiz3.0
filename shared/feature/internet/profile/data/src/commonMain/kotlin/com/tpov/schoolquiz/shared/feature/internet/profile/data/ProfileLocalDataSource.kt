package com.tpov.schoolquiz.shared.feature.internet.profile.data

import com.tpov.schoolquiz.shared.core.persistence.UserProfileDao
import com.tpov.schoolquiz.shared.feature.internet.profile.data.mapper.toDomain
import com.tpov.schoolquiz.shared.feature.internet.profile.data.mapper.toEntity
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ProfileLocalDataSource {
    fun observe(uid: String): Flow<UserProfile?>

    suspend fun find(uid: String): UserProfile?

    suspend fun upsert(profile: UserProfile)
}

class RoomProfileLocalDataSource(
    private val dao: UserProfileDao,
) : ProfileLocalDataSource {
    override fun observe(uid: String): Flow<UserProfile?> =
        dao.observeByUid(uid).map { it?.toDomain() }

    override suspend fun find(uid: String): UserProfile? =
        dao.findByUid(uid)?.toDomain()

    override suspend fun upsert(profile: UserProfile) {
        dao.upsert(profile.toEntity())
    }
}
