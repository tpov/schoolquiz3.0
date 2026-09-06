package com.tpov.schoolquiz.shared.feature.lesson.data.fake

import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Профиль и очередь в памяти — ровно те примитивы, поверх которых написаны транзакции DAO.
 *
 * Тела `unlockLessonWithOutbox` и `rollbackLessonUnlock` наследуются от самого DAO, поэтому здесь
 * проверяется настоящая логика, а не её копия. Чего фейк не изображает — атомарности: откат
 * транзакции при исключении внутри неё проверяется против настоящего SQLite в
 * `shared/core/persistence` (`LessonUnlockLocalHalfTest`).
 */
class FakeUserStatsDao(
    seed: UserStatsEntity? = null,
) : UserStatsDao {

    private val rows = MutableStateFlow(listOfNotNull(seed))
    private val outbox = MutableStateFlow<List<OutboxEntity>>(emptyList())
    private var nextId = 1L

    val outboxRows: List<OutboxEntity> get() = outbox.value

    fun statsOf(uid: String): UserStatsEntity? = rows.value.firstOrNull { it.uid == uid }

    /** Переводит запись в состояние, которое назначил бы движок. */
    fun setOutboxState(
        mutationId: String,
        state: String,
    ) {
        outbox.value = outbox.value.map { if (it.mutationId == mutationId) it.copy(state = state) else it }
    }

    override fun observeByUid(uid: String): Flow<UserStatsEntity?> =
        rows.map { list -> list.firstOrNull { it.uid == uid } }

    override suspend fun findByUid(uid: String): UserStatsEntity? = statsOf(uid)

    override suspend fun upsert(entity: UserStatsEntity) {
        rows.value = rows.value.filterNot { it.uid == entity.uid } + entity
    }

    override suspend fun updateDeveloperLevel(
        uid: String,
        value: Int,
    ) {
        rows.value = rows.value.map { if (it.uid == uid) it.copy(developerLevel = value) else it }
    }

    /** −1 на занятый ключ — так же, как отвечает подавленная `IGNORE`-вставка в SQLite. */
    override suspend fun enqueueOutboxRow(entity: OutboxEntity): Long {
        if (outbox.value.any { it.mutationId == entity.mutationId }) return -1L
        val id = nextId++
        outbox.value = outbox.value + entity.copy(id = id)
        return id
    }

    override suspend fun outboxRowState(mutationId: String): String? =
        outbox.value.firstOrNull { it.mutationId == mutationId }?.state

    override suspend fun deleteOutboxRow(mutationId: String) {
        outbox.value = outbox.value.filterNot { it.mutationId == mutationId }
    }

    override fun observeQuarantinedEntityRefs(
        ownerUid: String,
        operation: String,
    ): Flow<List<String>> =
        combine(outbox, rows) { queue, _ -> queue }
            .map { queue ->
                queue
                    .filter { it.ownerUid == ownerUid && it.operation == operation && it.state == "QUARANTINED" }
                    .mapNotNull { it.entityRef }
            }
    /**
     * Неотправленные тела этой операции — всё, кроме карантинных: их локальная половина уже
     * откачена, и вычитать её второй раз значило бы отнять у игрока возвращённое.
     */
    override suspend fun undeliveredPayloads(
        ownerUid: String,
        operation: String,
    ): List<String> =
        outbox.value
            .filter { it.ownerUid == ownerUid && it.operation == operation && it.state != "QUARANTINED" }
            .map { it.payload }
}
