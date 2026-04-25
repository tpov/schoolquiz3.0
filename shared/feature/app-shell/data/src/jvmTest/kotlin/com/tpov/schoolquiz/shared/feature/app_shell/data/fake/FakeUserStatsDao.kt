package com.tpov.schoolquiz.shared.feature.app_shell.data.fake

import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

// 04-testing.md §4.1 canonical FakeUserStatsDao
class FakeUserStatsDao : UserStatsDao {
    private val _flow = MutableStateFlow<UserStatsEntity?>(null)
    var lastUpserted: UserStatsEntity? = null
    var updateDeveloperLevelCalls: Int = 0

    override fun observeByUid(uid: String): Flow<UserStatsEntity?> = _flow

    override suspend fun findByUid(uid: String): UserStatsEntity? = _flow.value

    override suspend fun upsert(entity: UserStatsEntity) {
        lastUpserted = entity
        _flow.value = entity
    }

    override suspend fun updateDeveloperLevel(uid: String, value: Int) {
        updateDeveloperLevelCalls++
        _flow.value = _flow.value?.copy(developerLevel = value)
    }

    fun emit(entity: UserStatsEntity?) {
        _flow.value = entity
    }
}
