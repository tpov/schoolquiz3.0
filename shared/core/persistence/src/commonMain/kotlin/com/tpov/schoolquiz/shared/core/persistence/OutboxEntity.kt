package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Одна таблица отложенных действий на все типы (AD-5).
 *
 * Заменяет три очереди, каждая со своей формой: результаты урока, оценки квестов и заявки на
 * арену переезжают сюда. Новый тип действия не требует правки схемы — тип лежит строкой в
 * [operation], тело в [payload], и ядро в него не смотрит.
 *
 * Уникальный индекс по [mutationId] — не украшение: ключ рождается вместе с намерением игрока и
 * должен быть один на одно действие (AD-2), иначе повторная постановка в очередь создаст вторую
 * операцию с тем же смыслом и разными ключами.
 */
@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["mutation_id"], unique = true),
        Index(value = ["owner_uid", "state", "next_retry_at_ms"]),
    ],
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "mutation_id") val mutationId: String,
    @ColumnInfo(name = "owner_uid") val ownerUid: String,
    @ColumnInfo(name = "operation") val operation: String,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "entity_ref") val entityRef: String?,
    @ColumnInfo(name = "expected_version") val expectedVersion: Long?,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
    @ColumnInfo(name = "next_retry_at_ms") val nextRetryAtMs: Long,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
)
