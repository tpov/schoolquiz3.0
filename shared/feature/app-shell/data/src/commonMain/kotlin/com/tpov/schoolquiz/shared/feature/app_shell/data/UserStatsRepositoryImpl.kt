package com.tpov.schoolquiz.shared.feature.app_shell.data

import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import com.tpov.schoolquiz.shared.core.sync.Syncable
import com.tpov.schoolquiz.shared.feature.app_shell.data.mapper.toDomain
import com.tpov.schoolquiz.shared.feature.app_shell.data.mapper.toEntity
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.PendingLocalCharges
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
    /**
     * Локальные траты, о которых сервер ещё не знает. По умолчанию их нет — так собирается
     * репозиторий в тестах, которым до очереди дела нет.
     */
    private val pendingCharges: PendingLocalCharges = PendingLocalCharges { 0L },
) : UserStatsRepository, Syncable {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeStats(): Flow<UserStats> =
        currentUidFlow().flatMapLatest { uid ->
            if (uid == null) {
                flowOf(UserStats.guest())
            } else {
                userStatsDao.observeByUid(uid).map { it?.toDomain() ?: UserStats.guest() }
            }
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
            // The fetch was in flight while the player may have bought a lesson, and that purchase
            // already wrote the server's own answer into this row. Replacing the row wholesale
            // would roll it back — the charge stands on the server and the lesson shuts again on
            // the device. Unlocks are monotone, so keeping both sides is always correct.
            val held = userStatsDao.findByUid(uid)?.lessonUnlocks.orEmpty()
            val fresh = raw.toEntity(uid)
            // И то же самое с балансом, только вычитанием: снимок сервера не знает про покупку,
            // которая списана локально и ещё не доехала. Оставить его как есть значит вернуть
            // игроку уже потраченное — он потратит эти нолики второй раз, а откат карантина
            // добавит возврат к восстановленному балансу. Запись уехала — слагаемое исчезло само.
            val undelivered = runCatching { pendingCharges.undeliveredNolics(uid) }.getOrDefault(0L)
            userStatsDao.upsert(
                fresh.copy(
                    lessonUnlocks = held + fresh.lessonUnlocks,
                    nolics = (fresh.nolics - undelivered).coerceAtLeast(0L),
                ),
            )
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
