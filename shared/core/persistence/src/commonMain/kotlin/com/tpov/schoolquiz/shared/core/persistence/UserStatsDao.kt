package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserStatsDao {

    @Query("SELECT * FROM user_stats WHERE uid = :uid")
    fun observeByUid(uid: String): Flow<UserStatsEntity?>

    @Query("SELECT * FROM user_stats WHERE uid = :uid")
    suspend fun findByUid(uid: String): UserStatsEntity?

    @Upsert
    suspend fun upsert(entity: UserStatsEntity)

    @Query("UPDATE user_stats SET developerLevel = :value WHERE uid = :uid")
    suspend fun updateDeveloperLevel(uid: String, value: Int)
}
