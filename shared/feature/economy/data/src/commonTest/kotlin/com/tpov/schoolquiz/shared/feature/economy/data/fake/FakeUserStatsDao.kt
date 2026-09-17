package com.tpov.schoolquiz.shared.feature.economy.data.fake

import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

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

    // Половина очереди этому фейку не нужна: он про профиль, а не про отправку.
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
}
