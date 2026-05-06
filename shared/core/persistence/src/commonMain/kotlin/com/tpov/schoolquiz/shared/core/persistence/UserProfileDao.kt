package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE uid = :uid")
    fun observeByUid(uid: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE uid = :uid")
    suspend fun findByUid(uid: String): UserProfileEntity?

    @Upsert
    suspend fun upsert(entity: UserProfileEntity)
}
