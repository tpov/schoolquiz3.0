package com.tpov.common.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.tpov.common.data.model.local.StructureCategoryDataEntity
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

@Dao
interface StructureCategoryDataDao {
    @Insert
    suspend fun insert(categoryData: StructureCategoryDataEntity)

    @Query("SELECT * FROM structure_category_data")
    suspend fun getAllFailedCategory(): List<StructureCategoryDataEntity>
    @Delete
    @Query("DELETE FROM structure_category_data WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: Int)

    @Query("DELETE FROM structure_category_data")
    suspend fun clearFailedCategory()
}