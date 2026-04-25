package com.tpov.schoolquiz.shared.feature.app_shell.data

import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import com.tpov.schoolquiz.shared.core.sync.Syncable
import com.tpov.schoolquiz.shared.feature.app_shell.data.mapper.toDomain
import com.tpov.schoolquiz.shared.feature.app_shell.data.mapper.toEntity
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class UserStatsRepositoryImpl(
    private val remoteDataSource: UserStatsDataSource,
    private val userStatsDao: UserStatsDao,
    private val currentUidFlow: () -> Flow<String?>,
) : UserStatsRepository, Syncable {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeStats(): Flow<UserStats> =
        currentUidFlow().flatMapLatest { uid ->
            if (uid == null) flowOf(UserStats.guest())
            else userStatsDao.observeByUid(uid).map { it?.toDomain() ?: UserStats.guest() }
        }

    override suspend fun currentStats(): UserStats {
        val uid = currentUidFlow().first() ?: return UserStats.guest()
        return userStatsDao.findByUid(uid)?.toDomain() ?: UserStats.guest()
    }

    override suspend fun setLocalDeveloperLevel(value: Int) {
        require(value >= 0) { "developerLevel must be non-negative, was: $value" }
        val uid = currentUidFlow().first() ?: return
        if (userStatsDao.findByUid(uid) == null) return
        userStatsDao.updateDeveloperLevel(uid, value)
    }

    override suspend fun refreshProfile(): Result<Unit> {
        val uid = currentUidFlow().first() ?: return Result.failure(IllegalStateException("Not authenticated"))
        return try {
            val raw = remoteDataSource.fetchRaw()
            userStatsDao.upsert(raw.toEntity(uid))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sync(): Result<Unit> {
        if (currentUidFlow().first() == null) return Result.success(Unit)
        return refreshProfile()
    }
}
