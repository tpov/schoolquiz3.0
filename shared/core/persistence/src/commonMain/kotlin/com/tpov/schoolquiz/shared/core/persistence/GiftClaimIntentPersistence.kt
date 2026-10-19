package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Одна незавершённая заявка подарка на пару «владелец + вид» (epic-commerce 7.4).
 *
 * Прямой вызываемый, а не строка очереди: сервер держит собственный забор
 * `box_operations/{sha256(operationId)}`, поэтому дисциплина воспроизводится локально —
 * намерение (operationId + тело) лежит здесь ДО запроса и до самого ответа. Умер процесс —
 * строка пережила его, и повтор уезжает с тем же operationId и тем же телом; исход любого рода —
 * даже DAILY_LIMIT — строку разрешает.
 */
@Entity(tableName = "gift_claim_intents", primaryKeys = ["owner_uid", "kind"])
data class GiftClaimIntentEntity(
    @ColumnInfo(name = "owner_uid") val ownerUid: String,
    val kind: String,
    @ColumnInfo(name = "operation_id") val operationId: String,
    /** Тело вызываемого ровно в том виде, в каком оно уедет повтором: JSON. */
    val payload: String,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
)

@Dao
interface GiftClaimIntentDao {
    @Query("SELECT * FROM gift_claim_intents WHERE owner_uid = :ownerUid AND kind = :kind")
    suspend fun find(ownerUid: String, kind: String): GiftClaimIntentEntity?

    @Query("SELECT * FROM gift_claim_intents WHERE owner_uid = :ownerUid AND kind = :kind")
    fun observe(ownerUid: String, kind: String): Flow<GiftClaimIntentEntity?>

    /** Вторая заявка того же вида не создаёт вторую строку: возвращает -1, если место занято. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: GiftClaimIntentEntity): Long

    /** Разрешает строку только по своему operationId: чужая или обновлённая не трогается. */
    @Query(
        "DELETE FROM gift_claim_intents WHERE owner_uid = :ownerUid AND kind = :kind " +
            "AND operation_id = :operationId",
    )
    suspend fun resolve(ownerUid: String, kind: String, operationId: String): Int
}
