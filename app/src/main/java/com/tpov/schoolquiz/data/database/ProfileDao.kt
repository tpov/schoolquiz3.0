package com.tpov.schoolquiz.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertProfile(profile: ProfileEntity)

    @Query("SELECT * FROM profiles")
    fun getProfileFlow(): Flow<ProfileEntity>

    @Query("SELECT * FROM profiles")
    fun getProfile(): ProfileEntity

    @Query("SELECT * FROM profiles WHERE tpovId LIKE :tpovId")
    fun getProfileByTpovId(tpovId: Int): ProfileEntity

    @Query("SELECT * FROM profiles")
    fun getAllProfilesList(): ProfileEntity

    @Query("SELECT * FROM profiles WHERE idFirebase = :id")
    fun getProfileByFirebaseId(id: String): ProfileEntity

    @Query("SELECT * FROM profiles")
    fun getAllProfiles(): List<ProfileEntity>

    @Update
    fun updateProfiles(profileEntity: ProfileEntity): Int

    @Query("SELECT COUNT(*) FROM profiles")
    fun getProfileCount(): Int
}