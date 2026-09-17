package com.tpov.schoolquiz.shared.feature.app_shell.data.fake

import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

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

    // Половина очереди: этому фейку она не нужна — профиль в неё не пишет и не читает её сам,
    // число неотправленных трат приходит к нему портом. Пусто, а не заглушка с поведением,
    // чтобы тест, случайно понадеявшийся на очередь здесь, упал, а не тихо прошёл.
    override suspend fun enqueueOutboxRow(entity: OutboxEntity): Long = error("очередь этому фейку не нужна")

    override suspend fun outboxRowState(mutationId: String): String? = null

    override suspend fun deleteOutboxRow(mutationId: String): Unit = error("очередь этому фейку не нужна")

    override fun observeQuarantinedEntityRefs(
        ownerUid: String,
        operation: String,
    ): Flow<List<String>> = flowOf(emptyList())

    override suspend fun undeliveredPayloads(
        ownerUid: String,
        operation: String,
    ): List<String> = emptyList()

    fun emit(entity: UserStatsEntity?) {
        _flow.value = entity
    }
}
