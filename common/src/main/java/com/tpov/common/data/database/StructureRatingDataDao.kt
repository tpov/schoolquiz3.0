package com.tpov.common.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tpov.common.data.model.local.StructureRatingDataEntity

@Dao
interface StructureRatingDataDao {
    @Insert
    suspend fun insert(ratingData: StructureRatingDataEntity)

    @Query("SELECT * FROM structure_rating_data")
    suspend fun getAllFailedRatings(): List<StructureRatingDataEntity>

    @Query("DELETE FROM structure_rating_data")
    suspend fun clearFailedRatings()
}