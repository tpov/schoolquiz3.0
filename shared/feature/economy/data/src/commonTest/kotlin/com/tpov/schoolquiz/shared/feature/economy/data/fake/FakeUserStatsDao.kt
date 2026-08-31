package com.tpov.schoolquiz.shared.feature.economy.data.fake

import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory stand-in for the stats table.
 *
 * The canonical fake lives in app-shell's jvmTest, which this module cannot see; the two are kept
 * deliberately alike so a reader moving between them is not surprised.
 */
class FakeUserStatsDao(seed: UserStatsEntity? = null) : UserStatsDao {
    private val stored = MutableStateFlow(seed)

    override fun observeByUid(uid: String): Flow<UserStatsEntity?> = stored

    override suspend fun findByUid(uid: String): UserStatsEntity? = stored.value

    override suspend fun upsert(entity: UserStatsEntity) {
        stored.value = entity
    }

    override suspend fun updateDeveloperLevel(uid: String, value: Int) {
        stored.value = stored.value?.copy(developerLevel = value)
    }

    fun current(): UserStatsEntity? = stored.value
}
