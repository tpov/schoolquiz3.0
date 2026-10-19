package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * Местный счётчик серии между словами сервера.
 *
 * Серверное слово (`base_*`) — то, от которого счёт был посеян; расхождение баз означает, что
 * сервер сказал новое слово (синхронизация, второе устройство), и местный счёт пересеивается, а
 * не складывается с чужим. Без этой пары reconnect после визита второго устройства заявлял бы
 * поверх чужого слова.
 */
@Entity(tableName = "gift_box_local_streak")
data class GiftBoxLocalStreakEntity(
    @PrimaryKey @ColumnInfo(name = "owner_uid") val ownerUid: String,
    @ColumnInfo(name = "base_box_count") val baseBoxCount: Int,
    @ColumnInfo(name = "base_streak_days") val baseStreakDays: Int,
    @ColumnInfo(name = "base_next_box_at_ms") val baseNextBoxAtMs: Long,
    @ColumnInfo(name = "box_count") val boxCount: Int,
    @ColumnInfo(name = "streak_days") val streakDays: Int,
    @ColumnInfo(name = "next_box_at_ms") val nextBoxAtMs: Long,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
)

@Dao
interface GiftBoxLocalStreakDao {
    @Query("SELECT * FROM gift_box_local_streak WHERE owner_uid = :ownerUid")
    suspend fun find(ownerUid: String): GiftBoxLocalStreakEntity?

    @Upsert
    suspend fun upsert(entity: GiftBoxLocalStreakEntity)

    @Query("DELETE FROM gift_box_local_streak WHERE owner_uid = :ownerUid")
    suspend fun delete(ownerUid: String)
}
